// package com.datn.finrisk.application.dtos;

// import lombok.Data;
// import java.math.BigDecimal;
// import jakarta.validation.constraints.DecimalMin;
// import jakarta.validation.constraints.NotBlank;
// import jakarta.validation.constraints.NotNull;

// @Data
// public class TransactionRequest {
    
 
//     @NotNull(message = "LỖI: ID tài khoản gửi không được để trống")
//     private Long fromAccountId;

 
//     @NotBlank(message = "LỖI: Số tài khoản nhận không được để trống")
//     private String toAccount; 
    
//     @NotNull(message = "LỖI: Số tiền không được để trống")
 
//     @DecimalMin(value = "10000.0", message = "LỖI BẢO MẬT: Số tiền giao dịch phải lớn hơn hoặc bằng 10.000 VND")
//     private BigDecimal amount;  
    
//     private String description;
// }


package com.datn.finrisk.application.dtos;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime; //   BỔ SUNG IMPORT NÀY
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class TransactionRequest {
    
    @NotNull(message = "LỖI: ID tài khoản gửi không được để trống")
    private Long fromAccountId;

    @NotBlank(message = "LỖI: Số tài khoản nhận không được để trống")
    private String toAccount; 
    
    @NotNull(message = "LỖI: Số tiền không được để trống")
    @DecimalMin(value = "10000.0", message = "LỖI BẢO MẬT: Số tiền giao dịch phải lớn hơn hoặc bằng 10.000 VND")
    private BigDecimal amount;  
    
    private String description;

    // 🚀 THÊM TRƯỜNG NÀY ĐỂ HỨNG THỜI GIAN GIẢ LẬP TỪ POSTMAN (KHÔNG GẮN ANNOTATION VALIDATE)
    private LocalDateTime createdAt;
}