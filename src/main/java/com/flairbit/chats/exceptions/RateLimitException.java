package com.flairbit.chats.exceptions;

public class RateLimitException extends RuntimeException {
    public RateLimitException(String msg) { super(msg); }
}