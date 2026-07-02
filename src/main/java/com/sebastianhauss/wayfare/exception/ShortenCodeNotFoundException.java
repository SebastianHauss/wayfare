package com.sebastianhauss.wayfare.exception;

public class ShortenCodeNotFoundException extends RuntimeException {
    public ShortenCodeNotFoundException(String message) {
        super(message);
    }
}
