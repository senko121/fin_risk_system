package com.datn.finrisk.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiErrorResponse {
    private LocalDateTime timestamp;
    private int status;              // Mã HTTP (ví dụ: 400, 404, 500)
    private String errorCode;        // Mã lỗi định danh (ví dụ: "ERR_VALIDATION", "ERR_FUNDS")
    private String message;          // Thông báo lỗi chung chung cho người dùng
    private Object details;          // Chi tiết lỗi (Ví dụ: danh sách các trường nhập sai)
}