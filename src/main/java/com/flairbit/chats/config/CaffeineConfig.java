package com.flairbit.chats.config;

import com.flairbit.chats.dto.ProfileChatDto;
import com.flairbit.chats.dto.UserDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, ProfileChatDto> profileCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(50_000)
                .build();
    }

    @Bean
    public Cache<UUID, UserDTO> userCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(50_000)
                .build();
    }
}
