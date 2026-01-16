package com.flairbit.chats.controller;

import com.flairbit.chats.dto.ChatMessageRequest;
import com.flairbit.chats.dto.ChatMessageResponse;
import com.flairbit.chats.dto.ChatSessionResponse;
import com.flairbit.chats.dto.Error;
import com.flairbit.chats.dto.InitChatRequest;
import com.flairbit.chats.exceptions.BadRequestException;
import com.flairbit.chats.exceptions.FraudPreventionException;
import com.flairbit.chats.exceptions.RateLimitException;
import com.flairbit.chats.models.ChatMessage;
import com.flairbit.chats.security.RateLimiter;
import com.flairbit.chats.service.SpamService;
import com.flairbit.chats.service.chats.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final SpamService spamService;

    @PostMapping(value = "/init", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatSessionResponse> initChat(@Valid @RequestBody InitChatRequest req) {
        ChatSessionResponse resp = chatService.initChat(req.getFromEmail(), req.getToEmail(), req.getIntent());
        return ResponseEntity.ok(resp);
    }

    @PostMapping(value = "/message", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatMessageResponse> sendMessage(@Valid @RequestBody ChatMessageRequest req) {
        String senderEmail = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        if (Objects.isNull(senderEmail)) {
            throw new BadRequestException("Sender email is required");
        }

        if (!RateLimiter.allow(senderEmail)) {
            throw new RateLimitException("Too many requests. Max 20 messages per minute per user.");
        }

        if (spamService.isSpamAttempt(req.getContent(), senderEmail)) {
            throw new FraudPreventionException("Repeated identical messages detected. Please slow down.");
        }

        ChatMessage msg = chatService.sendMessage(
                req.getSessionId(), senderEmail, req.getIntent(),
                req.getContent(), req.getClientMessageId());
        if (msg == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ChatMessageResponse.from(msg));
    }


    @GetMapping(value = "/{sessionId}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ChatMessageResponse>> history(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "50") int limit) {
        List<ChatMessage> msgs = chatService.getHistory(sessionId, limit);
        return ResponseEntity.ok(msgs.stream().map(ChatMessageResponse::from).toList());
    }

    @GetMapping(value = "/{sessionId}/unread", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ChatMessageResponse>> unread(
            @PathVariable UUID sessionId,
            @RequestParam @Email String readerEmail,
            @RequestParam String intent) {
        List<ChatMessage> msgs = chatService.getUnread(sessionId, readerEmail, intent);
        return ResponseEntity.ok(msgs.stream().map(ChatMessageResponse::from).toList());
    }
}