 


package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.AdminAiLogDTO;
import com.datn.finrisk.core.entities.AiScanLog;
import com.datn.finrisk.core.repository.AiScanLogRepository;
import com.datn.finrisk.core.repository.UserRepository;
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

    @Autowired
    private UserRepository userRepository;  

    @GetMapping
    public ResponseEntity<Page<AdminAiLogDTO>> getAllAiLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AiScanLog> logPage = aiScanLogRepository.findAll(pageable);
 
        Page<AdminAiLogDTO> dtoPage = logPage.map(log -> {
            AdminAiLogDTO dto = new AdminAiLogDTO();
            dto.setId(log.getId());
            dto.setTransactionId(log.getTransactionId());
            dto.setUserId(log.getUserId());
            
 
            userRepository.findById(log.getUserId()).ifPresent(user -> {
                dto.setUserFullName(user.getFullName());
            });

            dto.setScanType(log.getScanType());
            dto.setResultLabel(log.getResultLabel());
            dto.setConfidenceScore(log.getConfidenceScore());
            dto.setProcessTimeMs(log.getProcessTimeMs());
            dto.setEmotionDetails(log.getEmotionDetails());
            dto.setCreatedAt(log.getCreatedAt());
            return dto;
        });
        
        return ResponseEntity.ok(dtoPage);
    }
}