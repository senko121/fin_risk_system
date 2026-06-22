 
package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.BehaviorInsightResult;
import com.datn.finrisk.application.dtos.BehaviorProfileDTO;
import com.datn.finrisk.core.entities.PeerGroup;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionAiInsight;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserBehaviorProfile;
import com.datn.finrisk.core.repository.UserBehaviorProfileRepository;
import com.datn.finrisk.core.utils.MahalanobisCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BehavioralProfilingService {

    @Autowired
    private MahalanobisCalculator mathCalculator;

    @Autowired
    private UserBehaviorProfileRepository profileRepository;

    @Autowired
    private PeerGroupService peerGroupService;

    private static final int DIMENSIONS = MahalanobisCalculator.DIMENSIONS; // P1.5: 6 chiều
 
    public BehaviorInsightResult calculateBehavioralAnomalyScore(
            Transaction currentTx,
            UserBehaviorProfile profile,
            double gapSeconds,
            double recipientNovelty) {

        List<TransactionAiInsight> insights = new ArrayList<>();

 
        if (profile == null || profile.getTxCount() < 5) {
            insights.add(new TransactionAiInsight(
                null,
                "COLD_START",
                "Hệ thống chưa đủ dữ liệu để phân tích thói quen ("
                    + (profile == null ? 0 : profile.getTxCount())
                    + "/5 giao dịch). Đang trong giai đoạn học ban đầu.",
                "SAFE",
                0.0
            ));
            return new BehaviorInsightResult(0, insights);
        }

        double[] currentVector = extractFeatureVector(currentTx, gapSeconds, recipientNovelty);
        double[] meanVector    = convertListToArray(profile.getMeanVector());
        double[] ewmaVariance  = convertListToArray(profile.getEwmaVariance());
        int txCount            = profile.getTxCount();
 
        int totalScore;
        double dSquaredForLog = 0.0;
        String phase, method;

        if (txCount < 10) {
            // P2.1: dùng Peer-Group Mahalanobis nếu user có dữ liệu nhân khẩu
            User sender = currentTx.getFromAccount().getUser();
            java.util.Optional<PeerGroup> peerOpt = peerGroupService.findPeerGroup(sender);
            if (peerOpt.isPresent()) {
                PeerGroup pg     = peerOpt.get();
                double[] pgMean  = convertListToArray(pg.getMeanVector());
                double[][] pgCov = peerGroupService.toCovArray(pg.getCovarianceMatrix());
                double dSquared  = mathCalculator.calculateFromPrecomputedCov(
                                       currentVector, pgMean, pgCov, 0.1);
                totalScore     = normalizeMahalanobis(dSquared);
                dSquaredForLog = dSquared;
                phase  = "COLD_START";
                method = "PEER_MAHALANOBIS";
            } else {
                double raw = mathCalculator.calculateEuclidean(currentVector, meanVector);
                totalScore    = normalizeEuclidean(raw);
                dSquaredForLog = raw * raw;
                phase  = "COLD_START";
                method = "EUCLIDEAN";
            }

        } else if (txCount < 50) {
            // P1.1: WARM_START — Mahalanobis blended với global prior, bắt đầu từ 10 tx
            double[][] covC = convertNestedListToArray(profile.getCovarianceMatrixC());
            double dSquared = mathCalculator.calculateWithGlobalPrior(
                                  currentVector, meanVector, covC, txCount);
            totalScore     = normalizeMahalanobis(dSquared);
            dSquaredForLog = dSquared;
            phase  = "WARM_START";
            method = "GLOBAL_PRIOR_MAHALANOBIS";

        } else if (txCount <= 150) {
            double raw   = mathCalculator.calculateEuclidean(currentVector, meanVector);
            int scoreEuc = normalizeEuclidean(raw);

            double[][] covC    = convertNestedListToArray(profile.getCovarianceMatrixC());
            double dSquared    = mathCalculator.calculateMahalanobis(
                                     currentVector, meanVector, covC, txCount, 0.1);
            int scoreMaha      = normalizeMahalanobis(dSquared);
            double weightMaha  = (txCount - 50) / 100.0;
            double weightEuc   = 1.0 - weightMaha;
            totalScore         = (int) Math.round((scoreEuc * weightEuc) + (scoreMaha * weightMaha));
            dSquaredForLog     = dSquared;
            phase  = "TRANSITION";
            method = "BLEND";

        } else {
            double[][] covC = convertNestedListToArray(profile.getCovarianceMatrixC());
            double dSquared = mathCalculator.calculateMahalanobis(
                                  currentVector, meanVector, covC, txCount, 0.001);
            totalScore     = normalizeMahalanobis(dSquared);
            dSquaredForLog = dSquared;
            phase  = "MATURE";
            method = "MAHALANOBIS";
        }

 
        totalScore = Math.max(0, Math.min(totalScore, 100));

 
        double zAmount    = safeZ(currentVector[0], meanVector[0], ewmaVariance[0]);
        double zTimeSin   = safeZ(currentVector[1], meanVector[1], ewmaVariance[1]);
        double zTimeCos   = safeZ(currentVector[2], meanVector[2], ewmaVariance[2]);
        double zFrequency = safeZ(currentVector[3], meanVector[3], ewmaVariance[3]);
        double zRecipient = computeRecipientZ(currentVector[4], meanVector[4], ewmaVariance[4],
                                              recipientNovelty);
 
        double zTime = Math.sqrt(zTimeSin * zTimeSin + zTimeCos * zTimeCos) / Math.sqrt(2.0);
 
        double actualAmount  = Math.expm1(currentVector[0]);
        double avgAmount     = Math.expm1(meanVector[0]);
        double stdAmount     = ewmaVariance[0] > 0
                               ? Math.expm1(Math.sqrt(ewmaVariance[0])) : 0;

        double actualGapSec  = Math.expm1(currentVector[3]);
        double avgGapSec     = Math.expm1(meanVector[3]);

        String actualHourStr = formatHourFromTx(currentTx);
        String avgHourStr    = buildAvgHour(meanVector);

        double avgNoveltyRate = meanVector[4];  
        insights.add(buildAmountInsight(actualAmount, avgAmount, stdAmount, zAmount));
        insights.add(buildTimeInsight(actualHourStr, avgHourStr, zTime, currentTx));
        insights.add(buildFrequencyInsight(actualGapSec, avgGapSec, zFrequency));
        insights.add(buildRecipientInsight(recipientNovelty, avgNoveltyRate, zRecipient));
 
        String debugMsg = String.format(
            "Phase=%s | Method=%s | txCount=%d | D²=%.2f | gap=%.0fs | isNewRecipient=%s",
            phase, method, txCount, dSquaredForLog, gapSeconds, recipientNovelty == 1.0
        );
        insights.add(new TransactionAiInsight(null, "BEHAVIOR_SCORE", debugMsg, "HIDDEN",
                                              (double) totalScore));

        return new BehaviorInsightResult(totalScore, insights);
    }
 
    private TransactionAiInsight buildAmountInsight(
            double actual, double avg, double std, double z) {

        String type;
        String msg;
        double ratio = avg > 0 ? actual / avg : 0;

        if (z < 1.5) {
            type = "SAFE";
            if (actual < avg) {
                msg = String.format(
                    "GD %s thấp hơn thói quen (~%s ±%s). Mức tiêu thụ nhẹ, bình thường. Z=%.2f",
                    fmtMoney(actual), fmtMoney(avg), fmtMoney(std), z);
            } else {
                msg = String.format(
                    "GD %s xấp xỉ thói quen (~%s ±%s). Không có dấu hiệu bất thường. Z=%.2f",
                    fmtMoney(actual), fmtMoney(avg), fmtMoney(std), z);
            }
        } else if (z < 2.5) {
            type = "WARNING";
            if (ratio > 1) {
                msg = String.format(
                    "GD %s cao hơn %.1f lần thói quen (~%s ±%s). Cần theo dõi thêm bối cảnh. Z=%.2f",
                    fmtMoney(actual), ratio, fmtMoney(avg), fmtMoney(std), z);
            } else {
                msg = String.format(
                    "GD %s thấp hơn đáng kể thói quen (~%s ±%s). Có thể là giao dịch thăm dò. Z=%.2f",
                    fmtMoney(actual), fmtMoney(avg), fmtMoney(std), z);
            }
        } else {
            type = "DANGER";
            if (ratio > 5) {
                msg = String.format(
                    "⚠ GD %s cao hơn %.1f lần thói quen (~%s ±%s). "
                    + "Mức tăng đột biến — dấu hiệu chuyển tiền khẩn cấp dưới áp lực. Z=%.2f",
                    fmtMoney(actual), ratio, fmtMoney(avg), fmtMoney(std), z);
            } else if (ratio > 1) {
                msg = String.format(
                    "GD %s vượt ngưỡng an toàn (%.1f lần thói quen ~%s ±%s). "
                    + "Bất thường rõ rệt. Z=%.2f",
                    fmtMoney(actual), ratio, fmtMoney(avg), fmtMoney(std), z);
            } else {
                msg = String.format(
                    "GD %s thấp bất thường so với thói quen %s. "
                    + "Nghi vấn giao dịch thăm dò trước khi chuyển số tiền lớn hơn. Z=%.2f",
                    fmtMoney(actual), fmtMoney(avg), z);
            }
        }

        return new TransactionAiInsight(null, "AMOUNT", msg, type, z);
    }

    private TransactionAiInsight buildTimeInsight(
            String actualHour, String avgHour, double z, Transaction tx) {

        int hour = tx.getCreatedAt().getHour();
        boolean isNight = hour >= 22 || hour < 5;
        String type;
        String msg;

        if (z < 1.5) {
            type = "SAFE";
            msg  = String.format(
                "GD lúc %s, gần với khung giờ thường xuyên của bạn (~%s). "
                + "Thời điểm phù hợp thói quen. Z=%.2f",
                actualHour, avgHour, z);

        } else if (z < 2.5) {
            type = "WARNING";
            if (isNight) {
                msg = String.format(
                    "GD lúc %s — ngoài giờ hoạt động thông thường của bạn (~%s). "
                    + "Giao dịch đêm khuya cần chú ý. Z=%.2f",
                    actualHour, avgHour, z);
            } else {
                msg = String.format(
                    "GD lúc %s, lệch khỏi khung giờ quen thuộc (~%s). "
                    + "Không nguy hiểm nhưng đáng theo dõi. Z=%.2f",
                    actualHour, avgHour, z);
            }
        } else {
            type = "DANGER";
            if (isNight) {
                msg = String.format(
                    "⚠ GD lúc %s — rất bất thường với người quen giao dịch ~%s. "
                    + "Giao dịch đêm khuya kết hợp với các yếu tố rủi ro khác. Z=%.2f",
                    actualHour, avgHour, z);
            } else {
                msg = String.format(
                    "GD lúc %s lệch xa hẳn thói quen (~%s). "
                    + "Giờ bất thường — có thể đang không tự chủ về thời gian. Z=%.2f",
                    actualHour, avgHour, z);
            }
        }

        return new TransactionAiInsight(null, "TIME", msg, type, z);
    }

    private TransactionAiInsight buildFrequencyInsight(
            double actualGapSec, double avgGapSec, double z) {

        String type;
        String msg;
        String actualFmt = fmtDuration(actualGapSec);
        String avgFmt    = fmtDuration(avgGapSec);
        double ratio     = avgGapSec > 0 ? avgGapSec / Math.max(actualGapSec, 1) : 0;

        if (z < 1.5) {
            type = "SAFE";
            msg  = String.format(
                "GD cách trước %s, phù hợp tần suất thông thường của bạn (~%s/lần). Z=%.2f",
                actualFmt, avgFmt, z);

        } else if (z < 2.5) {
            type = "WARNING";
            if (actualGapSec < avgGapSec) {
                msg = String.format(
                    "GD cách trước %s — nhanh hơn thói quen (~%s/lần). "
                    + "Tốc độ hơi dồn dập, cần theo dõi. Z=%.2f",
                    actualFmt, avgFmt, z);
            } else {
                msg = String.format(
                    "GD sau khoảng nghỉ dài %s (thường %s/lần). "
                    + "Giao dịch đột xuất sau thời gian không hoạt động. Z=%.2f",
                    actualFmt, avgFmt, z);
            }
        } else {
            type = "DANGER";
            if (actualGapSec < 300) {
                msg = String.format(
                    "⚠ GD chỉ %s sau lần trước — dồn dập %.0f lần so với thói quen (~%s/lần). "
                    + "Dấu hiệu bị thúc ép hoặc tấn công tự động. Z=%.2f",
                    actualFmt, ratio, avgFmt, z);
            } else {
                msg = String.format(
                    "GD cách trước %s, nhanh hơn đáng kể so với thói quen (~%s/lần). "
                    + "Tần suất bất thường. Z=%.2f",
                    actualFmt, avgFmt, z);
            }
        }

        return new TransactionAiInsight(null, "FREQUENCY", msg, type, z);
    }

    private TransactionAiInsight buildRecipientInsight(
            double recipientNovelty, double avgNoveltyRate, double z) {

        String type;
        String msg;
        double familiarRate = 1.0 - avgNoveltyRate;

        if (recipientNovelty == 0.0) {
 
            type = "SAFE";
            msg  = String.format(
                "Người nhận quen thuộc, đã có lịch sử giao dịch trước đây. "
                + "%.0f%% GD của bạn là người quen. Z=0.00",
                familiarRate * 100);

        } else if (z < 1.5) {
 
            type = "SAFE";
            msg  = String.format(
                "Người nhận mới, nhưng bạn thường xuyên giao dịch với người lạ "
                + "(%.0f%% lịch sử). Bình thường với thói quen của bạn. Z=%.2f",
                avgNoveltyRate * 100, z);

        } else if (z < 2.5) {
            type = "WARNING";
            msg  = String.format(
                "Người nhận mới chưa từng xuất hiện trong lịch sử. "
                + "Thông thường %.0f%% GD của bạn là người quen. "
                + "Cần xác nhận đây đúng người bạn muốn chuyển. Z=%.2f",
                familiarRate * 100, z);

        } else {
            type = "DANGER";
            if (familiarRate > 0.9) {
                msg = String.format(
                    "⚠ Người nhận hoàn toàn lạ — %.0f%% GD trước của bạn là người quen. "
                    + "Bất thường nghiêm trọng. Nghi vấn bị lừa đảo dẫn dắt "
                    + "chuyển tiền sang tài khoản không quen biết. Z=%.2f",
                    familiarRate * 100, z);
            } else {
                msg = String.format(
                    "Người nhận mới, trong khi %.0f%% GD thường của bạn là người quen. "
                    + "Rủi ro đáng kể cần xem xét thêm. Z=%.2f",
                    familiarRate * 100, z);
            }
        }

        return new TransactionAiInsight(null, "RECIPIENT", msg, type, z);
    }

 
    public BehaviorProfileDTO getReadableProfile(Long userId) {
        java.util.Optional<UserBehaviorProfile> profileOpt = profileRepository.findByUserId(userId);
        if (profileOpt.isEmpty()) return buildEmptyProfileDTO(userId, 0);

        UserBehaviorProfile profile = profileOpt.get();
        if (profile.getMeanVector() == null || profile.getMeanVector().isEmpty())
            return buildEmptyProfileDTO(userId, profile.getTxCount());

        return buildFullProfile(userId, profile);
    }

    public List<BehaviorProfileDTO> getAnomalyHistory(Long userId, int days) {
        return List.of(getReadableProfile(userId));
    }
 
    private double safeZ(double current, double mean, double variance) {
        if (variance < 1e-10) return 0.0;
        return Math.abs(current - mean) / Math.sqrt(variance);
    }
 
    private double computeRecipientZ(double current, double mean, double variance,
                                     double recipientNovelty) {
        if (recipientNovelty == 0.0) return 0.0;
        if (variance < 1e-10) return 3.0;  
        return Math.abs(current - mean) / Math.sqrt(variance);
    }

    private double[] extractFeatureVector(Transaction tx, double gapSeconds,
                                           double recipientNovelty) {
        double logAmount = Math.log1p(tx.getAmount().doubleValue());
        double hour      = tx.getCreatedAt().getHour()
                         + tx.getCreatedAt().getMinute() / 60.0;
        double hourRad   = (hour / 24.0) * 2 * Math.PI;
        // P1.5: chiều 6 — tỷ lệ giao dịch/số dư (log-scale để ổn định)
        double balance      = tx.getFromAccount().getBalance().doubleValue();
        double balanceRatio = balance > 0 ? tx.getAmount().doubleValue() / balance : 0.0;
        return new double[]{
            logAmount,
            Math.sin(hourRad),
            Math.cos(hourRad),
            Math.log1p(Math.max(gapSeconds, 1.0)),
            recipientNovelty,
            Math.log1p(balanceRatio)
        };
    }

    private int normalizeEuclidean(double raw) {
        return (int) Math.min((raw / 4.0) * 100, 100);
    }

    private int normalizeMahalanobis(double dSquared) {
        return (int) Math.min((dSquared / 20.51) * 100, 100);
    }

 
    private String fmtMoney(double amount) {
        if (amount >= 1_000_000_000) {
            return String.format("%.1f tỷđ", amount / 1_000_000_000);
        } else if (amount >= 1_000_000) {
            return String.format("%.1f triệuđ", amount / 1_000_000);
        } else if (amount >= 1_000) {
            return String.format("%.0f nghìnđ", amount / 1_000);
        } else {
            return String.format("%.0fđ", amount);
        }
    }
 
    private String fmtDuration(double seconds) {
        long sec = (long) seconds;
        if (sec < 60)     return sec + " giây";
        if (sec < 3600)   return (sec / 60) + " phút " + (sec % 60) + " giây";
        if (sec < 86400)  return (sec / 3600) + " giờ " + ((sec % 3600) / 60) + " phút";
        return (sec / 86400) + " ngày";
    }
 
    private String formatHourFromTx(Transaction tx) {
        return String.format("%02d:%02d",
            tx.getCreatedAt().getHour(),
            tx.getCreatedAt().getMinute());
    }
 
    private String buildAvgHour(double[] mean) {
        if (mean.length <= 2) return "N/A";
        double hourRad = Math.atan2(mean[1], mean[2]);
        double hourDec = (hourRad < 0 ? hourRad + 2 * Math.PI : hourRad)
                         * (24.0 / (2 * Math.PI));
        int h = (int) hourDec;
        int m = (int) ((hourDec - h) * 60);
        return String.format("%02d:%02d", h, m);
    }
 

    // P1.5: pad đến DIMENSIONS để tương thích ngược với profile DB 5D cũ
    private double[] convertListToArray(List<Double> list) {
        double[] result = new double[DIMENSIONS];
        if (list == null) return result;
        for (int i = 0; i < Math.min(list.size(), DIMENSIONS); i++) {
            Double val = list.get(i);
            result[i] = (val == null || Double.isNaN(val) || Double.isInfinite(val)) ? 0.0 : val;
        }
        return result;
    }

    // P1.5: pad đến DIMENSIONS×DIMENSIONS để tương thích ngược với covariance DB 5×5 cũ
    private double[][] convertNestedListToArray(List<List<Double>> nested) {
        double[][] array = new double[DIMENSIONS][DIMENSIONS];
        if (nested == null) return array;
        for (int i = 0; i < Math.min(nested.size(), DIMENSIONS); i++) {
            List<Double> row = nested.get(i);
            if (row == null) continue;
            for (int j = 0; j < Math.min(row.size(), DIMENSIONS); j++) {
                Double val = row.get(j);
                array[i][j] = (val == null || Double.isNaN(val) || Double.isInfinite(val))
                              ? 0.0 : val;
            }
        }
        return array;
    }

    private double calculateCurrentAnomalyScore(UserBehaviorProfile profile) {
        if (profile.getTxCount() < 5 || profile.getMeanVector() == null
                || profile.getEwmaMeanVector() == null) return 0.0;

        double[] mean     = convertListToArray(profile.getMeanVector());
        double[] ewmaMean = convertListToArray(profile.getEwmaMeanVector());
        int txCount       = profile.getTxCount();

        if (txCount < 10) {
            double raw = mathCalculator.calculateEuclidean(ewmaMean, mean);
            return Math.min((raw / 4.0) * 100, 100);
        } else if (txCount < 50) {
            double[][] covC = convertNestedListToArray(profile.getCovarianceMatrixC());
            double dSquared = mathCalculator.calculateWithGlobalPrior(ewmaMean, mean, covC, txCount);
            return Math.min((dSquared / 20.51) * 100, 100);
        } else {
            double[][] covC = convertNestedListToArray(profile.getCovarianceMatrixC());
            double lambda   = txCount < 150 ? 0.1 : 0.001;
            double dSquared = mathCalculator.calculateMahalanobis(ewmaMean, mean, covC,
                                                                   txCount, lambda);
            return Math.min((dSquared / 20.51) * 100, 100);
        }
    }

    private BehaviorProfileDTO buildEmptyProfileDTO(Long userId, Integer txCount) {
        return BehaviorProfileDTO.builder()
            .userId(userId).txCount(txCount != null ? txCount : 0)
            .profilingPhase("COLD_START").profileReliability(0.0)
            .avgAmount(0).avgTransactionHour("N/A").avgGapHours(0)
            .stdAmount(0.0).stdGapHours(0.0).anomalyScore(0)
            .anomalyLevel("NORMAL").calculationMethod("EUCLIDEAN").lastUpdated("N/A")
            .amountAnomalyScore(0.0).hourAnomalyScore(0.0)
            .frequencyAnomalyScore(0.0).recipientAnomalyScore(0.0).build();
    }

    private BehaviorProfileDTO buildFullProfile(Long userId, UserBehaviorProfile profile) {
        double[] mean = convertListToArray(profile.getMeanVector());
        double[] ewma = convertListToArray(profile.getEwmaMeanVector());
        double[] eVar = convertListToArray(profile.getEwmaVariance());
        int n         = profile.getTxCount();

        double avgAmount   = mean.length > 0 ? Math.expm1(mean[0]) : 0;
        double stdAmount   = eVar.length > 0 ? Math.expm1(Math.sqrt(eVar[0])) : 0;
        double avgGapHours = mean.length > 3 ? Math.expm1(mean[3]) / 3600.0 : 0;
        double stdGapHours = eVar.length > 3 ? Math.expm1(Math.sqrt(eVar[3])) / 3600.0 : 0;

        double zAmount    = eVar[0] > 0 ? Math.abs(ewma[0] - mean[0]) / Math.sqrt(eVar[0]) : 0;
        double zHour      = eVar[1] > 0 ? Math.abs(ewma[1] - mean[1]) / Math.sqrt(eVar[1]) : 0;
        double zFrequency = eVar[3] > 0 ? Math.abs(ewma[3] - mean[3]) / Math.sqrt(eVar[3]) : 0;
        double zRecipient = eVar[4] > 0 ? Math.abs(ewma[4] - mean[4]) / Math.sqrt(eVar[4]) : 0;

        double anomalyScore   = calculateCurrentAnomalyScore(profile);
        String anomalyLevel   = anomalyScore < 40 ? "NORMAL" : anomalyScore < 70 ? "SUSPICIOUS" : "CRITICAL";
        String phase   = n < 10 ? "COLD_START"
                       : n < 50  ? "WARM_START"
                       : n < 150 ? "TRANSITION" : "MATURE";
        String method  = n < 10 ? "EUCLIDEAN"
                       : n < 50  ? "GLOBAL_PRIOR_MAHALANOBIS"
                       : n < 150 ? "BLEND" : "MAHALANOBIS";
        double reliability    = Math.min(n / 150.0, 1.0);

        return BehaviorProfileDTO.builder()
            .userId(userId).txCount(n).profilingPhase(phase)
            .profileReliability(reliability).calculationMethod(method)
            .lastUpdated(profile.getUpdatedAt() != null ? profile.getUpdatedAt().toString() : "N/A")
            .avgAmount(avgAmount).stdAmount(stdAmount)
            .avgTransactionHour(buildAvgHour(mean))
            .avgGapHours(avgGapHours).stdGapHours(stdGapHours)
            .anomalyScore(anomalyScore).anomalyLevel(anomalyLevel)
            .amountAnomalyScore(Math.min(zAmount    / 3.0 * 100, 100))
            .hourAnomalyScore  (Math.min(zHour      / 3.0 * 100, 100))
            .frequencyAnomalyScore(Math.min(zFrequency / 3.0 * 100, 100))
            .recipientAnomalyScore(Math.min(zRecipient / 3.0 * 100, 100))
            .build();
    }
}

