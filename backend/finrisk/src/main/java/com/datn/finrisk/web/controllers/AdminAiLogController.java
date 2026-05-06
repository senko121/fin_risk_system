package com.datn.finrisk.web.controllers;

import com.datn.finrisk.core.entities.AiScanLog;
import com.datn.finrisk.core.repository.AiScanLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-logs")
@CrossOrigin(origins = "*") // Hoặc cấu hình CORS theo hệ thống của bro
public class AdminAiLogController {

    @Autowired
    private AiScanLogRepository aiScanLogRepository;

    // API kéo toàn bộ lịch sử AI, sắp xếp mới nhất lên đầu
    @GetMapping
    public ResponseEntity<List<AiScanLog>> getAllAiLogs() {
        List<AiScanLog> logs = aiScanLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(logs);
    }
}