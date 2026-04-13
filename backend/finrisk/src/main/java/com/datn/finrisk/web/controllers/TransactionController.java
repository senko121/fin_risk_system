package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.TransactionRequest;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.services.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "http://localhost:5173") // Cho phép React gọi
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @PostMapping("/process")
    public ResponseEntity<?> processTransaction(@RequestBody TransactionRequest request) {
        try {
            // Chạy luồng giao dịch & Đánh giá rủi ro
            Transaction result = transactionService.processTransaction(
                    request.getFromAccountId(),
                    request.getToAccount(),
                    request.getAmount(),
                    request.getEmotion()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}