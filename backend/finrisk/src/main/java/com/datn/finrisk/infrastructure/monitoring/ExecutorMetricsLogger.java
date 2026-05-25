package com.datn.finrisk.infrastructure.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Logs queue depth, active-thread count, and completion rate for the three async
 * executor pools every 30 seconds. Emits OVERLOAD warnings when queue fill ratio
 * exceeds the warning threshold so ops can act before timeout cascades occur.
 *
 * Executors monitored:
 *   aiTaskExecutor          — background tasks (OTP, email, audit, ML learning)
 *   realtimeEmotionExecutor — per-frame STREAM emotion detection
 *   biometricVerifyExecutor — FINALIZE face identity + batch emotion
 */
@Slf4j
@Component
public class ExecutorMetricsLogger {

    private static final int QUEUE_WARN_PERCENT = 70;

    @Autowired
    @Qualifier("aiTaskExecutor")
    private ThreadPoolTaskExecutor bgExecutor;

    @Autowired
    @Qualifier("realtimeEmotionExecutor")
    private ThreadPoolTaskExecutor realtimeExecutor;

    @Autowired
    @Qualifier("biometricVerifyExecutor")
    private ThreadPoolTaskExecutor biometricExecutor;

    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    public void logMetrics() {
        logPool("BG-Task       [aiTaskExecutor]", bgExecutor);
        logPool("RT-Emotion    [realtimeEmotionExecutor]", realtimeExecutor);
        logPool("BioVerify     [biometricVerifyExecutor]", biometricExecutor);
    }

    private void logPool(String label, ThreadPoolTaskExecutor taskExecutor) {
        ThreadPoolExecutor pool = taskExecutor.getThreadPoolExecutor();
        int active    = pool.getActiveCount();
        int poolSize  = pool.getPoolSize();
        int queueSize = pool.getQueue().size();
        int queueCap  = queueSize + pool.getQueue().remainingCapacity(); // total = size + remaining
        long completed = pool.getCompletedTaskCount();
        int queuePct  = queueCap > 0 ? (queueSize * 100 / queueCap) : 0;

        if (queuePct >= QUEUE_WARN_PERCENT) {
            log.warn("[EXECUTOR-METRICS][{}] ⚠️ OVERLOAD — threads={}/{} queue={}/{} ({}%) completed={}  "
                    + "Risk: task rejection or timeout cascade imminent.",
                label, active, poolSize, queueSize, queueCap, queuePct, completed);
        } else {
            log.info("[EXECUTOR-METRICS][{}] threads={}/{} queue={}/{} ({}%) completed={}",
                label, active, poolSize, queueSize, queueCap, queuePct, completed);
        }
    }
}
