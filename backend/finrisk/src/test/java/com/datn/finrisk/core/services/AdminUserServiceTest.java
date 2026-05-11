// package com.datn.finrisk.core.services;

// import com.datn.finrisk.application.dtos.AdminUserDTO;
// import com.datn.finrisk.core.entities.SystemConfigLog;
// import com.datn.finrisk.core.entities.Transaction;
// import com.datn.finrisk.core.entities.User;
// import com.datn.finrisk.core.repository.SystemConfigLogRepository;
// import com.datn.finrisk.core.repository.TransactionRepository;
// import com.datn.finrisk.core.repository.UserRepository;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Nested;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.ArgumentCaptor;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;

// import java.math.BigDecimal;
// import java.time.LocalDateTime;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;
// import java.util.Map;
// import java.util.Optional;

// import static org.assertj.core.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.ArgumentMatchers.contains;
// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.*;

// @ExtendWith(MockitoExtension.class)
// @DisplayName("AdminUserService - Full Test Suite")
// class AdminUserServiceTest {

//     @Mock private UserRepository userRepository;
//     @Mock private AuditLogService auditLogService;
//     @Mock private SystemConfigLogRepository configLogRepository;
//     @Mock private TransactionRepository transactionRepository;

//     @InjectMocks
//     private AdminUserService adminUserService;

//     private static final String ADMIN = "superadmin";

//     // ─────────────────────────────────────────────────────────────
//     // Helpers
//     // ─────────────────────────────────────────────────────────────
//     private User buildUser(Long id, String username, String status) {
//         User u = new User();
//         u.setId(id);
//         u.setUsername(username);
//         u.setFullName("Full " + username);
//         u.setPhoneNumber("0900000000");
//         u.setEmail(username + "@test.com");
//         u.setStatus(status);
//         u.setSuspiciousSession(false);
//         u.setAdminFlagged(false);
//         u.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
//         return u;
//     }

//     private Transaction buildTransaction(Long id, double amount, String status, int riskScore) {
//         Transaction tx = new Transaction();
//         tx.setId(id);
//         tx.setAmount(BigDecimal.valueOf(amount));
//         tx.setStatus(status);
//         tx.setTotalRiskScore(riskScore);
//         tx.setCreatedAt(LocalDateTime.of(2024, 6, 15, 10, 30));
//         return tx;
//     }

//     // ══════════════════════════════════════════════════════════════
//     // 1. getAllUsers()
//     // ══════════════════════════════════════════════════════════════
//     @Nested
//     @DisplayName("getAllUsers()")
//     class GetAllUsersTests {

//         @Test
//         @DisplayName("Trả về danh sách DTO từ tất cả user trong DB")
//         void shouldReturnAllUsersAsDTO() {
//             User u1 = buildUser(1L, "alice", "ACTIVE");
//             User u2 = buildUser(2L, "bob", "LOCKED");
//             when(userRepository.findAll()).thenReturn(Arrays.asList(u1, u2));

//             List<AdminUserDTO> result = adminUserService.getAllUsers();

//             assertThat(result).hasSize(2);
//             assertThat(result.get(0).getUsername()).isEqualTo("alice");
//             assertThat(result.get(1).getUsername()).isEqualTo("bob");
//         }

//         @Test
//         @DisplayName("Trả về danh sách rỗng khi không có user")
//         void shouldReturnEmptyListWhenNoUsers() {
//             when(userRepository.findAll()).thenReturn(Collections.emptyList());

//             assertThat(adminUserService.getAllUsers()).isEmpty();
//         }

//         @Test
//         @DisplayName("DTO phải chứa đủ các field cơ bản (id, username, email, status)")
//         void shouldMapFieldsCorrectly() {
//             User u = buildUser(5L, "charlie", "ACTIVE");
//             u.setEmail("charlie@test.com");
//             when(userRepository.findAll()).thenReturn(List.of(u));

//             AdminUserDTO dto = adminUserService.getAllUsers().get(0);

//             assertThat(dto.getId()).isEqualTo(5L);
//             assertThat(dto.getUsername()).isEqualTo("charlie");
//             assertThat(dto.getEmail()).isEqualTo("charlie@test.com");
//             assertThat(dto.getStatus()).isEqualTo("ACTIVE");
//         }

