package com.flairbit.chats.service.chats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flairbit.chats.dto.ChatMessageResponse;
import com.flairbit.chats.dto.ChatSessionResponse;
import com.flairbit.chats.dto.ProfileChatDto;
import com.flairbit.chats.models.ChatMessage;
import com.flairbit.chats.models.ChatMessageOutbox;
import com.flairbit.chats.models.ChatSession;
import com.flairbit.chats.repo.ChatMessageJDBCRepository;
import com.flairbit.chats.repo.ChatMessageOutboxJDBCRepository;
import com.flairbit.chats.repo.ChatSessionJDBCRepository;
import com.flairbit.chats.service.ProfileService;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatServiceImp implements ChatService {
    private final ChatSessionJDBCRepository sessionRepo;
    private final ChatMessageJDBCRepository msgRepo;
    private final ChatMessageOutboxJDBCRepository outboxRepo;
    private final ChatSessionService sessionService;
    private final ProfileService profileService;
    private final ObjectMapper json;
    private final SimpMessagingTemplate messaging;

    @Override
    public ChatSessionResponse initChat(String fromEmail, String toEmail, String intent) {
        ChatSession session = sessionService.getOrCreateSession(fromEmail, toEmail, intent);
        return new ChatSessionResponse(session.getId());
    }

    @Override
    public ChatMessage sendMessage(UUID sessionId, String senderEmail, String intent,
                                   String content, UUID clientMessageId) {

        if (msgRepo.existsByClientMsgId(clientMessageId)) {
            log.info("Ignoring duplicate message: {}", clientMessageId);
            return null;
        }

        ChatSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Chat session not found: " + sessionId));

        ProfileChatDto senderProfile = profileService.getByEmail(senderEmail, intent);
        verifyParticipant(session, senderProfile.getId());

        ChatMessage message = ChatMessage.builder()
                .session(session)
                .senderProfileId(senderProfile.getId())
                .content(content)
                .sentAt(Instant.now())
                .delivered(false)
                .seen(false)
                .clientMsgId(clientMessageId)
                .build();

        message = msgRepo.save(message);
        broadcast(message, session);
        return message;
    }

    @Override
    public void markDelivered(UUID messageId, String readerEmail, String intent) {
        ChatMessage message = msgRepo.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Message not found: " + messageId));

        ProfileChatDto reader = profileService.getByEmail(readerEmail, intent);
        if (message.getSenderProfileId().equals(reader.getId())) return; // ignore self

        boolean changed = false;
        if (!message.isDelivered()) { message.setDelivered(true); changed = true; }
        if (!message.isSeen()) { message.setSeen(true); changed = true; }

        if (changed) {
            msgRepo.save(message);
            broadcastDeliveryUpdate(message);
        }
    }

    @Override
    public void markRead(UUID messageId, String readerEmail, String intent) {
        ChatMessage message = msgRepo.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Message not found: " + messageId));

        ProfileChatDto reader = profileService.getByEmail(readerEmail, intent);
        if (message.getSenderProfileId().equals(reader.getId())) return;

        if (!message.isSeen()) {
            message.setSeen(true);
            msgRepo.save(message);
            broadcastDeliveryUpdate(message);
        }
    }

    @Override
    public List<ChatMessage> getHistory(UUID sessionId, int limit) {
        return msgRepo.findBySessionIdOrderBySentAtDesc(sessionId, limit);
    }

    @Override
    public List<ChatMessage> getUnread(UUID sessionId, String readerEmail, String intent) {
        ProfileChatDto reader = profileService.getByEmail(readerEmail, intent);
        return msgRepo.findUnseenBySessionAndReader(sessionId, reader.getId());
    }


    private void verifyParticipant(ChatSession session, UUID profileId) {
        if (!session.getSenderProfileId().equals(profileId) &&
                !session.getReceiverProfileId().equals(profileId)) {
            throw new SecurityException("Profile " + profileId + " not part of session " + session.getId());
        }
    }

    private void broadcast(ChatMessage message, ChatSession session) {
        ChatMessageResponse payload = ChatMessageResponse.from(message);
        String topic = "/topic/session." + session.getId();

        outboxRepo.saveAll(List.of(
                outbox(topic, payload),
                outbox(userQueue(session.getSenderProfileId()), payload),
                outbox(userQueue(session.getReceiverProfileId()), payload)
        ));
    }

    private void broadcastDeliveryUpdate(ChatMessage message) {
        ChatMessageResponse payload = ChatMessageResponse.from(message);
        messaging.convertAndSend("/topic/session." + message.getSession().getId(), payload);
    }

    private String userQueue(UUID profileId) {
        return "user:" + profileId + ":/queue/messages";
    }

    private ChatMessageOutbox outbox(String destination, ChatMessageResponse payload) {
        try {
            return ChatMessageOutbox.builder()
                    .id(UUID.randomUUID())
                    .destination(destination)
                    .payload(json.writeValueAsString(payload))
                    .createdAt(Instant.now())
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payload for destination: " + destination, e);
        }
    }
}
