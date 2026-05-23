package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.exceptions.BusinessLogicException;   
import com.datn.finrisk.core.repository.UserSecurityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

//Transaction B11: kiểm tra PIN người dùn vừa nhập có khớp vơi mã PIN hash trông db ko bằng verifyPin -> Transaction B2 Phase 2: Accountrepository
@Service
public class PinService {

    @Autowired
    private UserSecurityRepository userSecurityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

 
    @Transactional(noRollbackFor = BusinessLogicException.class)
    public boolean verifyPin(UserSecurity security, String rawPin) {

 
        if (security.getLockUntil() != null && security.getLockUntil().isAfter(LocalDateTime.now())) {
 
            throw new BusinessLogicException("ERR_PIN_LOCKED", "Tài khoản đang bị tạm khóa do nhập sai PIN quá nhiều lần. Vui lòng thử lại sau!");
        }
 
        if (security.getLockUntil() != null && security.getLockUntil().isBefore(LocalDateTime.now())) {
            security.setLockUntil(null);
            security.setFailedPinAttempts(0);
        }
 
        if (security.getPinHash() == null || !security.getIsPinSetup()) {
 
             throw new BusinessLogicException("ERR_PIN_NOT_SETUP", "Người dùng chưa cài đặt Mã PIN!");
        }
 
        boolean isMatch = passwordEncoder.matches(rawPin, security.getPinHash());
 
        if (isMatch) {
            security.setFailedPinAttempts(0);  
            security.setLockUntil(null);
            userSecurityRepository.save(security);
            return true;
        } else {
 
            int attempts = security.getFailedPinAttempts() + 1;
            security.setFailedPinAttempts(attempts);
            
            int maxAttempts = 5;
            int remainingAttempts = maxAttempts - attempts;
            
            if (attempts >= maxAttempts) {
 
                security.setLockUntil(LocalDateTime.now().plusMinutes(15));
                userSecurityRepository.save(security);
 
                throw new BusinessLogicException("ERR_PIN_LOCKED_NOW", "Tài khoản đã bị khóa 15 phút do nhập sai PIN " + maxAttempts + " lần!");
            } else {
                userSecurityRepository.save(security);
 
                throw new BusinessLogicException("ERR_WRONG_PIN", "Mã PIN không chính xác! Bạn còn " + remainingAttempts + " lần thử.");
            }
        }
    }
   
    
    @Transactional
    public String setupOrChangePin(Long userId, String oldPin, String newPin) {
        UserSecurity security = userSecurityRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessLogicException("ERR_NOT_FOUND", "Lỗi hệ thống: Không tìm thấy hồ sơ bảo mật!"));

 
        if (!security.getIsPinSetup() || security.getPinHash() == null) {
            security.setPinHash(passwordEncoder.encode(newPin));
            security.setIsPinSetup(true);
            security.setLastPinChange(LocalDateTime.now());
            userSecurityRepository.save(security);
            return "Cài đặt Mã PIN lần đầu thành công!";
        }

 
        if (oldPin == null || oldPin.isEmpty()) {
            throw new BusinessLogicException("ERR_BAD_REQUEST", "Vui lòng nhập Mã PIN cũ để xác thực!");
        }

 
        if (!passwordEncoder.matches(oldPin, security.getPinHash())) {
            throw new BusinessLogicException("ERR_WRONG_PIN", "Mã PIN cũ không chính xác!");
        }

 
        security.setPinHash(passwordEncoder.encode(newPin));
        security.setLastPinChange(LocalDateTime.now());
        userSecurityRepository.save(security);
        
        return "Thay đổi Mã PIN thành công!";
    }
}