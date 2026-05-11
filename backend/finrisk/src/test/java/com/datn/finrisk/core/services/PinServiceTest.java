package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.exceptions.BusinessLogicException;
import com.datn.finrisk.core.repository.UserSecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BỔ SUNG edge case & case quan trọng cho PinService.
 * File này bổ sung cho PinServiceTest.java gốc, không trùng lặp test cũ.
 *
 * Danh sách gap đã lấp:
 *
 * [verifyPin]
 *   ✅ isPinSetup=true nhưng pinHash=null  → ERR_PIN_NOT_SETUP (state không nhất quán)
 *   ✅ Sai PIN lần 4 (boundary): đúng 1 lần thử còn lại
 *   ✅ Sai PIN lần 1 từ đầu: đúng 4 lần thử còn lại
 *   ✅ Nhập đúng PIN sau 3 lần sai trước đó → failedAttempts reset về 0
 *   ✅ Gọi verifyPin khi đang trong thời gian bị khóa → vẫn bị chặn (trạng thái khóa bền vững)
 *   ✅ LockUntil == LocalDateTime.now() (boundary time: isAfter vs isBefore)
 *   ✅ save() chỉ gọi đúng 1 lần khi nhập đúng PIN (không double-save)
 *
 * [setupOrChangePin]
 *   ✅ isPinSetup=true nhưng pinHash=null → rơi vào nhánh cài đặt lần đầu
 *   ✅ oldPin = null (không phải chuỗi rỗng) → ERR_BAD_REQUEST
 *   ✅ newPin trùng với oldPin → hệ thống hiện tại cho phép (document behavior)
 *   ✅ lastPinChange được cập nhật đúng khi đổi PIN thành công
 *   ✅ passwordEncoder.encode() được gọi đúng 1 lần với newPin
 *   ✅ Cài PIN lần đầu: oldPin không null cũng không được xác thực (bỏ qua)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PinService — Bổ sung Edge Cases")
class PinServiceTest {

    @Mock
    private UserSecurityRepository userSecurityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PinService pinService;

    private UserSecurity mockSecurity;

    @BeforeEach
    void setUp() {
        mockSecurity = new UserSecurity();
        mockSecurity.setId(1L);
        mockSecurity.setIsPinSetup(true);
        mockSecurity.setPinHash("hashed_pin_123");
        mockSecurity.setFailedPinAttempts(0);
        mockSecurity.setLockUntil(null);
    }

    // ====================================================================
    // NHÓM 1 (BỔ SUNG): verifyPin — Edge Cases
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 1 (Bổ sung) — verifyPin Edge Cases")
    class VerifyPinEdgeCases {

        // ==========================================================
        // TEST 1: State không nhất quán — isPinSetup=true nhưng pinHash=null
        // Tại sao quan trọng: Data migration hoặc bug tạo account có thể để lại
        // state này trong DB. Code check cả hai điều kiện (OR), nên cần test cả nhánh.
        // ==========================================================
        @Test
        @DisplayName("⚠️ isPinSetup=true nhưng pinHash=null → Vẫn báo ERR_PIN_NOT_SETUP")
        void verifyPin_isPinSetupTrueButHashNull_throwsPinNotSetup() {
            mockSecurity.setIsPinSetup(true);   // cờ bật
            mockSecurity.setPinHash(null);       // nhưng hash không tồn tại

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                    () -> pinService.verifyPin(mockSecurity, "123456"));

            assertEquals("ERR_PIN_NOT_SETUP", ex.getErrorCode());
            // Không được phép gọi passwordEncoder khi chưa có hash
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        // ==========================================================
        // TEST 2: Boundary - Sai PIN lần 1 từ đầu → còn 4 lần thử
        // Tại sao quan trọng: Kiểm tra công thức (maxAttempts - attempts) đúng ngay từ đầu.
        // ==========================================================
        @Test
        @DisplayName("⚠️ Sai PIN lần đầu tiên → Message báo còn đúng 4 lần thử")
        void verifyPin_firstWrongAttempt_reports4RemainingAttempts() {
            when(passwordEncoder.matches("wrong", "hashed_pin_123")).thenReturn(false);
            mockSecurity.setFailedPinAttempts(0); // Chưa sai lần nào

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                    () -> pinService.verifyPin(mockSecurity, "wrong"));

            assertEquals("ERR_WRONG_PIN", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("4 lần thử"),
                    "Sau lần sai đầu tiên phải báo còn 4 lần, thực tế: " + ex.getMessage());
            assertEquals(1, mockSecurity.getFailedPinAttempts());
        }