//         @Test
//         @DisplayName("hasFaceData = true khi user có base64FaceImage")
//         void shouldSetHasFaceDataTrueWhenFaceImageExists() {
//             User u = buildUser(1L, "faceuser", "ACTIVE");
//             u.setBase64FaceImage("base64string...");
//             when(userRepository.findAll()).thenReturn(List.of(u));

//             AdminUserDTO dto = adminUserService.getAllUsers().get(0);

//             assertThat(dto.isHasFaceData()).isTrue();
//         }

//         @Test
//         @DisplayName("hasFaceData = false khi base64FaceImage là null")
//         void shouldSetHasFaceDataFalseWhenFaceImageNull() {
//             User u = buildUser(1L, "noface", "ACTIVE");
//             u.setBase64FaceImage(null);
//             when(userRepository.findAll()).thenReturn(List.of(u));

//             assertThat(adminUserService.getAllUsers().get(0).isHasFaceData()).isFalse();
//         }

//         @Test
//         @DisplayName("hasFaceData = false khi base64FaceImage là chuỗi trắng")
//         void shouldSetHasFaceDataFalseWhenFaceImageBlank() {
//             User u = buildUser(1L, "blankface", "ACTIVE");
//             u.setBase64FaceImage("   ");
//             when(userRepository.findAll()).thenReturn(List.of(u));

//             assertThat(adminUserService.getAllUsers().get(0).isHasFaceData()).isFalse();
//         }

//         @Test
//         @DisplayName("suspiciousSession = true khi adminFlagged = true (dù suspiciousSession = false)")
//         void shouldSetSuspiciousTrueWhenAdminFlagged() {
//             User u = buildUser(1L, "flagged", "ACTIVE");
//             u.setSuspiciousSession(false);
//             u.setAdminFlagged(true);
//             when(userRepository.findAll()).thenReturn(List.of(u));

//             assertThat(adminUserService.getAllUsers().get(0).isSuspiciousSession()).isTrue();
//         }

//         @Test
//         @DisplayName("suspiciousSession = true khi suspiciousSession = true (dù adminFlagged = false)")
//         void shouldSetSuspiciousTrueWhenSuspiciousSession() {
//             User u = buildUser(1L, "suspicious", "ACTIVE");
//             u.setSuspiciousSession(true);
//             u.setAdminFlagged(false);
//             when(userRepository.findAll()).thenReturn(List.of(u));

//             assertThat(adminUserService.getAllUsers().get(0).isSuspiciousSession()).isTrue();
//         }
//     }

//     // ══════════════════════════════════════════════════════════════
//     // 2. toggleUserStatus()
//     // ══════════════════════════════════════════════════════════════
//     @Nested
//     @DisplayName("toggleUserStatus()")
//     class ToggleUserStatusTests {

//         @Test
//         @DisplayName("ACTIVE → LOCKED: status đổi đúng")
//         void shouldLockActiveUser() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             AdminUserDTO result = adminUserService.toggleUserStatus(1L, ADMIN);

//             assertThat(result.getStatus()).isEqualTo("LOCKED");
//         }

//         @Test
//         @DisplayName("LOCKED → ACTIVE: status đổi đúng")
//         void shouldUnlockLockedUser() {
//             User user = buildUser(1L, "bob", "LOCKED");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             AdminUserDTO result = adminUserService.toggleUserStatus(1L, ADMIN);

//             assertThat(result.getStatus()).isEqualTo("ACTIVE");
//         }

//         @Test
//         @DisplayName("Khi khóa user ACTIVE: refreshToken phải bị set null (force logout)")
//         void shouldClearRefreshTokenWhenLocking() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setCurrentRefreshToken("existing_token_xyz");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.toggleUserStatus(1L, ADMIN);

//             ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
//             verify(userRepository).save(captor.capture());
//             assertThat(captor.getValue().getCurrentRefreshToken()).isNull();
//         }

//         @Test
//         @DisplayName("Khi mở khóa user LOCKED: refreshToken không bị đụng vào")
//         void shouldNotTouchRefreshTokenWhenUnlocking() {
//             User user = buildUser(1L, "bob", "LOCKED");
//             user.setCurrentRefreshToken(null);
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.toggleUserStatus(1L, ADMIN);

//             // Chỉ kiểm tra save() được gọi; token vẫn null (không thay đổi)
//             verify(userRepository).save(any());
//         }

