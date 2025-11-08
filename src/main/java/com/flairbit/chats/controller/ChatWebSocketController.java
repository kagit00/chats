package com.flairbit.chats.controller;


import com.flairbit.chats.dto.ChatMessageRequest;
import com.flairbit.chats.service.chats.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final RabbitTemplate rabbitTemplate;
    private final ChatService chatService;
    private final SimpMessagingTemplate messaging;

    @MessageMapping("/chat.send")
    public void handleChatSend(ChatMessageRequest request, Principal principal) {
        log.info("Received chat.send from {}: {}", principal.getName(), request.getContent());

        rabbitTemplate.convertAndSend(
                "amq.topic",
                "app/chat.send",
                request
        );
    }

    @MessageMapping("/ping")
    @SendToUser("/queue/pong")
    public String ping(Principal principal) {
        return "Pong at " + Instant.now() + " for " + principal.getName();
    }
}