        // ==========================================================
        // TEST 3: Boundary - Sai PIN lần 4 → còn đúng 1 lần thử cuối cùng
        // Tại sao quan trọng: Off-by-one error ở đây = user bị khóa sớm 1 lần hoặc muộn 1 lần.
        // ==========================================================
        @Test
        @DisplayName("⚠️ Sai PIN lần thứ 4 (gần kịch trần) → Báo còn đúng 1 lần thử cuối")
        void verifyPin_fourthWrongAttempt_reports1RemainingAttempt() {
            when(passwordEncoder.matches("wrong", "hashed_pin_123")).thenReturn(false);
            mockSecurity.setFailedPinAttempts(3); // Đã sai 3 lần trước

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                    () -> pinService.verifyPin(mockSecurity, "wrong"));

            assertEquals("ERR_WRONG_PIN", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("1 lần thử"),
                    "Sau lần sai thứ 4 phải báo còn đúng 1 lần, thực tế: " + ex.getMessage());
            assertEquals(4, mockSecurity.getFailedPinAttempts());
            // Chưa đến 5 lần → chưa bị khóa
            assertNull(mockSecurity.getLockUntil(), "Chưa được phép khóa ở lần thứ 4");
        }

        // ==========================================================
        // TEST 4: Nhập đúng PIN sau khi đã sai trước đó → failedAttempts PHẢI reset về 0
        // Tại sao quan trọng: Nếu không reset, user sẽ bị khóa ở lần thứ (5 - attempts cũ)
        // trong phiên tiếp theo thay vì lần thứ 5 kể từ đầu.
        // ==========================================================
        @Test
        @DisplayName("✅ Nhập đúng PIN sau 3 lần sai trước đó → failedAttempts reset về 0")
        void verifyPin_correctPinAfterPreviousFailures_resetsCounter() {
            when(passwordEncoder.matches("123456", "hashed_pin_123")).thenReturn(true);
            mockSecurity.setFailedPinAttempts(3); // Đã từng sai 3 lần ở phiên trước

            boolean result = pinService.verifyPin(mockSecurity, "123456");

            assertTrue(result);
            assertEquals(0, mockSecurity.getFailedPinAttempts(),
                    "failedAttempts PHẢI reset về 0 sau khi nhập đúng, dù trước đó đã sai bao nhiêu lần");
        }

        // ==========================================================
        // TEST 5: Gọi verifyPin khi đang trong thời gian bị khóa → vẫn bị chặn ngay lập tức
        // Tại sao quan trọng: Kiểm tra tính bền vững của trạng thái khóa.
        //   Nếu code check sai thứ tự (check hash trước, check lock sau),
        //   hacker có thể bypass bằng cách gửi request liên tục.
        // ==========================================================
        @Test
        @DisplayName("🚨 Gọi verifyPin liên tiếp khi đang bị khóa → Luôn bị chặn, không check mật khẩu")
        void verifyPin_callWhileLocked_alwaysBlocksWithoutCheckingPin() {
            mockSecurity.setLockUntil(LocalDateTime.now().plusMinutes(14));

            // Gọi 3 lần liên tiếp
            for (int i = 0; i < 3; i++) {
                BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                        () -> pinService.verifyPin(mockSecurity, "123456"));
                assertEquals("ERR_PIN_LOCKED", ex.getErrorCode());
            }

            // Tuyệt đối không được check password khi đang bị khóa
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        // ==========================================================
        // TEST 6: Boundary time — LockUntil == LocalDateTime.now() (chính xác tại thời điểm hết hạn)
        // Tại sao quan trọng: Code dùng isAfter() — nếu dùng isAfterOrEqual() behavior sẽ khác.
        //   Khi lockUntil == now thì isAfter(now) = FALSE → đáng lẽ phải cho vào.
        //   Nhưng isBefore(now) cũng = FALSE → không tự reset được.
        //   Đây là race condition nhỏ cần document rõ behavior.
        // ==========================================================
        @Test
        @DisplayName("⚠️ LockUntil đúng bằng thời điểm hiện tại → isAfter=false, được thử PIN (edge boundary)")
        void verifyPin_lockUntilExactlyNow_allowsAttempt() {
            // LockUntil = now: isAfter(now) = false → không vào nhánh "đang khóa"
            // isBefore(now) = false → không tự reset
            // → Đi thẳng đến check hash
            mockSecurity.setLockUntil(LocalDateTime.now());

            when(passwordEncoder.matches("123456", "hashed_pin_123")).thenReturn(true);

            // Hành vi mong đợi: được phép thử (vì isAfter = false)
            assertDoesNotThrow(() -> {
                boolean result = pinService.verifyPin(mockSecurity, "123456");
                assertTrue(result, "Tại boundary lockUntil==now, isAfter=false nên được phép thử PIN");
            });
        }

        // ==========================================================
        // TEST 7: save() được gọi đúng 1 lần khi nhập đúng PIN — không double-save
        // Tại sao quan trọng: Double-save trong cùng một transaction gây overhead,
        //   và nếu có interceptor/audit log thì sẽ tạo 2 record thay vì 1.
        // ==========================================================
        @Test
        @DisplayName("✅ Nhập đúng PIN → save() được gọi đúng 1 lần, không gọi thừa")
        void verifyPin_correctPin_saveCalledExactlyOnce() {
            when(passwordEncoder.matches("123456", "hashed_pin_123")).thenReturn(true);

            pinService.verifyPin(mockSecurity, "123456");

            verify(userSecurityRepository, times(1)).save(mockSecurity);
        }
    }

    // ====================================================================
    // NHÓM 2 (BỔ SUNG): setupOrChangePin — Edge Cases
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 2 (Bổ sung) — setupOrChangePin Edge Cases")
    class SetupOrChangePinEdgeCases {

        // ==========================================================
        // TEST 8: isPinSetup=true nhưng pinHash=null → rơi vào nhánh cài đặt lần đầu
        // Tại sao quan trọng: Điều kiện rẽ nhánh là (!isPinSetup || pinHash==null).
        //   State này tồn tại khi data bị corrupt hoặc migration lỗi.
        //   Phải test để đảm bảo user vẫn có thể tự cứu (re-setup PIN).
        // ==========================================================
        @Test
        @DisplayName("⚠️ isPinSetup=true nhưng pinHash=null → Xử lý như cài đặt lần đầu, không yêu cầu oldPin")
        void setupOrChangePin_isPinSetupTrueButHashNull_treatedAsFirstSetup() {
            mockSecurity.setIsPinSetup(true);
            mockSecurity.setPinHash(null); // Hash bị mất

            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(mockSecurity));
            when(passwordEncoder.encode("123456")).thenReturn("new_hash");

            // Không truyền oldPin, phải thành công vì rơi vào nhánh first-setup
            String result = pinService.setupOrChangePin(1L, null, "123456");

            assertEquals("Cài đặt Mã PIN lần đầu thành công!", result);
            assertEquals("new_hash", mockSecurity.getPinHash());
            assertTrue(mockSecurity.getIsPinSetup());
        }

        // ==========================================================
        // TEST 9: oldPin = null (không phải chuỗi rỗng) → ERR_BAD_REQUEST
        // Tại sao quan trọng: Code check `oldPin == null || oldPin.isEmpty()`.
        //   Test gốc chỉ test oldPin="" (isEmpty). Null là input hợp lệ từ API
        //   khi client không gửi field này. Phải test riêng.
        // ==========================================================
        @Test
        @DisplayName("❌ Đổi PIN với oldPin = null (không truyền) → ERR_BAD_REQUEST")
        void setupOrChangePin_oldPinIsNull_throwsBadRequest() {
            // isPinSetup=true và pinHash có giá trị → rơi vào nhánh đổi PIN
            mockSecurity.setIsPinSetup(true);
            mockSecurity.setPinHash("hashed_pin_123");

            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(mockSecurity));

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                    () -> pinService.setupOrChangePin(1L, null, "new_pin"));

            assertEquals("ERR_BAD_REQUEST", ex.getErrorCode());
            // Không được check hash khi chưa có oldPin
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        // ==========================================================
        // TEST 10: newPin trùng với oldPin → Hệ thống hiện tại cho phép (document behavior)
        // Tại sao quan trọng: Đây là security concern — nhiều hệ thống không cho đặt lại
        //   PIN cũ. Code hiện tại KHÔNG chặn. Test này document behavior hiện tại.
        //   Nếu sau này thêm validation "không được dùng PIN cũ" thì test này sẽ fail
        //   → buộc developer phải update test, không vô tình bỏ qua logic mới.
        // ==========================================================
        @Test
        @DisplayName("⚠️ newPin trùng với oldPin → Hệ thống hiện tại KHÔNG chặn (document behavior)")
        void setupOrChangePin_newPinSameAsOldPin_currentlyAllowed() {
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(mockSecurity));
            when(passwordEncoder.matches("123456", "hashed_pin_123")).thenReturn(true);
            when(passwordEncoder.encode("123456")).thenReturn("same_hash_rehashed");

            // Hiện tại không có validation chặn newPin == oldPin
            String result = pinService.setupOrChangePin(1L, "123456", "123456");

            assertEquals("Thay đổi Mã PIN thành công!", result);
            // NOTE: Nếu business yêu cầu chặn trường hợp này,
            // cần thêm validation và đổi test này thành assertThrows.
        }

        // ==========================================================
        // TEST 11: lastPinChange được cập nhật đúng timestamp khi đổi PIN thành công
        // Tại sao quan trọng: lastPinChange dùng để enforce chính sách "phải đổi PIN
        //   sau X ngày". Nếu không được set đúng, policy này vô dụng.
        // ==========================================================
        @Test
        @DisplayName("✅ Đổi PIN thành công → lastPinChange được cập nhật thành timestamp hiện tại")
        void setupOrChangePin_changePinSuccess_updatesLastPinChangeTimestamp() {
            LocalDateTime beforeTest = LocalDateTime.now().minusSeconds(1);

            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(mockSecurity));
            when(passwordEncoder.matches("old_pin", "hashed_pin_123")).thenReturn(true);
            when(passwordEncoder.encode("new_pin")).thenReturn("new_hash");

            pinService.setupOrChangePin(1L, "old_pin", "new_pin");

            assertNotNull(mockSecurity.getLastPinChange(), "lastPinChange không được null sau khi đổi PIN");
            assertTrue(mockSecurity.getLastPinChange().isAfter(beforeTest),
                    "lastPinChange phải là thời điểm sau khi gọi method, không phải timestamp cũ");
        }

        // ==========================================================
        // TEST 12: passwordEncoder.encode() được gọi đúng 1 lần với newPin — không encode lại
        // Tại sao quan trọng: Nếu bị encode 2 lần (double-hash), PIN sẽ bị lưu sai
        //   và user không bao giờ đăng nhập lại được.
        // ==========================================================
        @Test
        @DisplayName("✅ Đổi PIN → encode() chỉ gọi đúng 1 lần với newPin, không double-encode")
        void setupOrChangePin_changePinSuccess_encodeCalledExactlyOnceWithNewPin() {
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(mockSecurity));
            when(passwordEncoder.matches("old_pin", "hashed_pin_123")).thenReturn(true);
            when(passwordEncoder.encode("new_pin")).thenReturn("new_hash");

            pinService.setupOrChangePin(1L, "old_pin", "new_pin");

            // Verify encode() gọi đúng 1 lần với đúng giá trị newPin
            verify(passwordEncoder, times(1)).encode(eq("new_pin"));
            // Đảm bảo không encode oldPin hay bất kỳ string nào khác
            verify(passwordEncoder, never()).encode(eq("old_pin"));
        }

        // ==========================================================
        // TEST 13: Cài PIN lần đầu với oldPin không null → oldPin hoàn toàn bị bỏ qua
        // Tại sao quan trọng: Client có thể gửi nhầm cả oldPin và newPin trong lần đầu.
        //   Service phải xử lý đúng: bỏ qua oldPin, dùng newPin để cài đặt.
        //   Tránh trường hợp code vô tình check oldPin trong nhánh first-setup.
        // ==========================================================
        @Test
        @DisplayName("✅ Cài PIN lần đầu có kèm oldPin → oldPin bị bỏ qua hoàn toàn, vẫn thành công")
        void setupOrChangePin_firstSetupWithOldPinProvided_ignoresOldPin() {
            mockSecurity.setIsPinSetup(false);
            mockSecurity.setPinHash(null);

            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(mockSecurity));
            when(passwordEncoder.encode("new_pin")).thenReturn("new_hash");

            // Truyền oldPin dù đây là lần đầu cài (client gửi thừa field)
            String result = pinService.setupOrChangePin(1L, "some_old_pin", "new_pin");

            assertEquals("Cài đặt Mã PIN lần đầu thành công!", result);
            // oldPin không được dùng để check
            verify(passwordEncoder, never()).matches(anyString(), anyString());
            // Chỉ encode newPin
            verify(passwordEncoder, times(1)).encode(eq("new_pin"));
        }
    }
}