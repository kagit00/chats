package com.flairbit.chats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileChatDto {
    private UUID id;
    private String displayName;
    private String userEmail;
    private UUID userId;
}
