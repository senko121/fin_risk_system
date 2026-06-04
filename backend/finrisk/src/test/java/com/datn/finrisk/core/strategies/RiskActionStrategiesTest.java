package com.datn.finrisk.core.strategies;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.exceptions.BusinessLogicException;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Risk Action Strategies — Unit Tests")
@ExtendWith(MockitoExtension.class)
class RiskActionStrategiesTest {

    // ── OtpActionStrategy ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("OtpActionStrategy")
    class OtpStrategy {

        @Mock private TransactionRepository transactionRepository;
        @InjectMocks private OtpActionStrategy strategy;

        @Test
        @DisplayName("execute() sets riskLevel=MEDIUM_1, status=PENDING_PIN_OTP and saves")
        void execute_setsCorrectFields() {
            Transaction tx = new Transaction();
            when(transactionRepository.save(any())).thenReturn(tx);

            Transaction result = strategy.execute(tx);

            assertThat(tx.getRiskLevel()).isEqualTo("MEDIUM_1");
            assertThat(tx.getStatus()).isEqualTo("PENDING_PIN_OTP");
            verify(transactionRepository).save(tx);
        }

        @Test
        @DisplayName("execute() returns the saved transaction")
        void execute_returnsSaved() {
            Transaction tx     = new Transaction();
            Transaction saved  = new Transaction();
            when(transactionRepository.save(any())).thenReturn(saved);

            assertThat(strategy.execute(tx)).isSameAs(saved);
        }
    }

    // ── PinActionStrategy ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("PinActionStrategy")
    class PinStrategy {

        @Mock private TransactionRepository transactionRepository;
        @InjectMocks private PinActionStrategy strategy;

        @Test
        @DisplayName("execute() sets riskLevel=LOW, status=PENDING_PIN and saves")
        void execute_setsCorrectFields() {
            Transaction tx = new Transaction();
            when(transactionRepository.save(any())).thenReturn(tx);

            strategy.execute(tx);

            assertThat(tx.getRiskLevel()).isEqualTo("LOW");
            assertThat(tx.getStatus()).isEqualTo("PENDING_PIN");
            verify(transactionRepository).save(tx);
        }

        @Test
        @DisplayName("execute() returns the saved transaction")
        void execute_returnsSaved() {
            Transaction tx    = new Transaction();
            Transaction saved = new Transaction();
            when(transactionRepository.save(any())).thenReturn(saved);

            assertThat(strategy.execute(tx)).isSameAs(saved);
        }
    }

    // ── BasicFaceActionStrategy ───────────────────────────────────────────────

    @Nested
    @DisplayName("BasicFaceActionStrategy")
    class BasicFaceStrategy {

        @Mock private TransactionRepository transactionRepository;
        @InjectMocks private BasicFaceActionStrategy strategy;

        private Transaction buildTxWithUser(boolean hasFace) {
            User user = new User();
            if (hasFace) user.setFaceEmbeddings("[[0.1,0.2]]");

            Account account = new Account();
            account.setUser(user);

            Transaction tx = new Transaction();
            tx.setFromAccount(account);
            return tx;
        }

        @Test
        @DisplayName("user has face embeddings → sets MEDIUM_2 / PENDING_PIN_FACE, saves")
        void execute_userHasFace_setsCorrectFields() {
            Transaction tx = buildTxWithUser(true);
            when(transactionRepository.save(any())).thenReturn(tx);

            strategy.execute(tx);

            assertThat(tx.getRiskLevel()).isEqualTo("MEDIUM_2");
            assertThat(tx.getStatus()).isEqualTo("PENDING_PIN_FACE");
            verify(transactionRepository).save(tx);
        }

        @Test
        @DisplayName("user has NO face embeddings → throws BusinessLogicException ERR_NO_FACE_SETUP")
        void execute_noFace_throwsBusinessLogicException() {
            Transaction tx = buildTxWithUser(false);

            assertThatThrownBy(() -> strategy.execute(tx))
                    .isInstanceOf(BusinessLogicException.class)
                    .hasMessageContaining("FaceID");
        }

        @Test
        @DisplayName("null face embeddings → throws BusinessLogicException")
        void execute_nullFace_throwsException() {
            User user = new User(); // faceEmbeddings defaults to null
            Account acct = new Account();
            acct.setUser(user);
            Transaction tx = new Transaction();
            tx.setFromAccount(acct);

            assertThatThrownBy(() -> strategy.execute(tx))
                    .isInstanceOf(BusinessLogicException.class);
        }
    }

