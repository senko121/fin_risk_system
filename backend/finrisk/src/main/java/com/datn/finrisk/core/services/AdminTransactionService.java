package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.AdminTransactionDTO;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminTransactionService {

    @Autowired
    private TransactionRepository transactionRepository;

    public Page<AdminTransactionDTO> getTransactions(int page, int size, String search, String status, String riskLevel) {
        // Mặc định sắp xếp giao dịch mới nhất lên đầu
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        //   BỘ BUILDER LỌC ĐỘNG (DYNAMIC SPECIFICATION)
        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Tối ưu hóa: JOIN FETCH nối từ Transaction -> Account -> User
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("fromAccount", JoinType.LEFT)
                    .fetch("user", JoinType.LEFT); //   Nối thêm bảng User vào để lấy tên
            }

            // 1. Lọc theo trạng thái (VD: PENDING, SUCCESS)
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 2. Lọc theo mức độ rủi ro (VD: HIGH, MEDIUM_2)
            if (riskLevel != null && !riskLevel.isEmpty()) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }

            // 3. Tìm kiếm tự do (Gõ số tài khoản hoặc IP đều tìm được)
            if (search != null && !search.isEmpty()) {
                String likePattern = "%" + search + "%";
                Predicate toAcc = cb.like(root.get("toAccountNumber"), likePattern);
                Predicate ip = cb.like(root.get("locationIp"), likePattern);
                // Tìm cả trong số tài khoản của người gửi (nằm ở bảng Account)
                Predicate fromAcc = cb.like(root.get("fromAccount").get("accountNumber"), likePattern);
                
                predicates.add(cb.or(toAcc, ip, fromAcc));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Gọi DB đúng 1 lần (hoặc 2 lần vì có query Count của Page), tự động ép điều kiện
        Page<Transaction> transactionPage = transactionRepository.findAll(spec, pageable);

        // Chuyển đổi Entity sang DTO để trả về
        return transactionPage.map(this::mapToDTO);
    }

    private AdminTransactionDTO mapToDTO(Transaction t) {
        AdminTransactionDTO dto = new AdminTransactionDTO();
        dto.setId(t.getId());
        
        // Map dữ liệu Người gửi (Tránh lỗi NullPointerException)
        if (t.getFromAccount() != null) {
            dto.setSenderAccountNumber(t.getFromAccount().getAccountNumber());
            
            if (t.getFromAccount().getUser() != null) {
                dto.setSenderFullName(t.getFromAccount().getUser().getFullName());
                dto.setSenderUsername(t.getFromAccount().getUser().getUsername());
                dto.setSenderSuspicious(t.getFromAccount().getUser().isSuspiciousSession());
            }
        }
        
        // Map dữ liệu Người nhận
        dto.setToAccountNumber(t.getToAccountNumber());
        dto.setToBankCode(t.getToBankCode());
        
        // Map dữ liệu Tiền bạc
        dto.setAmount(t.getAmount());
        // dto.setFee(t.getFee()); // Mở comment ra nếu Entity Transaction đã có fee
        // dto.setTransactionType(t.getTransactionType()); // Mở comment nếu đã có
        dto.setDescription(t.getDescription());
        
        // Map bối cảnh AI và Rủi ro
        dto.setLocationIp(t.getLocationIp());
        dto.setDeviceFingerprint(t.getDeviceFingerprint());
        dto.setStatus(t.getStatus());
        dto.setRiskLevel(t.getRiskLevel());
        dto.setTotalRiskScore(t.getTotalRiskScore());
        dto.setEmotionSignal(t.getEmotionSignal());
        dto.setFailedAiAttempts(t.getFailedAiAttempts());
        
        dto.setCreatedAt(t.getCreatedAt());
        
        return dto;
    }
}