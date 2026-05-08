package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.exceptions.BusinessLogicException; // 🚀 IMPORT LỖI BUSINESS VÀO ĐÂY
import com.datn.finrisk.core.repository.UserSecurityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PinService {

    @Autowired
    private UserSecurityRepository userSecurityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 🚀 BƯỚC 1: Sửa cờ Transactional để KHÔNG ROLLBACK khi ném lỗi Business
    @Transactional(noRollbackFor = BusinessLogicException.class)
    public boolean verifyPin(UserSecurity security, String rawPin) {

        // 1. Kiểm tra xem tài khoản có đang bị khóa PIN không
        if (security.getLockUntil() != null && security.getLockUntil().isAfter(LocalDateTime.now())) {
            // 🚀 BƯỚC 2: Ném BusinessLogicException (Chỗ dòng 28 cũ của bro)
            throw new BusinessLogicException("ERR_PIN_LOCKED", "Tài khoản đang bị tạm khóa do nhập sai PIN quá nhiều lần. Vui lòng thử lại sau!");
        }

        // Nếu thời gian khóa đã hết, tự động mở khóa
        if (security.getLockUntil() != null && security.getLockUntil().isBefore(LocalDateTime.now())) {
            security.setLockUntil(null);
            security.setFailedPinAttempts(0);
        }

        // 2. Chặn nếu User chưa cài PIN
        if (security.getPinHash() == null || !security.getIsPinSetup()) {
             // 🚀 BƯỚC 3: Ném BusinessLogicException
             throw new BusinessLogicException("ERR_PIN_NOT_SETUP", "Người dùng chưa cài đặt Mã PIN!");
        }

        // 3. Giải mã Hash và so sánh
        boolean isMatch = passwordEncoder.matches(rawPin, security.getPinHash());

        // 4. Xử lý kết quả và đếm số lần sai
        if (isMatch) {
            security.setFailedPinAttempts(0); // Nhập đúng thì reset bộ đếm về 0
            security.setLockUntil(null);
            userSecurityRepository.save(security);
            return true;
        } else {
            // NẾU NHẬP SAI: Tăng biến đếm và tính toán số lần còn lại
            int attempts = security.getFailedPinAttempts() + 1;
            security.setFailedPinAttempts(attempts);
            
            int maxAttempts = 5;
            int remainingAttempts = maxAttempts - attempts;
            
            if (attempts >= maxAttempts) {
                // Nếu sai 5 lần -> Phạt thẻ đỏ, khóa 15 phút
                security.setLockUntil(LocalDateTime.now().plusMinutes(15));
                userSecurityRepository.save(security);
                // 🚀 BƯỚC 4: Ném BusinessLogicException
                throw new BusinessLogicException("ERR_PIN_LOCKED_NOW", "Tài khoản đã bị khóa 15 phút do nhập sai PIN " + maxAttempts + " lần!");
            } else {
                userSecurityRepository.save(security);
                // 🚀 BƯỚC 5: Ném BusinessLogicException báo số lần còn lại thay vì return false
                throw new BusinessLogicException("ERR_WRONG_PIN", "Mã PIN không chính xác! Bạn còn " + remainingAttempts + " lần thử.");
            }
        }
    }
   
    
    @Transactional
    public String setupOrChangePin(Long userId, String oldPin, String newPin) {
        UserSecurity security = userSecurityRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessLogicException("ERR_NOT_FOUND", "Lỗi hệ thống: Không tìm thấy hồ sơ bảo mật!"));

        // CÀI ĐẶT LẦN ĐẦU
        if (!security.getIsPinSetup() || security.getPinHash() == null) {
            security.setPinHash(passwordEncoder.encode(newPin));
            security.setIsPinSetup(true);
            security.setLastPinChange(LocalDateTime.now());
            userSecurityRepository.save(security);
            return "Cài đặt Mã PIN lần đầu thành công!";
        }

        // ĐỔI MÃ PIN (Yêu cầu PIN cũ)
        if (oldPin == null || oldPin.isEmpty()) {
            throw new BusinessLogicException("ERR_BAD_REQUEST", "Vui lòng nhập Mã PIN cũ để xác thực!");
        }

        // Kiểm tra PIN cũ có đúng không
        if (!passwordEncoder.matches(oldPin, security.getPinHash())) {
            throw new BusinessLogicException("ERR_WRONG_PIN", "Mã PIN cũ không chính xác!");
        }

        // Cập nhật PIN mới
        security.setPinHash(passwordEncoder.encode(newPin));
        security.setLastPinChange(LocalDateTime.now());
        userSecurityRepository.save(security);
        
        return "Thay đổi Mã PIN thành công!";
    }
}