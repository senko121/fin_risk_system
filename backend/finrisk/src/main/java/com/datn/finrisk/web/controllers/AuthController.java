// package com.datn.finrisk.web.controllers;

// import com.datn.finrisk.application.dtos.LoginRequest;
// import com.datn.finrisk.application.dtos.UserDTO; 
// import com.datn.finrisk.core.entities.User;
// import com.datn.finrisk.core.entities.Account; //   1. IMPORT ACCOUNT
// import com.datn.finrisk.core.repository.UserRepository;
// import com.datn.finrisk.core.repository.AccountRepository; //   2. IMPORT ACCOUNT REPO
// import com.datn.finrisk.core.security.JwtUtils;
// import com.datn.finrisk.core.services.AuthService;
// import com.datn.finrisk.core.services.RateLimitService;
// import com.datn.finrisk.core.services.AuditLogService;
// import jakarta.servlet.http.HttpServletRequest;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// import java.util.HashMap;
// import java.util.Map;

// @RestController
// @RequestMapping("/api/auth")
// @CrossOrigin(origins = "http://localhost:5173")
// public class AuthController {

//     @Autowired
//     private AuthService authService;

//     @Autowired
//     private JwtUtils jwtUtils;

//     @Autowired
//     private UserRepository userRepository;

//     //   3. GỌI THẰNG QUẢN LÝ KHO TÀI KHOẢN VÀO ĐÂY
//     @Autowired
//     private AccountRepository accountRepository;

//     @Autowired
//     private RateLimitService rateLimitService;

//     @Autowired
//     private AuditLogService auditLogService;

//     @PostMapping("/login")
//     public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
//         String username = loginRequest.getUsername();

//         if (rateLimitService.isLoginBlocked(username)) {
//             long secondsLeft = rateLimitService.getLockTimeRemaining(username);
//             long minutesLeft = (secondsLeft / 60) + 1;
//             auditLogService.logAction(username, "LOGIN_BLOCKED_ATTEMPT", "Cố tình đăng nhập khi đang bị khóa Rate Limit.");
//             return ResponseEntity.status(429).body("Tài khoản đã bị khóa do đăng nhập sai quá nhiều lần. Vui lòng thử lại sau " + minutesLeft + " phút!");
//         }

//         try {
//             authService.login(loginRequest);
//             rateLimitService.clearLoginAttempts(username);

//             User user = userRepository.findByUsername(username)
//                     .orElseThrow(() -> new RuntimeException("Lỗi hệ thống: Không tìm thấy user sau khi login!"));

//             // =========================================================
//             // HỆ THỐNG TRUY VẾT IP VÀ THIẾT BỊ (FINGERPRINTING)
//             // =========================================================
//             String currentIp = request.getRemoteAddr();
//             String currentDevice = request.getHeader("User-Agent");
            
//             if (currentDevice != null && currentDevice.length() > 250) {
//                 currentDevice = currentDevice.substring(0, 250);
//             }

//             boolean isSuspicious = false;

//             if (user.getLastLoginIp() != null && user.getLastLoginDevice() != null) {
//                 if (!user.getLastLoginIp().equals(currentIp) || !user.getLastLoginDevice().equals(currentDevice)) {
//                     isSuspicious = true;
//                     System.err.println("🚨 CẢNH BÁO: User " + username + " đăng nhập từ thiết bị/IP lạ!");
//                     auditLogService.logAction(username, "SUSPICIOUS_LOGIN", "Phát hiện đăng nhập từ môi trường lạ. IP: " + currentIp);
//                 }
//             }

//             user.setSuspiciousSession(isSuspicious);
//             user.setLastLoginIp(currentIp);
//             user.setLastLoginDevice(currentDevice);
//             // =========================================================

//             auditLogService.logAction(username, "LOGIN_SUCCESS", "Đăng nhập hệ thống thành công.");

//             String accessToken = jwtUtils.generateJwtToken(user);
//             String refreshToken = jwtUtils.generateRefreshToken(user);

//             user.setCurrentRefreshToken(refreshToken);
//             userRepository.save(user);

