package com.datn.finrisk.core.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("FinRisk Bank - Mã xác thực giao dịch (OTP)");
            message.setText("Xin chào,\n\n"
                    + "Hệ thống không thể gửi SMS đến số điện thoại của bạn, nên chúng tôi gửi mã qua Email này.\n"
                    + "Mã OTP xác thực giao dịch của bạn là: " + otp + "\n\n"
                    + "Mã này sẽ tự động hết hạn sau 3 phút. Tuyệt đối không chia sẻ mã này cho bất kỳ ai!\n"
                    + "Trân trọng,\nFinRisk Security Team");
            
            mailSender.send(message);
            System.out.println("📧 ĐÃ GỬI OTP FALLBACK QUA EMAIL TỚI: " + toEmail);
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi Email Fallback: " + e.getMessage());
        }
    }
}