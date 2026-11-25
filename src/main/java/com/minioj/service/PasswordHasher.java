package com.minioj.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class PasswordHasher {

    private static final String LONG_PASSWORD_PREFIX = "{sha384-bcrypt}";
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final int SALT_BYTES = 16;
    private static final HexFormat HEX = HexFormat.of();

    private final BCryptPasswordEncoder bcrypt;
    private final SecureRandom random = new SecureRandom();

    PasswordHasher(BCryptPasswordEncoder bcrypt) {
        this.bcrypt = bcrypt;
    }

    String encode(String password) {
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= BCRYPT_MAX_BYTES) {
            return bcrypt.encode(password);
        }

        // 中文等多字节密码可能超过 BCrypt 的字节上限。独立随机盐避免不同账号共用预哈希。
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return LONG_PASSWORD_PREFIX + HEX.formatHex(salt) + bcrypt.encode(prehash(bytes, salt));
    }

    boolean matches(String password, String hash) {
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        if (!hash.startsWith(LONG_PASSWORD_PREFIX)) {
            return bytes.length <= BCRYPT_MAX_BYTES && bcrypt.matches(password, hash);
        }

        int saltStart = LONG_PASSWORD_PREFIX.length();
        int saltEnd = saltStart + SALT_BYTES * 2;
        byte[] salt = HEX.parseHex(hash, saltStart, saltEnd);
        return bcrypt.matches(prehash(bytes, salt), hash.substring(saltEnd));
    }

    private String prehash(byte[] password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-384");
            digest.update(salt);
            return Base64.getEncoder().encodeToString(digest.digest(password));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-384 不可用", exception);
        }
    }
}
