package com.zaplink.util;

public final class Base62Encoder {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;

    private Base62Encoder() {}

    public static String encode(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be a positive integer, got: " + id);
        }
        StringBuilder sb = new StringBuilder();
        long n = id;
        while (n > 0) {
            sb.append(ALPHABET.charAt((int) (n % BASE)));
            n /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code must not be null or empty");
        }
        long result = 0;
        for (char c : code.toCharArray()) {
            int index = ALPHABET.indexOf(c);
            if (index == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: '" + c + "'");
            }
            result = result * BASE + index;
        }
        return result;
    }
}
