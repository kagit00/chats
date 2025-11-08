package com.flairbit.chats.dto;

import java.util.UUID;

public record UserDTO(UUID id, String email, String username) {}