package com.flairbit.chats.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_sessions", indexes = {
        @Index(name = "idx_chat_sessions_participants_intent", columnList = "profile1_id,profile2_id,intent")
}, uniqueConstraints = {@UniqueConstraint(columnNames = {"profile1_id","profile2_id","intent"})})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID senderProfileId;
    @Column(nullable = false)
    private UUID receiverProfileId;
    @Column(name = "intent", nullable = false)
    private String intent;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}