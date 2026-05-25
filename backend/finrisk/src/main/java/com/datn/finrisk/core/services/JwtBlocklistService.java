package com.datn.finrisk.core.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Date;

@Slf4j
@Service
public class JwtBlocklistService {

    private static final String BLOCKLIST_PREFIX = "jwt_blocklist:";

    @Autowired private StringRedisTemplate redisTemplate;

    public void block(String token, Date expiresAt) {
        long ttlSeconds = (expiresAt.getTime() - System.currentTimeMillis()) / 1000;
        if (ttlSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(tokenKey(token), "1", Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.error("[JWT-BLOCKLIST] Failed to block token: {}", e.getMessage());
        }
    }

    public boolean isBlocked(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey(token)));
        } catch (Exception e) {
            log.error("[JWT-BLOCKLIST] Blocklist check failed — fail-open: {}", e.getMessage());
            return false;
        }
    }

    private String tokenKey(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return BLOCKLIST_PREFIX + hex;
        } catch (Exception e) {
            log.error("[JWT-BLOCKLIST] SHA-256 failed, using hashCode fallback: {}", e.getMessage());
            return BLOCKLIST_PREFIX + Integer.toHexString(token.hashCode());
        }
    }
}
