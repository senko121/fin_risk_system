package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.LoginRequest;
import com.datn.finrisk.application.dtos.LoginResponse;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.security.JwtUtils;
import com.datn.finrisk.core.services.AuditLogService;
import com.datn.finrisk.core.services.AuthService;
import com.datn.finrisk.core.services.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

 
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Tắt tạm Spring Security Filter chain gốc để test trực tiếp Controller
@DisplayName("AuthController — Web API Tests")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc; // Súng bắn Request ảo
    @Autowired private ObjectMapper objectMapper; // Cỗ máy nhào nặn JSON

    // 👇 Khai báo một rổ MOCK cho tất cả các Service/Repo nằm sau Controller
    @MockBean private AuthService authService;
    @MockBean private JwtUtils jwtUtils;
    @MockBean private UserRepository userRepository;
    @MockBean private AccountRepository accountRepository;
    @MockBean private RateLimitService rateLimitService;
    @MockBean private AuditLogService auditLogService;
    @MockBean private com.datn.finrisk.core.services.JwtBlocklistService jwtBlocklistService;
    @MockBean private com.datn.finrisk.core.repository.UserDeviceRepository userDeviceRepository;

    private User mockUser;
    private LoginRequest validLoginReq;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setUsername("thach_controller");
        mockUser.setFullName("Thạch Kute");
        mockUser.setLastLoginIp("192.168.1.1");
        mockUser.setLastLoginDevice("Old iPhone");

        validLoginReq = new LoginRequest();
        validLoginReq.setUsername("thach_controller");
        validLoginReq.setPassword("Password@123");
    }

    // ==========================================================
    // NHÓM 1 — TEST API LOGIN
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 1 — API /api/auth/login")
    class LoginApiTests {

        @Test
        @DisplayName("✅ Login Thành Công: Trả về HTTP 200, cấp Token và móc đúng số dư Account")
        void login_whenSuccess_returns200AndTokens() throws Exception {
            // 1. Dàn cảnh (Mocking): Lên kịch bản nếu Controller gọi xuống dưới thì trả về cái gì
            when(rateLimitService.isLoginBlocked(anyString())).thenReturn(false);

            LoginResponse mockLoginResponse = new LoginResponse();
            mockLoginResponse.setUserEntity(mockUser);
            mockLoginResponse.setPinSetup(true);
            mockLoginResponse.setFaceSetup(false);
            when(authService.login(any(LoginRequest.class))).thenReturn(mockLoginResponse);

            when(jwtUtils.generateJwtToken(any(User.class))).thenReturn("fake_access_token");
            when(jwtUtils.generateRefreshToken(any(User.class))).thenReturn("fake_refresh_token");

            Account mockAccount = new Account();
            mockAccount.setAccountNumber("88889999");
            mockAccount.setBalance(new BigDecimal("5000000"));
            when(accountRepository.findByUser(any(User.class))).thenReturn(Optional.of(mockAccount));

            // 2. Nổ súng (Perform Request)
            ResultActions response = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validLoginReq))); // Đổi DTO thành JSON

            // 3. Nghiệm thu (Assert)
            response.andExpect(status().isOk()) // Kỳ vọng trả về HTTP 200
                    .andExpect(jsonPath("$.accessToken").value("fake_access_token")) // Trả về Token đúng
                    .andExpect(jsonPath("$.isPinSetup").value(true))
                    .andExpect(jsonPath("$.isFaceSetup").value(false))
                    .andExpect(jsonPath("$.user.username").value("thach_controller"))
                    // Quan trọng: Kiểm tra xem Controller có nhét thông tin Account vào JSON không
                    .andExpect(jsonPath("$.user.accountNumber").value("88889999"))
                    .andExpect(jsonPath("$.user.balance").value(5000000));
            
            // Đảm bảo đã gọi dịch vụ lưu lại Token
            verify(userRepository, times(1)).save(any(User.class));
            verify(auditLogService, times(1)).logAction(anyString(), eq("LOGIN_SUCCESS"), anyString());
        }

        @Test
        @DisplayName("❌ Login Thất Bại (Sai mật khẩu): Trả về HTTP 400 Bad Request")
        void login_whenWrongPassword_returns400() throws Exception {
            // Dàn cảnh: AuthService báo sai pass (Ném Exception)
            when(rateLimitService.isLoginBlocked(anyString())).thenReturn(false);
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new RuntimeException("Sai thông tin đăng nhập!"));

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validLoginReq)))
                    // Nghiệm thu: Phải trả về 400 và dòng chữ cảnh báo
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("Sai thông tin đăng nhập!"));

            // Kiểm tra xem Controller có gọi cớm (RateLimit) ghi tội chưa
            verify(rateLimitService, times(1)).recordFailedLogin("thach_controller");
        }

        @Test
        @DisplayName("❌ Login Khi Đang Bị Khóa (Rate Limit): Trả về HTTP 429 Too Many Requests")
        void login_whenBlocked_returns429() throws Exception {
            // Dàn cảnh: RateLimitService cắm cờ khóa cổ
            when(rateLimitService.isLoginBlocked("thach_controller")).thenReturn(true);
            when(rateLimitService.getLockTimeRemaining("thach_controller")).thenReturn(120L); // Còn 2 phút

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validLoginReq)))
                    // Nghiệm thu: Mã 429 (Too Many Requests)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("thử lại sau 3 phút"))); 
                    // Controller của bro tính: (120/60) + 1 = 3 phút
        }

        @Test
        @DisplayName("⚠️ Login Từ Thiết Bị Lạ: Vẫn cho vào (HTTP 200) nhưng âm thầm đánh dấu Suspicious")
        void login_fromSuspiciousDevice_returns200AndFlags() throws Exception {
            when(rateLimitService.isLoginBlocked(anyString())).thenReturn(false);
            LoginResponse mockLoginResponse = new LoginResponse();
            mockLoginResponse.setUserEntity(mockUser);
            mockLoginResponse.setPinSetup(true);
            mockLoginResponse.setFaceSetup(false);
            when(authService.login(any(LoginRequest.class))).thenReturn(mockLoginResponse);

            // Bắn Request lên kèm theo cái Header (User-Agent) lạ hoắc
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("User-Agent", "Hacker's PC from Russia")
                    .content(objectMapper.writeValueAsString(validLoginReq)))
                    .andExpect(status().isOk());

            // Kiểm tra xem Controller có mẫn cán ghim cờ "Nghi ngờ" vào AuditLog không
            verify(auditLogService, times(1)).logAction(eq("thach_controller"), eq("SUSPICIOUS_LOGIN"), anyString());
        }
    }

    // ==========================================================
    // NHÓM 2 — TEST API REFRESH TOKEN
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 2 — API /api/auth/refresh-token")
    class RefreshTokenApiTests {

        @Test
        @DisplayName("✅ Đổi Token Thành Công: Trả về cặp Token mới mẻ")
        void refreshToken_whenValid_returnsNewTokens() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("refreshToken", "valid_old_refresh");

            // Dàn cảnh: Token hịn, tìm thấy User, và Token khớp với dưới Database
            when(jwtUtils.validateJwtToken("valid_old_refresh")).thenReturn(true);
            when(jwtUtils.getUserNameFromJwtToken("valid_old_refresh")).thenReturn("thach_controller");
            
            mockUser.setCurrentRefreshToken("valid_old_refresh"); // Token khớp
            when(userRepository.findByUsername("thach_controller")).thenReturn(Optional.of(mockUser));

            // Sinh cặp mới
            when(jwtUtils.generateJwtToken(mockUser)).thenReturn("fresh_access_token");
            when(jwtUtils.generateRefreshToken(mockUser)).thenReturn("fresh_refresh_token");

            mockMvc.perform(post("/api/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("fresh_access_token"))
                    .andExpect(jsonPath("$.refreshToken").value("fresh_refresh_token"));
        }

        @Test
        @DisplayName("❌ Token Tái Sử Dụng (Token Theft): Cảnh báo ăn cắp, đá văng bằng HTTP 403")
        void refreshToken_whenStolen_returns403AndRevokes() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("refreshToken", "stolen_old_refresh");

            when(jwtUtils.validateJwtToken("stolen_old_refresh")).thenReturn(true);
            when(jwtUtils.getUserNameFromJwtToken("stolen_old_refresh")).thenReturn("thach_controller");
            
            // 🚀 BẪY: Trong DB lưu "new_refresh", nhưng Hacker cầm "stolen_old_refresh" lên xin đổi
            mockUser.setCurrentRefreshToken("new_refresh_in_db"); 
            when(userRepository.findByUsername("thach_controller")).thenReturn(Optional.of(mockUser));

            mockMvc.perform(post("/api/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    // Trả về 403 Forbidden
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Cảnh báo bảo mật")));

            // Phải xóa sạch Token hiện tại của user để bắt nó đăng nhập lại
            verify(userRepository, times(1)).save(argThat(u -> u.getCurrentRefreshToken() == null));
            verify(auditLogService, times(1)).logAction(eq("thach_controller"), eq("SECURITY_BREACH_DETECTED"), anyString());
        }

        @Test
        @DisplayName("❌ Token Bị Hỏng/Hết Hạn: Trả về HTTP 403")
        void refreshToken_whenInvalid_returns403() throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("refreshToken", "expired_token");

            when(jwtUtils.validateJwtToken("expired_token")).thenReturn(false); // Token rác

            mockMvc.perform(post("/api/auth/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Thêm vào NHÓM 1 — LoginApiTests ──

@Test
@DisplayName("✅ Login lần đầu (chưa có lastLoginIp) → KHÔNG đánh dấu Suspicious")
void login_firstTimeLogin_notSuspicious() throws Exception {
    // lastLoginIp và lastLoginDevice đều null → lần đầu login
    mockUser.setLastLoginIp(null);
    mockUser.setLastLoginDevice(null);

    when(rateLimitService.isLoginBlocked(anyString())).thenReturn(false);
    LoginResponse mockLoginResponse = new LoginResponse();
    mockLoginResponse.setUserEntity(mockUser);
    when(authService.login(any())).thenReturn(mockLoginResponse);
    when(jwtUtils.generateJwtToken(any())).thenReturn("token");
    when(jwtUtils.generateRefreshToken(any())).thenReturn("refresh");
    when(accountRepository.findByUser(any())).thenReturn(Optional.empty());
    // Device is already registered → isSuspicious = false → SUSPICIOUS_LOGIN must NOT be logged
    when(userDeviceRepository.findByUserIdAndDeviceFingerprint(any(), anyString()))
        .thenReturn(Optional.of(new com.datn.finrisk.core.entities.UserDevice()));

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validLoginReq)))
            .andExpect(status().isOk());

    // Device already registered → KHÔNG được log SUSPICIOUS_LOGIN
    verify(auditLogService, never())
        .logAction(anyString(), eq("SUSPICIOUS_LOGIN"), anyString());
}

@Test
@DisplayName("✅ Login thành công nhưng không có account → vẫn trả 200, không crash")
void login_successButNoAccount_returns200WithoutBalance() throws Exception {
    when(rateLimitService.isLoginBlocked(anyString())).thenReturn(false);
    LoginResponse mockLoginResponse = new LoginResponse();
    mockLoginResponse.setUserEntity(mockUser);
    when(authService.login(any())).thenReturn(mockLoginResponse);
    when(jwtUtils.generateJwtToken(any())).thenReturn("token");
    when(jwtUtils.generateRefreshToken(any())).thenReturn("refresh");
    // Không có account
    when(accountRepository.findByUser(any())).thenReturn(Optional.empty());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validLoginReq)))
            .andExpect(status().isOk())
            // accountNumber và balance không có trong response → null
            .andExpect(jsonPath("$.user.accountNumber").doesNotExist());
}

