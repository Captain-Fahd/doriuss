package io.github.cptfahd.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder =
            securityConfig.passwordEncoder();

    @Test
    void sameRawPasswordEncodesDifferently() {
        String raw = "correct-horse-battery-staple";

        String hash1 = passwordEncoder.encode(raw);
        String hash2 = passwordEncoder.encode(raw);

        assertNotEquals(hash1, hash2, "each encode() call should use a fresh random salt");
        assertTrue(hash1.startsWith("$2"), "BCrypt hashes start with $2");
        assertTrue(hash2.startsWith("$2"), "BCrypt hashes start with $2");
    }

    @Test
    void matchesIsTrueForTheRightPasswordAndFalseForAWrongOne() {
        String raw = "correct-horse-battery-staple";
        String hash = passwordEncoder.encode(raw);

        assertTrue(passwordEncoder.matches(raw, hash));
        assertFalse(passwordEncoder.matches("wrong-password", hash));
    }

    @Test
    void oneEncodeCallTakesRoughly100To300Milliseconds() {
        String raw = "correct-horse-battery-staple";

        long start = System.nanoTime();
        passwordEncoder.encode(raw);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("BCrypt encode() took " + elapsedMillis + "ms");
    }
}
