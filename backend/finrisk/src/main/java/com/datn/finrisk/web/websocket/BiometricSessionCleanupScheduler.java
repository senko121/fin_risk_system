package com.datn.finrisk.web.websocket;

import com.datn.finrisk.core.repository.BiometricSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Slf4j
@Component
@EnableScheduling
public class BiometricSessionCleanupScheduler {

    @Autowired
    private BiometricSessionRepository biometricSessionRepository;

    @Scheduled(fixedRate = 300000) // 5 phút chạy 1 lần
    @Transactional
    public void cleanupExpiredSessions() {
        biometricSessionRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("[BIOMETRIC-SCHEDULER] Expired biometric sessions purged from DB");
    }
}