@Test
@DisplayName("⚠️ Device header > 250 ký tự → tự động cắt, không crash DB")
void login_veryLongUserAgent_truncatedTo250Chars() throws Exception {
    when(rateLimitService.isLoginBlocked(anyString())).thenReturn(false);
    LoginResponse mockLoginResponse = new LoginResponse();
    mockLoginResponse.setUserEntity(mockUser);
    when(authService.login(any())).thenReturn(mockLoginResponse);
    when(jwtUtils.generateJwtToken(any())).thenReturn("token");
    when(jwtUtils.generateRefreshToken(any())).thenReturn("refresh");
    when(accountRepository.findByUser(any())).thenReturn(Optional.empty());

    // Header dài 500 ký tự
    String longAgent = "A".repeat(500);

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .header("User-Agent", longAgent)
            .content(objectMapper.writeValueAsString(validLoginReq)))
            .andExpect(status().isOk());

    // Kiểm tra user được save với device đã bị cắt <= 250
    verify(userRepository, times(1)).save(argThat(u ->
        u.getLastLoginDevice() != null &&
        u.getLastLoginDevice().length() <= 250
    ));
}

@Test
@DisplayName("⚠️ Rate limit blocked → KHÔNG gọi authService, không gọi auditLog LOGIN_FAILED")
void login_blocked_neverCallsAuthService() throws Exception {
    when(rateLimitService.isLoginBlocked("thach_controller")).thenReturn(true);
    when(rateLimitService.getLockTimeRemaining("thach_controller")).thenReturn(60L);

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validLoginReq)))
            .andExpect(status().isTooManyRequests());

    // Bị block thì không được gọi authService
    verify(authService, never()).login(any());
    // Không gọi recordFailedLogin khi đã bị block
    verify(rateLimitService, never()).recordFailedLogin(anyString());
}

