package com.datn.finrisk.application.dtos;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminTransactionDTO {
    private Long id;
    
    // ==========================================
    // 1. THÔNG TIN NGƯỜI GỬI (Kéo từ Account và User)
    // ==========================================
    private String senderAccountNumber; 
    private String senderFullName;      // Kéo từ User.fullName
    private String senderUsername;      // Kéo từ User.username
    private boolean senderSuspicious;   // Kéo từ User.isSuspiciousSession (Cảnh báo đỏ trên giao diện)

    // ==========================================
    // 2. THÔNG TIN NGƯỜI NHẬN
    // ==========================================
    private String toAccountNumber;
    private String toBankCode;          // Bổ sung: Biết là chuyển nội bộ hay liên ngân hàng

    // ==========================================
    // 3. THÔNG TIN TIỀN BẠC
    // ==========================================
    private BigDecimal amount;
    private BigDecimal fee;             // Bổ sung: Phí giao dịch (Nhớ map trong DB nhé)
    private String transactionType;     // Bổ sung: Loại giao dịch (TRANSFER, PAYMENT...)
    private String description;         // Bổ sung: Nội dung chuyển khoản

    // ==========================================
    // 4. BỐI CẢNH & RỦI RO (AI Core)
    // ==========================================
    private String locationIp;
    private String deviceFingerprint;
    private String status;
    private String riskLevel;
    private Integer totalRiskScore;
    private String emotionSignal;
    private Integer failedAiAttempts;   // Bổ sung: Xem user này quét mặt xịt mấy lần

    private LocalDateTime createdAt;
}