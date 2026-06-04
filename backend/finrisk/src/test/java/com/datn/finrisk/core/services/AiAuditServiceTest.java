package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.AiScanLog;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.AiScanLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiAuditService — Unit Tests")
class AiAuditServiceTest {

    @Mock private AiScanLogRepository aiScanLogRepo;
    @InjectMocks private AiAuditService aiAuditService;

    private Transaction buildTx(Long txId, Long userId) {
        User user = new User();
        user.setId(userId);

        Account account = new Account();
        account.setUser(user);

        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setFromAccount(account);
        return tx;
    }

    private EmotionAIResponse buildEmotion(String emotion, double confidence) {
        EmotionAIResponse resp = new EmotionAIResponse();
        resp.setEmotion(emotion);
        resp.setConfidence(confidence);
        resp.setProcessTimeMs(120L);
        resp.setProbDetails(Map.of("happy", 0.9, "sad", 0.1));
        return resp;
    }

    // ── logEmotionScan happy path ──────────────────────────────────────────────

    @Nested
    @DisplayName("logEmotionScan() — happy path")
    class HappyPath {

        @Test
        @DisplayName("saves AiScanLog with correct txId, userId and scan type")
        void savesCorrectFields() {
            Transaction tx         = buildTx(55L, 3L);
            EmotionAIResponse resp = buildEmotion("HAPPY", 0.92);
            when(aiScanLogRepo.save(any(AiScanLog.class))).thenAnswer(inv -> inv.getArgument(0));

            aiAuditService.logEmotionScan(tx, resp);

            ArgumentCaptor<AiScanLog> captor = ArgumentCaptor.forClass(AiScanLog.class);
            verify(aiScanLogRepo).save(captor.capture());
            AiScanLog saved = captor.getValue();

            assertThat(saved.getTransactionId()).isEqualTo(55L);
            assertThat(saved.getUserId()).isEqualTo(3L);
            assertThat(saved.getScanType()).isEqualTo("EMOTION_FACE");
            assertThat(saved.getResultLabel()).isEqualTo("HAPPY");
            assertThat(saved.getConfidenceScore()).isEqualTo(0.92);
        }

        @Test
        @DisplayName("emotion details JSON is non-blank in saved log")
        void emotionDetailsJson_nonBlank() {
            Transaction tx         = buildTx(10L, 1L);
            EmotionAIResponse resp = buildEmotion("NEUTRAL", 0.75);
            when(aiScanLogRepo.save(any(AiScanLog.class))).thenAnswer(inv -> inv.getArgument(0));

            aiAuditService.logEmotionScan(tx, resp);

            ArgumentCaptor<AiScanLog> captor = ArgumentCaptor.forClass(AiScanLog.class);
            verify(aiScanLogRepo).save(captor.capture());
            assertThat(captor.getValue().getEmotionDetails()).isNotBlank();
        }

        @Test
        @DisplayName("createdAt is set on saved log")
        void createdAt_isSet() {
            Transaction tx         = buildTx(20L, 2L);
            EmotionAIResponse resp = buildEmotion("SAD", 0.60);
            when(aiScanLogRepo.save(any(AiScanLog.class))).thenAnswer(inv -> inv.getArgument(0));

            aiAuditService.logEmotionScan(tx, resp);

            ArgumentCaptor<AiScanLog> captor = ArgumentCaptor.forClass(AiScanLog.class);
            verify(aiScanLogRepo).save(captor.capture());
            assertThat(captor.getValue().getCreatedAt()).isNotNull();
        }
    }

    // ── logEmotionScan edge cases ──────────────────────────────────────────────

    @Nested
    @DisplayName("logEmotionScan() — edge cases")
    class EdgeCases {

        @Test
        @DisplayName("repository throws → exception swallowed, no rethrow")
        void repoThrows_exceptionSwallowed() {
            Transaction tx         = buildTx(99L, 5L);
            EmotionAIResponse resp = buildEmotion("FEAR", 0.80);
            doThrow(new RuntimeException("DB error")).when(aiScanLogRepo).save(any());

            assertThatCode(() -> aiAuditService.logEmotionScan(tx, resp))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null probDetails → still saves without crashing")
        void nullProbDetails_savesContinues() {
            Transaction tx         = buildTx(77L, 4L);
            EmotionAIResponse resp = new EmotionAIResponse();
            resp.setEmotion("NEUTRAL");
            resp.setConfidence(0.5);
            resp.setProbDetails(null);
            when(aiScanLogRepo.save(any(AiScanLog.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> aiAuditService.logEmotionScan(tx, resp))
                    .doesNotThrowAnyException();
        }
    }
}
