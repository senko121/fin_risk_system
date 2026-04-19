package com.datn.finrisk.core.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class OtpService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final int OTP_EXPIRE_SECONDS = 180;

    /**
     * Lưu OTP lên Redis với debug full
     */
    public void saveOtp(Long transactionId, String otp) {
        String key = "otp_tx:" + transactionId;

        try {
            // 🔥 SAVE
            redisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(OTP_EXPIRE_SECONDS));

              // 🔥 TEST KEY (QUAN TRỌNG NHẤT)
            redisTemplate.opsForValue().set("compare_key", "from_spring");

            System.out.println("🔥 REDIS SAVE KEY: " + key);
            System.out.println("🔥 REDIS SAVE OTP: " + otp);

            // 🔥 READ BACK (quan trọng nhất)
            String stored = redisTemplate.opsForValue().get(key);
            System.out.println("🔥 REDIS READ BACK: " + stored);

            // 🔥 TTL CHECK
            Long ttl = redisTemplate.getExpire(key);
            System.out.println("🔥 REDIS TTL: " + ttl);

        } catch (Exception e) {
            System.err.println("❌ REDIS ERROR (SAVE): " + e.getMessage());
        }
    }

    /**
     * Verify OTP với debug
     */
    public boolean verifyOtp(Long transactionId, String inputOtp) {
        String key = "otp_tx:" + transactionId;

        try {
            String storedOtp = redisTemplate.opsForValue().get(key);

            System.out.println("🔍 VERIFY KEY: " + key);
            System.out.println("🔍 STORED OTP: " + storedOtp);
            System.out.println("🔍 INPUT OTP: " + inputOtp);

            if (storedOtp == null) {
                System.out.println("❌ OTP NULL (hết hạn hoặc chưa lưu)");
                return false;
            }

            if (storedOtp.equals(inputOtp)) {
                redisTemplate.delete(key);
                System.out.println("✅ OTP MATCH → DELETE KEY");
                return true;
            }

            System.out.println("❌ OTP KHÔNG KHỚP");
            return false;

        } catch (Exception e) {
            System.err.println("❌ REDIS ERROR (VERIFY): " + e.getMessage());
            return false;
        }
    }
}