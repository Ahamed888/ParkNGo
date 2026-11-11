package com.example.parkngo.user;

public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException() {
        super("This password reset link is invalid or has expired.");
    }
}
