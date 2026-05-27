
package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.LoginRequest;
import com.datn.finrisk.application.dtos.LoginResponse;
import com.datn.finrisk.application.dtos.UserDTO;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserDevice;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.UserDeviceRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.security.JwtUtils;
import com.datn.finrisk.core.services.AuditLogService;
import com.datn.finrisk.core.services.AuthService;
import com.datn.finrisk.core.services.JwtBlocklistService;
import com.datn.finrisk.core.services.RateLimitService;
import com.datn.finrisk.core.utils.DeviceFingerprintUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
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

    @Autowired
    private JwtBlocklistService jwtBlocklistService;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

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

            LoginResponse loginResult = authService.login(loginRequest);
            User user = loginResult.getUserEntity();
            rateLimitService.clearLoginAttempts(username);

            String currentIp = request.getRemoteAddr();
            String fingerprint = DeviceFingerprintUtil.resolve(
                    request.getHeader("User-Agent"),
                    request.getHeader("X-Device-Fingerprint"));

            // Suspicious = device has never been seen for this user before
            boolean isSuspicious = userDeviceRepository
                    .findByUserIdAndDeviceFingerprint(user.getId(), fingerprint)
                    .isEmpty();

            if (isSuspicious) {
                log.warn("[AUTH] New device detected user={} fingerprint={} ip={}", username, fingerprint, currentIp);
                auditLogService.logAction(username, "SUSPICIOUS_LOGIN",
                        "Phát hiện đăng nhập từ thiết bị mới. Fingerprint: " + fingerprint + " IP: " + currentIp);
            }

            user.setSuspiciousSession(isSuspicious);
            user.setLastLoginIp(currentIp);
            user.setLastLoginDevice(fingerprint);

            auditLogService.logAction(username, "LOGIN_SUCCESS", "Đăng nhập hệ thống thành công.");

            String accessToken = jwtUtils.generateJwtToken(user);
            String refreshToken = jwtUtils.generateRefreshToken(user);

            user.setCurrentRefreshToken(refreshToken);
            userRepository.save(user);

            upsertUserDevice(user, fingerprint, currentIp);
 
            UserDTO userSafeData = new UserDTO(user);
            
            Account userAccount = accountRepository.findByUser(user).orElse(null);
            if (userAccount != null) {
                userSafeData.setAccountNumber(userAccount.getAccountNumber());
                userSafeData.setBalance(userAccount.getBalance());
                log.debug("[AUTH] Account linked user={} account={}", username, userAccount.getAccountNumber());
            } else {
                log.warn("[AUTH] No bank account found for user={}", username);
            }
 

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("user", userSafeData); 
            
 
            response.put("isPinSetup", loginResult.isPinSetup());
            response.put("isFaceSetup", loginResult.isFaceSetup());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            rateLimitService.recordFailedLogin(username);
            auditLogService.logAction(username, "LOGIN_FAILED", "Sai mật khẩu. Lý do: " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            String jwt = headerAuth.substring(7);
            if (jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwt);
                jwtBlocklistService.block(jwt, jwtUtils.getExpirationFromToken(jwt));
                userRepository.findByUsername(username).ifPresent(user -> {
                    user.setCurrentRefreshToken(null);
                    userRepository.save(user);
                });
                auditLogService.logAction(username, "LOGOUT", "Đăng xuất thành công.");
            }
        }
        return ResponseEntity.ok("Đăng xuất thành công.");
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
                log.error("[AUTH][SECURITY] Refresh token theft detected — rotating token for user={}", username);
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

    private void upsertUserDevice(User user, String fingerprint, String ip) {
        UserDevice device = userDeviceRepository
                .findByUserIdAndDeviceFingerprint(user.getId(), fingerprint)
                .orElseGet(() -> {
                    UserDevice d = new UserDevice();
                    d.setUser(user);
                    d.setDeviceFingerprint(fingerprint);
                    d.setDeviceName(fingerprint);
                    d.setIsTrusted(true);
                    return d;
                });
        device.setLastUsedIp(ip);
        device.setLastUsedAt(LocalDateTime.now());
        userDeviceRepository.save(device);
    }
}