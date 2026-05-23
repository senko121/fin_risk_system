package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.ApiErrorResponse;
import com.datn.finrisk.core.exceptions.BusinessLogicException;
import lombok.extern.slf4j.Slf4j;

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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

 
    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessLogicException(BusinessLogicException ex) {
 
        log.warn("⚠️ Bị chặn bởi Business Rule [{}]: {}", ex.getErrorCode(), ex.getMessage());

 
        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())  
                .errorCode(ex.getErrorCode())            
                .message(ex.getMessage())              
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

 
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
                .status(HttpStatus.BAD_REQUEST.value())  
                .errorCode("ERR_VALIDATION_FAILED")
                .message("Dữ liệu đầu vào không hợp lệ!")
                .details(errors)  
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

 
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())  
                .errorCode("ERR_CONCURRENT_TRANSACTION")
                .message("Hệ thống đang xử lý một giao dịch khác trên tài khoản này. Vui lòng đợi vài giây và thử lại!")
                .build();

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

 
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value()) 
                .errorCode("ERR_BAD_REQUEST")
                .message(ex.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

 
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGlobalException(Exception ex) {
        ex.printStackTrace(); 
        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())  
                .errorCode("ERR_SYSTEM_UNKNOWN")
                .message("Hệ thống gặp sự cố không mong muốn. Vui lòng thử lại sau.")
                .build();

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}