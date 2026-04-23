package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.FaceRegisterRequest;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.services.UserService; //   IMPORT THÊM SERVICE NÀY
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map; //   IMPORT THÊM THẰNG NÀY ĐỂ ĐỌC MAP

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    //   BƠM THÊM USER SERVICE VÀO ĐÂY ĐỂ XỬ LÝ ĐỔI PASS
    @Autowired
    private UserService userService;

    // ==========================================================
    // KHU VỰC 1: CÁC API VỀ FACE ID (CỦA BRO GIỮ NGUYÊN)
    // ==========================================================

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

    // ==========================================================
    // KHU VỰC 2: CÁC API VỀ BẢO MẬT TÀI KHOẢN (THÊM MỚI)
    // ==========================================================

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
}