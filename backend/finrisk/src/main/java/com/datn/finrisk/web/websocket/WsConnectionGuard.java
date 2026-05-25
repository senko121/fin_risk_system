package com.datn.finrisk.web.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class WsConnectionGuard {

    private static final int MAX_CONNECTIONS_PER_IP = 5;

    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    public boolean tryAcquire(String ip) {
        AtomicInteger counter = counts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();
        if (current > MAX_CONNECTIONS_PER_IP) {
            counter.decrementAndGet();
            log.warn("[WS-GUARD] ip={} — connection limit ({}) exceeded, rejected", ip, MAX_CONNECTIONS_PER_IP);
            return false;
        }
        log.debug("[WS-GUARD] ip={} — slot acquired ({}/{})", ip, current, MAX_CONNECTIONS_PER_IP);
        return true;
    }

    public void release(String ip) {
        if (ip == null) return;
        AtomicInteger counter = counts.get(ip);
        if (counter == null) return;
        int remaining = counter.decrementAndGet();
        if (remaining <= 0) {
            counts.remove(ip);
        }
        log.debug("[WS-GUARD] ip={} — slot released ({} remaining)", ip, Math.max(remaining, 0));
    }

    /**
     * Safety Net: Tự động dọn dẹp các slot bị treo cứng định kỳ.
     * Chạy mỗi 10 phút (600,000 miligiây) một lần để giải phóng bộ nhớ.
     */
    @Scheduled(fixedRate = 600000)
    public void resetStaleSlots() {
        if (!counts.isEmpty()) {
            counts.clear();
            log.info("[WS-GUARD] Periodic safety net triggered: All slot counts reset to clear potential leaks.");
        }
    }
}