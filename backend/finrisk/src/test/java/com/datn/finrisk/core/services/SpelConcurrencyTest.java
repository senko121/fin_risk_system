package com.datn.finrisk.core.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2.3 — Load test cho SpEL expression cache dùng trong RiskEvaluationService.
 *
 * Kiểm tra tính đúng đắn và an toàn của ConcurrentHashMap<String, Expression>
 * khi 500 luồng đồng thời evaluate 50 rules khác nhau.
 *
 * Các rules mô phỏng đúng cấu trúc SpEL trong production:
 *   - #isNewRecipient == true
 *   - #tx.amount >= 10000000
 *   - #deviceTrusted == false AND #tx.amount >= 5000000
 *   v.v.
 */
@DisplayName("P2.3 — SpEL Cache Concurrency Load Test")
class SpelConcurrencyTest {

    private static final int THREAD_COUNT   = 500;
    private static final int RULE_COUNT     = 50;
    private static final int TIMEOUT_SECS   = 30;

    // Mô phỏng chính xác spelCache trong RiskEvaluationService
    private final SpelExpressionParser parser    = new SpelExpressionParser();
    private final ConcurrentHashMap<String, Expression> spelCache = new ConcurrentHashMap<>();

    private Expression getCachedExpression(String spel) {
        return spelCache.computeIfAbsent(spel, parser::parseExpression);
    }

    // ── Fixture: 50 rules mô phỏng production ────────────────────────────────

    private static final List<String> RULES = buildRules();

    private static List<String> buildRules() {
        List<String> rules = new ArrayList<>(RULE_COUNT);
        // Rules thực từ DB
        rules.add("#isNewRecipient == true");
        rules.add("#tx.amount >= 10000000");
        rules.add("#deviceTrusted == false");
        rules.add("#suspiciousSession == true");
        rules.add("#isNightTime == true");
        rules.add("#recentTxCount >= 3");
        rules.add("#balanceRatio >= 0.9");
        rules.add("#dailyTotalAmount >= 20000000");
        rules.add("#tx.amount < 500000");
        rules.add("#isNewRecipient == true AND #tx.amount >= 5000000 AND #deviceTrusted == false");
        rules.add("#deviceTrusted == false AND #tx.amount >= 5000000");
        // Rules giả lập thêm để đạt 50
        for (int i = rules.size(); i < RULE_COUNT; i++) {
            rules.add("#tx.amount >= " + (i * 100_000));
        }
        return rules;
    }

    // ── Context builder ───────────────────────────────────────────────────────

