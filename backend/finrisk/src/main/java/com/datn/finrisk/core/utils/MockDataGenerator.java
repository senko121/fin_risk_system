package com.datn.finrisk.core.utils;

import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class MockDataGenerator {
    private final Random random = new Random();

 
    public double nextGaussian(double mean, double stdDev) {
        return mean + random.nextGaussian() * stdDev;
    }
 
    public double generateGaussianHour(double mean, double stdDev) {
        double hour = nextGaussian(mean, stdDev);
        if (hour < 0) hour = (hour % 24) + 24;
        if (hour >= 24) hour = hour % 24;
        return hour;
    }
 
    public int nextInt(int min, int max) {
        return random.nextInt((max - min) + 1) + min;
    }
 
    public String getRandomSinkAccount(int maxIndex) {
        int index = nextInt(1, maxIndex);
        return String.format("SINK_ACC_%03d", index);
    }
}