    // ── PassActionStrategy ────────────────────────────────────────────────────

    @Nested
    @DisplayName("PassActionStrategy")
    class PassStrategy {

        @Mock private TransactionRepository        transactionRepository;
        @Mock private AccountRepository            accountRepository;
        @Mock private TransactionLedgerRepository  transactionLedgerRepository;
        @InjectMocks private PassActionStrategy strategy;

        private Transaction buildTx(BigDecimal amount) {
            User user = new User();
            user.setId(1L);

            Account sender = new Account();
            sender.setId(10L);
            sender.setAccountNumber("SRC001");
            sender.setBalance(new BigDecimal("1000000"));
            sender.setUser(user);

            Transaction tx = new Transaction();
            tx.setId(99L);
            tx.setFromAccount(sender);
            tx.setToAccountNumber("DST002");
            tx.setAmount(amount);
            return tx;
        }

        @Test
        @DisplayName("execute() sets riskLevel=LOW and status=SUCCESS")
        void execute_setsLowAndSuccess() {
            Transaction tx = buildTx(new BigDecimal("100000"));
            Account senderLocked = new Account();
            senderLocked.setId(10L);
            senderLocked.setBalance(new BigDecimal("1000000"));
            senderLocked.setUser(new User());

            when(transactionRepository.save(any())).thenReturn(tx);
            when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(senderLocked));
            when(accountRepository.findByAccountNumberForUpdate("DST002")).thenReturn(Optional.empty());
            when(transactionLedgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            strategy.execute(tx);

            assertThat(tx.getRiskLevel()).isEqualTo("LOW");
            assertThat(tx.getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("execute() deducts amount from sender balance")
        void execute_deductsSenderBalance() {
            Transaction tx = buildTx(new BigDecimal("200000"));
            Account senderLocked = new Account();
            senderLocked.setId(10L);
            senderLocked.setBalance(new BigDecimal("1000000"));
            senderLocked.setUser(new User());

            when(transactionRepository.save(any())).thenReturn(tx);
            when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(senderLocked));
            when(accountRepository.findByAccountNumberForUpdate("DST002")).thenReturn(Optional.empty());
            when(transactionLedgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            strategy.execute(tx);

            assertThat(senderLocked.getBalance()).isEqualByComparingTo("800000");
        }

        @Test
        @DisplayName("insufficient balance → throws BusinessLogicException")
        void execute_insufficientBalance_throws() {
            Transaction tx = buildTx(new BigDecimal("9000000")); // more than balance
            Account senderLocked = new Account();
            senderLocked.setId(10L);
            senderLocked.setBalance(new BigDecimal("100")); // tiny balance
            senderLocked.setUser(new User());

            when(transactionRepository.save(any())).thenReturn(tx);
            when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(senderLocked));

            assertThatThrownBy(() -> strategy.execute(tx))
                    .isInstanceOf(BusinessLogicException.class)
                    .hasMessageContaining("Số dư");
        }

        @Test
        @DisplayName("sender account not found → throws BusinessLogicException")
        void execute_senderNotFound_throws() {
            Transaction tx = buildTx(new BigDecimal("100"));

            when(transactionRepository.save(any())).thenReturn(tx);
            when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> strategy.execute(tx))
                    .isInstanceOf(BusinessLogicException.class)
                    .hasMessageContaining("Tài khoản nguồn");
        }

        @Test
        @DisplayName("receiver exists → credited and ledger saved")
        void execute_receiverCredited() {
            Transaction tx = buildTx(new BigDecimal("100000"));
            Account senderLocked = new Account();
            senderLocked.setId(10L);
            senderLocked.setBalance(new BigDecimal("1000000"));
            senderLocked.setUser(new User());

            Account receiver = new Account();
            receiver.setId(20L);
            receiver.setBalance(new BigDecimal("500000"));
            receiver.setUser(new User());

            when(transactionRepository.save(any())).thenReturn(tx);
            when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(senderLocked));
            when(accountRepository.findByAccountNumberForUpdate("DST002")).thenReturn(Optional.of(receiver));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionLedgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            strategy.execute(tx);

            assertThat(receiver.getBalance()).isEqualByComparingTo("600000");
            // DEBIT + CREDIT ledger entries
            verify(transactionLedgerRepository, times(2)).save(any());
        }
    }
}
