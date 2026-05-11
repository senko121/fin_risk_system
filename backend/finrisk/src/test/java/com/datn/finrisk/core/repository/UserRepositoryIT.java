package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Role;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserRepository — Integration Tests (H2)")
class UserRepositoryIT {

    @Autowired private TestEntityManager entityManager;
    @Autowired private UserRepository userRepository;

    private User savedUser;

    // ── Helper: Tạo User + UserSecurity đầy đủ ──
    private User createUser(String username, String phone, String email) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("Nguyễn Hoàng Thạch");
        user.setPhoneNumber(phone);
        user.setEmail(email);
        user = entityManager.persist(user);

        UserSecurity sec = new UserSecurity();
        sec.setPinHash("pin_123");
        sec.setPasswordHash("pass_123");
        sec.setIsPinSetup(false);
        sec.setTwoFactorEnabled(false);
        sec.setFailedLoginAttempts(0);
        sec.setFailedPinAttempts(0);
        sec.setUser(user);
        entityManager.persist(sec);

        user.setUserSecurity(sec);
        return user;
    }

    // ── Helper: Tạo User không có UserSecurity ──
    private User createUserWithoutSecurity(String username, String phone, String email) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("User Không Có Security");
        user.setPhoneNumber(phone);
        user.setEmail(email);
        return entityManager.persist(user);
    }

    @BeforeEach
    void setUp() {
        savedUser = createUser("admin_thach", "0988888888", "thach@finrisk.com");
        entityManager.flush();
        entityManager.clear(); // Xóa cache — bắt buộc chạy SQL thực tế
    }

    // ==========================================================
    // NHÓM 1 — ENTITYGRAPH: DIỆT N+1 QUERY
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 1 — @EntityGraph (Diệt N+1 Query)")
    class EntityGraphTests {

        @Test
        @DisplayName("✅ findByUsername: Kéo User + Security trong 1 SQL")
        void findByUsername_fetchesSecurityEagerly() {
            Optional<User> result = userRepository.findByUsername("admin_thach");

            assertTrue(result.isPresent());
            // Nếu @EntityGraph không hoạt động → LazyInitializationException ở đây
            assertDoesNotThrow(() -> {
                UserSecurity security = result.get().getUserSecurity();
                assertNotNull(security, "UserSecurity phải được JOIN FETCH cùng");
                assertEquals("pass_123", security.getPasswordHash());
                assertEquals("pin_123", security.getPinHash());
            }, "Phải có @EntityGraph để tránh LazyInitializationException");
        }

        @Test
        @DisplayName("✅ findById: Kéo User + Security trong 1 SQL")
        void findById_fetchesSecurityEagerly() {
            Optional<User> result = userRepository.findById(savedUser.getId());

            assertTrue(result.isPresent());
            assertDoesNotThrow(() -> {
                assertNotNull(result.get().getUserSecurity());
                assertEquals("pin_123", result.get().getUserSecurity().getPinHash());
            }, "findById cũng phải có @EntityGraph");
        }

        @Test
        @DisplayName("✅ findByUsername: Trả về đúng user, không lẫn user khác")
        void findByUsername_returnsCorrectUser() {
            // Tạo thêm user thứ 2
            createUser("user_khac", "0911111111", "other@finrisk.com");
            entityManager.flush();
            entityManager.clear();

            Optional<User> result = userRepository.findByUsername("admin_thach");

            assertTrue(result.isPresent());
            assertEquals("admin_thach", result.get().getUsername());
            assertNotEquals("user_khac", result.get().getUsername());
        }

        @Test
        @DisplayName("✅ findById: Trả về đúng user theo ID")
        void findById_returnsCorrectUser() {
            Optional<User> result = userRepository.findById(savedUser.getId());

            assertTrue(result.isPresent());
            assertEquals(savedUser.getId(), result.get().getId());
            assertEquals("admin_thach", result.get().getUsername());
        }
    }

    // ==========================================================
    // NHÓM 2 — NOT FOUND CASES
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 2 — Không tìm thấy")
    class NotFoundTests {

        @Test
        @DisplayName("❌ findByUsername: Username không tồn tại → Optional.empty()")
        void findByUsername_notExists_returnsEmpty() {
            assertTrue(userRepository.findByUsername("hacker_vo_danh").isEmpty());
        }

        @Test
        @DisplayName("❌ findById: ID không tồn tại → Optional.empty()")
        void findById_notExists_returnsEmpty() {
            assertTrue(userRepository.findById(99999L).isEmpty());
        }

        @Test
        @DisplayName("❌ findById: ID âm → Optional.empty()")
        void findById_negativeId_returnsEmpty() {
            assertTrue(userRepository.findById(-1L).isEmpty());
        }

        @Test
        @DisplayName("❌ findById: ID = 0 → Optional.empty()")
        void findById_zeroId_returnsEmpty() {
            assertTrue(userRepository.findById(0L).isEmpty());
        }
    }

    // ==========================================================
    // NHÓM 3 — EDGE CASES: USERNAME ĐẶC BIỆT
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 3 — Edge Cases: Username đặc biệt")
    class UsernameEdgeCaseTests {

        @Test
        @DisplayName("❌ Username IN HOA → không tìm thấy (H2 case-sensitive)")
        void findByUsername_uppercase_returnsEmpty() {
            Optional<User> result = userRepository.findByUsername("ADMIN_THACH");
            assertTrue(result.isEmpty(),
                "DB phân biệt hoa thường — viết hoa sẽ không tìm thấy");
        }

        @Test
        @DisplayName("❌ Username có khoảng trắng đầu → không tìm thấy")
        void findByUsername_leadingSpace_returnsEmpty() {
            Optional<User> result = userRepository.findByUsername(" admin_thach");
            assertTrue(result.isEmpty(),
                "Khoảng trắng đầu phải tạo ra username khác biệt");
        }

        @Test
        @DisplayName("❌ Username có khoảng trắng cuối → không tìm thấy")
        void findByUsername_trailingSpace_returnsEmpty() {
            Optional<User> result = userRepository.findByUsername("admin_thach ");
            assertTrue(result.isEmpty(),
                "Khoảng trắng cuối phải tạo ra username khác biệt");
        }

        @Test
        @DisplayName("❌ Username rỗng → không tìm thấy")
        void findByUsername_emptyString_returnsEmpty() {
            assertTrue(userRepository.findByUsername("").isEmpty());
        }

        @Test
            @DisplayName("✅ Username null → Trả về empty (JPA tự dịch thành IS NULL)")
            void findByUsername_null_returnsEmptyGracefully() {
                // JPA sẽ không crash, mà tự dịch thành WHERE username IS NULL
                Optional<User> result = userRepository.findByUsername(null);

                assertTrue(result.isEmpty(), "Khi truyền null, JPA phải an toàn trả về empty");
            }

        @Test
        @DisplayName("❌ SQL Injection trong username → không tìm thấy (JPA đã dùng prepared statement)")
        void findByUsername_sqlInjection_returnsEmpty() {
            String malicious = "' OR '1'='1'; DROP TABLE users; --";
            Optional<User> result = userRepository.findByUsername(malicious);
            // JPA dùng PreparedStatement nên injection không hoạt động
            assertTrue(result.isEmpty(),
                "SQL Injection phải bị chặn bởi PreparedStatement");
            // Đảm bảo bảng users vẫn còn
            assertTrue(userRepository.findByUsername("admin_thach").isPresent(),
                "Bảng users phải còn nguyên sau SQL injection attempt");
        }
    }

    // ==========================================================
    // NHÓM 4 — USER KHÔNG CÓ SECURITY
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 4 — User không có UserSecurity")
    class UserWithoutSecurityTests {

        @Test
        @DisplayName("✅ findByUsername: User không có Security → getUserSecurity() trả về null")
        void findByUsername_userWithoutSecurity_returnsNullSecurity() {
            createUserWithoutSecurity("user_no_sec", "0922222222", "nosec@finrisk.com");
            entityManager.flush();
            entityManager.clear();

            Optional<User> result = userRepository.findByUsername("user_no_sec");

            assertTrue(result.isPresent());
            // @EntityGraph với LEFT JOIN → security null nếu không có
            assertNull(result.get().getUserSecurity(),
                "User không có security thì getUserSecurity() phải trả về null");
        }

        @Test
        @DisplayName("✅ findById: User không có Security → không crash")
        void findById_userWithoutSecurity_doesNotCrash() {
            User noSecUser = createUserWithoutSecurity(
                "no_sec_by_id", "0933333333", "nosecid@finrisk.com");
            entityManager.flush();
            entityManager.clear();

            assertDoesNotThrow(() -> {
                Optional<User> result = userRepository.findById(noSecUser.getId());
                assertTrue(result.isPresent());
            });
        }
    }

    // ==========================================================
    // NHÓM 5 — DATA INTEGRITY (UNIQUE CONSTRAINTS)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 5 — Data Integrity (Unique Constraints)")
    class UniqueConstraintTests {

        @Test
        @DisplayName("❌ Trùng username → DB từ chối")
        void duplicateUsername_throwsException() {
            assertThrows(Exception.class, () -> {
                createUser("admin_thach", "0911111111", "new@finrisk.com");
                entityManager.flush();
            }, "DB phải cấm trùng username");
        }

        @Test
        @DisplayName("❌ Trùng số điện thoại → DB từ chối")
        void duplicatePhone_throwsException() {
            assertThrows(Exception.class, () -> {
                createUser("thach_fake", "0988888888", "fake@finrisk.com");
                entityManager.flush();
            }, "DB phải cấm trùng số điện thoại");
        }

        @Test
        @DisplayName("❌ Trùng email → DB từ chối")
        void duplicateEmail_throwsException() {
            assertThrows(Exception.class, () -> {
                createUser("thach_fake2", "0944444444", "thach@finrisk.com");
                entityManager.flush();
            }, "DB phải cấm trùng email");
        }
    }

    // ==========================================================
    // NHÓM 6 — DEFAULT VALUES VÀ BEHAVIOR
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 6 — Default Values và Behavior")
    class DefaultValueTests {

        @Test
        @DisplayName("✅ Status mặc định là ACTIVE")
        void defaultStatus_isActive() {
            Optional<User> result = userRepository.findByUsername("admin_thach");
            assertEquals("ACTIVE", result.get().getStatus());
        }

        @Test
        @DisplayName("✅ Role mặc định là USER")
        void defaultRole_isUser() {
            Optional<User> result = userRepository.findByUsername("admin_thach");
            assertEquals(Role.USER, result.get().getRole());
        }

        @Test
        @DisplayName("✅ isSuspiciousSession mặc định là false")
        void defaultSuspiciousSession_isFalse() {
            Optional<User> result = userRepository.findByUsername("admin_thach");
            assertFalse(result.get().isSuspiciousSession());
        }

        @Test
        @DisplayName("✅ isAdminFlagged mặc định là false")
        void defaultAdminFlagged_isFalse() {
            Optional<User> result = userRepository.findByUsername("admin_thach");
            assertFalse(result.get().isAdminFlagged());
        }

        @Test
        @DisplayName("✅ createdAt được tự động set khi tạo")
        void createdAt_isSetAutomatically() {
            Optional<User> result = userRepository.findByUsername("admin_thach");
            assertNotNull(result.get().getCreatedAt(),
                "createdAt phải được set tự động bởi @PrePersist hoặc default DB");
        }

        @Test
        @DisplayName("✅ Tìm lại sau khi update fullName → trả về data mới")
        void findByUsername_afterUpdate_returnsUpdatedData() {
            Optional<User> result = userRepository.findByUsername("admin_thach");
            User user = result.get();
            user.setFullName("Tên Mới Sau Khi Đổi");
            userRepository.save(user);
            entityManager.flush();
            entityManager.clear();

            Optional<User> updated = userRepository.findByUsername("admin_thach");
            assertEquals("Tên Mới Sau Khi Đổi", updated.get().getFullName());
        }
    }
}