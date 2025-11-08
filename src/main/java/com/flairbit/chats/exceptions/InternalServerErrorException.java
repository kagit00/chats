package com.flairbit.chats.exceptions;

public class InternalServerErrorException extends RuntimeException {
    public InternalServerErrorException(String m) {
        super(m);
    }
}