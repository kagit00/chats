package com.flairbit.chats.security;

import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {

    private static final Cache<String, AtomicInteger> limiter = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    public static boolean allow(String user) {
        String minuteKey = user + ":" + (System.currentTimeMillis() / 60000);

        // get(key, mappingFunction) is atomic
        AtomicInteger count = limiter.get(minuteKey, k -> new AtomicInteger(0));

        if (count.incrementAndGet() > 20) {
            return false;
        }
        return true;
    }
}