//         @Test
//         @DisplayName("Phải ghi AuditLog với action TOGGLE_USER_STATUS")
//         void shouldWriteAuditLog() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.toggleUserStatus(1L, ADMIN);

//             verify(auditLogService).logAction(eq(ADMIN), eq("TOGGLE_USER_STATUS"), anyString());
//         }

//         @Test
//         @DisplayName("AuditLog message phải chứa username của user bị thay đổi")
//         void shouldIncludeUsernameInAuditLog() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.toggleUserStatus(1L, ADMIN);

//             verify(auditLogService).logAction(anyString(), anyString(), contains("alice"));
//         }

//         @Test
//         @DisplayName("Phải ghi ConfigLog với action TOGGLE_USER_STATUS và JSON đúng")
//         void shouldWriteConfigLog() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.toggleUserStatus(1L, ADMIN);

//             ArgumentCaptor<SystemConfigLog> captor = ArgumentCaptor.forClass(SystemConfigLog.class);
//             verify(configLogRepository).save(captor.capture());

//             SystemConfigLog log = captor.getValue();
//             assertThat(log.getActionType()).isEqualTo("TOGGLE_USER_STATUS");
//             assertThat(log.getTargetTable()).isEqualTo("users");
//             assertThat(log.getTargetId()).isEqualTo(1L);
//             assertThat(log.getOldValue()).contains("ACTIVE");
//             assertThat(log.getNewValue()).contains("LOCKED");
//         }

//         @Test
//         @DisplayName("Ném RuntimeException khi user không tồn tại")
//         void shouldThrowWhenUserNotFound() {
//             when(userRepository.findById(999L)).thenReturn(Optional.empty());

//             assertThatThrownBy(() -> adminUserService.toggleUserStatus(999L, ADMIN))
//                     .isInstanceOf(RuntimeException.class)
//                     .hasMessageContaining("Không tìm thấy User!");
//         }

//         @Test
//         @DisplayName("Khi user không tồn tại: không ghi log nào cả")
//         void shouldNotWriteAnyLogWhenUserNotFound() {
//             when(userRepository.findById(999L)).thenReturn(Optional.empty());

//             assertThatThrownBy(() -> adminUserService.toggleUserStatus(999L, ADMIN))
//                     .isInstanceOf(RuntimeException.class);

//             verifyNoInteractions(auditLogService, configLogRepository);
//         }

//         @Test
//         @DisplayName("Status không phải ACTIVE (ví dụ PENDING): cũng chuyển sang ACTIVE")
//         void shouldActivateNonActiveStatus() {
//             User user = buildUser(1L, "pending", "PENDING");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             AdminUserDTO result = adminUserService.toggleUserStatus(1L, ADMIN);

//             assertThat(result.getStatus()).isEqualTo("ACTIVE");
//         }
//     }

//     // ══════════════════════════════════════════════════════════════
//     // 3. toggleSuspicious()
//     // ══════════════════════════════════════════════════════════════
//     @Nested
//     @DisplayName("toggleSuspicious()")
//     class ToggleSuspiciousTests {

//         @Test
//         @DisplayName("adminFlagged false → true: bật cờ cảnh báo")
//         void shouldFlagUser() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setAdminFlagged(false);
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             AdminUserDTO result = adminUserService.toggleSuspicious(1L, ADMIN);

//             // suspiciousSession trong DTO = suspiciousSession || adminFlagged = false || true = true
//             assertThat(result.isSuspiciousSession()).isTrue();
//         }

//         @Test
//         @DisplayName("adminFlagged true → false: gỡ cờ cảnh báo")
//         void shouldUnflagUser() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setAdminFlagged(true);
//             user.setSuspiciousSession(false);
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             AdminUserDTO result = adminUserService.toggleSuspicious(1L, ADMIN);

//             assertThat(result.isSuspiciousSession()).isFalse();
//         }

//         @Test
//         @DisplayName("Phải ghi AuditLog với action TOGGLE_ADMIN_FLAG")
//         void shouldWriteAuditLog() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.toggleSuspicious(1L, ADMIN);

//             verify(auditLogService).logAction(eq(ADMIN), eq("TOGGLE_ADMIN_FLAG"), anyString());
//         }

