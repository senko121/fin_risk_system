package com.datn.finrisk.core.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

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
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    // 2. Lấy thời gian khóa còn lại (để báo cho Frontend)
    public long getLockTimeRemaining(String username) {
        String lockKey = "login_locked:" + username;
        Long expire = redisTemplate.getExpire(lockKey);
        return (expire != null && expire > 0) ? expire : 0;
    }

    // 3. Ghi nhận 1 lần đăng nhập thất bại
    public void recordFailedLogin(String username) {
        String attemptKey = "login_attempts:" + username;
        String lockKey = "login_locked:" + username;

        // Tăng bộ đếm lên 1 (Nếu key chưa có, Redis tự tạo và set là 1)
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);

        if (attempts != null && attempts == 1) {
            // Nếu là lần sai đầu tiên, cho bộ đếm này sống 15 phút (Sau 15p tự reset về 0)
            redisTemplate.expire(attemptKey, Duration.ofMinutes(LOCK_TIME_DURATION));
        }

        System.out.println("⚠️ CẢNH BÁO: User " + username + " đăng nhập sai lần thứ " + attempts);

        // Đạt tới giới hạn -> KÍCH HOẠT KHÓA MÕM
        if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
            redisTemplate.opsForValue().set(lockKey, "LOCKED", Duration.ofMinutes(LOCK_TIME_DURATION));
            redisTemplate.delete(attemptKey); // Khóa xong thì xóa bộ đếm đi
            System.err.println("🚨 RATE LIMIT: Đã khóa tạm thời tài khoản " + username + " do Spam Brute-force!");
        }
    }

    // 4. Nếu đăng nhập thành công -> Xóa hết tội lỗi cũ
    public void clearLoginAttempts(String username) {
        redisTemplate.delete("login_attempts:" + username);
    }
}