package com.datn.finrisk.core.services;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService — Bộ Test Chống Brute-Force Toàn Diện")
class RateLimitServiceTest {

    // ========== CONSTANTS ==========
    private static final String   USERNAME      = "thach_hacker";
    private static final String   ATTEMPT_KEY   = "login_attempts:" + USERNAME;
    private static final String   LOCK_KEY      = "login_locked:"   + USERNAME;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final int      MAX_ATTEMPTS  = 5;

    // ========== MOCKS ==========
    @Mock private StringRedisTemplate             redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==========================================================
    // NHÓM 1 — isLoginBlocked()
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 1 — isLoginBlocked()")
    class IsLoginBlockedTests {

        @Test
        @DisplayName("✅ Có lock key trong Redis → true")
        void whenKeyExists_returnsTrue() {
            when(redisTemplate.hasKey(LOCK_KEY)).thenReturn(true);

            assertTrue(rateLimitService.isLoginBlocked(USERNAME));
        }

        @Test
        @DisplayName("✅ Không có lock key → false")
        void whenKeyNotExist_returnsFalse() {
            when(redisTemplate.hasKey(LOCK_KEY)).thenReturn(false);

            assertFalse(rateLimitService.isLoginBlocked(USERNAME));
        }

        @Test
        @DisplayName("✅ Redis trả null → false (tránh NullPointerException)")
        void whenRedisReturnsNull_returnsFalse() {
            when(redisTemplate.hasKey(LOCK_KEY)).thenReturn(null);

            assertFalse(rateLimitService.isLoginBlocked(USERNAME));
        }

        @Test
        @DisplayName("❌ Redis throw exception → false (fail-safe, không crash app)")
        void whenRedisThrows_returnsFalseSafely() {
            when(redisTemplate.hasKey(anyString()))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            // Nếu code gốc chưa có try-catch, test này sẽ FAIL → nhắc developer sửa
            assertDoesNotThrow(() -> {
                boolean result = rateLimitService.isLoginBlocked(USERNAME);
                assertFalse(result, "Khi Redis sập phải fail-safe về false");
            });
        }

        @Test
        @DisplayName("✅ Lock key phải đúng format 'login_locked:{username}'")
        void lockKeyFormatIsCorrect() {
            when(redisTemplate.hasKey("login_locked:alice")).thenReturn(true);

            assertTrue(rateLimitService.isLoginBlocked("alice"));
            // Đảm bảo không bị dùng nhầm key của user khác
            verify(redisTemplate, never()).hasKey("login_locked:" + USERNAME);
        }
    }

    // ==========================================================
    // NHÓM 2 — getLockTimeRemaining()
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 2 — getLockTimeRemaining()")
    class GetLockTimeRemainingTests {

        @Test
        @DisplayName("✅ Đang bị khóa → trả về đúng số giây còn lại")
        void whenLocked_returnsRemainingSeconds() {
            when(redisTemplate.getExpire(LOCK_KEY)).thenReturn(500L);

            assertEquals(500L, rateLimitService.getLockTimeRemaining(USERNAME));
        }

        @Test
        @DisplayName("✅ Redis trả null → 0")
        void whenRedisReturnsNull_returnsZero() {
            when(redisTemplate.getExpire(LOCK_KEY)).thenReturn(null);

            assertEquals(0L, rateLimitService.getLockTimeRemaining(USERNAME));
        }

        @Test
        @DisplayName("✅ Redis trả -1 (key tồn tại nhưng không có TTL) → 0")
        void whenKeyHasNoTtl_returnsZero() {
            when(redisTemplate.getExpire(LOCK_KEY)).thenReturn(-1L);

            assertEquals(0L, rateLimitService.getLockTimeRemaining(USERNAME));
        }

        @Test
        @DisplayName("✅ Redis trả -2 (key không tồn tại) → 0")
        void whenKeyNotExist_returnsZero() {
            when(redisTemplate.getExpire(LOCK_KEY)).thenReturn(-2L);

            assertEquals(0L, rateLimitService.getLockTimeRemaining(USERNAME));
        }