//         @Test
//         @DisplayName("Khi bật cờ: message audit chứa 'BẬT CẢNH BÁO'")
//         void shouldContainFlagOnMessageWhenFlagging() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setAdminFlagged(false);
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.toggleSuspicious(1L, ADMIN);

//             verify(auditLogService).logAction(anyString(), anyString(), contains("BẬT CẢNH BÁO"));
//         }

//         @Test
//         @DisplayName("Khi gỡ cờ: message audit chứa 'GỠ CẢNH BÁO'")
//         void shouldContainUnflagMessageWhenUnflagging() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setAdminFlagged(true);
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.toggleSuspicious(1L, ADMIN);

//             verify(auditLogService).logAction(anyString(), anyString(), contains("GỠ CẢNH BÁO"));
//         }

//         @Test
//         @DisplayName("Phải ghi ConfigLog với JSON chứa adminFlagged đúng old/new value")
//         void shouldWriteConfigLogWithCorrectJson() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setAdminFlagged(false);
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.toggleSuspicious(1L, ADMIN);

//             ArgumentCaptor<SystemConfigLog> captor = ArgumentCaptor.forClass(SystemConfigLog.class);
//             verify(configLogRepository).save(captor.capture());

//             SystemConfigLog log = captor.getValue();
//             assertThat(log.getActionType()).isEqualTo("TOGGLE_ADMIN_FLAG");
//             assertThat(log.getOldValue()).contains("false");
//             assertThat(log.getNewValue()).contains("true");
//         }

//         @Test
//         @DisplayName("Ném RuntimeException khi user không tồn tại")
//         void shouldThrowWhenUserNotFound() {
//             when(userRepository.findById(404L)).thenReturn(Optional.empty());

//             assertThatThrownBy(() -> adminUserService.toggleSuspicious(404L, ADMIN))
//                     .isInstanceOf(RuntimeException.class)
//                     .hasMessageContaining("Không tìm thấy User!");
//         }
//     }

//     // ══════════════════════════════════════════════════════════════
//     // 4. getRecentTransactionsByUserId()
//     // ══════════════════════════════════════════════════════════════
//     @Nested
//     @DisplayName("getRecentTransactionsByUserId()")
//     class GetRecentTransactionsTests {

//         @Test
//         @DisplayName("Trả về đúng số lượng transaction (tối đa 5)")
//         void shouldReturnUpToFiveTransactions() {
//             List<Transaction> txList = Arrays.asList(
//                     buildTransaction(1L, 100000, "SUCCESS", 10),
//                     buildTransaction(2L, 200000, "FAILED", 50),
//                     buildTransaction(3L, 300000, "SUCCESS", 5)
//             );
//             when(transactionRepository.findTop5ByFromAccountUserIdOrderByCreatedAtDesc(1L))
//                     .thenReturn(txList);

//             List<Map<String, Object>> result = adminUserService.getRecentTransactionsByUserId(1L);

//             assertThat(result).hasSize(3);
//         }

//         @Test
//         @DisplayName("ID giao dịch phải có prefix 'TX'")
//         void shouldPrefixTransactionIdWithTX() {
//             when(transactionRepository.findTop5ByFromAccountUserIdOrderByCreatedAtDesc(1L))
//                     .thenReturn(List.of(buildTransaction(42L, 500000, "SUCCESS", 20)));

//             Map<String, Object> tx = adminUserService.getRecentTransactionsByUserId(1L).get(0);

//             assertThat(tx.get("id")).isEqualTo("TX42");
//         }

//         @Test
//         @DisplayName("Map phải chứa đủ 5 key: id, amount, status, riskScore, date")
//         void shouldContainAllRequiredKeys() {
//             when(transactionRepository.findTop5ByFromAccountUserIdOrderByCreatedAtDesc(1L))
//                     .thenReturn(List.of(buildTransaction(1L, 100000, "SUCCESS", 10)));

//             Map<String, Object> tx = adminUserService.getRecentTransactionsByUserId(1L).get(0);

//             assertThat(tx).containsKeys("id", "amount", "status", "riskScore", "date");
//         }

//         @Test
//         @DisplayName("Date được format đúng 'dd/MM/yyyy HH:mm'")
//         void shouldFormatDateCorrectly() {
//             Transaction t = buildTransaction(1L, 100000, "SUCCESS", 10);
//             t.setCreatedAt(LocalDateTime.of(2024, 6, 15, 10, 30));
//             when(transactionRepository.findTop5ByFromAccountUserIdOrderByCreatedAtDesc(1L))
//                     .thenReturn(List.of(t));