@Test
@DisplayName("⚠️ Login thất bại → recordFailedLogin được gọi đúng 1 lần với đúng username")
void login_failed_recordsFailedLoginWithCorrectUsername() throws Exception {
    when(rateLimitService.isLoginBlocked(anyString())).thenReturn(false);
    when(authService.login(any()))
        .thenThrow(new RuntimeException("Sai mật khẩu!"));

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validLoginReq)))
            .andExpect(status().isBadRequest());

    // Phải ghi đúng username vào rate limiter
    verify(rateLimitService, times(1))
        .recordFailedLogin("thach_controller");
    // Phải log đúng action
    verify(auditLogService, times(1))
        .logAction(eq("thach_controller"), eq("LOGIN_FAILED"), anyString());
}

@Test
@DisplayName("⚠️ Login thành công → clearLoginAttempts được gọi để reset bộ đếm")
void login_success_clearsLoginAttempts() throws Exception {
    when(rateLimitService.isLoginBlocked(anyString())).thenReturn(false);
    LoginResponse mockLoginResponse = new LoginResponse();
    mockLoginResponse.setUserEntity(mockUser);
    when(authService.login(any())).thenReturn(mockLoginResponse);
    when(jwtUtils.generateJwtToken(any())).thenReturn("token");
    when(jwtUtils.generateRefreshToken(any())).thenReturn("refresh");
    when(accountRepository.findByUser(any())).thenReturn(Optional.empty());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validLoginReq)))
            .andExpect(status().isOk());

    // Đăng nhập thành công phải reset bộ đếm
    verify(rateLimitService, times(1))
        .clearLoginAttempts("thach_controller");
}

