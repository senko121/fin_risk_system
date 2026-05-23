 

package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.AdminTransactionDTO;
import com.datn.finrisk.application.dtos.TransactionRiskDetailDTO;
import com.datn.finrisk.core.entities.RiskScore;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionAiInsight;
import com.datn.finrisk.core.entities.UserBehaviorProfile;
import com.datn.finrisk.core.repository.RiskScoreRepository;
import com.datn.finrisk.core.repository.TransactionAiInsightRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserBehaviorProfileRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminTransactionService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired 
    private TransactionAiInsightRepository aiInsightRepository;
 
    @Autowired
    private UserBehaviorProfileRepository userBehaviorProfileRepository;

 
    public Page<AdminTransactionDTO> getTransactions(
            int page, int size,
            String search, String status, String riskLevel) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

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

        return txPage.map(t -> mapToSummaryDTO(t, recipientMap));
    }
 
    @Transactional(readOnly = true)
    public TransactionRiskDetailDTO getTransactionRiskDetail(Long txId) {
        Transaction t = transactionRepository.findById(txId)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch với ID: " + txId));

        TransactionRiskDetailDTO detailDTO = new TransactionRiskDetailDTO();
        detailDTO.setId(t.getId());
        detailDTO.setLocationIp(t.getLocationIp());
        detailDTO.setDeviceFingerprint(t.getDeviceFingerprint());
        detailDTO.setTotalRiskScore(t.getTotalRiskScore());

 
        List<RiskScore> scores = riskScoreRepository.findByTransactionIdInWithRule(List.of(txId));
        
        int totalRuleScore = scores.stream()
            .mapToInt(rs -> Math.max(rs.getAppliedScore(), 0))
            .sum();
        detailDTO.setRuleScore(totalRuleScore);

        detailDTO.setViolatedRules(
            scores.stream()
                .map(rs -> {
                    String name = rs.getRule() != null ? rs.getRule().getRuleName() : "Unknown Rule";
                    return name + " (+" + rs.getAppliedScore() + "đ)";
                })
                .collect(Collectors.toList())
        );

 
        List<TransactionAiInsight> insights = aiInsightRepository.findByTransactionIdIn(List.of(txId));
        List<TransactionRiskDetailDTO.AiInsightDTO> insightDTOs = new ArrayList<>(); 
        int behaviorScore = 0;

        for (TransactionAiInsight insight : insights) {
            if ("BEHAVIOR_SCORE".equals(insight.getFeatureName())) {
                behaviorScore = insight.getAnomalyScore().intValue();
            } else if (!"HIDDEN".equals(insight.getInsightType())) {
                TransactionRiskDetailDTO.AiInsightDTO iDto = new TransactionRiskDetailDTO.AiInsightDTO();
                iDto.setFeature(insight.getFeatureName());
                iDto.setMessage(insight.getInsightMessage());
                iDto.setType(insight.getInsightType());
                iDto.setAnomalyScore(insight.getAnomalyScore());
                insightDTOs.add(iDto);
            }
        }

        detailDTO.setBehaviorScore(behaviorScore);
        detailDTO.setAiInsights(insightDTOs);
 
        Long senderId = t.getFromAccount().getUser().getId();
        TransactionRiskDetailDTO.ProfileBaselineDTO baseline = buildProfileBaseline(senderId);
        detailDTO.setProfileBaseline(baseline);  

        return detailDTO;
    }

 
    private TransactionRiskDetailDTO.ProfileBaselineDTO buildProfileBaseline(Long userId) {
        
 
        UserBehaviorProfile profile = userBehaviorProfileRepository
            .findByUserId(userId)
            .orElse(null);

 
        if (profile == null
                || profile.getTxCount() < 5
                || profile.getMeanVector() == null
                || profile.getMeanVector().isEmpty()
                || profile.getEwmaVariance() == null
                || profile.getEwmaVariance().isEmpty()) {
            return null;
        }

        double[] mean    = convertListToArray(profile.getMeanVector());
        double[] ewmaVar = convertListToArray(profile.getEwmaVariance());
        int txCount      = profile.getTxCount();
 
        double avgAmountRaw = mean.length > 0 ? Math.expm1(mean[0]) : 0;
        double stdAmountRaw = ewmaVar.length > 0 && ewmaVar[0] > 0
            ? Math.expm1(Math.sqrt(ewmaVar[0])) : 0;

 
        String avgHour = "N/A";
        if (mean.length > 2) {
            double hourRad = Math.atan2(mean[1], mean[2]);
            double hourDec = (hourRad < 0 ? hourRad + 2 * Math.PI : hourRad)
                             * (24.0 / (2 * Math.PI));
            int h = (int) hourDec;
            int m = (int) ((hourDec - h) * 60);
            avgHour = String.format("%02d:%02d", h, m);
        }

 
        double avgGapSec = mean.length > 3 ? Math.expm1(mean[3]) : 0;
        double avgGapHours = avgGapSec / 3600.0;

 
        double noveltyRateRaw = mean.length > 4 ? mean[4] : 0;

 
        String fmtAvgAmount = formatMoney(avgAmountRaw);
        String fmtStdAmount = "±" + formatMoney(stdAmountRaw);
        String fmtGapHours  = avgGapHours < 1
            ? String.format("%.0f phút/lần", avgGapHours * 60)
            : String.format("%.1f giờ/lần", avgGapHours);
        String fmtNovelty   = String.format("%.0f%% người lạ", noveltyRateRaw * 100);

        String phase = txCount < 50 ? "COLD_START"
                     : txCount < 150 ? "TRANSITION" : "MATURE";

 
        double amountCV = 0;
        if (ewmaVar.length > 0 && ewmaVar[0] > 0 && mean[0] > 0) {
            double stdLog = Math.sqrt(ewmaVar[0]);
            amountCV = stdLog / Math.abs(mean[0]);  
        }
 
        double circularVariance = 1.0;
        if (mean.length > 2) {
            double magnitude = Math.sqrt(mean[1] * mean[1] + mean[2] * mean[2]);
            circularVariance = 1.0 - Math.min(magnitude, 1.0);
        }
 
        double frequencyCV = 0;
        if (ewmaVar.length > 3 && ewmaVar[3] > 0 && mean[3] > 0) {
            double stdLog = Math.sqrt(ewmaVar[3]);
            frequencyCV = stdLog / Math.abs(mean[3]);
        }
 
        TransactionRiskDetailDTO.ProfileBaselineDTO dto = new TransactionRiskDetailDTO.ProfileBaselineDTO();

 
        dto.setAvgAmount(fmtAvgAmount);
        dto.setStdAmount(fmtStdAmount);
        dto.setAvgHour(avgHour);
        dto.setAvgGapHours(fmtGapHours);
        dto.setNoveltyRate(fmtNovelty);
        dto.setTxCount(txCount);
        dto.setPhase(phase);

 
        dto.setAmountCV(amountCV);
        dto.setCircularVariance(circularVariance);
        dto.setFrequencyCV(frequencyCV);
        dto.setNoveltyRateRaw(noveltyRateRaw);

        return dto;
    }

 
    private String formatMoney(double amount) {
        if (amount >= 1_000_000_000)
            return String.format("%.1f tỷđ", amount / 1_000_000_000);
        if (amount >= 1_000_000)
            return String.format("%.1f triệuđ", amount / 1_000_000);
        if (amount >= 1_000)
            return String.format("%.0f nghìnđ", amount / 1_000);
        return String.format("%.0fđ", amount);
    }

 
    private double[] convertListToArray(List<Double> list) {
        if (list == null || list.isEmpty()) return new double[5];
        return list.stream().mapToDouble(v -> v == null ? 0.0 : v).toArray();
    }

 
    private AdminTransactionDTO mapToSummaryDTO(Transaction t, Map<String, String> recipientMap) {
        AdminTransactionDTO dto = new AdminTransactionDTO();
        dto.setId(t.getId());

        if (t.getFromAccount() != null) {
            dto.setSenderAccountNumber(t.getFromAccount().getAccountNumber());
            if (t.getFromAccount().getUser() != null) {
                dto.setSenderFullName(t.getFromAccount().getUser().getFullName());
                dto.setSenderUsername(t.getFromAccount().getUser().getUsername());
                dto.setSenderSuspicious(t.getFromAccount().getUser().isSuspiciousSession());
            }
        }

        dto.setToAccountNumber(t.getToAccountNumber());
        dto.setToBankCode(t.getToBankCode());
        dto.setRecipientFullName(recipientMap.getOrDefault(t.getToAccountNumber(), "Người nhận ngoài hệ thống"));

        dto.setAmount(t.getAmount());
        dto.setStatus(t.getStatus());
        dto.setRiskLevel(t.getRiskLevel());
        dto.setTotalRiskScore(t.getTotalRiskScore());
        dto.setEmotionSignal(t.getEmotionSignal());
        dto.setCreatedAt(t.getCreatedAt());

        return dto;
    }
}