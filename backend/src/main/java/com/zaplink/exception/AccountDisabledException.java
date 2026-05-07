package com.zaplink.exception;

public class AccountDisabledException extends RuntimeException {
    public AccountDisabledException() {
        super("Your account has been disabled");
    }
}
