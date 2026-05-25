package com.datn.finrisk;

import com.datn.finrisk.infrastructure.config.MdcTaskDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableScheduling
@EnableAsync
public class AppConfig {

    @Value("${ai.timeout.connect-ms:3000}")
    private int aiConnectTimeoutMs;

    @Value("${ai.timeout.read-ms:15000}")
    private int aiReadTimeoutMs;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiConnectTimeoutMs);
        factory.setReadTimeout(aiReadTimeoutMs);
        return new RestTemplate(factory);
    }

    /**
     * Background low-priority tasks: OTP send, email, audit logging, behavior learning.
     * Return type changed from Executor → ThreadPoolTaskExecutor so ExecutorMetricsLogger
     * can inspect queue depth without an unchecked cast.
     * Behaviour is unchanged: core=5, max=10, queue=500.
     */
    @Bean(name = "aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("BG-Task-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Per-frame realtime emotion (WebSocket STREAM path).
     * Completely isolated from FINALIZE biometric tasks — eliminates cross-starvation.
     *
     * DiscardOldestPolicy: when saturated, removes the stalest queued frame and enqueues
     * the freshest one. STREAM frames are expendable — a dropped frame shows UNKNOWN briefly
     * on the live UI; it never affects the FINALIZE biometric outcome (which uses the
     * BufferedFrames snapshot, not the per-frame emotion labels).
     */
    @Bean(name = "realtimeEmotionExecutor")
    public ThreadPoolTaskExecutor realtimeEmotionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("RT-Emotion-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Biometric FINALIZE executor: face identity verification + batch emotion evaluation.
     * 100% isolated from STREAM load — FINALIZE always gets dedicated threads.
     *
     * Sizing: core=4/max=8 handles ~20 concurrent HIGH-risk FINALIZEs (2 subtasks each).
     * Queue=50 absorbs short bursts without task rejection.
     *
     * AbortPolicy: explicit rejection under extreme overload is a clean failure
     * (caller sees RejectedExecutionException → FINAL_RESULT ERROR) rather than
     * a silent 20 s timeout cascade that generates false biometric rejects.
     */
    @Bean(name = "biometricVerifyExecutor")
    public ThreadPoolTaskExecutor biometricVerifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("BioVerify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
