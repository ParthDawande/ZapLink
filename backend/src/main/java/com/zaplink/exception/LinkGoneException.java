package com.zaplink.exception;

public class LinkGoneException extends RuntimeException {

    private final String title;
    private final String errorMessage;

    protected LinkGoneException(String title, String errorMessage) {
        super(errorMessage);
        this.title = title;
        this.errorMessage = errorMessage;
    }

    public String getTitle() {
        return title;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
