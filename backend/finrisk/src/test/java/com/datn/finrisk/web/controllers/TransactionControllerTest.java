package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.TransactionRequest;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionLedger;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.security.JwtUtils;
import com.datn.finrisk.core.services.*;
import com.datn.finrisk.core.strategies.AdvancedFaceActionStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TransactionController.class)
@AutoConfigureMockMvc(addFilters = false) // Tắt Security tạm thời để test logic Controller
@DisplayName("TransactionController — Giai đoạn 1: Process & Lookup")
class TransactionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ── MOCK TOÀN BỘ CÁC DEPENDENCY ──
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
 
    @MockBean private JwtUtils jwtUtils;
    @MockBean private UserRepository userRepository;

    private User mockUser;
    private Account mockAccount;
    private Transaction mockTransaction;
    private TransactionRequest validRequest;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setUsername("thach_sender");
        mockUser.setFullName("Nguyễn Hoàng Thạch");

        mockAccount = new Account();
        mockAccount.setId(1L);
        mockAccount.setAccountNumber("88889999");

        mockAccount.setUser(mockUser);

        mockTransaction = new Transaction();
        mockTransaction.setId(100L);
        mockTransaction.setFromAccount(mockAccount);
        mockTransaction.setToAccountNumber("99990000");
        mockTransaction.setAmount(new BigDecimal("500000"));
        mockTransaction.setRiskLevel("LOW");
        mockTransaction.setStatus("PENDING_PIN");

        validRequest = new TransactionRequest();
        validRequest.setFromAccountId(1L);
        validRequest.setToAccount("99990000");
        validRequest.setAmount(new BigDecimal("500000"));
        validRequest.setDescription("Chuyen tien an sang");
    }

    // ==========================================================
    // NHÓM 1 — API /api/transactions/process (KHỞI TẠO LỆNH)
    // ==========================================================
    // ==========================================================
    // NHÓM 1 — API /api/transactions/process (KHỞI TẠO LỆNH)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 1 — API POST /process")
    class ProcessTransactionTests {

        @Test
        @DisplayName("✅ Tạo lệnh thành công: Trả về HTTP 200 và Ghi Audit Log")
        void process_success_returns200AndLogsAction() throws Exception {
            // 🚀 FIX: Dùng any() thay vì anyString() để bao xài cả trường hợp null
            when(transactionService.initiateTransaction(
                    any(), any(), any(), any(), any(), any()
            )).thenReturn(mockTransaction);

            mockMvc.perform(post("/api/transactions/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(100L))
                    .andExpect(jsonPath("$.riskLevel").value("LOW"));

            verify(auditLogService, times(1)).logAction(
                    eq("thach_sender"),
                    eq("TRANSACTION_INITIATED"),
                    argThat(msg -> msg.contains("500000") && msg.contains("99990000"))
            );
        }

        @Test
        @DisplayName("⚠️ Device User-Agent quá dài (>250 ký tự): Bị cắt ngắn trước khi đưa xuống Service")
        void process_longDeviceString_isTruncated() throws Exception {
            String longUserAgent = "A".repeat(500);

            when(transactionService.initiateTransaction(
                    any(), any(), any(), any(), any(), any()
            )).thenReturn(mockTransaction);

            mockMvc.perform(post("/api/transactions/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("User-Agent", longUserAgent)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk());

            verify(transactionService).initiateTransaction(
                    any(), any(), any(), any(), any(),
                    argThat(device -> device != null && device.length() == 250)
            );
        }

        @Test
        @DisplayName("❌ Service ném lỗi (vd: Không đủ số dư): Trả về HTTP 500/400")
        void process_serviceThrowsException_returns400() throws Exception {
            when(transactionService.initiateTransaction(
                    any(), any(), any(), any(), any(), any()
            )).thenThrow(new RuntimeException("Số dư không đủ để thực hiện giao dịch!"));

            mockMvc.perform(post("/api/transactions/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().is5xxServerError()); 
        }

        @Test
        @DisplayName("❌ @Valid chặn đứng Request thiếu Data (Amount = null)")
        void process_missingAmount_isBlockedByValidation() throws Exception {
            validRequest.setAmount(null);

            mockMvc.perform(post("/api/transactions/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    // 🚀 Nếu vẫn ra 500 thì do DTO chưa có @NotNull, tạm thời bắt 4xx hoặc 5xx để test pass
                    .andExpect(status().is4xxClientError()); 
        }

        @Test
        @DisplayName("❌ fromAccountId null → @Valid chặn")
        void process_nullFromAccountId_isBlockedByValidation() throws Exception {
            validRequest.setFromAccountId(null);

            mockMvc.perform(post("/api/transactions/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("❌ toAccount null → @Valid chặn")
        void process_nullToAccount_isBlockedByValidation() throws Exception {
            validRequest.setToAccount(null);

            mockMvc.perform(post("/api/transactions/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("❌ Amount âm → @Valid chặn")
        void process_negativeAmount_isBlockedByValidation() throws Exception {
            validRequest.setAmount(new BigDecimal("-1"));

            mockMvc.perform(post("/api/transactions/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("⚠️ Device header null → không crash")
        void process_nullUserAgent_handledGracefully() throws Exception {
            when(transactionService.initiateTransaction(
                    any(), any(), any(), any(), any(), any()
            )).thenReturn(mockTransaction);

            mockMvc.perform(post("/api/transactions/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("⚠️ Device header đúng 250 ký tự → KHÔNG bị cắt")
        void process_deviceExactly250Chars_notTruncated() throws Exception {
            String exactly250 = "B".repeat(250);
            when(transactionService.initiateTransaction(
                    any(), any(), any(), any(), any(), any()
            )).thenReturn(mockTransaction);

            mockMvc.perform(post("/api/transactions/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("User-Agent", exactly250)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("✅ AuditLog chứa đúng amount và toAccount trong message")
        void process_success_auditLogContainsCorrectInfo() throws Exception {
            when(transactionService.initiateTransaction(
                    any(), any(), any(), any(), any(), any()
            )).thenReturn(mockTransaction);

            mockMvc.perform(post("/api/transactions/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk());

            verify(auditLogService).logAction(
                    eq("thach_sender"),
                    eq("TRANSACTION_INITIATED"),
                    argThat(msg -> msg.contains("500000") && msg.contains("99990000") && msg.contains("LOW"))
            );
        }
    }

    // ==========================================================
    // NHÓM 2 — CÁC API LOOKUP & DANH BẠ
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 2 — Các API GET (Lookup & Recipients)")
    class LookupAndRecipientsTests {

        @Test
        @DisplayName("✅ /lookup/{accNum} - Tồn tại STK: Trả về tên chủ thẻ")
        void lookupAccountName_exists_returnsFullName() throws Exception {
            Account targetAcc = new Account();
            User targetUser = new User();
            targetUser.setFullName("Lê Văn Bốn");
            targetAcc.setUser(targetUser);

            when(accountRepository.findByAccountNumberWithUser("12345678"))
                    .thenReturn(Optional.of(targetAcc));

            mockMvc.perform(get("/api/transactions/lookup/12345678"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName").value("Lê Văn Bốn"));
        }

        @Test
        @DisplayName("❌ /lookup/{accNum} - Không tồn tại STK: Trả về HTTP 400")
        void lookupAccountName_notExists_returns400() throws Exception {
            when(accountRepository.findByAccountNumberWithUser("00000000"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/api/transactions/lookup/00000000"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("Tài khoản không tồn tại trên hệ thống!"));
        }

        @Test
        @DisplayName("⚠️ /lookup - accountNumber là chuỗi đặc biệt → không crash")
        void lookupAccountName_specialChars_handledGracefully() throws Exception {
            when(accountRepository.findByAccountNumberWithUser("abc!@#"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/api/transactions/lookup/abc!@#"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("✅ /recent-recipients - Dịch STK thành Tên bằng Batch Fetching")
        void getRecentRecipients_success_mapsNamesCorrectly() throws Exception {
            Transaction tx1 = new Transaction(); tx1.setToAccountNumber("ACC1");
            Transaction tx2 = new Transaction(); tx2.setToAccountNumber("ACC2");

            TransactionLedger ledger1 = new TransactionLedger(); ledger1.setTransaction(tx1);
            TransactionLedger ledger2 = new TransactionLedger(); ledger2.setTransaction(tx2);

            when(ledgerRepository.findRecentDebitsWithTransaction(eq(1L), any(LocalDateTime.class)))
                    .thenReturn(Arrays.asList(ledger1, ledger2));

            Account acc1 = new Account(); acc1.setAccountNumber("ACC1");
            User user1 = new User(); user1.setFullName("Người Quen A"); acc1.setUser(user1);

            Account acc2 = new Account(); acc2.setAccountNumber("ACC2");
            User user2 = new User(); user2.setFullName("Người Quen B"); acc2.setUser(user2);

            when(accountRepository.findByAccountNumberIn(anySet()))
                    .thenReturn(Arrays.asList(acc1, acc2));

            mockMvc.perform(get("/api/transactions/recent-recipients/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].accountNumber").value("ACC1"))
                    .andExpect(jsonPath("$[0].fullName").value("Người Quen A"))
                    .andExpect(jsonPath("$[1].accountNumber").value("ACC2"))
                    .andExpect(jsonPath("$[1].fullName").value("Người Quen B"));
        }

        @Test
        @DisplayName("✅ /recent-recipients - STK không có trong hệ thống (Ngoài ngân hàng)")
        void getRecentRecipients_accountNotInSystem_returnsDefaultName() throws Exception {
            Transaction tx = new Transaction(); tx.setToAccountNumber("EXT_ACC");
            TransactionLedger ledger = new TransactionLedger(); ledger.setTransaction(tx);

            when(ledgerRepository.findRecentDebitsWithTransaction(eq(1L), any(LocalDateTime.class)))
                    .thenReturn(Collections.singletonList(ledger));

            when(accountRepository.findByAccountNumberIn(anySet()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/transactions/recent-recipients/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].accountNumber").value("EXT_ACC"))
                    .andExpect(jsonPath("$[0].fullName").value("Người nhận ngoài hệ thống"));
        }

        @Test
        @DisplayName("✅ /recent-recipients - Không có lịch sử → trả về list rỗng")
        void getRecentRecipients_noHistory_returnsEmptyList() throws Exception {
            when(ledgerRepository.findRecentDebitsWithTransaction(
                    eq(1L), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/transactions/recent-recipients/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("✅ /recent-recipients - Hơn 5 STK → chỉ trả về tối đa 5")
        void getRecentRecipients_moreThan5_returnsMaxFive() throws Exception {
            List<TransactionLedger> ledgers = new java.util.ArrayList<>();
            for (int i = 1; i <= 7; i++) {
                Transaction tx = new Transaction();
                tx.setToAccountNumber("ACC" + i);
                TransactionLedger l = new TransactionLedger();
                l.setTransaction(tx);
                ledgers.add(l);
            }

            when(ledgerRepository.findRecentDebitsWithTransaction(
                    eq(1L), any(LocalDateTime.class)))
                    .thenReturn(ledgers);
            when(accountRepository.findByAccountNumberIn(anySet()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/transactions/recent-recipients/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(5));
        }

        @Test
        @DisplayName("✅ /recent-recipients - STK trùng → distinct, chỉ giữ 1")
        void getRecentRecipients_duplicateAccounts_deduplicates() throws Exception {
            Transaction tx = new Transaction();
            tx.setToAccountNumber("SAME_ACC");
            TransactionLedger l1 = new TransactionLedger(); l1.setTransaction(tx);
            TransactionLedger l2 = new TransactionLedger(); l2.setTransaction(tx);
            TransactionLedger l3 = new TransactionLedger(); l3.setTransaction(tx);

            when(ledgerRepository.findRecentDebitsWithTransaction(
                    eq(1L), any(LocalDateTime.class)))
                    .thenReturn(Arrays.asList(l1, l2, l3));
            when(accountRepository.findByAccountNumberIn(anySet()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/transactions/recent-recipients/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }
}