//             Map<String, Object> result = adminUserService.getRecentTransactionsByUserId(1L).get(0);

//             assertThat(result.get("date")).isEqualTo("15/06/2024 10:30");
//         }

//         @Test
//         @DisplayName("amount và riskScore được ánh xạ đúng")
//         void shouldMapAmountAndRiskScoreCorrectly() {
//             when(transactionRepository.findTop5ByFromAccountUserIdOrderByCreatedAtDesc(1L))
//                     .thenReturn(List.of(buildTransaction(1L, 999999.0, "FAILED", 88)));

//             Map<String, Object> tx = adminUserService.getRecentTransactionsByUserId(1L).get(0);

//             assertThat(tx.get("amount")).isEqualTo(BigDecimal.valueOf(999999.0));
//             assertThat(tx.get("riskScore")).isEqualTo(88);
//             assertThat(tx.get("status")).isEqualTo("FAILED");
//         }

//         @Test
//         @DisplayName("Trả về danh sách rỗng khi user không có giao dịch nào")
//         void shouldReturnEmptyListWhenNoTransactions() {
//             when(transactionRepository.findTop5ByFromAccountUserIdOrderByCreatedAtDesc(99L))
//                     .thenReturn(Collections.emptyList());

//             assertThat(adminUserService.getRecentTransactionsByUserId(99L)).isEmpty();
//         }
//     }

//     // ══════════════════════════════════════════════════════════════
//     // 5. resetFaceBiometric()
//     // ══════════════════════════════════════════════════════════════
//     @Nested
//     @DisplayName("resetFaceBiometric()")
//     class ResetFaceBiometricTests {

//         @Test
//         @DisplayName("Xóa khuôn mặt thành công: base64FaceImage phải là null sau khi save")
//         void shouldClearFaceImageSuccessfully() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setBase64FaceImage("iVBORw0KGgoAAAANSUhEUgAA...");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             AdminUserDTO result = adminUserService.resetFaceBiometric(1L, ADMIN);

//             assertThat(result.isHasFaceData()).isFalse();

//             ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
//             verify(userRepository).save(captor.capture());
//             assertThat(captor.getValue().getBase64FaceImage()).isNull();
//         }

//         @Test
//         @DisplayName("Ném RuntimeException khi user chưa đăng ký khuôn mặt (null)")
//         void shouldThrowWhenFaceImageIsNull() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setBase64FaceImage(null);
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));

//             assertThatThrownBy(() -> adminUserService.resetFaceBiometric(1L, ADMIN))
//                     .isInstanceOf(RuntimeException.class)
//                     .hasMessageContaining("chưa đăng ký khuôn mặt");
//         }

//         @Test
//         @DisplayName("Ném RuntimeException khi base64FaceImage là chuỗi trắng")
//         void shouldThrowWhenFaceImageIsBlank() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setBase64FaceImage("   ");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));

//             assertThatThrownBy(() -> adminUserService.resetFaceBiometric(1L, ADMIN))
//                     .isInstanceOf(RuntimeException.class)
//                     .hasMessageContaining("chưa đăng ký khuôn mặt");
//         }

//         @Test
//         @DisplayName("Ném RuntimeException khi user không tồn tại")
//         void shouldThrowWhenUserNotFound() {
//             when(userRepository.findById(999L)).thenReturn(Optional.empty());

//             assertThatThrownBy(() -> adminUserService.resetFaceBiometric(999L, ADMIN))
//                     .isInstanceOf(RuntimeException.class)
//                     .hasMessageContaining("Không tìm thấy User!");
//         }

//         @Test
//         @DisplayName("Phải ghi AuditLog với action RESET_FACE_DATA")
//         void shouldWriteAuditLog() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setBase64FaceImage("somedata");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.resetFaceBiometric(1L, ADMIN);

//             verify(auditLogService).logAction(eq(ADMIN), eq("RESET_FACE_DATA"), anyString());
//         }

//         @Test
//         @DisplayName("AuditLog message phải chứa username của user bị xóa khuôn mặt")
//         void shouldIncludeUsernameInAuditLog() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setBase64FaceImage("somedata");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.resetFaceBiometric(1L, ADMIN);

