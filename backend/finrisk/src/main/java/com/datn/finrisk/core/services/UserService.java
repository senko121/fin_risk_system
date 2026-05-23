package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.repository.UserSecurityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSecurityRepository userSecurityRepository;  

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) throws Exception {
 
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception("Không tìm thấy người dùng!"));
 
        UserSecurity security = userSecurityRepository.findByUserId(userId)
                .orElseThrow(() -> new Exception("Lỗi hệ thống: Không tìm thấy hồ sơ bảo mật!"));
 
        if (!passwordEncoder.matches(oldPassword, security.getPasswordHash())) {
            throw new Exception("Mật khẩu hiện tại không chính xác!");
        }
 
        if (passwordEncoder.matches(newPassword, security.getPasswordHash())) {
            throw new Exception("Mật khẩu mới không được trùng với mật khẩu cũ!");
        }
 
        security.setPasswordHash(passwordEncoder.encode(newPassword));
        security.setLastPasswordChange(LocalDateTime.now()); 
        
        userSecurityRepository.save(security);
    }
}