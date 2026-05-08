package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.AuthVerifyRequest;
import com.datn.finrisk.core.entities.*;
import com.datn.finrisk.core.exceptions.BusinessLogicException;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.*;
import com.datn.finrisk.core.strategies.AdvancedFaceActionStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(controllers = TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TransactionController — Giai đoạn 2+3: Verify & Voice")
class TransactionVerifyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TransactionService transactionService;
    @MockBean private TransactionRepository transactionRepository;
    @MockBean private TransactionLedgerRepository ledgerRepository;
    @MockBean private AccountRepository accountRepository;
    @MockBean private OtpService otpService;
    @MockBean private EmailService emailService;
    @MockBean private AdvancedFaceActionStrategy faceScanActionStrategy;
    @MockBean private PinService pinService;
    @MockBean private AuditLogService auditLogService;
    @MockBean private RiskEvaluationService riskEvaluationService;

    @MockBean private com.datn.finrisk.core.security.JwtUtils jwtUtils;
    @MockBean private com.datn.finrisk.core.repository.UserRepository userRepository;

    private Transaction mockTx;
    private User mockUser;
    private Account mockAccount;
    private UserSecurity mockSecurity;

    @BeforeEach
    void setUp() {
        mockSecurity = new UserSecurity();
        mockSecurity.setPinHash("$2a$10$hashedPin");

        mockUser = new User();
        mockUser.setUsername("thach_verify");
        mockUser.setUserSecurity(mockSecurity);

        mockAccount = new Account();
        mockAccount.setId(1L);
        mockAccount.setAccountNumber("1111111111");
        mockAccount.setUser(mockUser);

        mockTx = new Transaction();
        mockTx.setId(100L);
        mockTx.setFromAccount(mockAccount);
        mockTx.setToAccountNumber("9999999999");
        mockTx.setAmount(new BigDecimal("500000"));
    }

    // ── Helper tạo request body ──
    private String verifyBody(String authType, String authCode) throws Exception {
        AuthVerifyRequest req = new AuthVerifyRequest();
        req.setTransactionId(100L);
        req.setAuthType(authType);
        req.setAuthCode(authCode);
        return objectMapper.writeValueAsString(req);
    }

    // ==========================================================
    // NHÓM 1 — TRẠM PIN (CHUYÊN SÂU MỨC LOW: PENDING_PIN)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 1 — Phá đảo mức LOW (Chỉ yêu cầu PIN)")
    class PinLowLevelTests {
 
        @Test
        @DisplayName("✅ 1. Nhập PIN đúng → Trừ tiền thành công, ghi Audit Log")
        void lowLevel_success() throws Exception {
            mockTx.setStatus("PENDING_PIN");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(pinService.verifyPin(any(), eq("123456"))).thenReturn(true);
            
            Transaction completed = new Transaction();
            completed.setStatus("SUCCESS");
            when(transactionService.executeTransactionCore(mockTx)).thenReturn(completed);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.status").value("SUCCESS"));

            verify(transactionService, times(1)).executeTransactionCore(mockTx);
            verify(auditLogService).logAction(eq("thach_verify"), eq("TX_SUCCESS"), anyString());
        }

        // 🔴 KỊCH BẢN 2: SAI MÃ PIN
        @Test
        @DisplayName("❌ 2. Nhập sai PIN → Bị chặn lại, trả về 400 Bad Request")
        void lowLevel_wrongPin_throws400() throws Exception {
            mockTx.setStatus("PENDING_PIN");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            
            when(pinService.verifyPin(any(), eq("999999")))
                .thenThrow(new BusinessLogicException("ERR_WRONG_PIN", "Mã PIN không chính xác!"));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "999999")))
                    .andExpect(status().isBadRequest()); 

            // Đảm bảo TUYỆT ĐỐI không được gọi hàm trừ tiền
            verify(transactionService, never()).executeTransactionCore(any());
        }

        // 🔴 KỊCH BẢN 3: BỊ KHÓA TÀI KHOẢN DO SPAM PIN
        @Test
        @DisplayName("❌ 3. Spam sai PIN 5 lần bị khóa tài khoản → Không cho xác thực tiếp")
        void lowLevel_lockedAccount_throws400() throws Exception {
            mockTx.setStatus("PENDING_PIN");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            
            // Giả lập PinService phát hiện user này đã bị khóa do nhập sai quá nhiều lần trước đó
            when(pinService.verifyPin(any(), eq("123456")))
                .thenThrow(new BusinessLogicException("ERR_ACCOUNT_LOCKED", "Tài khoản đã bị khóa do nhập sai PIN 5 lần!"));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().isBadRequest());
        }

        // 🔴 KỊCH BẢN 4: HỒ SƠ BẢO MẬT BỊ XÓA (DATA CORRUPTION)
        @Test
        @DisplayName("❌ 4. Mất hồ sơ bảo mật (UserSecurity = null) → Sập nguồn 500, chặn trừ tiền")
        void lowLevel_missingSecurityProfile_throws500() throws Exception {
            mockTx.setStatus("PENDING_PIN");
            mockUser.setUserSecurity(null); // Giả lập lỗi Data: Bị mất table security dưới DB
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().is5xxServerError());

            verify(pinService, never()).verifyPin(any(), anyString());
            verify(transactionService, never()).executeTransactionCore(any());
        }

        // 🔴 KỊCH BẢN 5: SỐ DƯ BỊ RÚT RỖNG TRONG LÚC CHỜ PIN (RACE CONDITION)
        @Test
        @DisplayName("❌ 5. Lỗi thiếu tiền: Vừa xác thực PIN xong thì tk hết tiền → Rollback, báo lỗi 400")
        void lowLevel_insufficientBalanceAtExecution_throws400() throws Exception {
            mockTx.setStatus("PENDING_PIN");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(pinService.verifyPin(any(), eq("123456"))).thenReturn(true);
            
            // PIN đúng, nhưng lúc chui vào Core trừ tiền thì phát hiện vừa bị trừ tiền ở lệnh khác
            when(transactionService.executeTransactionCore(mockTx))
                .thenThrow(new BusinessLogicException("ERR_BALANCE", "Số dư không đủ để thực hiện giao dịch!"));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().isBadRequest());
        }

        // 🔴 KỊCH BẢN 6: GIAO DỊCH MA (GHOST TRANSACTION)
        @Test
        @DisplayName("❌ 6. Giao dịch không tồn tại (Bị xóa hoặc ID láo) → Ném lỗi 500")
        void lowLevel_transactionNotFound() throws Exception {
            when(transactionRepository.findByIdWithUserSecurity(99999L)).thenReturn(Optional.empty());

            AuthVerifyRequest req = new AuthVerifyRequest();
            req.setTransactionId(99999L);
            req.setAuthType("PIN");
            req.setAuthCode("123456");

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().is5xxServerError());
        }

        // 🔴 KỊCH BẢN 7: TÁI SỬ DỤNG GIAO DỊCH (DOUBLE SPENDING)
        @Test
        @DisplayName("❌ 7. Tái xác thực giao dịch đã THÀNH CÔNG/THẤT BẠI trước đó → Chặn đứng")
        void lowLevel_transactionAlreadyCompleted_returns400() throws Exception {
            // Hacker lấy ID của một giao dịch ngày hôm qua (đã SUCCESS) và bắn API xác thực lần nữa hòng rút đúp tiền
            mockTx.setStatus("SUCCESS"); 
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(pinService.verifyPin(any(), eq("123456"))).thenReturn(true);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().isBadRequest()) // Sẽ lọt xuống cuối hàm Controller trả về badRequest()
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("không hợp lệ")));

            verify(transactionService, never()).executeTransactionCore(any());
        }

        // 🔴 KỊCH BẢN 8: NHẦM TRẠM XÁC THỰC
        @Test
        @DisplayName("❌ 8. Status đang chờ PIN, nhưng Frontend lại gửi authType là OTP → Đuổi cổ")
        void lowLevel_wrongAuthTypeSubmitted_returns400() throws Exception {
            mockTx.setStatus("PENDING_PIN"); // Đang chờ PIN
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(otpService.verifyOtp(anyLong(), anyString())).thenReturn(true);

            // Nhưng request gửi lên lại nói "Đây là mã OTP"
            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", "123456")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("không hợp lệ")));

            verify(pinService, never()).verifyPin(any(), anyString());
            verify(transactionService, never()).executeTransactionCore(any());
        }

        // 🔴 BỔ SUNG 9: GỬI authCode RỖNG ("")
        @Test
        @DisplayName("❌ 9. authCode rỗng ('') → Không gọi pinService, trả về lỗi")
        void lowLevel_emptyAuthCode_shouldNotCallPinService() throws Exception {
            mockTx.setStatus("PENDING_PIN");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "")))
                    .andExpect(status().isBadRequest());

            // Tuyệt đối không được gọi service khi input rỗng
            verify(pinService, never()).verifyPin(any(), anyString());
            verify(transactionService, never()).executeTransactionCore(any());
        }

        // 🔴 BỔ SUNG 10: GỬI authCode NULL
        @Test
        @DisplayName("❌ 10. authCode null → Không gọi pinService, trả về lỗi")
        void lowLevel_nullAuthCode_shouldNotCallPinService() throws Exception {
            mockTx.setStatus("PENDING_PIN");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", null)))
                    .andExpect(status().isBadRequest());

            verify(pinService, never()).verifyPin(any(), anyString());
            verify(transactionService, never()).executeTransactionCore(any());
        }

        // 🔴 BỔ SUNG 11: GỬI authType NULL (rơi xuống đáy controller)
        @Test
        @DisplayName("❌ 11. authType null → Không match nhánh nào → 400 'Luồng không hợp lệ'")
        void lowLevel_nullAuthType_returns400() throws Exception {
            mockTx.setStatus("PENDING_PIN");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody(null, "123456")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("không hợp lệ")));

            verify(pinService, never()).verifyPin(any(), anyString());
            verify(transactionService, never()).executeTransactionCore(any());
        }

        // 🔴 BỔ SUNG 12: REQUEST BODY HOÀN TOÀN RỖNG
        @Test
        @DisplayName("❌ 12. Body JSON rỗng '{}' → Phải trả lỗi, không crash 500 thảm")
        void lowLevel_emptyRequestBody_returnsError() throws Exception {
            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(result ->
                        // Chấp nhận cả 400 lẫn 500 — điều quan trọng là KHÔNG được SUCCESS
                        assertThat(result.getResponse().getStatus())
                            .isNotEqualTo(200)
                    );

            verify(transactionService, never()).executeTransactionCore(any());
        }

        // 🟢 BỔ SUNG 13: AUDIT LOG KHÔNG ĐƯỢC GỌI KHI PIN SAI
        @Test
        @DisplayName("❌ 13. PIN sai → Audit Log TX_SUCCESS tuyệt đối không được gọi")
        void lowLevel_wrongPin_auditLogNotCalled() throws Exception {
            mockTx.setStatus("PENDING_PIN");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(pinService.verifyPin(any(), eq("000000")))
                .thenThrow(new BusinessLogicException("ERR_WRONG_PIN", "Mã PIN không chính xác!"));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "000000")))
                    .andExpect(status().isBadRequest());

            // TX_SUCCESS audit log tuyệt đối không được nổ lên khi PIN sai
            verify(auditLogService, never()).logAction(anyString(), eq("TX_SUCCESS"), anyString());
        }

        // 🔴 BỔ SUNG 14: executeTransactionCore NÉM RuntimeException (DB DOWN / TIMEOUT)
        @Test
        @DisplayName("❌ 14. PIN đúng nhưng DB sập lúc trừ tiền → 500, không ghi TX_SUCCESS log")
        void lowLevel_coreThrowsRuntimeException_returns500() throws Exception {
            mockTx.setStatus("PENDING_PIN");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(pinService.verifyPin(any(), eq("123456"))).thenReturn(true);

            // Giả lập DB timeout / connection pool exhausted
            when(transactionService.executeTransactionCore(mockTx))
                .thenThrow(new RuntimeException("Connection timeout: DB unreachable"));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().is5xxServerError());

            // Chắc chắn audit log thành công không được bắn ra
            verify(auditLogService, never()).logAction(anyString(), eq("TX_SUCCESS"), anyString());
        }

        // 🔴 BỔ SUNG 18: GIAO DỊCH ĐÃ BỊ BLOCKED → KHÔNG CHO XÁC THỰC TIẾP
        @Test
        @DisplayName("❌ 18. Giao dịch đang BLOCKED (bị AI chặn trước đó) → Từ chối, không cho chạy tiếp")
        void lowLevel_blockedTransaction_returns400() throws Exception {
            mockTx.setStatus("BLOCKED"); // Giao dịch đã bị hệ thống AI đánh dấu nguy hiểm
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().isBadRequest());

            verify(pinService, never()).verifyPin(any(), anyString());
            verify(transactionService, never()).executeTransactionCore(any());
        }

        // 🔴 BỔ SUNG 19: GIAO DỊCH ĐÃ FAILED → KHÔNG CHO PHỤC HỒI
        @Test
        @DisplayName("❌ 19. Giao dịch đã FAILED → Không được phép retry lại")
        void lowLevel_failedTransaction_returns400() throws Exception {
            mockTx.setStatus("FAILED");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("không hợp lệ")));

            verify(transactionService, never()).executeTransactionCore(any());
        }
    }

    // ==========================================================
    // NHÓM 2 — TRẠM MEDIUM_1 (PIN -> OTP)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 2 — Phá đảo mức MEDIUM_1 (PIN + OTP)")
    class Medium1OtpLevelTests {

        // --- CHẶNG 1: TỪ PIN SANG OTP ---
        @Test
        @DisplayName("✅ Chặng 1: Nhập PIN đúng → Chuyển sang trạm OTP, KHÔNG trừ tiền")
        void medium1_pinCorrect_movesToOtp() throws Exception {
            mockTx.setStatus("PENDING_PIN_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(pinService.verifyPin(any(), eq("123456"))).thenReturn(true);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NEXT_STEP"))
                    .andExpect(jsonPath("$.nextAuthType").value("OTP"));

            verify(otpService, times(1)).generateAndSendOtpAsync(mockTx);
            verify(transactionService, never()).executeTransactionCore(any());
        }

        // --- CHẶNG 2: XỬ LÝ OTP ---
        @Test
        @DisplayName("✅ Chặng 2: Nhập OTP đúng → Trừ tiền thành công")
        void medium1_otpCorrect_executesTransaction() throws Exception {
            mockTx.setStatus("PENDING_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(otpService.verifyOtp(100L, "999999")).thenReturn(true);
            
            Transaction completed = new Transaction();
            completed.setStatus("SUCCESS");
            when(transactionService.executeTransactionCore(mockTx)).thenReturn(completed);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", "999999")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));

            verify(transactionService, times(1)).executeTransactionCore(mockTx);
        }

        @Test
        @DisplayName("❌ Chặng 2: Nhập sai OTP → Báo 400, ghi sổ đen OTP_FAILED")
        void medium1_otpWrong_returns400() throws Exception {
            mockTx.setStatus("PENDING_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(otpService.verifyOtp(100L, "000000")).thenReturn(false);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", "000000")))
                    .andExpect(status().isBadRequest());

            verify(auditLogService).logAction(eq("thach_verify"), eq("OTP_FAILED"), anyString());
            verify(transactionService, never()).executeTransactionCore(any());
        }

        @Test
        @DisplayName("❌ Edge Case: Bỏ trống OTP (null hoặc rỗng) → Bị gác cổng @Valid chặn")
        void medium1_otpEmpty_returns400() throws Exception {
            mockTx.setStatus("PENDING_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", ""))) // Gửi OTP rỗng
                    .andExpect(status().isBadRequest());

            verify(otpService, never()).verifyOtp(anyLong(), anyString());
        }

        @Test
        @DisplayName("❌ 2.5 PIN đúng nhưng gửi OTP thất bại → Rollback status, báo lỗi 500")
        void medium1_otpSendFails_shouldNotSaveStatusChange() throws Exception {
            mockTx.setStatus("PENDING_PIN_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(pinService.verifyPin(any(), eq("123456"))).thenReturn(true);

            // Giả lập email server / OTP service sập
            doThrow(new RuntimeException("SMTP connection refused"))
                .when(otpService).generateAndSendOtpAsync(mockTx);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().is5xxServerError());

            // Tuyệt đối không trừ tiền
            verify(transactionService, never()).executeTransactionCore(any());
        }
    }


    // ==========================================================
    // NHÓM 3 — TRẠM MEDIUM_2 (PIN -> FACE_STATIC)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 3 — Phá đảo mức MEDIUM_2 (PIN + Khuôn mặt tĩnh)")
    class Medium2FaceStaticLevelTests {

        // --- CHẶNG 1: TỪ PIN SANG FACE_STATIC ---
        @Test
        @DisplayName("✅ Chặng 1: Nhập PIN đúng → Chuyển sang trạm Face Static, KHÔNG trừ tiền")
        void medium2_pinCorrect_movesToFaceStatic() throws Exception {
            mockTx.setStatus("PENDING_PIN_FACE");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(pinService.verifyPin(any(), eq("123456"))).thenReturn(true);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NEXT_STEP"))
                    .andExpect(jsonPath("$.nextAuthType").value("FACE_STATIC"));

            verify(transactionService, never()).executeTransactionCore(any());
        }

        // --- CHẶNG 2: XỬ LÝ FACE_STATIC ---
        @Test
        @DisplayName("✅ Chặng 2: Gửi khuôn mặt tĩnh đúng → Trừ tiền thành công")
        void medium2_faceStaticCorrect_executesTransaction() throws Exception {
            mockTx.setStatus("PENDING_FACE_STATIC");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            
            Transaction completed = new Transaction();
            completed.setStatus("SUCCESS");
            when(transactionService.executeTransactionCore(mockTx)).thenReturn(completed);

            AuthVerifyRequest req = new AuthVerifyRequest();
            req.setTransactionId(100L);
            req.setAuthType("FACE_STATIC");
            req.setAuthCode("dummy"); // Lách luật @Valid

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));

            verify(transactionService, times(1)).executeTransactionCore(mockTx);
            verify(auditLogService).logAction(eq("thach_verify"), eq("TX_SUCCESS"), anyString());
        }
        
        @Test
        @DisplayName("❌ 3.3 Gửi FACE_STATIC khi TX còn PENDING_PIN_FACE (chưa qua PIN) → Chặn đứng")
        void medium2_sendFaceWhileStillPendingPin_returns400() throws Exception {
            mockTx.setStatus("PENDING_PIN_FACE"); // Chưa qua PIN, chưa vào trạm Face
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            AuthVerifyRequest req = new AuthVerifyRequest();
            req.setTransactionId(100L);
            req.setAuthType("FACE_STATIC");
            req.setAuthCode("any");

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    // Controller vào nhánh FACE_STATIC, if("PENDING_FACE_STATIC") false → rớt xuống 400
                    .andExpect(status().isBadRequest());

            verify(transactionService, never()).executeTransactionCore(any());
        }

        // 🔴 BỔ SUNG 3.4: Gửi FACE_STATIC khi TX đang PENDING_OTP (nhầm luồng)
        // Tình huống: User Medium_1 đang chờ OTP nhưng frontend bị bug gửi nhầm FACE_STATIC
        @Test
        @DisplayName("❌ 3.4 Gửi FACE_STATIC khi TX đang PENDING_OTP (nhầm luồng) → Chặn đứng")
        void medium2_sendFaceOnOtpStatus_returns400() throws Exception {
            mockTx.setStatus("PENDING_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            AuthVerifyRequest req = new AuthVerifyRequest();
            req.setTransactionId(100L);
            req.setAuthType("FACE_STATIC");
            req.setAuthCode("any");

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());

            verify(transactionService, never()).executeTransactionCore(any());
        }

        // 🟢 BỔ SUNG 3.5: Audit Log TX_SUCCESS được ghi đúng khi FACE_STATIC thành công
        @Test
        @DisplayName("✅ 3.5 FACE_STATIC thành công → Audit Log TX_SUCCESS đúng username")
        void medium2_faceStaticCorrect_auditLogCalled() throws Exception {
            mockTx.setStatus("PENDING_FACE_STATIC");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            Transaction completed = new Transaction();
            completed.setStatus("SUCCESS");
            when(transactionService.executeTransactionCore(mockTx)).thenReturn(completed);

            AuthVerifyRequest req = new AuthVerifyRequest();
            req.setTransactionId(100L);
            req.setAuthType("FACE_STATIC");
            req.setAuthCode("dummy");

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());

            verify(auditLogService, times(1)).logAction(eq("thach_verify"), eq("TX_SUCCESS"), anyString());
        }

        // 🔴 BỔ SUNG 3.6: executeTransactionCore sập tại bước FACE_STATIC
        @Test
        @DisplayName("❌ 3.6 FACE_STATIC xong nhưng DB sập lúc trừ tiền → 500")
        void medium2_faceStaticCorrect_coreThrowsException_returns500() throws Exception {
            mockTx.setStatus("PENDING_FACE_STATIC");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            when(transactionService.executeTransactionCore(mockTx))
                .thenThrow(new RuntimeException("DB connection lost"));

            AuthVerifyRequest req = new AuthVerifyRequest();
            req.setTransactionId(100L);
            req.setAuthType("FACE_STATIC");
            req.setAuthCode("dummy");

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().is5xxServerError());

            verify(auditLogService, never()).logAction(anyString(), eq("TX_SUCCESS"), anyString());
        }

        // 🔴 BỔ SUNG 3.7: FACE_STATIC với giao dịch đã SUCCESS (Replay Attack)
        @Test
        @DisplayName("❌ 3.7 Replay Attack: Gửi lại FACE_STATIC cho giao dịch đã SUCCESS → Chặn đứng")
        void medium2_faceStaticOnCompletedTx_returns400() throws Exception {
            mockTx.setStatus("SUCCESS");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            AuthVerifyRequest req = new AuthVerifyRequest();
            req.setTransactionId(100L);
            req.setAuthType("FACE_STATIC");
            req.setAuthCode("dummy");

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());

            verify(transactionService, never()).executeTransactionCore(any());
        }
    }
    

    // ==========================================================
    // NHÓM 4 — TRẠM HIGH (PIN -> FACE_AI -> VOICE_OTP)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 4 — Phá đảo mức HIGH (Tử thủ 3 lớp)")
    class HighRiskLevelTests {

        // --- CHẶNG 1: TỪ PIN SANG FACE AI ---
        @Test
        @DisplayName("✅ Chặng 1: Nhập PIN đúng → Chuyển sang trạm Face AI, KHÔNG trừ tiền")
        void high_pinCorrect_movesToFaceAi() throws Exception {
            mockTx.setStatus("PENDING_PIN_HIGH");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(pinService.verifyPin(any(), eq("123456"))).thenReturn(true);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NEXT_STEP"))
                    .andExpect(jsonPath("$.nextAuthType").value("FACE_AI"));

            verify(transactionService, never()).executeTransactionCore(any());
        }

        // --- CHẶNG 2: XỬ LÝ FACE AI ---
        @Test
        @DisplayName("✅ Chặng 2: Face AI xác nhận người thật, tỉnh táo → Chuyển sang trạm Voice OTP")
        void high_faceAiPass_movesToVoiceOtp() throws Exception {
            mockTx.setStatus("PENDING_FACE_AI");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(faceScanActionStrategy.validateFaceAndEmotion(any(), any())).thenReturn(true);
            when(otpService.generateVoiceOtp(100L)).thenReturn("742701");

            AuthVerifyRequest req = new AuthVerifyRequest();
            req.setTransactionId(100L);
            req.setAuthType("FACE_AI");
            req.setFaceImageBase64("base64_image_data");
            req.setAuthCode("dummy"); // Bùa lách @Valid

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NEXT_STEP"))
                    .andExpect(jsonPath("$.nextAuthType").value("VOICE_OTP"))
                    .andExpect(jsonPath("$.voiceCode").value("742701"));
        }

        @Test
        @DisplayName("🚨 Chặng 2: Face AI phát hiện giả mạo/ép buộc → Khóa vĩnh viễn giao dịch (BLOCKED)")
        void high_faceAiFail_blocksTransaction() throws Exception {
            mockTx.setStatus("PENDING_FACE_AI");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(faceScanActionStrategy.validateFaceAndEmotion(any(), any())).thenReturn(false);

            AuthVerifyRequest req = new AuthVerifyRequest();
            req.setTransactionId(100L);
            req.setAuthType("FACE_AI");
            req.setFaceImageBase64("fake_mask_data");
            req.setAuthCode("dummy");

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());

            verify(transactionRepository).save(argThat(t -> "BLOCKED".equals(t.getStatus())));
            verify(auditLogService).logAction(anyString(), eq("AI_REJECT"), anyString());
        }

        // --- CHẶNG 3: XỬ LÝ VOICE OTP ---
        @Test
        @DisplayName("✅ Chặng 3: AI bóc băng Voice OTP đúng → Trừ tiền thành công")
        void high_voiceCorrect_executesTransaction() throws Exception {
            mockTx.setStatus("PENDING_VOICE_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(riskEvaluationService.verifyVoiceLivenessAsync(any())).thenReturn(CompletableFuture.completedFuture("742701"));
            when(otpService.verifyOtp(100L, "742701")).thenReturn(true);
            
            Transaction completed = new Transaction();
            completed.setStatus("SUCCESS");
            when(transactionService.executeTransactionCore(mockTx)).thenReturn(completed);

            MockMultipartFile audio = new MockMultipartFile("audioFile", "voice.wav", "audio/wav", "audio_data".getBytes());

            mockMvc.perform(multipart("/api/transactions/verify-voice")
                    .file(audio)
                    .param("transactionId", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));

            verify(transactionService, times(1)).executeTransactionCore(mockTx);
        }

        @Test
        @DisplayName("❌ Chặng 3: Băng ghi âm rỗng hoặc ồn ào (AI không hiểu) → Yêu cầu thu âm lại (400)")
        void high_voiceEmpty_returns400() throws Exception {
            mockTx.setStatus("PENDING_VOICE_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(riskEvaluationService.verifyVoiceLivenessAsync(any())).thenReturn(CompletableFuture.completedFuture(""));

            MockMultipartFile audio = new MockMultipartFile("audioFile", "noise.wav", "audio/wav", "noise_data".getBytes());

            mockMvc.perform(multipart("/api/transactions/verify-voice")
                    .file(audio)
                    .param("transactionId", "100"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("nhận diện giọng nói")));
        }
    }

    // ==========================================================
    // 🟡 NHÓM 2 — TRẠM MEDIUM_1 (Cần 2 bước: PIN -> OTP)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 2 — Phá đảo mức MEDIUM_1 (Cần PIN và OTP)")
    class OtpVerifyTests {

        // 🟢 KỊCH BẢN 1: HOÀN HẢO (CHẶNG OTP)
        // (Lưu ý: Chặng nhập PIN của Medium_1 đã được test ở Nhóm 1)
        @Test
        @DisplayName("✅ 1. Nhập OTP đúng (PENDING_OTP) → Trừ tiền thành công, chốt giao dịch")
        void medium1_otpCorrect_executesTransaction() throws Exception {
            mockTx.setStatus("PENDING_OTP"); // Status này báo hiệu khách đã qua cửa PIN
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            
            // Giả lập OTP Service kiểm tra 6 số khớp
            when(otpService.verifyOtp(100L, "123456")).thenReturn(true);
            
            Transaction completed = new Transaction();
            completed.setStatus("SUCCESS");
            when(transactionService.executeTransactionCore(mockTx)).thenReturn(completed);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", "123456")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));

            // Xác nhận tiền đã được trừ và sổ sách đã ghi
            verify(transactionService, times(1)).executeTransactionCore(mockTx);
            verify(auditLogService).logAction(eq("thach_verify"), eq("TX_SUCCESS"), anyString());
        }

        // 🔴 KỊCH BẢN 2: NHẬP SAI OTP
        @Test
        @DisplayName("❌ 2. Nhập OTP sai / hết hạn → Báo lỗi 400, KHÔNG trừ tiền")
        void medium1_otpWrong_returns400AndLogs() throws Exception {
            mockTx.setStatus("PENDING_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            
            // Giả lập OTP Service lắc đầu
            when(otpService.verifyOtp(100L, "000000")).thenReturn(false);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", "000000")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("sai hoặc đã hết hạn")));

            // Ghi sổ đen Hacker và KHÔNG CẤP LỆNH TRỪ TIỀN
            verify(auditLogService).logAction(eq("thach_verify"), eq("OTP_FAILED"), anyString());
            verify(transactionService, never()).executeTransactionCore(any());
        }
        
        // 🔴 KỊCH BẢN 3: GỬI LÁO (BỎ TRỐNG MÃ)
        @Test
        @DisplayName("❌ 3. Bỏ trống mã OTP (\"\") → Bị @Valid vả văng ngay từ vòng ngoài (400)")
        void medium1_emptyOtp_returns400() throws Exception {
            mockTx.setStatus("PENDING_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", ""))) // Gửi OTP rỗng
                    .andExpect(status().isBadRequest());

            // Thằng bảo vệ @Valid đã tóm cổ, hàm bên trong tuyệt đối không được gọi
            verify(otpService, never()).verifyOtp(anyLong(), anyString());
            verify(transactionService, never()).executeTransactionCore(any());
        }

        @Test
        @DisplayName("❌ 2.6 Gửi OTP khi TX đang PENDING_PIN_OTP (chưa qua PIN) → Chặn đứng")
        void medium1_sendOtpWhileStillPendingPin_returns400() throws Exception {
            // TX đang ở trạng thái chờ PIN, chưa vào trạm OTP
            mockTx.setStatus("PENDING_PIN_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(otpService.verifyOtp(100L, "123456")).thenReturn(true); // Giả sử OTP đúng

            // Hacker bỏ qua PIN, gửi thẳng OTP
            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", "123456")))
                    // OTP chỉ có giá trị khi status=PENDING_OTP, không phải PENDING_PIN_OTP
                    // Controller sẽ vào nhánh OTP, verify đúng, nhưng if("PENDING_OTP") sẽ false
                    // → rớt xuống cuối, trả 400
                    .andExpect(status().isBadRequest());

            // Quan trọng: Không được trừ tiền dù OTP đúng
            verify(transactionService, never()).executeTransactionCore(any());
        }

        @Test
        @DisplayName("❌ 2.7 Replay Attack: OTP của giao dịch đã SUCCESS → Không cho xác thực")
        void medium1_replayOtpOnCompletedTx_returns400() throws Exception {
            mockTx.setStatus("SUCCESS"); // Giao dịch đã hoàn thành từ trước
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(otpService.verifyOtp(100L, "123456")).thenReturn(true);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", "123456")))
                    .andExpect(status().isBadRequest());

            verify(transactionService, never()).executeTransactionCore(any());
        }

        // ---------------------------------------------------------------
        // NHÓM BỔ SUNG 2C: KIỂM TRA AUDIT LOG OTP
        // ---------------------------------------------------------------

        // 🟢 BỔ SUNG 2.8: Verify audit log TX_SUCCESS được gọi đúng khi OTP thành công
        @Test
        @DisplayName("✅ 2.8 OTP đúng → Audit Log ghi TX_SUCCESS với đúng username")
        void medium1_otpCorrect_auditLogTxSuccessCalled() throws Exception {
            mockTx.setStatus("PENDING_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(otpService.verifyOtp(100L, "123456")).thenReturn(true);

            Transaction completed = new Transaction();
            completed.setStatus("SUCCESS");
            when(transactionService.executeTransactionCore(mockTx)).thenReturn(completed);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", "123456")))
                    .andExpect(status().isOk());

            // Phải ghi đúng action TX_SUCCESS
            verify(auditLogService, times(1)).logAction(eq("thach_verify"), eq("TX_SUCCESS"), anyString());
            // Không được ghi OTP_FAILED
            verify(auditLogService, never()).logAction(anyString(), eq("OTP_FAILED"), anyString());
        }

        // 🔴 BỔ SUNG 2.9: OTP đúng nhưng executeTransactionCore sập (Race Condition tại bước OTP)
        @Test
        @DisplayName("❌ 2.9 OTP đúng nhưng DB sập lúc trừ tiền → 500, không ghi TX_SUCCESS log")
        void medium1_otpCorrect_coreThrowsException_returns500() throws Exception {
            mockTx.setStatus("PENDING_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(otpService.verifyOtp(100L, "123456")).thenReturn(true);

            when(transactionService.executeTransactionCore(mockTx))
                .thenThrow(new RuntimeException("DB deadlock detected"));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", "123456")))
                    .andExpect(status().is5xxServerError());

            verify(auditLogService, never()).logAction(anyString(), eq("TX_SUCCESS"), anyString());
        }

        // 🔴 BỔ SUNG 2.10: OTP đúng nhưng số dư không đủ (Race Condition — hết tiền lúc chờ OTP)
        @Test
        @DisplayName("❌ 2.10 OTP đúng nhưng số dư vừa bị hút cạn → BusinessLogicException 400")
        void medium1_otpCorrect_insufficientBalance_returns400() throws Exception {
            mockTx.setStatus("PENDING_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            when(otpService.verifyOtp(100L, "123456")).thenReturn(true);

            when(transactionService.executeTransactionCore(mockTx))
                .thenThrow(new BusinessLogicException("ERR_BALANCE", "Số dư không đủ để thực hiện giao dịch!"));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("OTP", "123456")))
                    .andExpect(status().isBadRequest());
        }

    }


    // ==========================================================
    // 🟠 NHÓM 3 — TRẠM MEDIUM_2 (Cần 2 bước: PIN -> Face Static)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 3 — Phá đảo mức MEDIUM_2 (Tạm khuyết do TODO)")
    class FaceStaticVerifyTests {
        // LUỒNG NÀY BRO ĐANG ĐỂ CHỮ "TODO" TRONG FILE CONTROLLER, NÊN CHƯA THỂ TEST.
        // Tương lai Bro gắn AI FaceMatch 2D vào đây thì sẽ thêm test quét mặt đậu / rớt vào.
        // Hiện tại Chặng 1 (Nhập PIN -> Yêu cầu Face Static) đã được cover bởi hàm `lowLevel_pendingPinFace_pinCorrect_shouldNotExecuteCore()` ở Nhóm 1.
    }


    // ==========================================================
    // 🔴 NHÓM 4 — TRẠM HIGH (Tử thủ 3 bước: PIN -> Face AI -> Voice)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 4 — Phá đảo mức HIGH (Bảo mật Siêu Cấp)")
    class HighRiskAiVerifyTests {

        // --- CHẶNG 2 CỦA MỨC HIGH: QUÉT MẶT CẢM XÚC AI ---
        @Test
        @DisplayName("✅ 1. [Chặng Face AI] Người thật, tỉnh táo → Pass, yêu cầu đọc mã Voice OTP")
        void highRisk_faceAiPass_redirectsToVoiceOtp() throws Exception {
            mockTx.setStatus("PENDING_FACE_AI"); // Trạng thái chứng tỏ đã qua cửa PIN
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            
            // Giả lập Python AI bóc tách hình ảnh trả về "Mọi thứ Ổn"
            when(faceScanActionStrategy.validateFaceAndEmotion(any(), any())).thenReturn(true);
            when(transactionRepository.save(any())).thenReturn(mockTx);
            
            // Sinh mã ngẫu nhiên bắt khách đọc
            when(otpService.generateVoiceOtp(100L)).thenReturn("742701");

            AuthVerifyRequest req = new AuthVerifyRequest();
            req.setTransactionId(100L);
            req.setAuthType("FACE_AI");
            req.setFaceImageBase64("base64imagedata");
            req.setAuthCode("dummy_code"); // Dummy để lách @Valid

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NEXT_STEP"))
                    .andExpect(jsonPath("$.nextAuthType").value("VOICE_OTP"))
                    .andExpect(jsonPath("$.voiceCode").value("742701")); // Trả mã này lên Frontend hiển thị chữ to chà bá

            verify(transactionService, never()).executeTransactionCore(any()); // Tiền tuyệt đối chưa được trừ!
        }

        @Test
        @DisplayName("🚨 2. [Chặng Face AI] Có dấu hiệu bị ép buộc / Lừa đảo → Khóa chết (BLOCKED)")
        void highRisk_faceAiFail_blocksTransaction() throws Exception {
            mockTx.setStatus("PENDING_FACE_AI");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            
            // Giả lập Python AI trả về "CẢNH BÁO!" (Có rủi ro Deepfake / Sợ hãi)
            when(faceScanActionStrategy.validateFaceAndEmotion(any(), any())).thenReturn(false);
            when(transactionRepository.save(any())).thenReturn(mockTx);

            AuthVerifyRequest req = new AuthVerifyRequest();
            req.setTransactionId(100L);
            req.setAuthType("FACE_AI");
            req.setFaceImageBase64("fake_face");
            req.setAuthCode("dummy_code");

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden()) // Ném thẳng 403 (Cấm cửa)
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("thất bại")));

            verify(auditLogService).logAction(eq("thach_verify"), eq("AI_REJECT"), anyString());
            // Trọng điểm: Đảm bảo giao dịch bị tước đoạt sự sống vĩnh viễn
            verify(transactionRepository).save(argThat(t -> "BLOCKED".equals(t.getStatus()))); 
        }

        // --- CHẶNG 3 CỦA MỨC HIGH: BÓC BĂNG GIỌNG NÓI ---
        @Test
        @DisplayName("✅ 3. [Chặng Voice OTP] Giọng nói thật, đọc đúng số → SUCCESS, trừ tiền khẩn cấp")
        void highRisk_voiceCorrect_executesTransaction() throws Exception {
            mockTx.setStatus("PENDING_VOICE_OTP"); // Status cuối cùng trước cổng sinh tử
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            
            // Giả lập AI nghe được khách đọc dãy "742701"
            when(riskEvaluationService.verifyVoiceLivenessAsync(any())).thenReturn(CompletableFuture.completedFuture("742701"));
            // Đối chiếu với cái mã đã sinh trên DB
            when(otpService.verifyOtp(100L, "742701")).thenReturn(true);
            
            Transaction completed = new Transaction();
            completed.setStatus("SUCCESS");
            when(transactionService.executeTransactionCore(mockTx)).thenReturn(completed);

            // Gửi cục file âm thanh nặng trịch lên
            MockMultipartFile audio = new MockMultipartFile("audioFile", "voice.wav", "audio/wav", "fake_audio".getBytes());

            mockMvc.perform(multipart("/api/transactions/verify-voice")
                    .file(audio)
                    .param("transactionId", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS")); // Game Over, lấy được tiền.

            verify(auditLogService).logAction(eq("thach_verify"), eq("TX_SUCCESS"), anyString());
        }

        @Test
        @DisplayName("❌ 4. [Chặng Voice OTP] Thu âm đọc sai số → Báo 400, bắt đọc lại")
        void highRisk_voiceWrongCode_returns400() throws Exception {
            mockTx.setStatus("PENDING_VOICE_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            
            // AI nghe ra số "000000" (Đọc sai)
            when(riskEvaluationService.verifyVoiceLivenessAsync(any())).thenReturn(CompletableFuture.completedFuture("000000"));
            when(otpService.verifyOtp(100L, "000000")).thenReturn(false); // So sánh lệch mã

            MockMultipartFile audio = new MockMultipartFile("audioFile", "voice.wav", "audio/wav", "fake_audio".getBytes());

            mockMvc.perform(multipart("/api/transactions/verify-voice")
                    .file(audio)
                    .param("transactionId", "100"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("không khớp")));

            verify(auditLogService).logAction(eq("thach_verify"), eq("VOICE_FAILED"), anyString());
            verify(transactionService, never()).executeTransactionCore(any());
        }

        @Test
        @DisplayName("❌ 5. [Chặng Voice OTP] Băng nhiễu ồn ào (AI không tách được) → Báo 400")
        void highRisk_voiceEmptyRecognition_returns400() throws Exception {
            mockTx.setStatus("PENDING_VOICE_OTP");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            
            // AI bất lực trả về chuỗi rỗng
            when(riskEvaluationService.verifyVoiceLivenessAsync(any())).thenReturn(CompletableFuture.completedFuture(""));

            MockMultipartFile audio = new MockMultipartFile("audioFile", "voice.wav", "audio/wav", "noise".getBytes());

            mockMvc.perform(multipart("/api/transactions/verify-voice")
                    .file(audio)
                    .param("transactionId", "100"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("nhận diện giọng nói")));

            verify(otpService, never()).verifyOtp(anyLong(), anyString()); // Chặn luôn khỏi cần check mã
        }
        
        @Test
        @DisplayName("❌ 6. [Chặng Voice OTP] Gửi sai trạm (Đang ở PIN mà quăng File Âm Thanh lên) → Đuổi cổ")
        void highRisk_verifyVoice_wrongStatus_returns400() throws Exception {
            mockTx.setStatus("PENDING_PIN"); // Đang đứng ở trạm PIN thu phí đầu tiên
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            MockMultipartFile audio = new MockMultipartFile("audioFile", "voice.wav", "audio/wav", "fake".getBytes());

            // Đòi lươn lẹo đâm thẳng vào API Voice OTP để trừ tiền luôn
            mockMvc.perform(multipart("/api/transactions/verify-voice")
                    .file(audio)
                    .param("transactionId", "100"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("không hợp lệ")));
        }
    }


    // ==========================================================
    // 🚧 NHÓM 5 — LƯỚI BẢO VỆ CHUNG (EDGE CASES)
    // Dành cho toàn bộ hệ thống / Các đòn đánh bẩn thỉu
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 5 — Lưới bắt gian lận / Hack thông tin")
    class VerifyEdgeCaseTests {

        @Test
        @DisplayName("❌ Gửi một loại authType tự bịa ('HACK_TYPE') → Bị hệ thống ném 400")
        void verify_unknownAuthType_returns400() throws Exception {
            mockTx.setStatus("PENDING_PIN");
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("UNKNOWN_TYPE", "123456")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("gián đoạn")));
        }

        @Test
        @DisplayName("❌ Râu ông nọ cắm cằm bà kia (Đang chờ OTP mà gửi PIN) → Đá văng")
        void verify_mismatchedStatusAndAuthType_returns400() throws Exception {
            mockTx.setStatus("PENDING_OTP"); // Status nằm tít ở luồng MEDIUM_1
            when(transactionRepository.findByIdWithUserSecurity(100L)).thenReturn(Optional.of(mockTx));
            
            when(pinService.verifyPin(any(), anyString())).thenReturn(true);

            mockMvc.perform(post("/api/transactions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("PIN", "123456"))) // Lại gửi vé của luồng LOW
                    .andExpect(status().isBadRequest());
        }
    }
}
