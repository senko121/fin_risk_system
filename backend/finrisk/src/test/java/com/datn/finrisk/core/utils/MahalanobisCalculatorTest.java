package com.datn.finrisk.core.utils;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MahalanobisCalculator — Unit Tests")
class MahalanobisCalculatorTest {

    private MahalanobisCalculator calculator;
    private static final Offset<Double> EPS = Offset.offset(1e-9);

    @BeforeEach
    void setUp() {
        calculator = new MahalanobisCalculator();
    }

    // ── calculateEuclidean ────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateEuclidean()")
    class CalculateEuclidean {

        @Test
        @DisplayName("identical vectors → 0")
        void identicalVectors_zero() {
            double[] v = {1.0, 2.0, 3.0, 4.0, 5.0};
            assertThat(calculator.calculateEuclidean(v, v)).isCloseTo(0.0, EPS);
        }

        @Test
        @DisplayName("unit difference in one dimension → 1.0")
        void unitDiff_returnsOne() {
            double[] current = {1.0, 0.0, 0.0, 0.0, 0.0};
            double[] mean    = {0.0, 0.0, 0.0, 0.0, 0.0};
            assertThat(calculator.calculateEuclidean(current, mean)).isCloseTo(1.0, EPS);
        }

        @Test
        @DisplayName("3-4-0-0-0 diff → 5 (Pythagorean triple)")
        void pythagoreanTriple() {
            double[] current = {3.0, 0.0, 0.0, 4.0, 0.0};
            double[] mean    = {0.0, 0.0, 0.0, 0.0, 0.0};
            assertThat(calculator.calculateEuclidean(current, mean)).isCloseTo(5.0, EPS);
        }

        @Test
        @DisplayName("all dimensions equal 1 diff → √5")
        void allOnes_sqrtFive() {
            double[] current = {1.0, 1.0, 1.0, 1.0, 1.0};
            double[] mean    = {0.0, 0.0, 0.0, 0.0, 0.0};
            assertThat(calculator.calculateEuclidean(current, mean))
                    .isCloseTo(Math.sqrt(5.0), Offset.offset(1e-6));
        }

        @Test
        @DisplayName("negative differences treated the same as positive (squared)")
        void negativeDiffs_sameAsPositive() {
            double[] c1 = { 1.0, 0.0, 0.0, 0.0, 0.0};
            double[] c2 = {-1.0, 0.0, 0.0, 0.0, 0.0};
            double[] m  = { 0.0, 0.0, 0.0, 0.0, 0.0};
            assertThat(calculator.calculateEuclidean(c1, m))
                    .isCloseTo(calculator.calculateEuclidean(c2, m), EPS);
        }
    }

    // ── calculateMahalanobis ──────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateMahalanobis()")
    class CalculateMahalanobis {

        @Test
        @DisplayName("identical current and mean → ≈ 0")
        void identicalVectors_nearZero() {
            double[] v = {1.0, 2.0, 3.0, 4.0, 5.0};
            double[][] cov = new double[5][5];
            double result = calculator.calculateMahalanobis(v, v, cov, 10, 0.1);
            assertThat(result).isCloseTo(0.0, Offset.offset(1e-6));
        }

        @Test
        @DisplayName("result is always ≥ 0 (non-negative)")
        void result_nonNegative() {
            double[] current = {5.0, 3.0, 1.0, 2.0, 0.5};
            double[] mean    = {1.0, 1.0, 1.0, 1.0, 1.0};
            double[][] cov   = new double[5][5];
            assertThat(calculator.calculateMahalanobis(current, mean, cov, 50, 0.1))
                    .isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("singular covariance with lambda=0 → fallback Euclidean, no exception")
        void singularCovariance_fallsBackToEuclidean() {
            double[] current = {2.0, 0.0, 0.0, 0.0, 0.0};
            double[] mean    = {0.0, 0.0, 0.0, 0.0, 0.0};
            double[][] cov   = new double[5][5]; // all zeros + lambda 0 → singular
            // Must not throw
            double result = calculator.calculateMahalanobis(current, mean, cov, 1, 0.0);
            assertThat(result).isGreaterThanOrEqualTo(0.0).isFinite();
        }

        @Test
        @DisplayName("NaN in covariance → sanitized to 0, no exception")
        void nanInCovariance_sanitized() {
            double[] current = {1.0, 0.0, 0.0, 0.0, 0.0};
            double[] mean    = {0.0, 0.0, 0.0, 0.0, 0.0};
            double[][] cov   = new double[5][5];
            cov[0][0] = Double.NaN;
            double result = calculator.calculateMahalanobis(current, mean, cov, 10, 0.1);
            assertThat(result).isFinite().isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("Infinity in covariance → sanitized to 0, no exception")
        void infinityInCovariance_sanitized() {
            double[] current = {1.0, 0.0, 0.0, 0.0, 0.0};
            double[] mean    = {0.0, 0.0, 0.0, 0.0, 0.0};
            double[][] cov   = new double[5][5];
            cov[2][2] = Double.POSITIVE_INFINITY;
            double result = calculator.calculateMahalanobis(current, mean, cov, 10, 0.1);
            assertThat(result).isFinite().isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("larger lambda increases regularization — result is finite")
        void largeLambda_stillFinite() {
            double[] current = {10.0, 5.0, 3.0, 8.0, 2.0};
            double[] mean    = {1.0,  1.0, 1.0, 1.0, 1.0};
            double[][] cov   = new double[5][5];
            double result = calculator.calculateMahalanobis(current, mean, cov, 100, 10.0);
            assertThat(result).isFinite().isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("result grows with larger deviation from mean")
        void largerDeviation_largerResult() {
            double[] mean = {0.0, 0.0, 0.0, 0.0, 0.0};
            double[][] cov = new double[5][5];

            double[] small = {1.0, 0.0, 0.0, 0.0, 0.0};
            double[] large = {5.0, 0.0, 0.0, 0.0, 0.0};

            double r1 = calculator.calculateMahalanobis(small, mean, cov, 100, 0.1);
            double r2 = calculator.calculateMahalanobis(large, mean, cov, 100, 0.1);
            assertThat(r2).isGreaterThan(r1);
        }
    }
}
