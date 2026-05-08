package com.datn.finrisk.core.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays; // Thêm import này

@Service
public class RateLimitService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // Cấu hình luật: Sai 5 lần -> Khóa 15 phút
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION = 15; 

    // 1. Kiểm tra xem tài khoản có đang bị khóa không?
    public boolean isLoginBlocked(String username) {
        String lockKey = "login_locked:" + username;
        try {
            // 🚀 BỌC TRY-CATCH: Lỡ Redis sập thì cứ nhắm mắt cho qua (Fail-safe)
            return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
        } catch (Exception e) {
            System.err.println("⚠️ Lỗi kiểm tra Blocked, bypass tạm thời: " + e.getMessage());
            return false; 
        }
    }

    // 2. Lấy thời gian khóa còn lại (để báo cho Frontend)
    public long getLockTimeRemaining(String username) {
        String lockKey = "login_locked:" + username;
        try {
            // 🚀 BỌC TRY-CATCH: Redis sập thì báo là còn 0 giây
            Long expire = redisTemplate.getExpire(lockKey);
            return (expire != null && expire > 0) ? expire : 0;
        } catch (Exception e) {
            System.err.println("⚠️ Lỗi đếm thời gian, bypass tạm thời: " + e.getMessage());
            return 0;
        }
    }

    // 3. Ghi nhận 1 lần đăng nhập thất bại
    public void recordFailedLogin(String username) {
        String attemptKey = "login_attempts:" + username;
        String lockKey = "login_locked:" + username;

        try {
            // Tăng bộ đếm lên 1
            Long attempts = redisTemplate.opsForValue().increment(attemptKey);

            if (attempts != null && attempts == 1) {
                redisTemplate.expire(attemptKey, Duration.ofMinutes(LOCK_TIME_DURATION));
            }

            System.out.println("⚠️ CẢNH BÁO: User " + username + " đăng nhập sai lần thứ " + attempts);

            // Đạt tới giới hạn -> KÍCH HOẠT KHÓA MÕM
            if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
                redisTemplate.opsForValue().set(lockKey, "LOCKED", Duration.ofMinutes(LOCK_TIME_DURATION));
                redisTemplate.delete(attemptKey);
                System.err.println("🚨 RATE LIMIT: Đã khóa tạm thời tài khoản " + username + " do Spam Brute-force!");
            }
        } catch (Exception e) {
            // 🚀 BỌC TRY-CATCH: Nếu không ghi được tội thì thôi, không cho sập app
            System.err.println("⚠️ Lỗi ghi nhận failed login: " + e.getMessage());
        }
    }

    // 4. Nếu đăng nhập thành công -> Xóa hết tội lỗi cũ
    public void clearLoginAttempts(String username) {
        String attemptKey = "login_attempts:" + username;
        String lockKey = "login_locked:" + username;
        
        try {
            // 🚀 FIX LỖI SỐ 1: Quét sạch cả Sổ đếm VÀ Còng số 8
            redisTemplate.delete(Arrays.asList(attemptKey, lockKey)); 
        } catch (Exception e) {
            // 🚀 BỌC TRY-CATCH chống sập
            System.err.println("⚠️ Lỗi xóa án tích: " + e.getMessage());
        }
    }
}