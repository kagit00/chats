package com.flairbit.chats.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class SpamService {

    private final Map<String, Deque<Instant>> messageHistory = new ConcurrentHashMap<>();

    public boolean isSpamAttempt(String content, String senderEmail) {
        // Use a hash of the content to keep the key size manageable
        String contentHash = String.valueOf(content.hashCode());
        String key = senderEmail + ":" + contentHash;
        
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(30);

        // Get the queue of timestamps for this specific message by this user
        Deque<Instant> timestamps = messageHistory.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        // 1. SLIDE THE WINDOW: Remove timestamps older than 30 seconds from now
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }

        // 2. CHECK THE LOG: If there are already 5 identical messages in the last 30s
        if (timestamps.size() >= 5) {
            return true; 
        }

        // 3. LOG THE EVENT: Add the current timestamp
        timestamps.addLast(now);
        return false;
    }
}