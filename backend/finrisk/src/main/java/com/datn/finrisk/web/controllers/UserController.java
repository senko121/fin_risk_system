package com.datn.finrisk.web.controllers;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.repository.AccountRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import com.datn.finrisk.application.dtos.FaceRegisterRequest;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.services.UserService; 
import com.datn.finrisk.core.repository.UserContactRepository;
import com.datn.finrisk.application.dtos.PinSetupRequest;
import com.datn.finrisk.core.services.PinService;
import com.datn.finrisk.core.repository.UserSecurityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map; 

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserContactRepository contactRepo;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PinService pinService;

    @Autowired
    private UserSecurityRepository userSecurityRepository;
 
    @PostMapping("/register-face")
    public ResponseEntity<?> registerFace(@RequestBody FaceRegisterRequest request) {
        try {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

            if (request.getBase64FaceImage() == null || request.getBase64FaceImage().isEmpty()) {
                return ResponseEntity.badRequest().body("Dữ liệu ảnh không hợp lệ!");
            }
 
            user.setBase64FaceImage(request.getBase64FaceImage());
            userRepository.save(user);

            return ResponseEntity.ok("Đăng ký dữ liệu khuôn mặt thành công!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
 
    @GetMapping("/{userId}/face-image")
    public ResponseEntity<?> getFaceImage(@PathVariable Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

            if (user.getBase64FaceImage() == null || user.getBase64FaceImage().isEmpty()) {
                return ResponseEntity.badRequest().body("Người dùng chưa đăng ký khuôn mặt!");
            }
 
            return ResponseEntity.ok(user.getBase64FaceImage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

 
    @PostMapping("/{id}/change-password")
    public ResponseEntity<?> changePassword(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String oldPass = request.get("oldPassword");
            String newPass = request.get("newPassword");
            
            userService.changePassword(id, oldPass, newPass);
            return ResponseEntity.ok("Cập nhật mật khẩu thành công!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{userId}/contacts")
    public ResponseEntity<?> getContacts(@PathVariable Long userId) {
 
        return ResponseEntity.ok(contactRepo.findContactsWithLastTransaction(userId));
    }

 
    @GetMapping("/{userId}/generate-qr")
    public ResponseEntity<?> generateMyQRCode(@PathVariable Long userId) {
        try {
 
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
 
            Account account = accountRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Người dùng chưa có tài khoản ngân hàng!"));
 
            String qrContent = String.format("FINRISK|%s|%s", account.getAccountNumber(), user.getFullName());
 
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, 300, 300);
  
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();
              
            String base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
 
            return ResponseEntity.ok(Map.of("qrCodeBase64", base64Image, "content", qrContent));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Lỗi tạo mã QR: " + e.getMessage()));
        }
    } 
    @PostMapping("/security/pin")
    public ResponseEntity<?> setupOrChangePin(@RequestBody PinSetupRequest request) {
        try { 
            String message = pinService.setupOrChangePin(
                    request.getUserId(), 
                    request.getOldPin(), 
                    request.getNewPin()
            );
             
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", message));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "message", e.getMessage()));
        }
    }

    @GetMapping("/{userId}/security-status")
    public ResponseEntity<?> getSecurityStatus(@PathVariable Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

            com.datn.finrisk.core.entities.UserSecurity security = userSecurityRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bảo mật!"));

            Map<String, Object> status = new java.util.HashMap<>(); 
            status.put("isPinSetup", security.getIsPinSetup()); 
            status.put("isFaceSetup", user.getBase64FaceImage() != null && !user.getBase64FaceImage().isEmpty());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}