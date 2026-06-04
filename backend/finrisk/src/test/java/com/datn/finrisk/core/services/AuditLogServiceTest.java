package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.AuditLog;
import com.datn.finrisk.core.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService — Unit Tests")
class AuditLogServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @InjectMocks private AuditLogService auditLogService;

    // ── logAction happy path ──────────────────────────────────────────────────

    @Nested
    @DisplayName("logAction() — happy path")
    class HappyPath {

        @Test
        @DisplayName("saves AuditLog with correct username, action, details")
        void savesCorrectEntry() {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

            auditLogService.logAction("alice", "LOGIN", "Logged in from 192.168.1.1");

            verify(auditLogRepository).save(captor.capture());
            AuditLog saved = captor.getValue();
            assertThat(saved.getUsername()).isEqualTo("alice");
            assertThat(saved.getAction()).isEqualTo("LOGIN");
            assertThat(saved.getDetails()).isEqualTo("Logged in from 192.168.1.1");
        }

        @Test
        @DisplayName("timestamp is set on saved entry")
        void timestampIsSet() {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

            auditLogService.logAction("alice", "TX_SUCCESS", "details");

            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("multiple calls → each saved individually")
        void multipleCalls_eachSaved() {
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            auditLogService.logAction("alice", "ACTION_1", "d1");
            auditLogService.logAction("bob",   "ACTION_2", "d2");

            verify(auditLogRepository, times(2)).save(any(AuditLog.class));
        }
    }

    // ── logAction edge cases ──────────────────────────────────────────────────

    @Nested
    @DisplayName("logAction() — edge cases")
    class EdgeCases {

        @Test
        @DisplayName("null username → stored as SYSTEM/UNKNOWN")
        void nullUsername_storedAsSystemUnknown() {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

            auditLogService.logAction(null, "SYSTEM_EVENT", "boot");

            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getUsername()).isEqualTo("SYSTEM/UNKNOWN");
        }

        @Test
        @DisplayName("repository throws → exception swallowed, no rethrow")
        void repoThrows_exceptionSwallowed() {
            doThrow(new RuntimeException("DB down")).when(auditLogRepository).save(any());

            assertThatCode(() -> auditLogService.logAction("alice", "TX_FAIL", "oops"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("empty details string → still saved without error")
        void emptyDetails_savedSuccessfully() {
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> auditLogService.logAction("alice", "LOGOUT", ""))
                    .doesNotThrowAnyException();
            verify(auditLogRepository).save(any());
        }

        @Test
        @DisplayName("SYSTEM action with null details → still saved")
        void systemActionNullDetails_savedSuccessfully() {
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> auditLogService.logAction("SYSTEM", "SCHEDULER_RUN", null))
                    .doesNotThrowAnyException();
            verify(auditLogRepository).save(any());
        }
    }
}