//             // =========================================================
//             //   4. MẶC ÁO KHOÁC VÀ NHÉT TIỀN VÀO TÚI CHO USERDTO
//             // =========================================================
//             UserDTO userSafeData = new UserDTO(user);
            
//             Account userAccount = accountRepository.findByUser(user).orElse(null);
//             if (userAccount != null) {
//                 userSafeData.setAccountNumber(userAccount.getAccountNumber());
//                 userSafeData.setBalance(userAccount.getBalance());
//                 System.out.println("✅ Đã móc thành công tài khoản: " + userAccount.getAccountNumber());
//             } else {
//                 System.out.println("❌ CẢNH BÁO: User này chưa có tài khoản ngân hàng dưới DB!");
//             }
//             // =========================================================

//             Map<String, Object> response = new HashMap<>();
//             response.put("accessToken", accessToken);
//             response.put("refreshToken", refreshToken);
//             response.put("user", userSafeData); 

//             return ResponseEntity.ok(response);

//         } catch (RuntimeException e) {
//             rateLimitService.recordFailedLogin(username);
//             auditLogService.logAction(username, "LOGIN_FAILED", "Sai mật khẩu. Lý do: " + e.getMessage());
//             return ResponseEntity.badRequest().body(e.getMessage());
//         }
//     }

//     @PostMapping("/refresh-token")
//     public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
//         String requestRefreshToken = request.get("refreshToken");

//         if (requestRefreshToken != null && jwtUtils.validateJwtToken(requestRefreshToken)) {
//             String username = jwtUtils.getUserNameFromJwtToken(requestRefreshToken);
//             User user = userRepository.findByUsername(username)
//                     .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

//             if (!requestRefreshToken.equals(user.getCurrentRefreshToken())) {
//                 user.setCurrentRefreshToken(null); 
//                 userRepository.save(user);
//                 System.err.println("🚨 BÁO ĐỘNG: Phát hiện Token bị đánh cắp của user: " + username);
//                 auditLogService.logAction(username, "SECURITY_BREACH_DETECTED", "Hệ thống Theft Detection phát hiện Refresh Token bất thường.");
//                 return ResponseEntity.status(403).body("Cảnh báo bảo mật: Token bất thường. Vui lòng đăng nhập lại ngay lập tức!");
//             }

//             String newAccessToken = jwtUtils.generateJwtToken(user);
//             String newRefreshToken = jwtUtils.generateRefreshToken(user);

//             user.setCurrentRefreshToken(newRefreshToken);
//             userRepository.save(user);

//             Map<String, Object> response = new HashMap<>();
//             response.put("accessToken", newAccessToken);
//             response.put("refreshToken", newRefreshToken); 

//             return ResponseEntity.ok(response);
//         }

//         return ResponseEntity.status(403).body("Refresh Token không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại!");
//     }
// }

