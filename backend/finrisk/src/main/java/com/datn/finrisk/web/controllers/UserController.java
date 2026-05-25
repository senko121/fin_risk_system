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
import com.datn.finrisk.core.services.FaceEnrollService;
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
public class UserController {

    @Autowired private UserRepository userRepository;
    @Autowired private UserService userService;
    @Autowired private UserContactRepository contactRepo;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PinService pinService;
    @Autowired private UserSecurityRepository userSecurityRepository;
    @Autowired private FaceEnrollService faceEnrollService;  // ← SERVICE MỚI

    /**
     * Đăng ký khuôn mặt.
     * Gọi Python /enroll-face → nhận embedding vector → lưu vào face_embedding.
     * KHÔNG còn lưu ảnh raw vào base64FaceImage nữa.
     */
    @PostMapping("/register-face")
    public ResponseEntity<?> registerFace(@RequestBody FaceRegisterRequest request) {
        try {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

            if (request.getBase64FaceImage() == null || request.getBase64FaceImage().isEmpty()) {
                return ResponseEntity.badRequest().body("Dữ liệu ảnh không hợp lệ!");
            }

            // Gọi Python để extract embedding
            String embeddingJson = faceEnrollService.enrollFace(
                    user.getId(), request.getBase64FaceImage());

            if (embeddingJson == null) {
                return ResponseEntity.badRequest().body(
                    "Không thể nhận diện khuôn mặt trong ảnh. Vui lòng chụp lại rõ hơn!");
            }

            // Lưu embedding vào DB, KHÔNG lưu ảnh raw
            user.setFaceEmbedding(embeddingJson);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đăng ký dữ liệu khuôn mặt thành công!"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    /**
     * @deprecated Endpoint cũ trả về ảnh raw — giữ lại để backward compat.
     * Sau khi tất cả user migrate xong thì xóa.
     */
    @Deprecated
    @GetMapping("/{userId}/face-image")
    public ResponseEntity<?> getFaceImage(@PathVariable Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

            // Ưu tiên trả về thông tin embedding (không trả ảnh raw vì lý do bảo mật)
            if (user.hasFaceEmbedding()) {
                return ResponseEntity.ok(Map.of(
                    "status", "EMBEDDING",
                    "message", "Người dùng đã đăng ký khuôn mặt (embedding mode)"
                ));
            }

            // Fallback: user cũ chưa migrate
            if (user.hasLegacyFaceImage()) {
                return ResponseEntity.ok(user.getBase64FaceImage());
            }

            return ResponseEntity.badRequest().body("Người dùng chưa đăng ký khuôn mặt!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/change-password")
    public ResponseEntity<?> changePassword(@PathVariable Long id,
                                            @RequestBody Map<String, String> request) {
        try {
            userService.changePassword(id, request.get("oldPassword"), request.get("newPassword"));
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

            String qrContent = String.format("FINRISK|%s|%s",
                    account.getAccountNumber(), user.getFullName());

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            String base64Image = "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(pngOutputStream.toByteArray());

            return ResponseEntity.ok(Map.of("qrCodeBase64", base64Image, "content", qrContent));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Lỗi tạo mã QR: " + e.getMessage()));
        }
    }

    @PostMapping("/security/pin")
    public ResponseEntity<?> setupOrChangePin(@RequestBody PinSetupRequest request) {
        try {
            String message = pinService.setupOrChangePin(
                    request.getUserId(), request.getOldPin(), request.getNewPin());
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
            com.datn.finrisk.core.entities.UserSecurity security = userSecurityRepository
                    .findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bảo mật!"));

            Map<String, Object> status = new java.util.HashMap<>();
            status.put("isPinSetup", security.getIsPinSetup());
            // isFaceSetup = true nếu có embedding MỚI hoặc ảnh cũ (backward compat)
            status.put("isFaceSetup", user.hasFaceEmbedding() || user.hasLegacyFaceImage());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}