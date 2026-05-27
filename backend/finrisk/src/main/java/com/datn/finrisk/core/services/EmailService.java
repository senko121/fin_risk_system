package com.datn.finrisk.core.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    
    @Async("aiTaskExecutor")
    public void sendOtpEmail(String toEmail, String otp) {
        try {


            String safeOtp = (otp == null) ? "[OTP unavailable]" : otp;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("FinRisk Bank - Mã xác thực giao dịch (OTP)");

            message.setText(
                    "Xin chào,\n\n"
                    + "Hệ thống không thể gửi SMS đến số điện thoại của bạn, nên chúng tôi gửi mã qua Email này.\n"
                    + "Mã OTP xác thực giao dịch của bạn là: " + safeOtp + "\n\n"
                    + "Mã này sẽ tự động hết hạn sau 3 phút. Tuyệt đối không chia sẻ mã này cho bất kỳ ai!\n"
                    + "Trân trọng,\nFinRisk Security Team"
            );

            mailSender.send(message);

            System.out.println("📧 ĐÃ GỬI OTP FALLBACK QUA EMAIL TỚI: " + toEmail);

        } catch (Exception e) {
            log.error("[Email] Failed to send OTP fallback email to {}: {}", toEmail, e.getMessage(), e);
        }
    }
}