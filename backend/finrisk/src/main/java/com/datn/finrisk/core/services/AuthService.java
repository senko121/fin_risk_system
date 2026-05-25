 

package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.LoginRequest;
import com.datn.finrisk.application.dtos.LoginResponse;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.repository.UserSecurityRepository;
import lombok.extern.slf4j.Slf4j;  
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
 
@Slf4j  
@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserSecurityRepository userSecurityRepository; 
    @Autowired private PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        log.info("=== BẮT ĐẦU XỬ LÝ LOGIN CHO USER: {} ===", request.getUsername()); 
 
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.error("❌ LỖI: Không tìm thấy username: {}", request.getUsername());
                    return new RuntimeException("Tài khoản không tồn tại!");
                });

        UserSecurity security = user.getUserSecurity();
        if (security == null) {
            throw new RuntimeException("Lỗi hệ thống: Không tìm thấy hồ sơ bảo mật!");
        }
 
        String rawPassword = request.getPassword();
        String dbHash = security.getPasswordHash();

        log.info("🔍 ĐANG SO SÁNH MẬT KHẨU...");

        boolean isMatch = passwordEncoder.matches(rawPassword, dbHash);
        log.debug("🎯 KẾT QUẢ SO SÁNH: {}", isMatch);

        if (!isMatch) {
            log.warn("⚠️ CẢNH BÁO: Sai mật khẩu cho user: {}", request.getUsername());
            throw new RuntimeException("Sai mật khẩu!");
        }
 
        Account userAccount = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> {
                    log.error("❌ LỖI: User {} không có tài khoản ngân hàng!", request.getUsername());
                    return new RuntimeException("Người dùng chưa có tài khoản ngân hàng!");
                });

        log.info("✅ LOGIN THÀNH CÔNG CHO USER: {}", request.getUsername());

        LoginResponse response = new LoginResponse();
        response.setUserId(user.getId());
        response.setFullName(user.getFullName());
        response.setAccountNumber(userAccount.getAccountNumber());
        response.setBalance(userAccount.getBalance());
        response.setMessage("Đăng nhập thành công!");
        
        boolean hasFace = user.hasFaceEmbedding() || user.hasLegacyFaceImage();
        response.setFaceSetup(hasFace);
        
        boolean hasPin = security.getPinHash() != null && !security.getPinHash().trim().isEmpty();
        response.setPinSetup(hasPin);
        response.setUserEntity(user);

        return response;
    }
}