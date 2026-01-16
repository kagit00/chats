package com.flairbit.chats.dto;

import java.time.LocalDateTime;

public class MessageWithTime {
    public final String content;
    public final LocalDateTime time;
    public MessageWithTime(String content, LocalDateTime time) {
        this.content = content;
        this.time = time;
    }
}