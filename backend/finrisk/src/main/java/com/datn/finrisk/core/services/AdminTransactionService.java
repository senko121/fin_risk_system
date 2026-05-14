package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.AdminTransactionDTO;
import com.datn.finrisk.core.entities.RiskScore;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.RiskScoreRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminTransactionService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    public Page<AdminTransactionDTO> getTransactions(
            int page, int size,
            String search, String status, String riskLevel) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // ================================================================
        // QUERY 1: Load transactions + Account + User (to-one ONLY)
        // ✅ KHÔNG fetch riskScores ở đây → tránh HHH90003004
        // ================================================================
        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("fromAccount", JoinType.LEFT)
                    .fetch("user", JoinType.LEFT);
                query.distinct(true);
            }

            if (status != null && !status.isBlank())
                predicates.add(cb.equal(root.get("status"), status));
            if (riskLevel != null && !riskLevel.isBlank())
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            if (search != null && !search.isBlank()) {
                String like = "%" + search + "%";
                predicates.add(cb.or(
                    cb.like(root.get("toAccountNumber"), like),
                    cb.like(root.get("locationIp"), like),
                    cb.like(root.get("fromAccount").get("accountNumber"), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Transaction> txPage = transactionRepository.findAll(spec, pageable);
        List<Transaction> txList = txPage.getContent();

        if (txList.isEmpty()) {
            return Page.empty(pageable);
        }

        // ================================================================
        // QUERY 2: Batch load recipient names (1 query cho cả page)
        // ================================================================
        List<String> toAccNums = txList.stream()
            .map(Transaction::getToAccountNumber)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

        Map<String, String> recipientMap = new HashMap<>();
        if (!toAccNums.isEmpty()) {
            transactionRepository.findRecipientNamesBulk(toAccNums)
                .forEach(row -> recipientMap.put((String) row[0], (String) row[1]));
        }

        // ================================================================
        // QUERY 3: Batch load RiskScores + Rule (1 query cho cả page)
        // ✅ Thay thế N lần gọi findByTransactionId()
        // ================================================================
        List<Long> txIds = txList.stream()
            .map(Transaction::getId)
            .collect(Collectors.toList());

        Map<Long, List<RiskScore>> riskScoreMap = riskScoreRepository
            .findByTransactionIdInWithRule(txIds)
            .stream()
            .collect(Collectors.groupingBy(rs -> rs.getTransaction().getId()));

        // ================================================================
        // MAP sang DTO (KHÔNG gọi DB thêm nữa)
        // ================================================================
        return txPage.map(t -> mapToDTO(t, recipientMap, riskScoreMap));
    }

    private AdminTransactionDTO mapToDTO(
            Transaction t,
            Map<String, String> recipientMap,
            Map<Long, List<RiskScore>> riskScoreMap) {

        AdminTransactionDTO dto = new AdminTransactionDTO();
        dto.setId(t.getId());

        // Sender info - đã fetch join, 0 query
        if (t.getFromAccount() != null) {
            dto.setSenderAccountNumber(t.getFromAccount().getAccountNumber());
            if (t.getFromAccount().getUser() != null) {
                dto.setSenderFullName(t.getFromAccount().getUser().getFullName());
                dto.setSenderUsername(t.getFromAccount().getUser().getUsername());
                dto.setSenderSuspicious(t.getFromAccount().getUser().isSuspiciousSession());
            }
        }

        // Recipient info - lấy từ Map, 0 query
        dto.setToAccountNumber(t.getToAccountNumber());
        dto.setToBankCode(t.getToBankCode());
        dto.setRecipientFullName(
            recipientMap.getOrDefault(t.getToAccountNumber(), "Người nhận ngoài hệ thống")
        );

        // Basic fields
        dto.setAmount(t.getAmount());
        dto.setDescription(t.getDescription());
        dto.setLocationIp(t.getLocationIp());
        dto.setDeviceFingerprint(t.getDeviceFingerprint());
        dto.setStatus(t.getStatus());
        dto.setRiskLevel(t.getRiskLevel());
        dto.setTotalRiskScore(t.getTotalRiskScore());
        dto.setEmotionSignal(t.getEmotionSignal());
        dto.setFailedAiAttempts(t.getFailedAiAttempts());
        dto.setCreatedAt(t.getCreatedAt());

        // Risk rules - lấy từ Map, 0 query
        List<RiskScore> scores = riskScoreMap.getOrDefault(t.getId(), List.of());
        dto.setViolatedRules(
            scores.stream()
                .map(rs -> {
                    String name = rs.getRule() != null
                        ? rs.getRule().getRuleName()
                        : "Unknown Rule";
                    return name + " (+" + rs.getAppliedScore() + "đ)";
                })
                .collect(Collectors.toList())
        );

        return dto;
    }
}