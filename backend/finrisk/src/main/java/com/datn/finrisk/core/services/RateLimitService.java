package com.datn.finrisk.core.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;

@Slf4j
@Service
public class RateLimitService {

    @Autowired
    private StringRedisTemplate redisTemplate;
 
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION = 15; 
 
    public boolean isLoginBlocked(String username) {
        String lockKey = "login_locked:" + username;
        try {
 
            return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
        } catch (Exception e) {
            log.error("[RATE-LIMIT] Redis isLoginBlocked check failed, bypassing: {}", e.getMessage());
            return false; 
        }
    }

 
    public long getLockTimeRemaining(String username) {
        String lockKey = "login_locked:" + username;
        try {
 
            Long expire = redisTemplate.getExpire(lockKey);
            return (expire != null && expire > 0) ? expire : 0;
        } catch (Exception e) {
            log.error("[RATE-LIMIT] Redis getLockTimeRemaining failed, returning 0: {}", e.getMessage());
            return 0;
        }
    }
 
    public void recordFailedLogin(String username) {
        String attemptKey = "login_attempts:" + username;
        String lockKey = "login_locked:" + username;

        try {
 
            Long attempts = redisTemplate.opsForValue().increment(attemptKey);

            if (attempts != null && attempts == 1) {
                redisTemplate.expire(attemptKey, Duration.ofMinutes(LOCK_TIME_DURATION));
            }

            log.warn("[RATE-LIMIT] Failed login attempt user={} count={}", username, attempts);

            if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
                redisTemplate.opsForValue().set(lockKey, "LOCKED", Duration.ofMinutes(LOCK_TIME_DURATION));
                redisTemplate.delete(attemptKey);
                log.error("[RATE-LIMIT] Account LOCKED user={} after {} failed attempts", username, MAX_LOGIN_ATTEMPTS);
            }
        } catch (Exception e) {
            log.error("[RATE-LIMIT] Redis recordFailedLogin failed user={}: {}", username, e.getMessage());
        }
    }
 
    public void clearLoginAttempts(String username) {
        String attemptKey = "login_attempts:" + username;
        String lockKey = "login_locked:" + username;
        
        try {
 
            redisTemplate.delete(Arrays.asList(attemptKey, lockKey)); 
        } catch (Exception e) {
 
            log.error("[RATE-LIMIT] Redis clearLoginAttempts failed user={}: {}", username, e.getMessage());
        }
    }
}