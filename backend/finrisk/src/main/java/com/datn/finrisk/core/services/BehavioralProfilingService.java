package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.BehaviorProfileDTO;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.UserBehaviorProfile;
import com.datn.finrisk.core.exceptions.BusinessLogicException;
import com.datn.finrisk.core.repository.UserBehaviorProfileRepository;
import com.datn.finrisk.core.utils.MahalanobisCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BehavioralProfilingService {

    @Autowired
    private MahalanobisCalculator mathCalculator;

    @Autowired
    private UserBehaviorProfileRepository profileRepository;

    private static final int DIMENSIONS = 5;

    // =====================================================
    // HÀM CHÍNH: Tính Anomaly Score cho transaction mới
    // =====================================================
    public int calculateBehavioralAnomalyScore(
            Transaction currentTx,
            UserBehaviorProfile profile,
            double gapSeconds,
            double recipientNovelty) {

        if (profile == null || profile.getTxCount() < 5) {
            return 0;
        }

        double[] currentVector = extractFeatureVector(
            currentTx, gapSeconds, recipientNovelty);
        double[] meanVector    = convertListToArray(profile.getMeanVector());
        int txCount            = profile.getTxCount();

        if (txCount < 50) {
            double raw = mathCalculator.calculateEuclidean(
                currentVector, meanVector);
            return normalizeEuclidean(raw);

        } else if (txCount <= 150) {
            double raw     = mathCalculator.calculateEuclidean(
                currentVector, meanVector);
            int scoreEuc   = normalizeEuclidean(raw);

            double[][] covC  = convertNestedListToArray(
                profile.getCovarianceMatrixC());
            double dSquared  = mathCalculator.calculateMahalanobis(
                currentVector, meanVector, covC, txCount, 0.1);
            int scoreMaha    = normalizeMahalanobis(dSquared);

            double weightMaha = (txCount - 50) / 100.0;
            double weightEuc  = 1.0 - weightMaha;
            return (int) Math.round(
                (scoreEuc * weightEuc) + (scoreMaha * weightMaha));

        } else {
            double[][] covC = convertNestedListToArray(
                profile.getCovarianceMatrixC());
            double dSquared = mathCalculator.calculateMahalanobis(
                currentVector, meanVector, covC, txCount, 0.001);
            return normalizeMahalanobis(dSquared);
        }
    }

    // HÀM ADMIN: Đọc profile dạng human-readable
 
    public BehaviorProfileDTO getReadableProfile(Long userId) {

        // 1. Dùng Optional để kiểm tra xem có dữ liệu hay không, KHÔNG quăng lỗi nữa
        java.util.Optional<UserBehaviorProfile> profileOpt = profileRepository.findByUserId(userId);

        // 2. FALLBACK 1: Nếu User lính mới tò te, chưa có bản ghi nào trong DB
        if (profileOpt.isEmpty()) {
            return buildEmptyProfileDTO(userId, 0);
        }

        UserBehaviorProfile profile = profileOpt.get();

        // 3. FALLBACK 2: Đã có bản ghi nhưng chưa sinh ra Vector (Giao dịch bị lỗi, bị hủy...)
        if (profile.getMeanVector() == null || profile.getMeanVector().isEmpty()) {
            return buildEmptyProfileDTO(userId, profile.getTxCount());
        }

        // 4. Mọi thứ OK -> Build đồ thị cho Admin xem
        return buildFullProfile(userId, profile);
    }

    public List<BehaviorProfileDTO> getAnomalyHistory(Long userId, int days) {
        // TODO: Tạo bảng behavior_anomaly_logs để lưu lịch sử về sau
        return List.of(getReadableProfile(userId));
    }

    // =====================================================
    // PRIVATE HELPERS
    // =====================================================
    private BehaviorProfileDTO buildEmptyProfileDTO(
            Long userId, Integer txCount) {
        return BehaviorProfileDTO.builder()
            .userId(userId)
            .txCount(txCount != null ? txCount : 0)
            .profilingPhase("COLD_START")
            .profileReliability(0.0)
            .avgAmount(0)
            .avgTransactionHour("N/A")
            .avgGapHours(0)
            .stdAmount(0.0)               // <-- Bổ sung
            .stdGapHours(0.0)             // <-- Bổ sung
            .anomalyScore(0)
            .anomalyLevel("NORMAL")
            .calculationMethod("EUCLIDEAN")
            .lastUpdated("N/A")
            .amountAnomalyScore(0.0)      // <-- Bổ sung cho Radar Chart
            .hourAnomalyScore(0.0)        // <-- Bổ sung cho Radar Chart
            .frequencyAnomalyScore(0.0)   // <-- Bổ sung cho Radar Chart
            .recipientAnomalyScore(0.0)   // <-- Bổ sung cho Radar Chart
            .build();
    }

    private double calculateCurrentAnomalyScore(UserBehaviorProfile profile) {
        if (profile.getTxCount() < 5
                || profile.getMeanVector() == null
                || profile.getEwmaMeanVector() == null) {
            return 0.0;
        }

        double[] mean     = convertListToArray(profile.getMeanVector());
        double[] ewmaMean = convertListToArray(profile.getEwmaMeanVector());
        int txCount       = profile.getTxCount();

        if (txCount < 50) {
            double raw = mathCalculator.calculateEuclidean(ewmaMean, mean);
            return Math.min((raw / 4.0) * 100, 100);
        } else {
            double[][] covC = convertNestedListToArray(
                profile.getCovarianceMatrixC());
            double lambda   = txCount < 150 ? 0.1 : 0.001;
            double dSquared = mathCalculator.calculateMahalanobis(
                ewmaMean, mean, covC, txCount, lambda);
            return Math.min((dSquared / 20.51) * 100, 100);
        }
    }

    private double[] extractFeatureVector(
            Transaction tx, double gapSeconds, double recipientNovelty) {
        double logAmount = Math.log1p(tx.getAmount().doubleValue());
        double hour      = tx.getCreatedAt().getHour()
                         + tx.getCreatedAt().getMinute() / 60.0;
        double hourRad   = (hour / 24.0) * 2 * Math.PI;
        return new double[]{
            logAmount,
            Math.sin(hourRad),
            Math.cos(hourRad),
            Math.log1p(gapSeconds),
            recipientNovelty
        };
    }

    private int normalizeEuclidean(double raw) {
        return (int) Math.min((raw / 4.0) * 100, 100);
    }

    private int normalizeMahalanobis(double dSquared) {
        return (int) Math.min((dSquared / 20.51) * 100, 100);
    }

    private double[] convertListToArray(List<Double> list) {
        return list.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private double[][] convertNestedListToArray(List<List<Double>> nested) {
        double[][] array = new double[DIMENSIONS][DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++)
            for (int j = 0; j < DIMENSIONS; j++)
                array[i][j] = nested.get(i).get(j);
        return array;
    }


    private BehaviorProfileDTO buildFullProfile(
            Long userId, UserBehaviorProfile profile) {

        double[] mean = convertListToArray(profile.getMeanVector());
        double[] ewma = convertListToArray(profile.getEwmaMeanVector());
        double[] eVar = convertListToArray(profile.getEwmaVariance());
        int n         = profile.getTxCount();

        // Dịch mean vector
        double avgAmount   = mean.length > 0 ? Math.exp(mean[0]) - 1 : 0;
        double stdAmount   = eVar.length > 0
            ? Math.exp(Math.sqrt(eVar[0])) - 1 : 0;

        String avgHour = buildAvgHour(mean);

        double avgGapHours = mean.length > 3
            ? (Math.exp(mean[3]) - 1) / 3600.0 : 0;
        double stdGapHours = eVar.length > 3
            ? (Math.exp(Math.sqrt(eVar[3])) - 1) / 3600.0 : 0;

        // Tính Z-Score từng chiều riêng lẻ → Breakdown cho Radar Chart
        double zAmount    = eVar[0] > 0
            ? Math.abs(ewma[0] - mean[0]) / Math.sqrt(eVar[0]) : 0;
        double zHour      = eVar[1] > 0
            ? Math.abs(ewma[1] - mean[1]) / Math.sqrt(eVar[1]) : 0;
        double zFrequency = eVar[3] > 0
            ? Math.abs(ewma[3] - mean[3]) / Math.sqrt(eVar[3]) : 0;
        double zRecipient = eVar[4] > 0
            ? Math.abs(ewma[4] - mean[4]) / Math.sqrt(eVar[4]) : 0;

        // Normalize Z-Score về [0, 100]
        // Z > 3 = cực kỳ bất thường = 100 điểm
        double amountScore    = Math.min(zAmount    / 3.0 * 100, 100);
        double hourScore      = Math.min(zHour      / 3.0 * 100, 100);
        double frequencyScore = Math.min(zFrequency / 3.0 * 100, 100);
        double recipientScore = Math.min(zRecipient / 3.0 * 100, 100);

        double anomalyScore = calculateCurrentAnomalyScore(profile);
        String anomalyLevel = anomalyScore < 40 ? "NORMAL"
                            : anomalyScore < 70 ? "SUSPICIOUS" : "CRITICAL";

        String phase       = n < 50  ? "COLD_START" :
                            n < 150 ? "TRANSITION" : "MATURE";
        String method      = n < 50  ? "EUCLIDEAN"  :
                            n < 150 ? "BLEND"      : "MAHALANOBIS";
        double reliability = Math.min(n / 150.0, 1.0);

        return BehaviorProfileDTO.builder()
            .userId(userId)
            .txCount(n)
            .profilingPhase(phase)
            .profileReliability(reliability)
            .calculationMethod(method)
            .lastUpdated(profile.getUpdatedAt() != null
                ? profile.getUpdatedAt().toString() : "N/A")
            // Hành vi trung bình
            .avgAmount(avgAmount)
            .stdAmount(stdAmount)
            .avgTransactionHour(avgHour)
            .avgGapHours(avgGapHours)
            .stdGapHours(stdGapHours)
            // Điểm số
            .anomalyScore(anomalyScore)
            .anomalyLevel(anomalyLevel)
            // Breakdown từng chiều
            .amountAnomalyScore(amountScore)
            .hourAnomalyScore(hourScore)
            .frequencyAnomalyScore(frequencyScore)
            .recipientAnomalyScore(recipientScore)
            .build();
    }

    private String buildAvgHour(double[] mean) {
        if (mean.length <= 2) return "N/A";
        double hourRad = Math.atan2(mean[1], mean[2]);
        double hourDec = (hourRad < 0
            ? hourRad + 2 * Math.PI
            : hourRad) * (24.0 / (2 * Math.PI));
        int h = (int) hourDec;
        int m = (int) ((hourDec - h) * 60);
        return String.format("%02d:%02d", h, m);
    }
}