//             verify(auditLogService).logAction(anyString(), anyString(), contains("alice"));
//         }

//         @Test
//         @DisplayName("Phải ghi ConfigLog với oldJson chứa nhãn [DỮ_LIỆU_ẢNH_ĐÃ_BỊ_HỦY] và newJson là null")
//         void shouldWriteConfigLogWithSafeLabel() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setBase64FaceImage("verylongbase64...");
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));
//             when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

//             adminUserService.resetFaceBiometric(1L, ADMIN);

//             ArgumentCaptor<SystemConfigLog> captor = ArgumentCaptor.forClass(SystemConfigLog.class);
//             verify(configLogRepository).save(captor.capture());

//             SystemConfigLog log = captor.getValue();
//             assertThat(log.getActionType()).isEqualTo("RESET_FACE_DATA");
//             assertThat(log.getOldValue()).contains("DỮ_LIỆU_ẢNH_ĐÃ_BỊ_HỦY");
//             assertThat(log.getNewValue()).contains("null");
//         }

//         @Test
//         @DisplayName("Khi user chưa đăng ký khuôn mặt: save() không được gọi")
//         void shouldNotSaveWhenNoFaceData() {
//             User user = buildUser(1L, "alice", "ACTIVE");
//             user.setBase64FaceImage(null);
//             when(userRepository.findById(1L)).thenReturn(Optional.of(user));

//             assertThatThrownBy(() -> adminUserService.resetFaceBiometric(1L, ADMIN))
//                     .isInstanceOf(RuntimeException.class);

//             verify(userRepository, never()).save(any());
//             verifyNoInteractions(auditLogService, configLogRepository);
//         }
//     }

//     // ══════════════════════════════════════════════════════════════
//     // 6. convertToDTO() – kiểm tra logic ẩn qua các method public
//     // ══════════════════════════════════════════════════════════════
//     @Nested
//     @DisplayName("convertToDTO() – logic mapping (via getAllUsers)")
//     class ConvertToDTOTests {

//         @Test
//         @DisplayName("suspiciousSession = false khi cả 2 cờ đều false")
//         void shouldBeFalseWhenBothFlagsAreFalse() {
//             User u = buildUser(1L, "normal", "ACTIVE");
//             u.setSuspiciousSession(false);
//             u.setAdminFlagged(false);
//             when(userRepository.findAll()).thenReturn(List.of(u));

//             assertThat(adminUserService.getAllUsers().get(0).isSuspiciousSession()).isFalse();
//         }

//         @Test
//         @DisplayName("suspiciousSession = true khi cả 2 cờ đều true")
//         void shouldBeTrueWhenBothFlagsAreTrue() {
//             User u = buildUser(1L, "flagged", "ACTIVE");
//             u.setSuspiciousSession(true);
//             u.setAdminFlagged(true);
//             when(userRepository.findAll()).thenReturn(List.of(u));

//             assertThat(adminUserService.getAllUsers().get(0).isSuspiciousSession()).isTrue();
//         }

//         @Test
//         @DisplayName("createdAt được ánh xạ đúng sang DTO")
//         void shouldMapCreatedAt() {
//             User u = buildUser(1L, "user", "ACTIVE");
//             LocalDateTime expected = LocalDateTime.of(2023, 12, 25, 8, 0);
//             u.setCreatedAt(expected);
//             when(userRepository.findAll()).thenReturn(List.of(u));

//             assertThat(adminUserService.getAllUsers().get(0).getCreatedAt()).isEqualTo(expected);
//         }

//         @Test
//         @DisplayName("lastLoginIp và lastLoginDevice được ánh xạ đúng")
//         void shouldMapLastLoginInfo() {
//             User u = buildUser(1L, "user", "ACTIVE");
//             u.setLastLoginIp("192.168.1.1");
//             u.setLastLoginDevice("Chrome/Windows");
//             when(userRepository.findAll()).thenReturn(List.of(u));

//             AdminUserDTO dto = adminUserService.getAllUsers().get(0);
//             assertThat(dto.getLastLoginIp()).isEqualTo("192.168.1.1");
//             assertThat(dto.getLastLoginDevice()).isEqualTo("Chrome/Windows");
//         }
//     }
// }