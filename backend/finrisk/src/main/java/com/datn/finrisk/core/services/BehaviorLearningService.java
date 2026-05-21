package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserBehaviorProfile;
import com.datn.finrisk.core.repository.UserBehaviorProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class BehaviorLearningService {

    @Autowired
    private UserBehaviorProfileRepository profileRepository;

    private static final int    DIMENSIONS = 5;
    private static final double EWMA_ALPHA = 0.05;

    // =========================================================
    // PUBLIC API — 2 entry points rõ ràng
    // =========================================================

    /**
     * PRODUCTION FLOW: Gọi sau mỗi giao dịch thật thành công.
     * Chạy bất đồng bộ để không block response trả về user.
     * Gap được tính từ now() vì đây là giao dịch thật.
     */
    @Async("aiTaskExecutor")
    @Transactional
    public void learnFromTransaction(Transaction tx, boolean isNewRecipient) {
        System.out.println("🧠 [ASYNC] Học giao dịch thật #" + tx.getId());

        User user = tx.getFromAccount().getUser();
        UserBehaviorProfile profile = profileRepository
            .findByUserId(user.getId())
            .orElseGet(() -> createInitialProfile(user));

        double gapSeconds = computeGap(profile.getLastTxTimestamp(), LocalDateTime.now());
        executeLearnCycle(tx, profile, gapSeconds, isNewRecipient, LocalDateTime.now());
    }

    /**
     * SEEDER FLOW: Gọi từ TimeMachineSeederService.
     * Chạy đồng bộ để đảm bảo thứ tự học đúng theo timeline giả lập.
     * Gap và timestamp được truyền vào từ ngoài (txTime fake).
     */
    @Transactional
    public void learnFromTransactionSync(
            Transaction tx,
            boolean isNewRecipient,
            double gapSeconds) {

        User user = tx.getFromAccount().getUser();
        UserBehaviorProfile profile = profileRepository
            .findByUserId(user.getId())
            .orElseGet(() -> createInitialProfile(user));

        // Dùng createdAt của tx làm mốc thời gian — không phải now()
        LocalDateTime txTime = tx.getCreatedAt() != null
            ? tx.getCreatedAt() : LocalDateTime.now();

        executeLearnCycle(tx, profile, gapSeconds, isNewRecipient, txTime);
    }

    // =========================================================
    // CORE LEARNING CYCLE — Dùng chung cho cả 2 flow
    // =========================================================

    private void executeLearnCycle(
            Transaction tx,
            UserBehaviorProfile profile,
            double gapSeconds,
            boolean isNewRecipient,
            LocalDateTime timestampToSave) {

        double recipientNovelty = isNewRecipient ? 1.0 : 0.0;

        // 1. Trích xuất và làm sạch vector đặc trưng
        double[] currentVector = extractAndSanitizeVector(tx, gapSeconds, recipientNovelty);

        // 2. Cập nhật Welford (Mean + Covariance Matrix C)
        updateWelford(profile, currentVector);

        // 3. Cập nhật EWMA (Hành vi ngắn hạn)
        updateEwma(profile, currentVector);

        // 4. Cập nhật mốc thời gian giao dịch cuối
        profile.setLastTxTimestamp(timestampToSave);

        // 5. Lưu xuống DB
        profileRepository.save(profile);

        System.out.println("✅ [LEARN] User #"
            + profile.getUser().getId()
            + " | tx_count=" + profile.getTxCount()
            + " | gap=" + String.format("%.0f", gapSeconds) + "s");
    }

    // =========================================================
    // WELFORD ONLINE ALGORITHM
    // =========================================================

    private void updateWelford(UserBehaviorProfile profile, double[] x) {
        int n = profile.getTxCount() + 1;
        profile.setTxCount(n);

        double[]   meanOld = convertListToArray(profile.getMeanVector());
        double[][] covCOld = convertNestedListToArray(profile.getCovarianceMatrixC());

        double[] meanNew  = new double[DIMENSIONS];
        double[] deltaOld = new double[DIMENSIONS];
        double[] deltaNew = new double[DIMENSIONS];

        for (int i = 0; i < DIMENSIONS; i++) {
            deltaOld[i] = x[i] - meanOld[i];
            meanNew[i]  = meanOld[i] + (deltaOld[i] / n);
            deltaNew[i] = x[i] - meanNew[i];
        }

        double[][] covCNew = new double[DIMENSIONS][DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            for (int j = 0; j < DIMENSIONS; j++) {
                covCNew[i][j] = covCOld[i][j] + (deltaOld[i] * deltaNew[j]);
            }
        }

        // Sanitize trước khi lưu
        profile.setMeanVector(convertArrayToList(sanitize(meanNew)));
        profile.setCovarianceMatrixC(convertMatrixToNestedList(sanitizeMatrix(covCNew)));
    }

    // =========================================================
    // EWMA ALGORITHM
    // =========================================================

    private void updateEwma(UserBehaviorProfile profile, double[] x) {
        int n = profile.getTxCount(); // Đã được tăng bởi updateWelford

        double[] ewmaMeanOld = convertListToArray(profile.getEwmaMeanVector());
        double[] ewmaVarOld  = convertListToArray(profile.getEwmaVariance());
        double[] ewmaMeanNew = new double[DIMENSIONS];
        double[] ewmaVarNew  = new double[DIMENSIONS];

        for (int i = 0; i < DIMENSIONS; i++) {
            if (n == 1) {
                // Giao dịch đầu tiên: khởi tạo bằng chính giá trị đó
                ewmaMeanNew[i] = x[i];
                ewmaVarNew[i]  = 0.0;
            } else {
                double diff    = x[i] - ewmaMeanOld[i];
                ewmaMeanNew[i] = (1 - EWMA_ALPHA) * ewmaMeanOld[i] + EWMA_ALPHA * x[i];
                ewmaVarNew[i]  = (1 - EWMA_ALPHA) * ewmaVarOld[i]  + EWMA_ALPHA * diff * diff;
            }
        }

        profile.setEwmaMeanVector(convertArrayToList(sanitize(ewmaMeanNew)));
        profile.setEwmaVariance(convertArrayToList(sanitize(ewmaVarNew)));
    }

    // =========================================================
    // FEATURE VECTOR EXTRACTION + SANITIZE
    // =========================================================

    /**
     * Trích xuất vector 5 chiều từ transaction:
     * [log(amount+1), sin(hour_rad), cos(hour_rad), log(gap+1), recipientNovelty]
     *
     * Toán học:
     * - log1p: Biến phân phối lệch phải (right-skewed) thành gần chuẩn hơn
     * - sin/cos hour: Circular encoding — tránh vấn đề 23h và 0h xa nhau khi dùng số thẳng
     * - recipientNovelty: 0 = người quen, 1 = người lạ hoàn toàn
     */
    private double[] extractAndSanitizeVector(
            Transaction tx, double gapSeconds, double recipientNovelty) {

        double amount  = (tx.getAmount() != null)
            ? tx.getAmount().doubleValue() : 0.0;

        // Gap tối thiểu 1 giây để tránh log1p(0) = 0 bị degenerate
        // và tuyệt đối không được âm
        double safeGap = Math.max(gapSeconds, 1.0);

        double logAmount = Math.log1p(amount);
        double hour      = tx.getCreatedAt().getHour()
                         + tx.getCreatedAt().getMinute() / 60.0;
        double hourRad   = (hour / 24.0) * 2 * Math.PI;
        double logGap    = Math.log1p(safeGap);

        double[] vector = {
            logAmount,
            Math.sin(hourRad),
            Math.cos(hourRad),
            logGap,
            recipientNovelty
        };

        return sanitize(vector);
    }

    // =========================================================
    // SANITIZE HELPERS — Tất cả NaN/Infinity → 0.0
    // =========================================================

    private double[] sanitize(double[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = (Double.isNaN(arr[i]) || Double.isInfinite(arr[i])) ? 0.0 : arr[i];
        }
        return result;
    }

    private double[][] sanitizeMatrix(double[][] mat) {
        double[][] result = new double[mat.length][mat[0].length];
        for (int i = 0; i < mat.length; i++) {
            for (int j = 0; j < mat[i].length; j++) {
                result[i][j] = (Double.isNaN(mat[i][j]) || Double.isInfinite(mat[i][j]))
                    ? 0.0 : mat[i][j];
            }
        }
        return result;
    }

    // =========================================================
    // GAP CALCULATOR
    // =========================================================

    /**
     * Tính khoảng cách thời gian giữa 2 mốc.
     * Trả về 86400.0 (1 ngày) nếu chưa có lastTimestamp.
     * Đảm bảo không bao giờ trả về số âm.
     */
    private double computeGap(LocalDateTime last, LocalDateTime current) {
        if (last == null) return 86400.0;
        long gap = Duration.between(last, current).getSeconds();
        return Math.max(gap, 1.0);
    }

    // =========================================================
    // INITIALIZER
    // =========================================================

    private UserBehaviorProfile createInitialProfile(User user) {
        UserBehaviorProfile p = new UserBehaviorProfile();
        p.setUser(user);
        p.setTxCount(0);

        List<Double> zeros = Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0);
        p.setMeanVector(new ArrayList<>(zeros));
        p.setEwmaMeanVector(new ArrayList<>(zeros));
        p.setEwmaVariance(new ArrayList<>(zeros));

        List<List<Double>> covZeros = new ArrayList<>();
        for (int i = 0; i < DIMENSIONS; i++) {
            covZeros.add(new ArrayList<>(zeros));
        }
        p.setCovarianceMatrixC(covZeros);

        return p;
    }

    // =========================================================
    // CONVERSION HELPERS
    // =========================================================

    private double[] convertListToArray(List<Double> list) {
        if (list == null || list.isEmpty()) return new double[DIMENSIONS];
        return list.stream().mapToDouble(v -> v == null ? 0.0 : v).toArray();
    }

    private List<Double> convertArrayToList(double[] array) {
        List<Double> list = new ArrayList<>();
        for (double v : array) list.add(v);
        return list;
    }

    private double[][] convertNestedListToArray(List<List<Double>> nestedList) {
        double[][] array = new double[DIMENSIONS][DIMENSIONS];
        if (nestedList == null) return array;
        for (int i = 0; i < DIMENSIONS; i++) {
            if (i >= nestedList.size()) break;
            List<Double> row = nestedList.get(i);
            for (int j = 0; j < DIMENSIONS; j++) {
                if (row == null || j >= row.size()) continue;
                Double val = row.get(j);
                array[i][j] = (val == null || Double.isNaN(val) || Double.isInfinite(val))
                    ? 0.0 : val;
            }
        }
        return array;
    }

    private List<List<Double>> convertMatrixToNestedList(double[][] matrix) {
        List<List<Double>> nested = new ArrayList<>();
        for (int i = 0; i < DIMENSIONS; i++) {
            List<Double> row = new ArrayList<>();
            for (int j = 0; j < DIMENSIONS; j++) row.add(matrix[i][j]);
            nested.add(row);
        }
        return nested;
    }
}