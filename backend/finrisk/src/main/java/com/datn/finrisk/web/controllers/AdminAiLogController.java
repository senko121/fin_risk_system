package com.datn.finrisk.web.controllers;

import com.datn.finrisk.core.entities.AiScanLog;
import com.datn.finrisk.core.repository.AiScanLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai-logs")
@CrossOrigin(origins = "*") 
public class AdminAiLogController {

    @Autowired
    private AiScanLogRepository aiScanLogRepository;

    // 🚀 Đã chuyển sang chế độ Phân trang (Pagination)
    @GetMapping
    public ResponseEntity<Page<AiScanLog>> getAllAiLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        // Tạo cấu hình phân trang, sắp xếp theo ngày tạo mới nhất
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        // Gọi repository lấy dữ liệu theo trang
        Page<AiScanLog> logPage = aiScanLogRepository.findAll(pageable);
        
        return ResponseEntity.ok(logPage);
    }
}