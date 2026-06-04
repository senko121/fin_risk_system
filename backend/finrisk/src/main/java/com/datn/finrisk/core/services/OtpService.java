 

 

package com.datn.finrisk.core.services;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;

import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class OtpService {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private EmailService emailService; 

    @Value("${twilio.account.sid}") private String twilioAccountSid;
    @Value("${twilio.auth.token}") private String twilioAuthToken;
    @Value("${twilio.phone.number}") private String twilioPhoneNumber;

    private static final int OTP_EXPIRE_SECONDS = 180;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();


@PostConstruct
    public void initTwilio() {
        try {
            Twilio.init(twilioAccountSid, twilioAuthToken);
        } catch (Exception e) {
            log.warn("[OTP] Twilio init failed (SMS disabled): {}", e.getMessage());
        }
    }

@Async("aiTaskExecutor")
    public CompletableFuture<Void> generateAndSendOtpAsync(Long txId, String phone, String email) {
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        this.saveOtp(txId, otp);

        boolean phoneValid = phone != null && phone.matches("^\\+?[1-9]\\d{6,14}$");

        if (phoneValid) {
            try {
                Message.creator(new PhoneNumber(phone), new PhoneNumber(twilioPhoneNumber),
                        "FinRisk OTP: " + otp + " (Có hiệu lực 3 phút)").create();
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                log.warn("[OtpService] SMS delivery failed for tx={} phone={}, falling back to email.",
                        txId, phone);
            }
        } else {
            log.warn("[OtpService] Invalid or missing phone for tx={} — skipping SMS, using email fallback.", txId);
        }

        try {
            if (email != null && !email.trim().isEmpty()) {
                emailService.sendOtpEmail(email, otp);
            } else {
                log.error("[OtpService] No email address for OTP fallback — user unreachable for tx={}.", txId);
            }
        } catch (Exception ex) {
            log.error("[OtpService] OTP email fallback failed for tx={}: {}", txId, ex.getMessage(), ex);
        }

        return CompletableFuture.completedFuture(null);
    }
public void saveOtp(Long transactionId, String otp) {
        String otpKey      = "otp_tx:" + transactionId;
        String attemptsKey = "otp_attempts_tx:" + transactionId;
        try {
            redisTemplate.opsForValue().set(otpKey, otp, Duration.ofSeconds(OTP_EXPIRE_SECONDS));
            redisTemplate.delete(attemptsKey);
        } catch (Exception e) {
            log.error("[OtpService] Redis OTP save failed for tx={}: {}", transactionId, e.getMessage(), e);
        }
    }

    public boolean verifyOtp(Long transactionId, String inputOtp) {
        String otpKey      = "otp_tx:" + transactionId;
        String attemptsKey = "otp_attempts_tx:" + transactionId;

        try {
            String raw = redisTemplate.opsForValue().get(attemptsKey);
            int attempts = raw != null ? Integer.parseInt(raw) : 0;
            if (attempts >= MAX_OTP_ATTEMPTS) {
                log.warn("[OtpService] OTP locked — max attempts reached for tx={}", transactionId);
                return false;
            }

            String storedOtp = redisTemplate.opsForValue().get(otpKey);
            if (storedOtp == null) {
                return false;
            }

            if (storedOtp.equals(inputOtp)) {
                redisTemplate.delete(otpKey);
                redisTemplate.delete(attemptsKey);
                return true;
            }

            Long newCount = redisTemplate.opsForValue().increment(attemptsKey);
            if (newCount != null && newCount == 1) {
                redisTemplate.expire(attemptsKey, Duration.ofSeconds(OTP_EXPIRE_SECONDS));
            }
            log.warn("[OtpService] Wrong OTP for tx={} attempt={}/{}", transactionId, newCount, MAX_OTP_ATTEMPTS);
            return false;
        } catch (Exception e) {
            log.error("[OtpService] Redis OTP verify failed for tx={}: {}", transactionId, e.getMessage(), e);
            return false;
        }
    }

    public String generateVoiceOtp(Long transactionId) {
        String otp         = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String otpKey      = "voice_otp_tx:" + transactionId;
        String attemptsKey = "voice_otp_attempts_tx:" + transactionId;
        try {
            redisTemplate.opsForValue().set(otpKey, otp, Duration.ofSeconds(OTP_EXPIRE_SECONDS));
            redisTemplate.delete(attemptsKey);
        } catch (Exception e) {
            log.error("[OtpService] Redis voice OTP save failed for tx={}: {}", transactionId, e.getMessage(), e);
        }
        return otp;
    }

    public boolean verifyVoiceOtp(Long transactionId, String inputOtp) {
        String otpKey      = "voice_otp_tx:" + transactionId;
        String attemptsKey = "voice_otp_attempts_tx:" + transactionId;
        try {
            String raw = redisTemplate.opsForValue().get(attemptsKey);
            int attempts = raw != null ? Integer.parseInt(raw) : 0;
            if (attempts >= MAX_OTP_ATTEMPTS) {
                log.warn("[OtpService] Voice OTP locked — max attempts reached for tx={}", transactionId);
                return false;
            }

            String storedOtp = redisTemplate.opsForValue().get(otpKey);
            if (storedOtp == null) {
                return false;
            }

            if (storedOtp.equals(inputOtp)) {
                redisTemplate.delete(otpKey);
                redisTemplate.delete(attemptsKey);
                return true;
            }

            Long newCount = redisTemplate.opsForValue().increment(attemptsKey);
            if (newCount != null && newCount == 1) {
                redisTemplate.expire(attemptsKey, Duration.ofSeconds(OTP_EXPIRE_SECONDS));
            }
            log.warn("[OtpService] Wrong voice OTP for tx={} attempt={}/{}", transactionId, newCount, MAX_OTP_ATTEMPTS);
            return false;
        } catch (Exception e) {
            log.error("[OtpService] Redis voice OTP verify failed for tx={}: {}", transactionId, e.getMessage(), e);
            return false;
        }
    }
}