package com.capstoneproject.codereviewsystem.exceptions;

public class WebhookAuthException extends RuntimeException {

    public WebhookAuthException(String message) {
        super(message);
    }
}