package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.LoginRequest;
import com.datn.finrisk.application.dtos.LoginResponse; // 🚀 Bổ sung import này
import com.datn.finrisk.application.dtos.UserDTO; 
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.Account; 
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.repository.AccountRepository; 
import com.datn.finrisk.core.security.JwtUtils;
import com.datn.finrisk.core.services.AuthService;
import com.datn.finrisk.core.services.RateLimitService;
import com.datn.finrisk.core.services.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private AuditLogService auditLogService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String username = loginRequest.getUsername();

        if (rateLimitService.isLoginBlocked(username)) {
            long secondsLeft = rateLimitService.getLockTimeRemaining(username);
            long minutesLeft = (secondsLeft / 60) + 1;
            auditLogService.logAction(username, "LOGIN_BLOCKED_ATTEMPT", "Cố tình đăng nhập khi đang bị khóa Rate Limit.");
            return ResponseEntity.status(429).body("Tài khoản đã bị khóa do đăng nhập sai quá nhiều lần. Vui lòng thử lại sau " + minutesLeft + " phút!");
        }

        try {
            // 🚀 BƯỚC 1: HỨNG LẠI KẾT QUẢ TỪ AUTH SERVICE
            LoginResponse loginResult = authService.login(loginRequest);
            rateLimitService.clearLoginAttempts(username);

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Lỗi hệ thống: Không tìm thấy user sau khi login!"));

            // =========================================================
            // HỆ THỐNG TRUY VẾT IP VÀ THIẾT BỊ (FINGERPRINTING)
            // =========================================================
            String currentIp = request.getRemoteAddr();
            String currentDevice = request.getHeader("User-Agent");
            
            if (currentDevice != null && currentDevice.length() > 250) {
                currentDevice = currentDevice.substring(0, 250);
            }

            boolean isSuspicious = false;

            if (user.getLastLoginIp() != null && user.getLastLoginDevice() != null) {
                if (!user.getLastLoginIp().equals(currentIp) || !user.getLastLoginDevice().equals(currentDevice)) {
                    isSuspicious = true;
                    System.err.println("🚨 CẢNH BÁO: User " + username + " đăng nhập từ thiết bị/IP lạ!");
                    auditLogService.logAction(username, "SUSPICIOUS_LOGIN", "Phát hiện đăng nhập từ môi trường lạ. IP: " + currentIp);
                }
            }

            user.setSuspiciousSession(isSuspicious);
            user.setLastLoginIp(currentIp);
            user.setLastLoginDevice(currentDevice);
            // =========================================================

            auditLogService.logAction(username, "LOGIN_SUCCESS", "Đăng nhập hệ thống thành công.");

            String accessToken = jwtUtils.generateJwtToken(user);
            String refreshToken = jwtUtils.generateRefreshToken(user);

            user.setCurrentRefreshToken(refreshToken);
            userRepository.save(user);

            // =========================================================
            //   4. MẶC ÁO KHOÁC VÀ NHÉT TIỀN VÀO TÚI CHO USERDTO
            // =========================================================
            UserDTO userSafeData = new UserDTO(user);
            
            Account userAccount = accountRepository.findByUser(user).orElse(null);
            if (userAccount != null) {
                userSafeData.setAccountNumber(userAccount.getAccountNumber());
                userSafeData.setBalance(userAccount.getBalance());
                System.out.println("✅ Đã móc thành công tài khoản: " + userAccount.getAccountNumber());
            } else {
                System.out.println("❌ CẢNH BÁO: User này chưa có tài khoản ngân hàng dưới DB!");
            }
            // =========================================================

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("user", userSafeData); 
            
            // 🚀 BƯỚC 2: NHÉT 2 CỜ BẢO MẬT VÀO GÓI HÀNG JSON TRẢ VỀ FRONTEND
            // (Nếu IDE báo lỗi chữ isPinSetup(), bro đổi thành getPinSetup() hoặc isPinSetup tùy theo cách Lombok generate nhé)
            response.put("isPinSetup", loginResult.isPinSetup());
            response.put("isFaceSetup", loginResult.isFaceSetup());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            rateLimitService.recordFailedLogin(username);
            auditLogService.logAction(username, "LOGIN_FAILED", "Sai mật khẩu. Lý do: " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        String requestRefreshToken = request.get("refreshToken");

        if (requestRefreshToken != null && jwtUtils.validateJwtToken(requestRefreshToken)) {
            String username = jwtUtils.getUserNameFromJwtToken(requestRefreshToken);
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

            if (!requestRefreshToken.equals(user.getCurrentRefreshToken())) {
                user.setCurrentRefreshToken(null); 
                userRepository.save(user);
                System.err.println("🚨 BÁO ĐỘNG: Phát hiện Token bị đánh cắp của user: " + username);
                auditLogService.logAction(username, "SECURITY_BREACH_DETECTED", "Hệ thống Theft Detection phát hiện Refresh Token bất thường.");
                return ResponseEntity.status(403).body("Cảnh báo bảo mật: Token bất thường. Vui lòng đăng nhập lại ngay lập tức!");
            }

            String newAccessToken = jwtUtils.generateJwtToken(user);
            String newRefreshToken = jwtUtils.generateRefreshToken(user);

            user.setCurrentRefreshToken(newRefreshToken);
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", newAccessToken);
            response.put("refreshToken", newRefreshToken); 

            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(403).body("Refresh Token không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại!");
    }
}