    private SimpleEvaluationContext buildContext(long amount, boolean newRecipient,
                                                  boolean deviceTrusted, boolean suspicious,
                                                  boolean nightTime, int recentTx,
                                                  double balanceRatio, double dailyTotal) {
        // Mô phỏng SimpleEvaluationContext như trong RiskEvaluationService
        SimpleEvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        ctx.setVariable("isNewRecipient",   newRecipient);
        ctx.setVariable("deviceTrusted",    deviceTrusted);
        ctx.setVariable("suspiciousSession",suspicious);
        ctx.setVariable("isNightTime",      nightTime);
        ctx.setVariable("recentTxCount",    recentTx);
        ctx.setVariable("balanceRatio",     balanceRatio);
        ctx.setVariable("dailyTotalAmount", dailyTotal);

        // tx proxy với amount (dùng Map thay vì entity để đơn giản)
        ctx.setVariable("tx", new TxProxy(amount));
        return ctx;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("500 luồng đồng thời evaluate 50 rules — không race condition, kết quả đúng")
    void givenHighConcurrency_whenEvaluateAllRules_thenNoRaceConditionAndResultsCorrect()
            throws InterruptedException {

        ExecutorService pool    = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch  ready   = new CountDownLatch(THREAD_COUNT);
        CountDownLatch  start   = new CountDownLatch(1);
        AtomicInteger   errors  = new AtomicInteger(0);
        AtomicInteger   matched = new AtomicInteger(0);

        for (int t = 0; t < THREAD_COUNT; t++) {
            pool.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                SimpleEvaluationContext ctx = buildContext(
                        15_000_000L, true, false, false, false, 0, 0.5, 5_000_000
                );
                for (String spel : RULES) {
                    try {
                        Boolean result = getCachedExpression(spel).getValue(ctx, Boolean.class);
                        if (Boolean.TRUE.equals(result)) matched.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        boolean finished = pool.awaitTermination(TIMEOUT_SECS, TimeUnit.SECONDS);

        assertTrue(finished, "Thread pool chưa xong sau " + TIMEOUT_SECS + "s");
        assertEquals(0, errors.get(), "Có race condition / exception: " + errors.get() + " lỗi");

        // Với amount=15M, newRecipient=true, deviceTrusted=false: nhiều rules phải match
        assertTrue(matched.get() > 0, "Không có rule nào matched — kiểm tra context");

        // Cache phải được populate (<=50 unique keys vì RULE_COUNT=50)
        assertTrue(spelCache.size() <= RULE_COUNT,
                "Cache size bất thường: " + spelCache.size());
        assertTrue(spelCache.size() > 0, "Cache rỗng — computeIfAbsent không hoạt động");
    }

    @Test
    @DisplayName("Cache tái sử dụng — sau 500 luồng, cache size không đổi")
    void givenCachePrewarmed_whenAdditional500Threads_thenCacheSizeStable()
            throws InterruptedException {

        // Warm up cache
        SimpleEvaluationContext warmCtx = buildContext(
                1_000_000L, false, true, false, false, 0, 0.1, 0
        );
        for (String spel : RULES) {
            getCachedExpression(spel).getValue(warmCtx, Boolean.class);
        }
        int sizeAfterWarmup = spelCache.size();

        // 500 luồng đồng thời — cache không được phình thêm
        ExecutorService pool  = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch  start = new CountDownLatch(1);

        for (int t = 0; t < THREAD_COUNT; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                SimpleEvaluationContext ctx = buildContext(
                        500_000L, true, false, false, true, 5, 0.95, 25_000_000
                );
                for (String spel : RULES) {
                    try { getCachedExpression(spel).getValue(ctx, Boolean.class); }
                    catch (Exception ignored) {}
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(TIMEOUT_SECS, TimeUnit.SECONDS);

        assertEquals(sizeAfterWarmup, spelCache.size(),
                "Cache size thay đổi sau load — có duplicate keys");
    }

    @Test
    @DisplayName("Throughput benchmark — 500 threads × 50 rules trong < 10s")
    void givenHighLoad_whenBenchmarked_thenThroughputAcceptable() throws InterruptedException {
        ExecutorService pool    = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch  ready   = new CountDownLatch(THREAD_COUNT);
        CountDownLatch  start   = new CountDownLatch(1);
        AtomicLong      evalOps = new AtomicLong(0);

        for (int t = 0; t < THREAD_COUNT; t++) {
            pool.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                SimpleEvaluationContext ctx = buildContext(
                        2_000_000L, false, true, false, false, 1, 0.4, 2_000_000
                );
                for (String spel : RULES) {
                    try {
                        getCachedExpression(spel).getValue(ctx, Boolean.class);
                        evalOps.incrementAndGet();
                    } catch (Exception ignored) {}
                }
            });
        }

        ready.await();
        long t0 = System.currentTimeMillis();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(TIMEOUT_SECS, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - t0;

        long expected = (long) THREAD_COUNT * RULE_COUNT;
        assertEquals(expected, evalOps.get(),
                "Số lần evaluate không khớp: expected=" + expected + " actual=" + evalOps.get());

        // 500 threads × 50 rules = 25.000 evaluations — phải xong trong < 10s
        assertTrue(elapsed < 10_000,
                "Throughput quá chậm: " + elapsed + "ms cho " + evalOps.get() + " evaluations");
    }

    // ── Inner helper proxy ────────────────────────────────────────────────────

    public static class TxProxy {
        private final long amount;
        public TxProxy(long amount) { this.amount = amount; }
        public long getAmount() { return amount; }
    }
}
