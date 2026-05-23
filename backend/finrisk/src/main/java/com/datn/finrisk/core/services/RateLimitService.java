package com.datn.finrisk.core.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays; 

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
            System.err.println("⚠️ Lỗi kiểm tra Blocked, bypass tạm thời: " + e.getMessage());
            return false; 
        }
    }

 
    public long getLockTimeRemaining(String username) {
        String lockKey = "login_locked:" + username;
        try {
 
            Long expire = redisTemplate.getExpire(lockKey);
            return (expire != null && expire > 0) ? expire : 0;
        } catch (Exception e) {
            System.err.println("⚠️ Lỗi đếm thời gian, bypass tạm thời: " + e.getMessage());
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

            System.out.println("⚠️ CẢNH BÁO: User " + username + " đăng nhập sai lần thứ " + attempts);

 
            if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
                redisTemplate.opsForValue().set(lockKey, "LOCKED", Duration.ofMinutes(LOCK_TIME_DURATION));
                redisTemplate.delete(attemptKey);
                System.err.println("🚨 RATE LIMIT: Đã khóa tạm thời tài khoản " + username + " do Spam Brute-force!");
            }
        } catch (Exception e) {
 
            System.err.println("⚠️ Lỗi ghi nhận failed login: " + e.getMessage());
        }
    }
 
    public void clearLoginAttempts(String username) {
        String attemptKey = "login_attempts:" + username;
        String lockKey = "login_locked:" + username;
        
        try {
 
            redisTemplate.delete(Arrays.asList(attemptKey, lockKey)); 
        } catch (Exception e) {
 
            System.err.println("⚠️ Lỗi xóa án tích: " + e.getMessage());
        }
    }
}