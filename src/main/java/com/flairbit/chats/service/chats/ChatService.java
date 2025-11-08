package com.flairbit.chats.service.chats;

import com.flairbit.chats.dto.ChatSessionResponse;
import com.flairbit.chats.models.ChatMessage;

import java.util.List;
import java.util.UUID;

public interface ChatService {
    ChatSessionResponse initChat(String fromEmail, String toEmail, String intent);
    ChatMessage sendMessage(UUID sessionId, String senderEmail, String intent, String content, UUID clientMessageId);
    void markDelivered(UUID messageId, String readerEmail, String intent);
    void markRead(UUID messageId, String readerEmail, String intent);
    List<ChatMessage> getHistory(UUID sessionId, int limit);
    List<ChatMessage> getUnread(UUID sessionId, String readerEmail, String intent);
}
