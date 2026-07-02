package com.sebastianhauss.wayfare.util;

public class Base62Encoder {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String encode(Long id) {
        if (id == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            int remainder = Math.toIntExact(id % 62);
            sb.append(ALPHABET.charAt(remainder));
            id = id / 62;
        }
        return sb.reverse().toString();
    }
}
