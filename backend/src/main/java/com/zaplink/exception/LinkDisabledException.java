package com.zaplink.exception;

public class LinkDisabledException extends LinkGoneException {

    public LinkDisabledException() {
        super("Link Disabled", "This link has been disabled.");
    }
}
