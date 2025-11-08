package com.flairbit.chats.service.chats;

import com.flairbit.chats.dto.ProfileChatDto;
import com.flairbit.chats.models.ChatSession;
import com.flairbit.chats.repo.ChatSessionJDBCRepository;
import com.flairbit.chats.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatSessionService {

    private final ChatSessionJDBCRepository repo;
    private final ProfileService profileService;

    public ChatSession getOrCreateSession(String fromEmail, String toEmail, String intent) {
        String a = canonical(fromEmail);
        String b = canonical(toEmail);

        if (a.equals(b)) {
            throw new IllegalArgumentException("Cannot create session with same email");
        }

        ProfileChatDto pA = profileService.getByEmail(a, intent);
        ProfileChatDto pB = profileService.getByEmail(b, intent);

        UUID senderId = pA.getId().compareTo(pB.getId()) <= 0 ? pA.getId() : pB.getId();
        UUID receiverId = pA.getId().compareTo(pB.getId()) <= 0 ? pB.getId() : pA.getId();

        return repo.getOrCreate(senderId, receiverId, intent);
    }

    private String canonical(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}