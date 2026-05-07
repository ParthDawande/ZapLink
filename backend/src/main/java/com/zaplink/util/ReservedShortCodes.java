package com.zaplink.util;

import java.util.Set;
import java.util.stream.Collectors;

public final class ReservedShortCodes {

    public static final Set<String> SET = Set.of(
            "api", "admin", "login", "register", "logout",
            "swagger-ui", "v3", "actuator", "health",
            "favicon.ico", "robots.txt"
    );

    // Lowercase version for case-insensitive lookup, built once at class load time.
    private static final Set<String> LOWER_SET = SET.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toUnmodifiableSet());

    private ReservedShortCodes() {}

    public static boolean isReserved(String code) {
        if (code == null) return false;
        return LOWER_SET.contains(code.toLowerCase());
    }
}