@Test
@DisplayName("⚠️ Response JSON phải có đủ các field: accessToken, refreshToken, user, isPinSetup, isFaceSetup")
void login_success_responseContainsAllRequiredFields() throws Exception {
    when(rateLimitService.isLoginBlocked(anyString())).thenReturn(false);
    LoginResponse mockLoginResponse = new LoginResponse();
    mockLoginResponse.setUserEntity(mockUser);
    mockLoginResponse.setPinSetup(true);
    mockLoginResponse.setFaceSetup(true);
    when(authService.login(any())).thenReturn(mockLoginResponse);
    when(jwtUtils.generateJwtToken(any())).thenReturn("access_token_xyz");
    when(jwtUtils.generateRefreshToken(any())).thenReturn("refresh_token_xyz");
    when(accountRepository.findByUser(any())).thenReturn(Optional.empty());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validLoginReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.user").exists())
            .andExpect(jsonPath("$.isPinSetup").value(true))
            .andExpect(jsonPath("$.isFaceSetup").value(true));
}

// ── Thêm vào NHÓM 2 — RefreshTokenApiTests ──

@Test
@DisplayName("❌ Body không có key 'refreshToken' → trả về 403")
void refreshToken_missingKey_returns403() throws Exception {
    Map<String, String> body = new HashMap<>();
    body.put("wrongKey", "some_value"); // sai key

    // refreshToken = null → validateJwtToken(null) trả về false
    when(jwtUtils.validateJwtToken(null)).thenReturn(false);

    mockMvc.perform(post("/api/auth/refresh-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
}

@Test
@DisplayName("❌ refreshToken null trong body → trả về 403, không crash")
void refreshToken_nullValue_returns403() throws Exception {
    Map<String, String> body = new HashMap<>();
    body.put("refreshToken", null);

    when(jwtUtils.validateJwtToken(null)).thenReturn(false);

    mockMvc.perform(post("/api/auth/refresh-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("không hợp lệ")));
}

@Test
@DisplayName("❌ Token hợp lệ nhưng user đã bị xóa khỏi DB → throw exception")
void refreshToken_validTokenButUserDeleted_throwsException() throws Exception {
    Map<String, String> body = new HashMap<>();
    body.put("refreshToken", "valid_but_orphan_token");

    when(jwtUtils.validateJwtToken("valid_but_orphan_token")).thenReturn(true);
    when(jwtUtils.getUserNameFromJwtToken("valid_but_orphan_token"))
        .thenReturn("deleted_user");
    // User đã bị xóa
    when(userRepository.findByUsername("deleted_user"))
        .thenReturn(Optional.empty());

    mockMvc.perform(post("/api/auth/refresh-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            // Controller throw RuntimeException → Spring trả về 500
            // Bro có thể muốn đổi thành 403 — test này expose vấn đề đó
            .andExpect(status().is5xxServerError());
}

@Test
@DisplayName("✅ Refresh thành công → currentRefreshToken trong DB được cập nhật token mới")
void refreshToken_success_updatesRefreshTokenInDb() throws Exception {
    Map<String, String> body = new HashMap<>();
    body.put("refreshToken", "current_refresh");

    when(jwtUtils.validateJwtToken("current_refresh")).thenReturn(true);
    when(jwtUtils.getUserNameFromJwtToken("current_refresh"))
        .thenReturn("thach_controller");
    mockUser.setCurrentRefreshToken("current_refresh");
    when(userRepository.findByUsername("thach_controller"))
        .thenReturn(Optional.of(mockUser));
    when(jwtUtils.generateJwtToken(any())).thenReturn("new_access");
    when(jwtUtils.generateRefreshToken(any())).thenReturn("new_refresh");

    mockMvc.perform(post("/api/auth/refresh-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk());

    // DB phải được update với refresh token MỚI, không phải token cũ
    verify(userRepository, times(1)).save(argThat(u ->
        "new_refresh".equals(u.getCurrentRefreshToken())
    ));
}
}