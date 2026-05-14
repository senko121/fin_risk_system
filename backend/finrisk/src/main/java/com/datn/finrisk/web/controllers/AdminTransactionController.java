package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.AdminTransactionDTO;
import com.datn.finrisk.core.services.AdminTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/transactions")
@CrossOrigin(origins = "http://localhost:5173") // Mở CORS cho Frontend ReactJS
public class AdminTransactionController {

    @Autowired
    private AdminTransactionService adminTransactionService;

    // 🚀 API Lấy danh sách giao dịch (Có phân trang và lọc)
    @GetMapping
    public ResponseEntity<?> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel) {
        try {
            // Gọi Service để lấy dữ liệu đã được lót DTO
            Page<AdminTransactionDTO> result = adminTransactionService.getTransactions(page, size, search, status, riskLevel);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // Log lỗi ra console để dev dễ debug
            e.printStackTrace(); 
            return ResponseEntity.badRequest().body("Lỗi tải danh sách giao dịch: " + e.getMessage());
        }
    }
}