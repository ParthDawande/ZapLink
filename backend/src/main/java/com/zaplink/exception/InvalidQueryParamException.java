package com.zaplink.exception;

public class InvalidQueryParamException extends RuntimeException {

    public InvalidQueryParamException(String message) {
        super(message);
    }
}
