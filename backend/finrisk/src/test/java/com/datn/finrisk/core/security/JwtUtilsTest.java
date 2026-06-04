package com.datn.finrisk.core.security;

import com.datn.finrisk.core.entities.Role;
import com.datn.finrisk.core.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtils — Unit Tests")
class JwtUtilsTest {

    private JwtUtils jwtUtils;

    // Must be ≥ 32 bytes for HS256
    private static final String SECRET           = "testSecretKeyThatIs32BytesOrMore!!";
    private static final int    EXPIRATION_MS    = 3_600_000;   // 1 hour
    private static final int    REFRESH_EXP_MS   = 86_400_000;  // 24 hours

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret",             SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs",        EXPIRATION_MS);
        ReflectionTestUtils.setField(jwtUtils, "jwtRefreshExpirationMs", REFRESH_EXP_MS);
    }

    private User buildUser(String username, Role role) {
        User u = new User();
        u.setId(42L);
        u.setUsername(username);
        u.setRole(role);
        u.setFullName("Test " + username);
        return u;
    }

    // ── generateJwtToken ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateJwtToken()")
    class GenerateJwtToken {

        @Test
        @DisplayName("returns non-blank token")
        void returnsNonBlankToken() {
            String token = jwtUtils.generateJwtToken(buildUser("alice", Role.USER));
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("generated token validates successfully")
        void generatedTokenIsValid() {
            String token = jwtUtils.generateJwtToken(buildUser("alice", Role.USER));
            assertThat(jwtUtils.validateJwtToken(token)).isTrue();
        }

        @Test
        @DisplayName("username round-trips through token")
        void usernameRoundTrip() {
            String token = jwtUtils.generateJwtToken(buildUser("bob", Role.USER));
            assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("bob");
        }

        @Test
        @DisplayName("role claim round-trips through token")
        void roleRoundTrip_user() {
            String token = jwtUtils.generateJwtToken(buildUser("charlie", Role.USER));
            assertThat(jwtUtils.getRoleFromJwtToken(token)).isEqualTo("USER");
        }

        @Test
        @DisplayName("ADMIN role round-trips through token")
        void roleRoundTrip_admin() {
            String token = jwtUtils.generateJwtToken(buildUser("admin", Role.ADMIN));
            assertThat(jwtUtils.getRoleFromJwtToken(token)).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("different users produce different tokens")
        void differentUsers_differentTokens() {
            String t1 = jwtUtils.generateJwtToken(buildUser("user1", Role.USER));
            String t2 = jwtUtils.generateJwtToken(buildUser("user2", Role.USER));
            assertThat(t1).isNotEqualTo(t2);
        }

        @Test
        @DisplayName("token expiry is in the future")
        void tokenExpiry_inFuture() {
            String token = jwtUtils.generateJwtToken(buildUser("dave", Role.USER));
            assertThat(jwtUtils.getExpirationFromToken(token)).isAfter(new Date());
        }
    }

    // ── generateRefreshToken ──────────────────────────────────────────────────

    @Nested
    @DisplayName("generateRefreshToken()")
    class GenerateRefreshToken {

        @Test
        @DisplayName("returns non-blank token")
        void returnsNonBlankToken() {
            String token = jwtUtils.generateRefreshToken(buildUser("alice", Role.USER));
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("refresh token is valid")
        void refreshTokenIsValid() {
            String token = jwtUtils.generateRefreshToken(buildUser("alice", Role.USER));
            assertThat(jwtUtils.validateJwtToken(token)).isTrue();
        }

        @Test
        @DisplayName("username extracted from refresh token matches input")
        void usernameRoundTrip() {
            String token = jwtUtils.generateRefreshToken(buildUser("dave", Role.USER));
            assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("dave");
        }

        @Test
        @DisplayName("refresh token expires later than access token")
        void refreshExpiresLaterThanAccess() {
            User user     = buildUser("eve", Role.USER);
            String access  = jwtUtils.generateJwtToken(user);
            String refresh = jwtUtils.generateRefreshToken(user);
            assertThat(jwtUtils.getExpirationFromToken(refresh))
                    .isAfter(jwtUtils.getExpirationFromToken(access));
        }
    }

    // ── validateJwtToken ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateJwtToken()")
    class ValidateJwtToken {

        @Test
        @DisplayName("malformed token → false")
        void malformedToken_returnsFalse() {
            assertThat(jwtUtils.validateJwtToken("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("empty string → false")
        void emptyToken_returnsFalse() {
            assertThat(jwtUtils.validateJwtToken("")).isFalse();
        }

        @Test
        @DisplayName("null string → false")
        void nullToken_returnsFalse() {
            assertThat(jwtUtils.validateJwtToken(null)).isFalse();
        }

        @Test
        @DisplayName("token signed with different secret → false")
        void wrongSecret_returnsFalse() {
            JwtUtils other = new JwtUtils();
            ReflectionTestUtils.setField(other, "jwtSecret",             "differentSecretKeyAtLeast32Bytes!!");
            ReflectionTestUtils.setField(other, "jwtExpirationMs",        EXPIRATION_MS);
            ReflectionTestUtils.setField(other, "jwtRefreshExpirationMs", REFRESH_EXP_MS);

            String foreignToken = other.generateJwtToken(buildUser("x", Role.USER));
            assertThat(jwtUtils.validateJwtToken(foreignToken)).isFalse();
        }

        @Test
        @DisplayName("expired token → false")
        void expiredToken_returnsFalse() {
            JwtUtils shortLived = new JwtUtils();
            ReflectionTestUtils.setField(shortLived, "jwtSecret",             SECRET);
            ReflectionTestUtils.setField(shortLived, "jwtExpirationMs",        -1000); // already expired
            ReflectionTestUtils.setField(shortLived, "jwtRefreshExpirationMs", REFRESH_EXP_MS);

            String expired = shortLived.generateJwtToken(buildUser("ghost", Role.USER));
            assertThat(jwtUtils.validateJwtToken(expired)).isFalse();
        }

        @Test
        @DisplayName("random garbage string → false")
        void garbageString_returnsFalse() {
            assertThat(jwtUtils.validateJwtToken("eyJhbGciOiJub25lIn0.e30.")).isFalse();
        }
    }

    // ── getExpirationFromToken ────────────────────────────────────────────────

    @Test
    @DisplayName("getExpirationFromToken() returns a future date")
    void getExpiration_returnsFutureDate() {
        String token = jwtUtils.generateJwtToken(buildUser("eve", Role.USER));
        assertThat(jwtUtils.getExpirationFromToken(token)).isAfter(new Date());
    }
}