        @Test
        @DisplayName("✅ Giá trị biên: còn đúng 1 giây → trả về 1 (không bị xử lý là 0)")
        void whenOneSecondRemaining_returnsOne() {
            when(redisTemplate.getExpire(LOCK_KEY)).thenReturn(1L);

            assertEquals(1L, rateLimitService.getLockTimeRemaining(USERNAME));
        }

        @Test
        @DisplayName("❌ Redis throw exception → 0 (fail-safe)")
        void whenRedisThrows_returnsZeroSafely() {
            when(redisTemplate.getExpire(anyString()))
                    .thenThrow(new RuntimeException("Timeout"));

            assertDoesNotThrow(() ->
                assertEquals(0L, rateLimitService.getLockTimeRemaining(USERNAME))
            );
        }
    }

    // ==========================================================
    // NHÓM 3 — recordFailedLogin()
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 3 — recordFailedLogin()")
    class RecordFailedLoginTests {

        @Test
        @DisplayName("⚠️ Sai lần 1 → tăng counter + set expire 15 phút cho counter")
        void firstAttempt_incrementsAndSetsExpire() {
            when(valueOperations.increment(ATTEMPT_KEY)).thenReturn(1L);

            rateLimitService.recordFailedLogin(USERNAME);

            verify(valueOperations).increment(ATTEMPT_KEY);
            verify(redisTemplate).expire(eq(ATTEMPT_KEY), eq(LOCK_DURATION));
            // Chưa được khóa
            verify(valueOperations, never()).set(eq(LOCK_KEY), anyString(), any(Duration.class));
        }

