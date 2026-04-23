package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 🚀 1. BẮT LỖI VALIDATION (@Min, @NotNull,...) TỪ DTO
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value()) // Mã 400
                .errorCode("ERR_VALIDATION_FAILED")
                .message("Dữ liệu đầu vào không hợp lệ!")
                .details(errors) // Gửi kèm Map chi tiết lỗi (ví dụ: amount -> Phải lớn hơn 10000)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 🚀 2. BẮT LỖI XUNG ĐỘT GIAO DỊCH CŨ CỦA ÔNG
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value()) // Mã 409
                .errorCode("ERR_CONCURRENT_TRANSACTION")
                .message("Hệ thống đang xử lý một giao dịch khác trên tài khoản này. Vui lòng đợi vài giây và thử lại!")
                .build();

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    // 🚀 3. BẮT LỖI LOGIC SAI CŨ CỦA ÔNG (IllegalArgument)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value()) // Mã 400
                .errorCode("ERR_BAD_REQUEST")
                .message(ex.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 🚀 4. LƯỚI VÉT ĐÁY: Bắt mọi lỗi tào lao chưa biết tên (để không bị 500 hay 403 bừa bãi)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGlobalException(Exception ex) {
        ex.printStackTrace(); // In ra console để Dev đọc

        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value()) // Mã 500
                .errorCode("ERR_SYSTEM_UNKNOWN")
                .message("Hệ thống gặp sự cố không mong muốn. Vui lòng thử lại sau.")
                .build();

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}