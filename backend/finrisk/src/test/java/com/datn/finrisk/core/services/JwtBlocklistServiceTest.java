package com.datn.finrisk.core.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtBlocklistService — Unit Tests")
class JwtBlocklistServiceTest {

    @Mock private StringRedisTemplate          redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private JwtBlocklistService jwtBlocklistService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── block() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("block()")
    class Block {

        @Test
        @DisplayName("future expiry → stores SHA-256 key in Redis with positive TTL")
        void futureExpiry_storesInRedis() {
            Date future = new Date(System.currentTimeMillis() + 60_000);
            jwtBlocklistService.block("my-access-token", future);
            verify(valueOps).set(anyString(), eq("1"), any(Duration.class));
        }

        @Test
        @DisplayName("already expired → Redis write is skipped")
        void alreadyExpired_noWrite() {
            Date past = new Date(System.currentTimeMillis() - 5_000);
            jwtBlocklistService.block("stale-token", past);
            verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("expiry = now (0 ms remaining) → no write")
        void expiryAtNow_noWrite() {
            Date now = new Date();
            jwtBlocklistService.block("zero-ttl-token", now);
            verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("Redis failure during block → exception swallowed, no rethrow")
        void redisFailureDuringBlock_swallowed() {
            doThrow(new RuntimeException("Redis down"))
                    .when(valueOps).set(anyString(), anyString(), any(Duration.class));
            Date future = new Date(System.currentTimeMillis() + 60_000);

            assertThatCode(() -> jwtBlocklistService.block("token", future))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("stored Redis key starts with blocklist prefix")
        void storedKey_hasBlocklistPrefix() {
            Date future = new Date(System.currentTimeMillis() + 60_000);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

            jwtBlocklistService.block("any-token", future);

            verify(valueOps).set(keyCaptor.capture(), eq("1"), any(Duration.class));
            assertThat(keyCaptor.getValue()).startsWith("jwt_blocklist:");
        }
    }

    // ── isBlocked() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isBlocked()")
    class IsBlocked {

        @Test
        @DisplayName("key present in Redis → true")
        void keyPresent_returnsTrue() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.TRUE);
            assertThat(jwtBlocklistService.isBlocked("blocked-token")).isTrue();
        }

        @Test
        @DisplayName("key absent in Redis → false")
        void keyAbsent_returnsFalse() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.FALSE);
            assertThat(jwtBlocklistService.isBlocked("clean-token")).isFalse();
        }

        @Test
        @DisplayName("Redis returns null → false (fail-open)")
        void redisNullResponse_returnsFalse() {
            when(redisTemplate.hasKey(anyString())).thenReturn(null);
            assertThat(jwtBlocklistService.isBlocked("any-token")).isFalse();
        }

        @Test
        @DisplayName("Redis throws → fail-open: false, no exception propagated")
        void redisThrows_failOpen_returnsFalse() {
            when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Timeout"));
            assertThat(jwtBlocklistService.isBlocked("any-token")).isFalse();
        }

        @Test
        @DisplayName("same token queried twice uses consistent SHA-256 hash key")
        void sameToken_consistentHashKey() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.FALSE);

            jwtBlocklistService.isBlocked("the-same-token");
            jwtBlocklistService.isBlocked("the-same-token");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate, times(2)).hasKey(captor.capture());

            List<String> keys = captor.getAllValues();
            assertThat(keys.get(0)).isEqualTo(keys.get(1));
        }

        @Test
        @DisplayName("different tokens produce different Redis keys")
        void differentTokens_differentKeys() {
            when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.FALSE);

            jwtBlocklistService.isBlocked("token-A");
            jwtBlocklistService.isBlocked("token-B");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate, times(2)).hasKey(captor.capture());

            List<String> keys = captor.getAllValues();
            assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
        }
    }
}
