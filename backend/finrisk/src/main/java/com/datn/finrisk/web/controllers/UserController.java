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


    // API 1: Đăng ký khuôn mặt gốc (Lưu Base64 vào Database)
    @PostMapping("/register-face")
    public ResponseEntity<?> registerFace(@RequestBody FaceRegisterRequest request) {
        try {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

            if (request.getBase64FaceImage() == null || request.getBase64FaceImage().isEmpty()) {
                return ResponseEntity.badRequest().body("Dữ liệu ảnh không hợp lệ!");
            }

            // Lưu ảnh Base64 vào DB
            user.setBase64FaceImage(request.getBase64FaceImage());
            userRepository.save(user);

            return ResponseEntity.ok("Đăng ký dữ liệu khuôn mặt thành công!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    // API 2: Lấy dữ liệu khuôn mặt gốc ra để chuẩn bị đưa cho Python sau này
    @GetMapping("/{userId}/face-image")
    public ResponseEntity<?> getFaceImage(@PathVariable Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

            if (user.getBase64FaceImage() == null || user.getBase64FaceImage().isEmpty()) {
                return ResponseEntity.badRequest().body("Người dùng chưa đăng ký khuôn mặt!");
            }

            // Trả về ảnh Base64
            return ResponseEntity.ok(user.getBase64FaceImage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }


    // API 3: Đổi mật khẩu an toàn (Băm BCrypt)
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
        // Dùng method mới vừa viết ở trên
        return ResponseEntity.ok(contactRepo.findContactsWithLastTransaction(userId));
    }


    // ==========================================
    // API 4: TẠO MÃ QR NHẬN TIỀN CHO USER
    // ==========================================
    @GetMapping("/{userId}/generate-qr")
    public ResponseEntity<?> generateMyQRCode(@PathVariable Long userId) {
        try {
            // 1. Tìm User
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

            // 2. Tìm Tài khoản của User đó (Nhờ cái file bro vừa gửi)
            Account account = accountRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Người dùng chưa có tài khoản ngân hàng!"));

            // 3. Tạo nội dung chuỗi QR (Format: FINRISK|SỐ_TÀI_KHOẢN|TÊN_NGƯỜI_NHẬN)
            String qrContent = String.format("FINRISK|%s|%s", account.getAccountNumber(), user.getFullName());

            // 4. Dùng thuật toán ZXing vẽ ma trận QR Code (Kích thước 300x300 pixel)
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, 300, 300);

            // 5. Chuyển ma trận thành ảnh PNG và mã hóa sang Base64
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();
            
            // Ép thêm tiền tố "data:image/png;base64," để React nhét thẳng vào thẻ <img> được luôn
            String base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);

            // Trả về cho Frontend
            return ResponseEntity.ok(Map.of("qrCodeBase64", base64Image, "content", qrContent));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Lỗi tạo mã QR: " + e.getMessage()));
        }
    }
}