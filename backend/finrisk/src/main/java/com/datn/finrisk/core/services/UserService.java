package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) throws Exception {
        // 1. Tìm User dưới Database
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception("Không tìm thấy người dùng!"));

        // 2. Kiểm tra mật khẩu cũ có khớp không (Dùng BCrypt để so sánh)
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new Exception("Mật khẩu hiện tại không chính xác!");
        }

        // 3. Chặn đổi trùng mật khẩu cũ
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new Exception("Mật khẩu mới không được trùng với mật khẩu cũ!");
        }

        // 4. Băm mật khẩu mới và Lưu
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}