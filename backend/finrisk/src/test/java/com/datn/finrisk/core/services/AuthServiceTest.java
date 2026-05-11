package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.LoginRequest;
import com.datn.finrisk.application.dtos.LoginResponse;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.repository.UserSecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — Unit Tests")
class AuthServiceTest {

    // =============================================
    // KHAI BÁO MOCK — phải đủ tất cả @Autowired
    // trong AuthService.java
    // =============================================
    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private UserSecurityRepository userSecurityRepository; // ← thêm vào
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService; // ← object THẬT, dùng mock ở trên

    private User mockUser;
    private UserSecurity mockSecurity;
    private Account mockAccount;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        mockSecurity = new UserSecurity();
        mockSecurity.setPasswordHash("hashed_123456");
        mockSecurity.setPinHash("hashed_pin");
        mockSecurity.setIsPinSetup(true);

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("thach");
        mockUser.setFullName("Nguyễn Hoàng Thạch");
        mockUser.setUserSecurity(mockSecurity);
        mockUser.setBase64FaceImage(null); // mặc định chưa đăng ký mặt

        mockAccount = new Account();
        mockAccount.setAccountNumber("0123456789");
        mockAccount.setBalance(new BigDecimal("5000000"));

        loginRequest = new LoginRequest();
        loginRequest.setUsername("thach");
        loginRequest.setPassword("123456");
    }

    // =============================================
    // NHÓM 1: HAPPY PATH
    // =============================================

    @Test
    @DisplayName("✅ Đăng nhập thành công — trả về đầy đủ thông tin")
    void login_correctCredentials_returnsLoginResponse() {
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("123456", "hashed_123456"))
            .thenReturn(true);
        when(accountRepository.findByUserId(1L))
            .thenReturn(Optional.of(mockAccount));

        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals("Nguyễn Hoàng Thạch", response.getFullName());
        assertEquals("0123456789", response.getAccountNumber());
        assertEquals(new BigDecimal("5000000"), response.getBalance());
        assertEquals("Đăng nhập thành công!", response.getMessage());
    }

    @Test
    @DisplayName("✅ Đã cài PIN → isPinSetup = true")
    void login_userHasPin_returnsPinSetupTrue() {
        mockSecurity.setPinHash("hashed_pin");
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(accountRepository.findByUserId(anyLong()))
            .thenReturn(Optional.of(mockAccount));

        LoginResponse response = authService.login(loginRequest);

        assertTrue(response.isPinSetup());
    }

    @Test
    @DisplayName("✅ Chưa cài PIN (pinHash null) → isPinSetup = false")
    void login_userHasNoPin_returnsPinSetupFalse() {
        mockSecurity.setPinHash(null); // chưa cài PIN
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(accountRepository.findByUserId(anyLong()))
            .thenReturn(Optional.of(mockAccount));

        LoginResponse response = authService.login(loginRequest);

        assertFalse(response.isPinSetup());
    }

    @Test
    @DisplayName("✅ Chưa cài PIN (pinHash rỗng) → isPinSetup = false")
    void login_userHasEmptyPin_returnsPinSetupFalse() {
        mockSecurity.setPinHash("   "); // rỗng sau trim
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(accountRepository.findByUserId(anyLong()))
            .thenReturn(Optional.of(mockAccount));

        LoginResponse response = authService.login(loginRequest);

        assertFalse(response.isPinSetup());
    }

    @Test
    @DisplayName("✅ Đã đăng ký khuôn mặt → isFaceSetup = true")
    void login_userHasFace_returnsFaceSetupTrue() {
        mockUser.setBase64FaceImage("base64imagedata...");
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(accountRepository.findByUserId(anyLong()))
            .thenReturn(Optional.of(mockAccount));

        LoginResponse response = authService.login(loginRequest);

        assertTrue(response.isFaceSetup());
    }

    @Test
    @DisplayName("✅ Chưa đăng ký khuôn mặt (null) → isFaceSetup = false")
    void login_userHasNoFace_returnsFaceSetupFalse() {
        mockUser.setBase64FaceImage(null);
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(accountRepository.findByUserId(anyLong()))
            .thenReturn(Optional.of(mockAccount));

        LoginResponse response = authService.login(loginRequest);

        assertFalse(response.isFaceSetup());
    }

    // =============================================
    // NHÓM 2: LỖI ĐẦU VÀO
    // =============================================

    @Test
    @DisplayName("❌ Username không tồn tại → ném exception đúng message")
    void login_userNotFound_throwsException() {
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> authService.login(loginRequest));

        assertEquals("Tài khoản không tồn tại!", ex.getMessage());
        // Dừng ngay, không được gọi passwordEncoder
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(accountRepository, never()).findByUserId(anyLong());
    }

    @Test
    @DisplayName("❌ Sai mật khẩu → ném exception đúng message")
    void login_wrongPassword_throwsException() {
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("123456", "hashed_123456"))
            .thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> authService.login(loginRequest));

        assertEquals("Sai mật khẩu!", ex.getMessage());
        // Dừng ngay, không được tìm account
        verify(accountRepository, never()).findByUserId(anyLong());
    }

    @Test
    @DisplayName("❌ UserSecurity null → ném exception hệ thống")
    void login_securityProfileNull_throwsSystemException() {
        mockUser.setUserSecurity(null); // security bị null
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.of(mockUser));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> authService.login(loginRequest));

        assertEquals("Lỗi hệ thống: Không tìm thấy hồ sơ bảo mật!", ex.getMessage());
        // Không được gọi passwordEncoder khi security null
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("❌ User chưa có tài khoản ngân hàng → ném exception")
    void login_noAccount_throwsException() {
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("123456", "hashed_123456"))
            .thenReturn(true);
        when(accountRepository.findByUserId(1L))
            .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> authService.login(loginRequest));

        assertEquals("Người dùng chưa có tài khoản ngân hàng!", ex.getMessage());
    }

    // =============================================
    // NHÓM 3: VERIFY FLOW — đảm bảo gọi đúng method
    // =============================================

    @Test
    @DisplayName("🔍 Login thành công → phải gọi findByUsername đúng 1 lần")
    void login_success_callsRepositoryExactlyOnce() {
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(accountRepository.findByUserId(anyLong()))
            .thenReturn(Optional.of(mockAccount));

        authService.login(loginRequest);

        verify(userRepository, times(1)).findByUsername("thach");
        verify(passwordEncoder, times(1)).matches("123456", "hashed_123456");
        verify(accountRepository, times(1)).findByUserId(1L);
    }

    @Test
    @DisplayName("🔍 Login thất bại (sai pass) → không được gọi accountRepository")
    void login_wrongPassword_neverCallsAccountRepo() {
        when(userRepository.findByUsername("thach"))
            .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.login(loginRequest));

        verify(accountRepository, never()).findByUserId(anyLong());
    }

    
}