package com.zaplink.exception;

public class LinkExpiredException extends LinkGoneException {

    public LinkExpiredException() {
        super("Link Expired", "This link has expired.");
    }
}
