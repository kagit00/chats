package com.flairbit.chats.dto;

import com.flairbit.chats.models.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponse {

    private UUID messageId;
    private UUID sessionId;
    private UUID senderId;
    private String senderDisplayName;
    private String senderEmail;
    private String content;
    private Instant sentAt;
    private boolean delivered;
    private boolean seen;

    public static ChatMessageResponse from(ChatMessage message) {
        return ChatMessageResponse.builder()
                .messageId(message.getId())
                .sessionId(message.getSession().getId())
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .delivered(message.isDelivered())
                .seen(message.isSeen())
                .build();
    }
}
