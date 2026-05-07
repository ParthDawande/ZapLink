package com.zaplink.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReservedShortCodesTest {

    @Test
    void api_isReserved() {
        assertTrue(ReservedShortCodes.isReserved("api"));
    }

    @Test
    void api_uppercase_isReserved_caseInsensitive() {
        assertTrue(ReservedShortCodes.isReserved("API"));
    }

    @Test
    void api_mixedCase_isReserved_caseInsensitive() {
        assertTrue(ReservedShortCodes.isReserved("Api"));
    }

    @Test
    void admin_isReserved() {
        assertTrue(ReservedShortCodes.isReserved("admin"));
    }

    @Test
    void login_isReserved() {
        assertTrue(ReservedShortCodes.isReserved("login"));
    }

    @Test
    void health_isReserved() {
        assertTrue(ReservedShortCodes.isReserved("health"));
    }

    @Test
    void abc_isNotReserved() {
        assertFalse(ReservedShortCodes.isReserved("abc"));
    }

    @Test
    void randomCode_isNotReserved() {
        assertFalse(ReservedShortCodes.isReserved("3d7"));
    }

    @Test
    void null_isNotReserved() {
        assertFalse(ReservedShortCodes.isReserved(null));
    }

    @Test
    void set_isImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> ReservedShortCodes.SET.add("shouldFail"));
    }
}
