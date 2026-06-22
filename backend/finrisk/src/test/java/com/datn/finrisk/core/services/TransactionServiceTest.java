package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.*;
import com.datn.finrisk.core.exceptions.BusinessLogicException;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.RiskPolicyRepository;
import com.datn.finrisk.core.repository.RiskScoreRepository;
import com.datn.finrisk.core.repository.TransactionAiInsightRepository;
import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserSecurityRepository;
import com.datn.finrisk.core.strategies.RiskActionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService — Unit Tests")
class TransactionServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TransactionLedgerRepository transactionLedgerRepository;
    @Mock private RiskEvaluationService riskEvaluationService;
    @Mock private RiskPolicyRepository riskPolicyRepo;
    @Mock private UserSecurityRepository userSecurityRepository;
    @Mock private Map<String, RiskActionStrategy> actionStrategies;
    // Các dependency được thêm trong quá trình refactor service
    @Mock private BehaviorLearningService behaviorLearningService;
    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private TransactionAiInsightRepository aiInsightRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Account mockSender;
    private Account mockReceiver;
    private UserSecurity mockSecurity;
    private User mockUser;
    private RiskPolicy mockPolicy;
    private RiskActionStrategy mockStrategy;

    @BeforeEach
    void setUp() {
        mockSecurity = new UserSecurity();
        mockSecurity.setPinHash("$2a$10$hashedPin");
        mockSecurity.setLockUntil(null);
        mockSecurity.setFailedPinAttempts(0);

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("thach");
        mockUser.setUserSecurity(mockSecurity);

        mockSender = new Account();
        mockSender.setId(100L);
        mockSender.setAccountNumber("1111111111");
        mockSender.setBalance(new BigDecimal("5000000"));
        mockSender.setUser(mockUser);

        mockReceiver = new Account();
        mockReceiver.setId(200L);
        mockReceiver.setAccountNumber("9999999999");
        mockReceiver.setBalance(new BigDecimal("1000000"));

        mockPolicy = new RiskPolicy();
        mockPolicy.setActionBeanName("pinActionStrategy");
        mockPolicy.setDescription("Duyệt bằng Soft PIN");

        mockStrategy = mock(RiskActionStrategy.class);
    }

    // =============================================
    // NHÓM 1: KIỂM TRA ĐIỀU KIỆN ĐẦU VÀO
    // =============================================

    @Nested
    @DisplayName("Nhóm 1 — Validate đầu vào")
    class ValidateInputTests {

        @Test
        @DisplayName("❌ Tài khoản không tồn tại → ERR_NOT_FOUND")
        void initiateTransaction_accountNotFound_throwsException() {
            when(accountRepository.findByIdWithUserAndSecurity(999L))
                .thenReturn(Optional.empty());

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> transactionService.initiateTransaction(
                    999L, "9999999999", new BigDecimal("100000"),
                    "Test", "127.0.0.1", "PC"));

            assertEquals("ERR_NOT_FOUND", ex.getErrorCode());
            // evaluateRisk giờ nhận 4 tham số
            verify(riskEvaluationService, never()).evaluateRisk(any(), anyBoolean(), any(), any());
        }

        @Test
        @DisplayName("❌ Chưa setup PIN → ERR_NO_PIN_SETUP")
        void initiateTransaction_pinNotSetup_throwsException() {
            mockSecurity.setPinHash(null);
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> transactionService.initiateTransaction(
                    100L, "9999999999", new BigDecimal("100000"),
                    "Test", "127.0.0.1", "PC"));

            assertEquals("ERR_NO_PIN_SETUP", ex.getErrorCode());
            verify(riskEvaluationService, never()).evaluateRisk(any(), anyBoolean(), any(), any());
        }

        @Test
        @DisplayName("❌ PIN setup nhưng hash rỗng → ERR_NO_PIN_SETUP")
        void initiateTransaction_pinHashEmpty_throwsException() {
            mockSecurity.setPinHash("   ");
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> transactionService.initiateTransaction(
                    100L, "9999999999", new BigDecimal("100000"),
                    "Test", "127.0.0.1", "PC"));

            assertEquals("ERR_NO_PIN_SETUP", ex.getErrorCode());
        }

        @Test
        @DisplayName("❌ Tài khoản đang bị khóa PIN → ERR_PIN_LOCKED")
        void initiateTransaction_accountLocked_throwsException() {
            mockSecurity.setLockUntil(LocalDateTime.now().plusMinutes(10));
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> transactionService.initiateTransaction(
                    100L, "9999999999", new BigDecimal("100000"),
                    "Test", "127.0.0.1", "PC"));

            assertEquals("ERR_PIN_LOCKED", ex.getErrorCode());
            verify(riskEvaluationService, never()).evaluateRisk(any(), anyBoolean(), any(), any());
        }

        @Test
        @DisplayName("✅ Khóa PIN đã hết hạn → vẫn cho giao dịch tiếp tục")
        void initiateTransaction_lockExpired_allowsTransaction() {
            mockSecurity.setLockUntil(LocalDateTime.now().minusMinutes(1));
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));
            when(riskEvaluationService.evaluateRisk(any(), anyBoolean(), any(), any())).thenReturn(0);
            // service gọi transactionRepository.save() trước khi tra policy
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(riskPolicyRepo.findByScore(0)).thenReturn(Optional.of(mockPolicy));
            when(actionStrategies.get("pinActionStrategy")).thenReturn(mockStrategy);
            when(mockStrategy.execute(any())).thenReturn(new Transaction());

            assertDoesNotThrow(() -> transactionService.initiateTransaction(
                100L, "9999999999", new BigDecimal("100000"),
                "Test", "127.0.0.1", "PC"));
        }

        @Test
        @DisplayName("❌ Số tiền = 0 → IllegalArgumentException")
        void initiateTransaction_zeroAmount_throwsException() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));

            assertThrows(IllegalArgumentException.class,
                () -> transactionService.initiateTransaction(
                    100L, "9999999999", BigDecimal.ZERO,
                    "Test", "127.0.0.1", "PC"));
        }

        @Test
        @DisplayName("❌ Số tiền âm → IllegalArgumentException")
        void initiateTransaction_negativeAmount_throwsException() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));

            assertThrows(IllegalArgumentException.class,
                () -> transactionService.initiateTransaction(
                    100L, "9999999999", new BigDecimal("-1"),
                    "Test", "127.0.0.1", "PC"));
        }

        @Test
        @DisplayName("❌ Số tiền null → không crash với NullPointerException")
        void initiateTransaction_nullAmount_throwsGracefully() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));

            assertThrows(IllegalArgumentException.class,
                () -> transactionService.initiateTransaction(
                    100L, "9999999999", null,
                    "Test", "127.0.0.1", "PC"));
        }

        @Test
        @DisplayName("❌ Tự chuyển cho chính mình → IllegalArgumentException")
        void initiateTransaction_selfTransfer_throwsException() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.initiateTransaction(
                    100L, "1111111111", new BigDecimal("100000"),
                    "Test", "127.0.0.1", "PC"));

            assertTrue(ex.getMessage().contains("Không thể tự chuyển tiền cho chính mình"));
        }

        @Test
        @DisplayName("❌ Số dư không đủ → ERR_INSUFFICIENT_BALANCE")
        void initiateTransaction_insufficientBalance_throwsException() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> transactionService.initiateTransaction(
                    100L, "9999999999", new BigDecimal("10000000"),
                    "Test", "127.0.0.1", "PC"));

            assertEquals("ERR_INSUFFICIENT_BALANCE", ex.getErrorCode());
            verify(riskEvaluationService, never()).evaluateRisk(any(), anyBoolean(), any(), any());
        }

        @Test
        @DisplayName("✅ Chuyển đúng bằng số dư → không lỗi insufficient")
        void initiateTransaction_exactBalance_allowsTransaction() {
            mockSender.setBalance(new BigDecimal("100000"));
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));
            when(riskEvaluationService.evaluateRisk(any(), anyBoolean(), any(), any())).thenReturn(0);
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(riskPolicyRepo.findByScore(0)).thenReturn(Optional.of(mockPolicy));
            when(actionStrategies.get("pinActionStrategy")).thenReturn(mockStrategy);
            when(mockStrategy.execute(any())).thenReturn(new Transaction());

            assertDoesNotThrow(() -> transactionService.initiateTransaction(
                100L, "9999999999", new BigDecimal("100000"),
                "Test", "127.0.0.1", "PC"));
        }
    }

    // =============================================
    // NHÓM 2: RISK ENGINE VÀ STRATEGY ROUTING
    // =============================================

    @Nested
    @DisplayName("Nhóm 2 — Risk Engine và Strategy")
    class RiskEngineTests {

        @Test
        @DisplayName("✅ Score 0 → điều hướng đến pinActionStrategy")
        void initiateTransaction_lowRisk_routesToPinStrategy() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));
            when(riskEvaluationService.evaluateRisk(any(), anyBoolean(), any(), any())).thenReturn(0);
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(riskPolicyRepo.findByScore(0)).thenReturn(Optional.of(mockPolicy));
            when(actionStrategies.get("pinActionStrategy")).thenReturn(mockStrategy);
            Transaction expected = new Transaction();
            when(mockStrategy.execute(any())).thenReturn(expected);

            Transaction result = transactionService.initiateTransaction(
                100L, "9999999999", new BigDecimal("100000"),
                "Test", "127.0.0.1", "PC");

            assertNotNull(result);
            verify(mockStrategy, times(1)).execute(any());
        }

        @Test
        @DisplayName("✅ IP và Device được set vào Transaction trước khi chấm điểm")
        void initiateTransaction_setsIpAndDeviceBeforeRiskEval() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));

            ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
            // evaluateRisk nhận 4 tham số: (Transaction, boolean, List<RiskScore>, List<TransactionAiInsight>)
            when(riskEvaluationService.evaluateRisk(txCaptor.capture(), anyBoolean(), any(), any()))
                .thenReturn(0);
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(riskPolicyRepo.findByScore(0)).thenReturn(Optional.of(mockPolicy));
            when(actionStrategies.get("pinActionStrategy")).thenReturn(mockStrategy);
            when(mockStrategy.execute(any())).thenReturn(new Transaction());

            transactionService.initiateTransaction(
                100L, "9999999999", new BigDecimal("100000"),
                "Test", "192.168.1.1", "Mozilla/5.0");

            Transaction capturedTx = txCaptor.getValue();
            assertEquals("192.168.1.1", capturedTx.getLocationIp());
            assertEquals("Mozilla/5.0", capturedTx.getDeviceFingerprint());
        }

        @Test
        @DisplayName("❌ Policy không tồn tại trong DB → ERR_POLICY_NOT_FOUND")
        void initiateTransaction_policyNotFound_throwsException() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));
            when(riskEvaluationService.evaluateRisk(any(), anyBoolean(), any(), any())).thenReturn(999);
            // save phải được gọi trước khi tra policy
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(riskPolicyRepo.findByScore(999)).thenReturn(Optional.empty());

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> transactionService.initiateTransaction(
                    100L, "9999999999", new BigDecimal("100000"),
                    "Test", "127.0.0.1", "PC"));

            assertEquals("ERR_POLICY_NOT_FOUND", ex.getErrorCode());
        }

        @Test
        @DisplayName("❌ Strategy bean không tồn tại → ERR_STRATEGY_NOT_FOUND")
        void initiateTransaction_strategyNotFound_throwsException() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));
            when(riskEvaluationService.evaluateRisk(any(), anyBoolean(), any(), any())).thenReturn(0);
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            mockPolicy.setActionBeanName("nonExistentStrategy");
            when(riskPolicyRepo.findByScore(0)).thenReturn(Optional.of(mockPolicy));
            when(actionStrategies.get("nonExistentStrategy")).thenReturn(null);

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> transactionService.initiateTransaction(
                    100L, "9999999999", new BigDecimal("100000"),
                    "Test", "127.0.0.1", "PC"));

            assertEquals("ERR_STRATEGY_NOT_FOUND", ex.getErrorCode());
        }

        @Test
        @DisplayName("✅ RiskScore được set vào Transaction trước khi execute strategy")
        void initiateTransaction_riskScoreSetOnTransaction() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));
            when(riskEvaluationService.evaluateRisk(any(), anyBoolean(), any(), any())).thenReturn(75);
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(riskPolicyRepo.findByScore(75)).thenReturn(Optional.of(mockPolicy));
            when(actionStrategies.get("pinActionStrategy")).thenReturn(mockStrategy);

            ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
            when(mockStrategy.execute(txCaptor.capture())).thenReturn(new Transaction());

            transactionService.initiateTransaction(
                100L, "9999999999", new BigDecimal("100000"),
                "Test", "127.0.0.1", "PC");

            assertEquals(75, txCaptor.getValue().getTotalRiskScore());
        }
    }

    // =============================================
    // NHÓM 3: executeTransactionCore
    // =============================================

    @Nested
    @DisplayName("Nhóm 3 — executeTransactionCore")
    class ExecuteTransactionCoreTests {

        private Transaction mockTx;

        @BeforeEach
        void setUpTx() {
            mockTx = new Transaction();
            mockTx.setId(1L);
            mockTx.setFromAccount(mockSender);
            mockTx.setToAccountNumber("9999999999");
            mockTx.setAmount(new BigDecimal("500000"));
        }

        @Test
        @DisplayName("✅ Trừ đúng tiền người gửi sau khi execute")
        void executeTransactionCore_deductsSenderBalance() {
            // claimForExecution phải trả về 1 để tiếp tục xử lý
            when(transactionRepository.claimForExecution(anyLong())).thenReturn(1);
            when(transactionRepository.save(any())).thenReturn(mockTx);
            // executeTransactionCore dùng findByIdForUpdate (pessimistic lock) thay vì findById
            when(accountRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(mockSender));
            when(accountRepository.save(any())).thenReturn(mockSender);
            when(transactionLedgerRepository.save(any())).thenReturn(new TransactionLedger());
            when(accountRepository.findByAccountNumberForUpdate("9999999999"))
                .thenReturn(Optional.empty());

            transactionService.executeTransactionCore(mockTx);

            // Số dư ban đầu 5,000,000 - 500,000 = 4,500,000
            assertEquals(new BigDecimal("4500000"), mockSender.getBalance());
        }

        @Test
        @DisplayName("✅ Cộng đúng tiền người nhận sau khi execute")
        void executeTransactionCore_creditsReceiverBalance() {
            when(transactionRepository.claimForExecution(anyLong())).thenReturn(1);
            when(transactionRepository.save(any())).thenReturn(mockTx);
            when(accountRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(mockSender));
            when(accountRepository.save(any())).thenReturn(mockSender);
            when(transactionLedgerRepository.save(any())).thenReturn(new TransactionLedger());
            when(accountRepository.findByAccountNumberForUpdate("9999999999"))
                .thenReturn(Optional.of(mockReceiver));

            transactionService.executeTransactionCore(mockTx);

            // Số dư ban đầu 1,000,000 + 500,000 = 1,500,000
            assertEquals(new BigDecimal("1500000"), mockReceiver.getBalance());
        }

        @Test
        @DisplayName("✅ Status được đổi thành SUCCESS sau execute")
        void executeTransactionCore_setsStatusToSuccess() {
            when(transactionRepository.claimForExecution(anyLong())).thenReturn(1);
            when(transactionRepository.save(any())).thenReturn(mockTx);
            when(accountRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(mockSender));
            when(accountRepository.save(any())).thenReturn(mockSender);
            when(transactionLedgerRepository.save(any())).thenReturn(new TransactionLedger());
            when(accountRepository.findByAccountNumberForUpdate("9999999999"))
                .thenReturn(Optional.empty());

            transactionService.executeTransactionCore(mockTx);

            assertEquals("SUCCESS", mockTx.getStatus());
        }

        @Test
        @DisplayName("✅ Tạo đúng 1 ledger entry DEBIT khi receiver ngoài hệ thống")
        void executeTransactionCore_createsDebitLedger() {
            when(transactionRepository.claimForExecution(anyLong())).thenReturn(1);
            when(transactionRepository.save(any())).thenReturn(mockTx);
            when(accountRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(mockSender));
            when(accountRepository.save(any())).thenReturn(mockSender);
            when(transactionLedgerRepository.save(any())).thenReturn(new TransactionLedger());
            when(accountRepository.findByAccountNumberForUpdate("9999999999"))
                .thenReturn(Optional.empty());

            transactionService.executeTransactionCore(mockTx);

            verify(transactionLedgerRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("✅ Tạo đúng 2 ledger entries khi receiver tồn tại")
        void executeTransactionCore_createsBothLedgerEntries() {
            when(transactionRepository.claimForExecution(anyLong())).thenReturn(1);
            when(transactionRepository.save(any())).thenReturn(mockTx);
            when(accountRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(mockSender));
            when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionLedgerRepository.save(any())).thenReturn(new TransactionLedger());
            when(accountRepository.findByAccountNumberForUpdate("9999999999"))
                .thenReturn(Optional.of(mockReceiver));

            transactionService.executeTransactionCore(mockTx);

            // DEBIT cho sender + CREDIT cho receiver = 2 lần save ledger
            verify(transactionLedgerRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("✅ Receiver không trong hệ thống → chỉ trừ tiền sender, không crash")
        void executeTransactionCore_externalReceiver_onlyDebitsSender() {
            when(transactionRepository.claimForExecution(anyLong())).thenReturn(1);
            when(transactionRepository.save(any())).thenReturn(mockTx);
            when(accountRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(mockSender));
            when(accountRepository.save(any())).thenReturn(mockSender);
            when(transactionLedgerRepository.save(any())).thenReturn(new TransactionLedger());
            when(accountRepository.findByAccountNumberForUpdate("9999999999"))
                .thenReturn(Optional.empty());

            assertDoesNotThrow(() -> transactionService.executeTransactionCore(mockTx));
            assertEquals(new BigDecimal("4500000"), mockSender.getBalance());
        }

        @Test
        @DisplayName("✅ Transaction được save với status SUCCESS")
        void executeTransactionCore_savesTransactionWithSuccessStatus() {
            ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
            when(transactionRepository.claimForExecution(anyLong())).thenReturn(1);
            when(transactionRepository.save(txCaptor.capture())).thenReturn(mockTx);
            when(accountRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(mockSender));
            when(accountRepository.save(any())).thenReturn(mockSender);
            when(transactionLedgerRepository.save(any())).thenReturn(new TransactionLedger());
            when(accountRepository.findByAccountNumberForUpdate(anyString()))
                .thenReturn(Optional.empty());

            transactionService.executeTransactionCore(mockTx);

            assertEquals("SUCCESS", txCaptor.getValue().getStatus());
        }

        // ── Atomic claim idempotency ──────────────────────────────────────────

        @Test
        @DisplayName("❌ claimForExecution = 0 → ERR_DUPLICATE_EXECUTION (giao dịch đã bị thread khác claim)")
        void executeTransactionCore_claimZero_throwsDuplicateExecution() {
            // claimForExecution trả 0 khi UPDATE không thay đổi row nào
            // (tx đã ở trạng thái PROCESSING hoặc SUCCESS do thread khác thắng race)
            when(transactionRepository.claimForExecution(anyLong())).thenReturn(0);

            BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> transactionService.executeTransactionCore(mockTx));

            assertEquals("ERR_DUPLICATE_EXECUTION", ex.getErrorCode());
        }

        @Test
        @DisplayName("❌ claimForExecution = 0 → balance KHÔNG bị trừ (no side effects)")
        void executeTransactionCore_claimZero_balanceUnchanged() {
            BigDecimal balanceBefore = mockSender.getBalance();
            when(transactionRepository.claimForExecution(anyLong())).thenReturn(0);

            assertThrows(BusinessLogicException.class,
                () -> transactionService.executeTransactionCore(mockTx));

            // Số dư không được thay đổi khi claim thất bại
            assertEquals(balanceBefore, mockSender.getBalance());
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("❌ claimForExecution = 0 → KHÔNG tạo ledger entry nào")
        void executeTransactionCore_claimZero_noLedgerCreated() {
            when(transactionRepository.claimForExecution(anyLong())).thenReturn(0);

            assertThrows(BusinessLogicException.class,
                () -> transactionService.executeTransactionCore(mockTx));

            verify(transactionLedgerRepository, never()).save(any());
        }
    }

    // =============================================
    // NHÓM 4: EDGE CASES
    // =============================================

    @Nested
    @DisplayName("Nhóm 4 — Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("⚠️ Chuyển số tiền rất nhỏ (1 đồng) → vẫn xử lý bình thường")
        void initiateTransaction_minimalAmount_allowsTransaction() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));
            when(riskEvaluationService.evaluateRisk(any(), anyBoolean(), any(), any())).thenReturn(0);
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(riskPolicyRepo.findByScore(0)).thenReturn(Optional.of(mockPolicy));
            when(actionStrategies.get("pinActionStrategy")).thenReturn(mockStrategy);
            when(mockStrategy.execute(any())).thenReturn(new Transaction());

            assertDoesNotThrow(() -> transactionService.initiateTransaction(
                100L, "9999999999", new BigDecimal("1"),
                "Test", "127.0.0.1", "PC"));
        }

        @Test
        @DisplayName("⚠️ Description null → không crash")
        void initiateTransaction_nullDescription_handledGracefully() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));
            when(riskEvaluationService.evaluateRisk(any(), anyBoolean(), any(), any())).thenReturn(0);
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(riskPolicyRepo.findByScore(0)).thenReturn(Optional.of(mockPolicy));
            when(actionStrategies.get("pinActionStrategy")).thenReturn(mockStrategy);
            when(mockStrategy.execute(any())).thenReturn(new Transaction());

            assertDoesNotThrow(() -> transactionService.initiateTransaction(
                100L, "9999999999", new BigDecimal("100000"),
                null, "127.0.0.1", "PC"));
        }

        @Test
        @DisplayName("⚠️ IP null → không crash, vẫn ghi transaction")
        void initiateTransaction_nullIp_handledGracefully() {
            when(accountRepository.findByIdWithUserAndSecurity(100L))
                .thenReturn(Optional.of(mockSender));
            when(riskEvaluationService.evaluateRisk(any(), anyBoolean(), any(), any())).thenReturn(0);
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(riskPolicyRepo.findByScore(0)).thenReturn(Optional.of(mockPolicy));
            when(actionStrategies.get("pinActionStrategy")).thenReturn(mockStrategy);
            when(mockStrategy.execute(any())).thenReturn(new Transaction());

            assertDoesNotThrow(() -> transactionService.initiateTransaction(
                100L, "9999999999", new BigDecimal("100000"),
                "Test", null, "PC"));
        }
    }
}
