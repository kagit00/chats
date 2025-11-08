package com.flairbit.chats.service;

import com.flairbit.chats.client.FlairBitClient;
import com.flairbit.chats.dto.ProfileChatDto;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final FlairBitClient flairBitClient;
    private final Cache<String, ProfileChatDto> profileCache;

    public ProfileChatDto getByEmail(String email, String intent) {
        String key = canonical(email) + "|" + intent;
        return profileCache.get(key, k -> flairBitClient.getProfileByUserEmail(canonical(email), intent));
    }

    private String canonical(String e) {
        return e == null ? null : e.trim().toLowerCase(Locale.ROOT);
    }
}

