package com.datn.finrisk.core.utils;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.springframework.stereotype.Component;

@Component
public class MahalanobisCalculator {

    private static final int DIMENSIONS = 5;

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
                
                // 🚀 SANITIZE: Khử độc dữ liệu ma trận thô từ DB lên chống nhiễm chéo lỗi hệ số
                if (Double.isNaN(covarianceC[i][j]) || Double.isInfinite(covarianceC[i][j])) {
                    covarianceC[i][j] = 0.0;
                }

                covArray[i][j] = covarianceC[i][j] / txCount;
                
                if (i == j) {
                    covArray[i][j] += lambda; 
                }
            }
        }

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