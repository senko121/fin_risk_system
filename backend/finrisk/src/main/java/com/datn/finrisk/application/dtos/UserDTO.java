// package com.datn.finrisk.application.dtos;

// import com.datn.finrisk.core.entities.User;
// import lombok.Data;
// import java.math.BigDecimal; //   Nhớ phải import cái này để dùng BigDecimal

// @Data
// public class UserDTO {
//     private Long id;
//     private String username;
//     private String fullName;
//     private String phoneNumber;
//     private String email;
//     private String role;
//     private String base64FaceImage;
    
//     //   BỔ SUNG 2 BIẾN NÀY ĐỂ REACT HIỂN THỊ SỐ TÀI KHOẢN VÀ SỐ DƯ
//     private String accountNumber;
//     private BigDecimal balance;

//     // Hàm Mapper: Biến Entity thành DTO
//     public UserDTO(User user) {
//         this.id = user.getId();
//         this.username = user.getUsername();
//         this.fullName = user.getFullName();
//         this.phoneNumber = user.getPhoneNumber();
//         this.email = user.getEmail();
//         this.role = user.getRole().name();
//         this.base64FaceImage = user.getBase64FaceImage();
//     }
// }

package com.datn.finrisk.application.dtos;

import com.datn.finrisk.core.entities.User;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String fullName;
    private String phoneNumber;
    private String email;
    private String role;
    
    // 🚀 ĐÃ XÓA: Biến base64FaceImage nặng nề để giải phóng băng thông
    // private String base64FaceImage; 
    
    // 🚀 THÊM MỚI: Cờ nhận thức sinh trắc học cho Frontend
    private boolean isFaceSetup; 
    
    private String accountNumber;
    private BigDecimal balance;

    // Hàm Mapper: Biến Entity thành DTO
    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.fullName = user.getFullName();
        this.phoneNumber = user.getPhoneNumber();
        this.email = user.getEmail();
        this.role = user.getRole().name();
        
        // 🚀 THÊM LOGIC MỚI: Tự động check DB xem có ảnh hay không để báo về Frontend
        this.isFaceSetup = user.getBase64FaceImage() != null && !user.getBase64FaceImage().trim().isEmpty();
    }
}