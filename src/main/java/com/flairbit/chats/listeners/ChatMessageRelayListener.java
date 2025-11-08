package com.flairbit.chats.listeners;

import com.flairbit.chats.config.RabbitConfig;
import com.flairbit.chats.dto.ChatMessageRequest;
import com.flairbit.chats.dto.ChatMessageResponse;
import com.flairbit.chats.service.chats.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageRelayListener {

    private final ChatService chatService;
    private final SimpMessagingTemplate messaging;

    @RabbitListener(
            queues = RabbitConfig.QUEUE_NAME,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void handleChatMessage(ChatMessageRequest request) {
        try {
            log.info("Received relay message from {} -> intent={} : {}",
                    request.getSenderEmail(), request.getIntent(), request.getContent());

            var saved = chatService.sendMessage(
                    request.getSessionId(),
                    request.getSenderEmail(),
                    request.getIntent(),
                    request.getContent(),
                    request.getClientMessageId()
            );

            var ack = ChatMessageResponse.from(saved);
            messaging.convertAndSendToUser(
                    request.getSenderEmail(),
                    "/queue/ack",
                    ack
            );

            log.info("Message persisted and ack sent for {}", request.getSenderEmail());

        } catch (Exception e) {
            log.error("Relay message processing failed: {}", e.getMessage(), e);
            messaging.convertAndSendToUser(
                    request.getSenderEmail(),
                    "/queue/error",
                    "Failed to process message: " + e.getMessage()
            );
        }
    }
}
