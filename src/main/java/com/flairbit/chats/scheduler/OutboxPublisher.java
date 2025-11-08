package com.flairbit.chats.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flairbit.chats.dto.ChatMessageResponse;
import com.flairbit.chats.models.ChatMessageOutbox;
import com.flairbit.chats.repo.ChatMessageOutboxJDBCRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import lombok.RequiredArgsConstructor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final ChatMessageOutboxJDBCRepository outboxRepo;
    private final SimpMessagingTemplate messaging;
    private final ObjectMapper json;

    private final int parallelWorkers = Math.max(2, Runtime.getRuntime().availableProcessors());
    private final ExecutorService executor = Executors.newFixedThreadPool(parallelWorkers, runnable -> {
        Thread t = new Thread(runnable);
        t.setName("outbox-worker-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    private final AtomicLong processedCounter = new AtomicLong();
    private final AtomicLong failedCounter = new AtomicLong();

    /**
     * Scheduler: run frequently and fetch available work.
     * We claim rows using repository.claimPendingBatch(...) which performs
     * SELECT ... FOR UPDATE SKIP LOCKED and updates next_retry_at (so other workers won't take them).
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.fixedDelay:1000}")
    public void publish() {
        try {
            // claim window — how long we block other workers from taking claimed rows
            Instant claimUntil = Instant.now().plusSeconds(60);
            List<ChatMessageOutbox> batch;

            // tunables
            int batchSize = 500;
            do {
                batch = outboxRepo.claimPendingBatch(batchSize, claimUntil);
                if (batch.isEmpty()) break;

                List<CompletableFuture<Void>> futures = batch.stream()
                        .map(o -> CompletableFuture.runAsync(() -> processMessage(o), executor)
                                .exceptionally(ex -> {
                                    log.error("Processing failed for {} : {}", o.getId(), ex.getMessage());
                                    return null;
                                }))
                        .toList();

                // block until this batch completes (but processing happens in separate threads)
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            } while (batch.size() == batchSize);

        } catch (Exception e) {
            log.error("OutboxPublisher main loop failed: {}", e.getMessage(), e);
        }
    }

    private void processMessage(ChatMessageOutbox o) {
        try {
            // deserialize
            ChatMessageResponse payload = json.readValue(o.getPayload(), ChatMessageResponse.class);

            // send
            if (o.getDestination().startsWith("user:")) {
                // format: user:<userId>:<destination>
                String[] parts = o.getDestination().split(":", 3);
                if (parts.length == 3) {
                    String user = parts[1];
                    String dest = parts[2];
                    messaging.convertAndSendToUser(user, dest, payload);
                } else {
                    log.warn("Malformed user destination: {}", o.getDestination());
                    throw new IllegalArgumentException("Malformed user destination");
                }
            } else {
                messaging.convertAndSend(o.getDestination(), payload);
            }

            // success
            outboxRepo.markProcessed(o.getId(), o.getRetryCount());
            processedCounter.incrementAndGet();
        } catch (Exception e) {
            log.error("Outbox send failed for id {} : {}", o.getId(), e.getMessage());
            handleFailure(o);
        }
    }

    private void handleFailure(ChatMessageOutbox o) {
        int retries = o.getRetryCount() + 1;
        // exponential backoff: min(60s, 2^retries)
        long seconds = Math.min(60L, (long) Math.pow(2, Math.min(retries, 10)));
        Instant nextRetry = Instant.now().plusSeconds(seconds);
        outboxRepo.markRetry(o.getId(), retries, nextRetry);
        failedCounter.incrementAndGet();

        if (retries > 10) {
            log.error("Outbox message {} exceeded max retries ({}). Marked for manual inspection.", o.getId(), retries);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public long getProcessedCount() { return processedCounter.get(); }
    public long getFailedCount() { return failedCounter.get(); }
}