        @ParameterizedTest(name = "Sai lần {0} → chỉ tăng counter, không set expire, không khóa")
        @ValueSource(longs = {2L, 3L, 4L})
        @DisplayName("⚠️ Sai lần 2-4 → chỉ tăng counter, KHÔNG reset expire, KHÔNG khóa")
        void intermediateAttempts_onlyIncrements(long attemptCount) {
            when(valueOperations.increment(ATTEMPT_KEY)).thenReturn(attemptCount);

            rateLimitService.recordFailedLogin(USERNAME);

            verify(valueOperations).increment(ATTEMPT_KEY);
            // KHÔNG được set lại expire → tránh reset 15 phút mỗi lần user gõ sai
            verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
            verify(valueOperations, never()).set(eq(LOCK_KEY), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("❌ Sai lần 5 (MAX) → khóa tài khoản 15 phút + xóa counter")
        void maxAttempts_locksAccountAndClearsCounter() {
            when(valueOperations.increment(ATTEMPT_KEY)).thenReturn(5L);

            rateLimitService.recordFailedLogin(USERNAME);

            // Phải cắm cờ LOCKED
            verify(valueOperations).set(eq(LOCK_KEY), eq("LOCKED"), eq(LOCK_DURATION));
            // Phải dọn counter
            verify(redisTemplate).delete(ATTEMPT_KEY);
            // Không được set expire cho attempt key sau khi đã lock
            verify(redisTemplate, never()).expire(eq(ATTEMPT_KEY), any(Duration.class));
        }

        @Test
        @DisplayName("❌ Sai lần 6+ (đã vượt MAX) → vẫn khóa (không bị bỏ qua)")
        void beyondMaxAttempts_stillLocksAccount() {
            // Edge case: Nếu counter entrypoint bị race và nhảy lên 6
            when(valueOperations.increment(ATTEMPT_KEY)).thenReturn(6L);

            rateLimitService.recordFailedLogin(USERNAME);

            verify(valueOperations).set(eq(LOCK_KEY), eq("LOCKED"), eq(LOCK_DURATION));
        }

        @Test
        @DisplayName("❌ Redis increment trả null → Không crash, không khóa nhầm")
        void whenIncrementReturnsNull_doesNotCrashOrLock() {
            // Redis có thể trả null khi timeout
            when(valueOperations.increment(ATTEMPT_KEY)).thenReturn(null);

            assertDoesNotThrow(() -> rateLimitService.recordFailedLogin(USERNAME));
            // Không được khóa khi không biết số lần thực sự
            verify(valueOperations, never()).set(eq(LOCK_KEY), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("❌ Redis throw exception → fail-safe, không crash app")
        void whenRedisThrows_doesNotPropagateException() {
            when(valueOperations.increment(anyString()))
                    .thenThrow(new RuntimeException("Redis down"));

            assertDoesNotThrow(() -> rateLimitService.recordFailedLogin(USERNAME));
        }

        @Test
        @DisplayName("✅ Thứ tự: increment → (nếu lần 1) expire → (nếu đủ 5) set lock + delete")
        void maxAttempts_operationOrderIsCorrect() {
            when(valueOperations.increment(ATTEMPT_KEY)).thenReturn(5L);
            var inOrder = inOrder(valueOperations, redisTemplate);

            rateLimitService.recordFailedLogin(USERNAME);

            inOrder.verify(valueOperations).increment(ATTEMPT_KEY);
            inOrder.verify(valueOperations).set(eq(LOCK_KEY), eq("LOCKED"), eq(LOCK_DURATION));
            inOrder.verify(redisTemplate).delete(ATTEMPT_KEY);
        }
    }

    // ==========================================================
    // NHÓM 4 — clearLoginAttempts()
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 4 — clearLoginAttempts()")
    class ClearLoginAttemptsTests {

        @Test
        @DisplayName("✅ Đăng nhập thành công → xóa sạch cả attempt key và lock key cùng lúc")
        void onSuccess_deletesBothKeys() {
            rateLimitService.clearLoginAttempts(USERNAME);

            // 🚀 BÀI TEST MỚI: Phải kiểm tra xem hệ thống có xóa đúng một cái List 2 key không
            verify(redisTemplate).delete(java.util.Arrays.asList(ATTEMPT_KEY, LOCK_KEY));
        }

        @Test
        @DisplayName("❌ Redis throw exception → fail-safe, không crash")
        void whenRedisThrows_doesNotPropagateException() {
            // 🚀 Do hàm delete giờ nhận vào 1 Collection (List), nên phải dùng any(Collection.class)
            doThrow(new RuntimeException("Redis unavailable"))
                    .when(redisTemplate).delete(any(java.util.Collection.class));

            assertDoesNotThrow(() -> rateLimitService.clearLoginAttempts(USERNAME));
        }
    }

    // ==========================================================
    // NHÓM 5 — ISOLATION: Các user KHÔNG ảnh hưởng lẫn nhau
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 5 — User Isolation (Cách ly tài khoản)")
    class UserIsolationTests {

        @Test
        @DisplayName("✅ Block user A không làm ảnh hưởng trạng thái user B")
        void blockingUserA_doesNotAffectUserB() {
            when(redisTemplate.hasKey("login_locked:alice")).thenReturn(true);
            when(redisTemplate.hasKey("login_locked:bob")).thenReturn(false);

            assertTrue(rateLimitService.isLoginBlocked("alice"));
            assertFalse(rateLimitService.isLoginBlocked("bob"));
        }

        @Test
        @DisplayName("✅ recordFailedLogin dùng đúng key riêng của từng user")
        void recordFailedLogin_usesCorrectKeyPerUser() {
            when(valueOperations.increment("login_attempts:alice")).thenReturn(1L);

            rateLimitService.recordFailedLogin("alice");

            verify(valueOperations).increment("login_attempts:alice");
            verify(valueOperations, never()).increment("login_attempts:bob");
            verify(valueOperations, never()).increment(ATTEMPT_KEY);
        }

        @Test
        @DisplayName("✅ clearLoginAttempts chỉ xóa key của đúng user đó")
        void clearLoginAttempts_onlyDeletesCorrectUserKey() {
            rateLimitService.clearLoginAttempts("alice");

            // 🚀 Kiểm tra xóa đúng List key của alice
            verify(redisTemplate).delete(java.util.Arrays.asList("login_attempts:alice", "login_locked:alice"));
            // Đảm bảo không xóa nhầm sang key của thach_hacker
            verify(redisTemplate, never()).delete(java.util.Arrays.asList(ATTEMPT_KEY, LOCK_KEY));
        }
    }
}