package com.sekai.song.util;

import org.apache.commons.codec.digest.DigestUtils;

public final class PasswordHashUtil {
    private static final String SALT = "sekai-song-player";

    private PasswordHashUtil() {
    }

    public static String hash(String rawPassword) {
        if (rawPassword == null) {
            return null;
        }
        return DigestUtils.sha256Hex(SALT + ":" + rawPassword);
    }

    public static boolean matches(String rawPassword, String hashedPassword) {
        return hashedPassword != null && hashedPassword.equals(hash(rawPassword));
    }
}
