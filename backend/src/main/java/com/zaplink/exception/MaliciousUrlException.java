package com.zaplink.exception;

public class MaliciousUrlException extends RuntimeException {

    public MaliciousUrlException(String message) {
        super(message);
    }
}
