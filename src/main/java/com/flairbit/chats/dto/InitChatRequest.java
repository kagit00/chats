package com.flairbit.chats.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitChatRequest {
    @Email
    @NotBlank
    private String fromEmail;
    @Email
    @NotBlank
    private String toEmail;
    @NotBlank
    private String intent;
}