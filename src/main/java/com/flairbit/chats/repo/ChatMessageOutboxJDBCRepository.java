package com.flairbit.chats.repo;

import com.flairbit.chats.models.ChatMessageOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ChatMessageOutboxJDBCRepository {

    private final JdbcTemplate jdbc;

    private static final String SQL_INSERT = """
        INSERT INTO chat_message_outbox (id, payload, destination, created_at, processed, retry_count, next_retry_at)
        VALUES (gen_random_uuid(), ?::jsonb, ?, now(), false, 0, now())
        """;

    private static final String SQL_SELECT_PENDING = """
        SELECT id, payload, destination, retry_count, created_at, next_retry_at
        FROM chat_message_outbox
        WHERE processed = false
          AND next_retry_at <= now()
        ORDER BY created_at
        LIMIT ?
        FOR UPDATE SKIP LOCKED
        """;

    private static final String SQL_UPDATE_NEXT_RETRY = """
        UPDATE chat_message_outbox SET next_retry_at = ?, retry_count = ? WHERE id = ?
        """;

    private static final String SQL_MARK_PROCESSED = """
        UPDATE chat_message_outbox SET processed = true, sent_at = now(), retry_count = ? WHERE id = ?
        """;

    public void saveAll(List<ChatMessageOutbox> outbox) {
        if (outbox == null || outbox.isEmpty()) return;

        jdbc.batchUpdate(SQL_INSERT, outbox, 100, (ps, o) -> {
            ps.setString(1, o.getPayload());
            ps.setString(2, o.getDestination());
        });
    }

    /**
     * Atomically claim up to batchSize rows by doing SELECT ... FOR UPDATE SKIP LOCKED
     * and updating their next_retry_at to claimUntil. This method MUST run in a transaction
     * so that the SELECT FOR UPDATE locks the rows until update completes.
     *
     * Returns the claimed rows as objects.
     */
    @Transactional
    public List<ChatMessageOutbox> claimPendingBatch(int batchSize, Instant claimUntil) {
        List<ChatMessageOutbox> rows = jdbc.query(SQL_SELECT_PENDING, ps -> ps.setInt(1, batchSize), (rs, i) -> {
            ChatMessageOutbox o = new ChatMessageOutbox();
            o.setId(rs.getObject("id", UUID.class));
            o.setPayload(rs.getString("payload"));
            o.setDestination(rs.getString("destination"));
            o.setRetryCount(rs.getInt("retry_count"));
            Timestamp created = rs.getTimestamp("created_at");
            if (created != null) o.setCreatedAt(created.toInstant());
            Timestamp nextRetry = rs.getTimestamp("next_retry_at");
            if (nextRetry != null) o.setNextRetryAt(nextRetry.toInstant());
            return o;
        });

        if (rows.isEmpty()) return Collections.emptyList();

        // Update next_retry_at to claimUntil for each id (still in same transaction)
        jdbc.batchUpdate(SQL_UPDATE_NEXT_RETRY, rows, 100, (ps, o) -> {
            ps.setTimestamp(1, Timestamp.from(claimUntil));
            ps.setInt(2, o.getRetryCount()); // keep retryCount unchanged for now
            ps.setObject(3, o.getId());
        });

        // return the rows — caller will process them (outside this transaction)
        return rows;
    }

    public void markProcessed(UUID id, int retryCount) {
        jdbc.update(SQL_MARK_PROCESSED, retryCount, id);
    }

    public void markRetry(UUID id, int retryCount, Instant nextRetry) {
        jdbc.update(SQL_UPDATE_NEXT_RETRY, Timestamp.from(nextRetry), retryCount, id);
    }
}
