package com.datn.finrisk.web.controllers;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.repository.AccountRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import com.datn.finrisk.application.dtos.FaceRegisterBatchRequest;
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
     * Đăng ký khuôn mặt nhiều góc (front, left, right, up, down).
     * Gọi Python /enroll-face-batch → nhận list embeddings → lưu vào face_embeddings.
     * Nên dùng endpoint này thay cho /register-face để verify ổn định hơn.
     */
    @PostMapping("/register-face-batch")
    public ResponseEntity<?> registerFaceBatch(@RequestBody FaceRegisterBatchRequest request) {
        try {
            if (request.getUserId() == null) {
                return ResponseEntity.badRequest().body("userId không được để trống!");
            }
            if (request.getImagesBase64() == null || request.getImagesBase64().isEmpty()) {
                return ResponseEntity.badRequest().body("Danh sách ảnh không hợp lệ!");
            }
            if (request.getImagesBase64().size() < 2) {
                return ResponseEntity.badRequest().body(
                        "Cần ít nhất 2 ảnh (front + 1 góc khác) để đăng ký multi-angle!");
            }

            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

            String embeddingsJson = faceEnrollService.enrollFaceBatch(
                    user.getId(), request.getImagesBase64());

            if (embeddingsJson == null) {
                return ResponseEntity.badRequest().body(
                        "Không thể nhận diện khuôn mặt trong các ảnh. Vui lòng chụp lại rõ hơn!");
            }

            user.setFaceEmbeddings(embeddingsJson);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đăng ký dữ liệu khuôn mặt đa góc thành công!",
                    "angles_accepted", request.getImagesBase64().size()
            ));

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
            status.put("isFaceSetup", user.hasFaceEmbeddings());
            status.put("faceMode", user.hasFaceEmbeddings() ? "MULTI_ANGLE" : "NONE");

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}