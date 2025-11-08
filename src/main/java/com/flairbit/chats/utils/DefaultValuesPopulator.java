package com.flairbit.chats.utils;

import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.util.UUID;

@UtilityClass
public final class DefaultValuesPopulator {
    public static UUID getUid() {
        return UUID.randomUUID();
    }

    public static LocalDateTime getCurrentTimestamp() {
        return LocalDateTime.now();
    }
}
