package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.PeerGroup;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.PeerGroupRepository;
import com.datn.finrisk.core.utils.MahalanobisCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * P2.1 — Peer-Group Profiling.
 *
 * Trả về mean và covariance của nhóm nhân khẩu học phù hợp để làm
 * baseline Mahalanobis khi user có < 10 giao dịch (cold-start).
 *
 * Nhóm được xác định bởi (age_range, region) của user.
 * Nếu user chưa có thông tin nhân khẩu, trả về empty.
 */
@Slf4j
@Service
public class PeerGroupService {

    private static final int DIMENSIONS = MahalanobisCalculator.DIMENSIONS;

    @Autowired
    private PeerGroupRepository peerGroupRepository;

    /**
     * Lấy peer group phù hợp với user.
     * Trả về empty nếu user thiếu age_range/region, hoặc chưa có group seed.
     */
    public Optional<PeerGroup> findPeerGroup(User user) {
        if (user == null || user.getAgeRange() == null || user.getRegion() == null) {
            return Optional.empty();
        }
        return peerGroupRepository.findByAgeRangeAndRegion(user.getAgeRange(), user.getRegion());
    }

    /**
     * Cập nhật thống kê peer group với feature vector mới (online update).
     * Dùng Welford incremental để cập nhật mean.
     * Gọi sau mỗi lần BehaviorLearningService học từ giao dịch thành công.
     */
    @Transactional
    public void updatePeerGroupStats(User user, double[] featureVector) {
        if (user == null || user.getAgeRange() == null || user.getRegion() == null) return;

        Optional<PeerGroup> opt = peerGroupRepository
                .findByAgeRangeAndRegion(user.getAgeRange(), user.getRegion());
        if (opt.isEmpty()) return;

        PeerGroup pg = opt.get();
        int n = pg.getSampleCount() + 1;
        pg.setSampleCount(n);

        double[] oldMean = toArray(pg.getMeanVector());
        double[] newMean = new double[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            newMean[i] = oldMean[i] + (featureVector[i] - oldMean[i]) / n;
        }
        pg.setMeanVector(toList(newMean));
        peerGroupRepository.save(pg);

        log.debug("[PeerGroup] Updated {}/{} n={}", user.getAgeRange(), user.getRegion(), n);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double[] toArray(List<Double> list) {
        double[] arr = new double[DIMENSIONS];
        if (list == null) return arr;
        for (int i = 0; i < Math.min(list.size(), DIMENSIONS); i++) {
            Double v = list.get(i);
            arr[i] = (v == null || Double.isNaN(v) || Double.isInfinite(v)) ? 0.0 : v;
        }
        return arr;
    }

    private List<Double> toList(double[] arr) {
        java.util.List<Double> list = new java.util.ArrayList<>(DIMENSIONS);
        for (double v : arr) list.add(v);
        return list;
    }

    /** Chuyển List<List<Double>> peer covariance thành double[][] cho Mahalanobis. */
    public double[][] toCovArray(List<List<Double>> nested) {
        double[][] arr = new double[DIMENSIONS][DIMENSIONS];
        if (nested == null) return arr;
        for (int i = 0; i < Math.min(nested.size(), DIMENSIONS); i++) {
            List<Double> row = nested.get(i);
            if (row == null) continue;
            for (int j = 0; j < Math.min(row.size(), DIMENSIONS); j++) {
                Double v = row.get(j);
                arr[i][j] = (v == null || Double.isNaN(v) || Double.isInfinite(v)) ? 0.0 : v;
            }
        }
        return arr;
    }
}
