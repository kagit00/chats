package com.flairbit.chats.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {
    private UUID sessionId;
    private String intent;
    @NotBlank
    private String content;
    @NotNull
    private UUID clientMessageId;
}