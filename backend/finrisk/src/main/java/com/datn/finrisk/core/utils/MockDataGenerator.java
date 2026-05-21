package com.datn.finrisk.core.utils;

import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class MockDataGenerator {
    private final Random random = new Random();

    /**
     * Sinh số ngẫu nhiên theo phân phối chuẩn Gaussian (Hình Chuông)
     * @param mean Điểm đỉnh muốn hội tụ (ví dụ: đỉnh ăn trưa là 12.0 giờ)
     * @param stdDev Độ giãn cách (ví dụ: 1.5 tiếng xung quanh đỉnh)
     */
    public double nextGaussian(double mean, double stdDev) {
        return mean + random.nextGaussian() * stdDev;
    }

    /**
     * Lấy giờ hợp lệ (0 đến 23.99) theo đỉnh mong muốn
     */
    public double generateGaussianHour(double mean, double stdDev) {
        double hour = nextGaussian(mean, stdDev);
        if (hour < 0) hour = (hour % 24) + 24;
        if (hour >= 24) hour = hour % 24;
        return hour;
    }

    /**
     * Trả về số nguyên ngẫu nhiên trong khoảng [min, max]
     */
    public int nextInt(int min, int max) {
        return random.nextInt((max - min) + 1) + min;
    }

    /**
     * Bốc ngẫu nhiên một số tài khoản rác để mô phỏng tính độc lạ (Novelty)
     */
    public String getRandomSinkAccount(int maxIndex) {
        int index = nextInt(1, maxIndex);
        return String.format("SINK_ACC_%03d", index);
    }
}