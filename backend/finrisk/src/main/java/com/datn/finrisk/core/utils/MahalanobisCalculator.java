package com.datn.finrisk.core.utils;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.springframework.stereotype.Component;

@Component
public class MahalanobisCalculator {

    // P1.5: tăng từ 5 → 6 (thêm chiều log1p(balanceRatio))
    public static final int DIMENSIONS = 6;

    // P1.1: phương sai global prior cho từng chiều
    // [logAmount, sin(hour), cos(hour), logGap, recipientNovelty, log1p(balanceRatio)]
    private static final double[] GLOBAL_PRIOR_VARIANCES = {1.5, 0.5, 0.5, 2.0, 0.25, 0.4};

    // Ngưỡng tx mà trọng số global prior giảm về 0
    private static final int GLOBAL_PRIOR_HORIZON = 50;

    public double calculateEuclidean(double[] currentTx, double[] meanVector) {
        double sumSq = 0.0;
        for (int i = 0; i < DIMENSIONS; i++) {
            double diff = currentTx[i] - meanVector[i];
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq);
    }

    public double calculateMahalanobis(double[] currentTx, double[] meanVector,
                                       double[][] covarianceC, int txCount, double lambda) {
        double[][] covArray = new double[DIMENSIONS][DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            for (int j = 0; j < DIMENSIONS; j++) {
                if (Double.isNaN(covarianceC[i][j]) || Double.isInfinite(covarianceC[i][j])) {
                    covarianceC[i][j] = 0.0;
                }
                covArray[i][j] = covarianceC[i][j] / txCount;
                if (i == j) {
                    covArray[i][j] += lambda;
                }
            }
        }
        return computeMahalanobisFromCov(currentTx, meanVector, covArray);
    }

    // P1.1: Mahalanobis với covariance blended giữa global prior và user data.
    // alpha giảm từ 1.0 → 0 khi txCount tăng từ 0 → GLOBAL_PRIOR_HORIZON.
    // Cho phép sử dụng Mahalanobis từ 10 giao dịch thay vì 50.
    public double calculateWithGlobalPrior(double[] currentTx, double[] meanVector,
                                           double[][] covarianceC, int txCount) {
        double alpha = Math.max(0.0, 1.0 - ((double) txCount / GLOBAL_PRIOR_HORIZON));

        double[][] covArray = new double[DIMENSIONS][DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            for (int j = 0; j < DIMENSIONS; j++) {
                double userCov = (txCount > 0) ? covarianceC[i][j] / txCount : 0.0;
                if (Double.isNaN(userCov) || Double.isInfinite(userCov)) userCov = 0.0;
                double globalCov = (i == j) ? GLOBAL_PRIOR_VARIANCES[i] : 0.0;
                covArray[i][j] = alpha * globalCov + (1.0 - alpha) * userCov;
                if (i == j) covArray[i][j] = Math.max(covArray[i][j], 0.01);
            }
        }
        return computeMahalanobisFromCov(currentTx, meanVector, covArray);
    }

    private double computeMahalanobisFromCov(double[] currentTx, double[] meanVector,
                                              double[][] covArray) {
        RealMatrix covMatrix = new Array2DRowRealMatrix(covArray);
        RealMatrix inverseCovMatrix;
        try {
            inverseCovMatrix = new LUDecomposition(covMatrix).getSolver().getInverse();
        } catch (Exception e) {
            System.err.println("⚠️ Lỗi nghịch đảo ma trận (Singular). Rơi vào trạng thái Fallback!");
            return calculateEuclidean(currentTx, meanVector);
        }

        double[] diff = new double[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            diff[i] = currentTx[i] - meanVector[i];
        }

        double dSquared = 0.0;
        for (int i = 0; i < DIMENSIONS; i++) {
            double tempSq = 0.0;
            for (int j = 0; j < DIMENSIONS; j++) {
                tempSq += diff[j] * inverseCovMatrix.getEntry(i, j);
            }
            dSquared += tempSq * diff[i];
        }
        return Math.max(0.0, dSquared);
    }
}
