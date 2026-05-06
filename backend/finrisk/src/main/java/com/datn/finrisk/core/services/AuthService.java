

// package com.datn.finrisk.core.services;

// import com.datn.finrisk.application.dtos.LoginRequest;
// import com.datn.finrisk.application.dtos.LoginResponse;
// import com.datn.finrisk.core.entities.Account;
// import com.datn.finrisk.core.entities.User;
// import com.datn.finrisk.core.entities.UserSecurity;
// import com.datn.finrisk.core.repository.AccountRepository;
// import com.datn.finrisk.core.repository.UserRepository;
// import com.datn.finrisk.core.repository.UserSecurityRepository;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;

// import java.util.List;
// import java.util.Optional;

// @Service
// public class AuthService {

//     @Autowired private UserRepository userRepository;
//     @Autowired private AccountRepository accountRepository;
//     @Autowired private UserSecurityRepository userSecurityRepository; // Bổ sung kho bảo mật

//     public LoginResponse login(LoginRequest request) {
//         LoginResponse response = new LoginResponse();

//         // 1. Tìm user
//         User user = userRepository.findByUsername(request.getUsername())
//                 .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại!"));

//         // 2. Tìm thông tin bảo mật của User này
//         UserSecurity security = userSecurityRepository.findByUserId(user.getId())
//                 .orElseThrow(() -> new RuntimeException("Lỗi hệ thống: Không tìm thấy hồ sơ bảo mật!"));

//         // 3. Kiểm tra mật khẩu (Tạm thời so sánh chuỗi. Sau này đưa BCrypt vào đây nhé)
//         if (!security.getPasswordHash().equals(request.getPassword())) {
//             throw new RuntimeException("Sai mật khẩu!");
//         }

//         // 4. Lấy tài khoản ngân hàng
//         List<Account> accounts = accountRepository.findAll();
//         Account userAccount = accounts.stream()
//                 .filter(acc -> acc.getUser().getId().equals(user.getId()))
//                 .findFirst()
//                 .orElseThrow(() -> new RuntimeException("Người dùng chưa có tài khoản ngân hàng!"));

//         response.setUserId(user.getId());
//         response.setFullName(user.getFullName());
//         response.setAccountNumber(userAccount.getAccountNumber());
//         response.setBalance(userAccount.getBalance());
//         response.setMessage("Đăng nhập thành công!");

//         return response;
//     }
// }


package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.LoginRequest;
import com.datn.finrisk.application.dtos.LoginResponse;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.repository.UserSecurityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserSecurityRepository userSecurityRepository; 

    public LoginResponse login(LoginRequest request) {
        LoginResponse response = new LoginResponse();

        // 1. Tìm user
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại!"));

        // 2. Tìm thông tin bảo mật của User này
        UserSecurity security = userSecurityRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Lỗi hệ thống: Không tìm thấy hồ sơ bảo mật!"));

        // 3. Kiểm tra mật khẩu (Tạm thời so sánh chuỗi. Sau này đưa BCrypt vào đây nhé)
        if (!security.getPasswordHash().equals(request.getPassword())) {
            throw new RuntimeException("Sai mật khẩu!");
        }

        // 4. Lấy tài khoản ngân hàng
        List<Account> accounts = accountRepository.findAll();
        Account userAccount = accounts.stream()
                .filter(acc -> acc.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Người dùng chưa có tài khoản ngân hàng!"));

        response.setUserId(user.getId());
        response.setFullName(user.getFullName());
        response.setAccountNumber(userAccount.getAccountNumber());
        response.setBalance(userAccount.getBalance());
        response.setMessage("Đăng nhập thành công!");
        
        // 🚀 ĐÃ NÂNG CẤP: Tự động check DB xem có ảnh hay không để báo về cho React
        boolean hasFace = user.getBase64FaceImage() != null && !user.getBase64FaceImage().trim().isEmpty();
        response.setFaceSetup(hasFace);

        return response;
    }
}