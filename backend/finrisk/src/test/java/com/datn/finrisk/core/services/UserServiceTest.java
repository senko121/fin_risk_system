package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.repository.UserSecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService - changePassword() Full Test Suite")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSecurityRepository userSecurityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────
    private User buildUser(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    private UserSecurity buildSecurity(Long userId, String passwordHash) {
        UserSecurity s = new UserSecurity();

        User user = new User();
        user.setId(userId);

        s.setUser(user);
        s.setPasswordHash(passwordHash);

        return s;
    }
    // ══════════════════════════════════════════════════════════════
    // 1. Happy Path
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Happy Path – đổi mật khẩu thành công")
    class HappyPath {

        @Test
        @DisplayName("Đổi mật khẩu hợp lệ: hash mới được lưu")
        void shouldChangePasswordSuccessfully() throws Exception {
            User user = buildUser(1L, "thanhng");
            UserSecurity security = buildSecurity(1L, "$2a$hashed_old");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("oldPass123", "$2a$hashed_old")).thenReturn(true);
            when(passwordEncoder.matches("newPass456", "$2a$hashed_old")).thenReturn(false);
            when(passwordEncoder.encode("newPass456")).thenReturn("$2a$hashed_new");

            assertThatCode(() -> userService.changePassword(1L, "oldPass123", "newPass456"))
                    .doesNotThrowAnyException();

            verify(userSecurityRepository).save(security);
            assertThat(security.getPasswordHash()).isEqualTo("$2a$hashed_new");
        }

        @Test
        @DisplayName("lastPasswordChange phải được cập nhật sau khi đổi thành công")
        void shouldUpdateLastPasswordChangeTimestamp() throws Exception {
            User user = buildUser(1L, "thanhng");
            UserSecurity security = buildSecurity(1L, "$2a$hashed_old");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("oldPass", "$2a$hashed_old")).thenReturn(true);
            when(passwordEncoder.matches("newPass", "$2a$hashed_old")).thenReturn(false);
            when(passwordEncoder.encode("newPass")).thenReturn("$2a$hashed_new");

            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            userService.changePassword(1L, "oldPass", "newPass");
            LocalDateTime after = LocalDateTime.now().plusSeconds(1);

            assertThat(security.getLastPasswordChange())
                    .isAfter(before)
                    .isBefore(after);
        }

        @Test
        @DisplayName("userSecurityRepository.save() phải được gọi đúng 1 lần")
        void shouldCallSaveExactlyOnce() throws Exception {
            User user = buildUser(2L, "another");
            UserSecurity security = buildSecurity(2L, "$2a$hash");

            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(2L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("old", "$2a$hash")).thenReturn(true);
            when(passwordEncoder.matches("new", "$2a$hash")).thenReturn(false);
            when(passwordEncoder.encode("new")).thenReturn("$2a$new_hash");

            userService.changePassword(2L, "old", "new");

            verify(userSecurityRepository, times(1)).save(any(UserSecurity.class));
        }

        @Test
        @DisplayName("passwordEncoder.encode() phải được gọi với newPassword")
        void shouldEncodeNewPasswordCorrectly() throws Exception {
            User user = buildUser(3L, "user3");
            UserSecurity security = buildSecurity(3L, "$2a$old");

            when(userRepository.findById(3L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(3L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("OldP@ss!", "$2a$old")).thenReturn(true);
            when(passwordEncoder.matches("NewP@ss!", "$2a$old")).thenReturn(false);
            when(passwordEncoder.encode("NewP@ss!")).thenReturn("$2a$encoded");

            userService.changePassword(3L, "OldP@ss!", "NewP@ss!");

            verify(passwordEncoder).encode("NewP@ss!");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2. User không tồn tại
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Bước 1 – User không tồn tại")
    class UserNotFoundTests {

        @Test
        @DisplayName("Ném Exception với message 'Không tìm thấy người dùng!'")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(999L, "any", "any"))
                    .isInstanceOf(Exception.class)
                    .hasMessage("Không tìm thấy người dùng!");
        }

        @Test
        @DisplayName("Khi user không tồn tại: userSecurityRepository không được gọi")
        void shouldNotCallSecurityRepoWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(999L, "any", "any"))
                    .isInstanceOf(Exception.class);

            verifyNoInteractions(userSecurityRepository);
            verifyNoInteractions(passwordEncoder);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. Hồ sơ bảo mật không tồn tại
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Bước 2 – Hồ sơ bảo mật không tồn tại")
    class SecurityProfileNotFoundTests {

        @Test
        @DisplayName("Ném Exception với message 'Lỗi hệ thống: Không tìm thấy hồ sơ bảo mật!'")
        void shouldThrowWhenSecurityProfileNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser(1L, "user")));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(1L, "any", "any"))
                    .isInstanceOf(Exception.class)
                    .hasMessage("Lỗi hệ thống: Không tìm thấy hồ sơ bảo mật!");
        }

        @Test
        @DisplayName("Khi hồ sơ bảo mật không tìm thấy: passwordEncoder không được gọi")
        void shouldNotCallPasswordEncoderWhenSecurityNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser(1L, "user")));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(1L, "any", "any"))
                    .isInstanceOf(Exception.class);

            verifyNoInteractions(passwordEncoder);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 4. Mật khẩu cũ không khớp
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Bước 3 – Mật khẩu cũ không khớp")
    class WrongOldPasswordTests {

        @Test
        @DisplayName("Ném Exception với message 'Mật khẩu hiện tại không chính xác!'")
        void shouldThrowWhenOldPasswordDoesNotMatch() {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("wrongOld", "$2a$hash")).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(1L, "wrongOld", "newPass"))
                    .isInstanceOf(Exception.class)
                    .hasMessage("Mật khẩu hiện tại không chính xác!");
        }

        @Test
        @DisplayName("Khi sai mật khẩu cũ: save() không được gọi")
        void shouldNotSaveWhenOldPasswordWrong() {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches(anyString(), eq("$2a$hash"))).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(1L, "wrongOld", "newPass"))
                    .isInstanceOf(Exception.class);

            verify(userSecurityRepository, never()).save(any());
        }

        @Test
        @DisplayName("Khi sai mật khẩu cũ: encode() không được gọi")
        void shouldNotEncodeWhenOldPasswordWrong() {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(1L, "wrongOld", "newPass"))
                    .isInstanceOf(Exception.class);

            verify(passwordEncoder, never()).encode(anyString());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 5. Mật khẩu mới trùng mật khẩu cũ
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Bước 4 – Mật khẩu mới trùng mật khẩu cũ")
    class SamePasswordTests {

        @Test
        @DisplayName("Ném Exception với message 'Mật khẩu mới không được trùng với mật khẩu cũ!'")
        void shouldThrowWhenNewPasswordSameAsOld() {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            // oldPassword khớp → true; newPassword cũng khớp hash cũ → true (trùng)
            when(passwordEncoder.matches("samePass", "$2a$hash")).thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword(1L, "samePass", "samePass"))
                    .isInstanceOf(Exception.class)
                    .hasMessage("Mật khẩu mới không được trùng với mật khẩu cũ!");
        }

        @Test
        @DisplayName("Khi mật khẩu mới trùng cũ: save() không được gọi")
        void shouldNotSaveWhenPasswordSame() {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("samePass", "$2a$hash")).thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword(1L, "samePass", "samePass"))
                    .isInstanceOf(Exception.class);

            verify(userSecurityRepository, never()).save(any());
        }

        @Test
        @DisplayName("Khi mật khẩu mới trùng cũ: encode() không được gọi")
        void shouldNotEncodeWhenPasswordSame() {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("samePass", "$2a$hash")).thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword(1L, "samePass", "samePass"))
                    .isInstanceOf(Exception.class);

            verify(passwordEncoder, never()).encode(anyString());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 6. Lỗi tầng database / infrastructure
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Infrastructure Errors – DB lỗi giữa chừng")
    class InfrastructureErrorTests {

        @Test
        @DisplayName("userSecurityRepository.save() ném exception: rollback và ném lên caller")
        void shouldPropagateExceptionWhenSaveFails() {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("oldPass", "$2a$hash")).thenReturn(true);
            when(passwordEncoder.matches("newPass", "$2a$hash")).thenReturn(false);
            when(passwordEncoder.encode("newPass")).thenReturn("$2a$new");
            doThrow(new RuntimeException("DB connection lost")).when(userSecurityRepository).save(any());

            assertThatThrownBy(() -> userService.changePassword(1L, "oldPass", "newPass"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB connection lost");
        }

        @Test
        @DisplayName("userRepository.findById() ném RuntimeException: không swallow exception")
        void shouldPropagateWhenUserRepoThrows() {
            when(userRepository.findById(1L)).thenThrow(new RuntimeException("DB timeout"));

            assertThatThrownBy(() -> userService.changePassword(1L, "old", "new"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB timeout");
        }

        @Test
        @DisplayName("userSecurityRepository.findByUserId() ném RuntimeException: propagate lên")
        void shouldPropagateWhenSecurityRepoThrows() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser(1L, "user")));
            when(userSecurityRepository.findByUserId(1L)).thenThrow(new RuntimeException("Query failed"));

            assertThatThrownBy(() -> userService.changePassword(1L, "old", "new"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Query failed");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 7. Edge Cases – đầu vào bất thường
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Edge Cases – đầu vào bất thường")
    class EdgeCaseTests {

        @Test
        @DisplayName("oldPassword là chuỗi rỗng: vẫn so sánh bình thường qua BCrypt")
        void shouldHandleEmptyOldPassword() {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("", "$2a$hash")).thenReturn(false); // "" không khớp

            assertThatThrownBy(() -> userService.changePassword(1L, "", "newPass"))
                    .isInstanceOf(Exception.class)
                    .hasMessage("Mật khẩu hiện tại không chính xác!");
        }

        @Test
        @DisplayName("newPassword là chuỗi rỗng: encode được gọi với chuỗi rỗng")
        void shouldEncodeEmptyNewPassword() throws Exception {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("oldPass", "$2a$hash")).thenReturn(true);
            when(passwordEncoder.matches("", "$2a$hash")).thenReturn(false);
            when(passwordEncoder.encode("")).thenReturn("$2a$empty");

            userService.changePassword(1L, "oldPass", "");

            verify(passwordEncoder).encode("");
        }

        @Test
        @DisplayName("userId âm (-1L): nếu không tìm thấy thì ném đúng exception")
        void shouldThrowForNegativeUserId() {
            when(userRepository.findById(-1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(-1L, "old", "new"))
                    .isInstanceOf(Exception.class)
                    .hasMessage("Không tìm thấy người dùng!");
        }

        @Test
        @DisplayName("Mật khẩu mới giống cũ về nội dung nhưng khác về case: BCrypt quyết định → không ném exception nếu matches=false")
        void shouldAllowPasswordThatDiffersInCaseOnly() throws Exception {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("Pass123", "$2a$hash")).thenReturn(true);  // old khớp
            when(passwordEncoder.matches("pass123", "$2a$hash")).thenReturn(false); // new khác case → BCrypt nói khác nhau
            when(passwordEncoder.encode("pass123")).thenReturn("$2a$new");

            assertThatCode(() -> userService.changePassword(1L, "Pass123", "pass123"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("passwordEncoder.matches() được gọi đúng 2 lần trong flow thành công")
        void shouldCallMatchesTwiceInSuccessPath() throws Exception {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("oldPass", "$2a$hash")).thenReturn(true);
            when(passwordEncoder.matches("newPass", "$2a$hash")).thenReturn(false);
            when(passwordEncoder.encode("newPass")).thenReturn("$2a$new");

            userService.changePassword(1L, "oldPass", "newPass");

            // Lần 1: kiểm tra old, Lần 2: kiểm tra trùng
            verify(passwordEncoder, times(2)).matches(anyString(), eq("$2a$hash"));
        }

        @Test
        @DisplayName("Thứ tự ghi hash: passwordHash mới phải được set trước khi save()")
        void shouldSetNewHashBeforeSave() throws Exception {
            User user = buildUser(1L, "user");
            UserSecurity security = buildSecurity(1L, "$2a$old_hash");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));
            when(passwordEncoder.matches("old", "$2a$old_hash")).thenReturn(true);
            when(passwordEncoder.matches("new", "$2a$old_hash")).thenReturn(false);
            when(passwordEncoder.encode("new")).thenReturn("$2a$new_hash");

            userService.changePassword(1L, "old", "new");

            ArgumentCaptor<UserSecurity> captor = ArgumentCaptor.forClass(UserSecurity.class);
            verify(userSecurityRepository).save(captor.capture());

            UserSecurity saved = captor.getValue();
            assertThat(saved.getPasswordHash()).isEqualTo("$2a$new_hash");
            assertThat(saved.getLastPasswordChange()).isNotNull();
        }
    }
}