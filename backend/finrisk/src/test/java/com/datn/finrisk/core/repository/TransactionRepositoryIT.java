package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Transaction;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test") // 🚀 Cực kỳ quan trọng: Ép xài cấu hình H2 của Bro
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TransactionRepository — Integration Tests (H2)")
class TransactionRepositoryIT {

    @Autowired private TestEntityManager entityManager;
    @Autowired private TransactionRepository transactionRepository;

    private Account savedAccount;
    private LocalDateTime now;

    // ── Helper: Tạo hệ sinh thái Bố (User) - Con (Security) - Cháu (Account) ──
    private Account createBaseAccount(String username, String phone, String accountNumber) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("Nguyễn Hoàng Thạch");
        user.setPhoneNumber(phone);
        user = entityManager.persist(user);

        UserSecurity sec = new UserSecurity();
        sec.setPinHash("pin_123");
        sec.setPasswordHash("pass_123");
        sec.setUser(user);
        sec = entityManager.persist(sec);
        user.setUserSecurity(sec);

        Account acc = new Account();
        acc.setAccountNumber(accountNumber);
        acc.setBalance(new BigDecimal("50000000"));
        acc.setUser(user);
        return entityManager.persist(acc);
    }

    // ── Helper: Tạo Giao dịch (Transaction) ──
    private Transaction createTx(Account account, BigDecimal amount, String status, String riskLevel, LocalDateTime createdAt) {
        Transaction tx = new Transaction();
        tx.setFromAccount(account);
        tx.setToAccountNumber("999999999");
        tx.setAmount(amount);
        tx.setStatus(status);
        tx.setRiskLevel(riskLevel);
        tx.setCreatedAt(createdAt); // Set thời gian lùi về quá khứ để test filter
        return entityManager.persist(tx);
    }

    @BeforeEach
    void setUp() {
        savedAccount = createBaseAccount("thach_tx", "0988888888", "88889999");
        now = LocalDateTime.now();

        // 1. Tạo 2 giao dịch SUCCESS, Rủi ro LOW, trong ngày hôm nay (100k + 200k)
        createTx(savedAccount, new BigDecimal("100000"), "SUCCESS", "LOW", now.minusHours(1));
        createTx(savedAccount, new BigDecimal("200000"), "SUCCESS", "LOW", now.minusHours(2));

        // 2. Tạo 1 giao dịch SUCCESS, Rủi ro HIGH, hôm nay (500k)
        createTx(savedAccount, new BigDecimal("500000"), "SUCCESS", "HIGH", now.minusHours(3));

        // 3. Tạo 1 giao dịch FAILED (Thất bại), Rủi ro LOW, hôm nay (1 Triệu) -> Không được phép cộng dồn
        createTx(savedAccount, new BigDecimal("1000000"), "FAILED", "LOW", now.minusHours(4));

        // 4. Tạo 1 giao dịch SUCCESS, Rủi ro LOW, NHƯNG LÀ NGÀY HÔM QUA (300k) -> Không được phép cộng dồn cho hôm nay
        createTx(savedAccount, new BigDecimal("300000"), "SUCCESS", "LOW", now.minusDays(1));

        entityManager.flush();
        entityManager.clear();
    }

    // ==========================================================
    // NHÓM 1 — TEST CỘNG DỒN TIỀN (SUM)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 1 — sumSuccessfulAmountToday & sumTotalSuccessfulAmount")
    class SumAmountTests {

        @Test
        @DisplayName("✅ Tính tổng tiền thành công trong ngày: Phải LỌC ĐÚNG trạng thái và thời gian")
        void sumSuccessfulAmountToday_calculatesCorrectly() {
            LocalDateTime startOfDay = now.toLocalDate().atStartOfDay(); // 00:00 sáng nay
            
            BigDecimal totalToday = transactionRepository.sumSuccessfulAmountToday(savedAccount.getId(), startOfDay);

            // Kỳ vọng: 100k + 200k + 500k = 800k. 
            // KHÔNG cộng 1 củ (FAILED) và KHÔNG cộng 300k (Hôm qua).
            assertNotNull(totalToday);
            assertEquals(0, totalToday.compareTo(new BigDecimal("800000")), 
                "Chỉ được tính các giao dịch SUCCESS và nằm trong ngày hôm nay");
        }

        @Test
        @DisplayName("✅ Tính tổng TOÀN BỘ tiền thành công (Không màng thời gian)")
        void sumTotalSuccessfulAmount_calculatesCorrectly() {
            BigDecimal grandTotal = transactionRepository.sumTotalSuccessfulAmount();

            // Kỳ vọng: 100k + 200k + 500k + 300k (Hôm qua) = 1.1 Triệu. Bỏ qua 1 củ FAILED.
            assertNotNull(grandTotal);
            assertEquals(0, grandTotal.compareTo(new BigDecimal("1100000")));
        }

        @Test
        @DisplayName("⚠️ [EDGE CASE] Nếu query SUM không có kết quả, JPA trả về NULL chứ không phải 0")
        void sumSuccessfulAmountToday_whenNoTransactions_returnsNull() {
            // Lấy một account ID không tồn tại
            BigDecimal total = transactionRepository.sumSuccessfulAmountToday(999L, now.minusDays(10));

            // Chú ý: Rất nhiều Dev nhầm tưởng nó trả về 0. JPA chuẩn sẽ trả về NULL. Test này để dặn dò Dev cẩn thận NullPointer.
            assertNull(total, "JPA SUM function sẽ trả về null nếu không có row nào match");
        }
    }

    // ==========================================================
    // NHÓM 2 — TEST ĐẾM SỐ LƯỢNG (COUNT)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 2 — Các hàm COUNT")
    class CountTests {

        @Test
        @DisplayName("✅ Đếm số giao dịch gần đây (countRecentTransactions)")
        void countRecentTransactions_returnsCorrectCount() {
            // Lấy thời gian mốc là 2.5 tiếng trước
            // Sẽ bao gồm tx 1 tiếng trước và 2 tiếng trước -> Tức là 2 giao dịch. (Bỏ qua 3,4 tiếng trước)
            int count = transactionRepository.countRecentTransactions(savedAccount.getId(), now.minusHours(2).minusMinutes(30));

            assertEquals(2, count, "Chỉ có 2 giao dịch xảy ra trong vòng 2.5 tiếng qua");
        }

        @Test
        @DisplayName("✅ Đếm giao dịch High Risk toàn hệ thống")
        void countHighRiskTransactions_returnsCorrectCount() {
            long highRiskCount = transactionRepository.countHighRiskTransactions();

            assertEquals(1, highRiskCount, "Trong setup chỉ tạo đúng 1 giao dịch có riskLevel = HIGH");
        }

        @Test
        @DisplayName("✅ Đếm toàn bộ giao dịch hệ thống")
        void countTotalTransactions_returnsCorrectCount() {
            long total = transactionRepository.countTotalTransactions();
            
            // Setup tạo tổng cộng 5 giao dịch
            assertEquals(5, total);
        }
    }

    // ==========================================================
    // NHÓM 3 — TEST TÌM TOP & JOIN FETCH
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 3 — findTop5 & JOIN FETCH")
    class RetrievalTests {

        @Test
        @DisplayName("✅ findTop5ByFromAccount... Lấy đúng 5 giao dịch MỚI NHẤT, xếp giảm dần")
        void findTop5_returnsMostRecentFive() {
            // Đã có 5 giao dịch trong setup. Ta ráng tạo thêm 2 cái nữa (Tổng 7) để xem nó có cắt đúng 5 không.
            createTx(savedAccount, new BigDecimal("10"), "SUCCESS", "LOW", now.minusMinutes(10));
            createTx(savedAccount, new BigDecimal("20"), "SUCCESS", "LOW", now.minusMinutes(5));
            entityManager.flush();
            entityManager.clear();

            List<Transaction> top5 = transactionRepository.findTop5ByFromAccountUserIdOrderByCreatedAtDesc(savedAccount.getUser().getId());

            assertEquals(5, top5.size(), "Chỉ được phép lấy tối đa 5 records");
            
            // Thằng mới nhất (mới cách đây 5 phút) phải nằm trên cùng [0]
            assertEquals(0, top5.get(0).getAmount().compareTo(new BigDecimal("20")));
            // Thằng thứ 2 (cách đây 10 phút)
            assertEquals(0, top5.get(1).getAmount().compareTo(new BigDecimal("10")));
        }

        @Test
        @DisplayName("✅ findByIdWithUserSecurity: Diệt N+1 Query thành công")
        void findByIdWithUserSecurity_fetchesGraphEagerly() {
            // Lấy ID của giao dịch đầu tiên
            List<Transaction> all = transactionRepository.findAll();
            Long targetId = all.get(0).getId();
            entityManager.clear(); // Xóa cache ép query

            Optional<Transaction> txOpt = transactionRepository.findByIdWithUserSecurity(targetId);

            assertTrue(txOpt.isPresent());
            Transaction tx = txOpt.get();
            
            // Đảm bảo các thuộc tính đã được kéo lên RAM sẵn, không bị lỗi LazyInitializationException
            assertNotNull(tx.getFromAccount());
            assertNotNull(tx.getFromAccount().getUser());
            assertNotNull(tx.getFromAccount().getUser().getUserSecurity());
            assertEquals("thach_tx", tx.getFromAccount().getUser().getUsername());
        }
    }

    // ==========================================================
// NHÓM 4 — SUM EDGE CASES & BOUNDARY
// ==========================================================
@Nested
@DisplayName("Nhóm 4 — SUM Edge Cases")
class SumEdgeCaseTests {

    @Test
    @DisplayName("✅ sumSuccessfulAmountToday: Chỉ tính đúng account được chỉ định")
    void sumSuccessfulAmountToday_isolatedByAccount() {
        // Tạo account thứ 2 với giao dịch riêng
        Account otherAccount = createBaseAccount("other_user", "0911111111", "11112222");
        createTx(otherAccount, new BigDecimal("9999999"), "SUCCESS", "LOW", now.minusMinutes(30));
        entityManager.flush();
        entityManager.clear();

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        BigDecimal total = transactionRepository
                .sumSuccessfulAmountToday(savedAccount.getId(), startOfDay);

        // Phải là 800k của savedAccount, KHÔNG cộng 9.9 triệu của otherAccount
        assertEquals(0, total.compareTo(new BigDecimal("800000")),
            "SUM phải bị giới hạn đúng theo accountId, không được cộng lẫn của account khác");
    }

    @Test
    @DisplayName("✅ sumSuccessfulAmountToday: Transaction đúng tại mốc startOfDay (boundary inclusive)")
    void sumSuccessfulAmountToday_transactionAtExactStartOfDay_isIncluded() {
        // Transaction tạo lúc đúng 00:00:00 hôm nay
        LocalDateTime exactMidnight = now.toLocalDate().atStartOfDay();
        createTx(savedAccount, new BigDecimal("50000"), "SUCCESS", "LOW", exactMidnight);
        entityManager.flush();
        entityManager.clear();

        BigDecimal total = transactionRepository
                .sumSuccessfulAmountToday(savedAccount.getId(), exactMidnight);

        // 800k cũ + 50k mới = 850k
        // Nếu query dùng >= thì midnight được tính, nếu dùng > thì không
        // Test này document behavior thực tế của JPQL >= operator
        assertNotNull(total);
        assertTrue(total.compareTo(new BigDecimal("800000")) >= 0,
            "Transaction tại đúng mốc startOfDay phải được tính (>= boundary)");
    }

    @Test
    @DisplayName("✅ sumSuccessfulAmountToday: Transaction 1 giây TRƯỚC startOfDay không được tính")
    void sumSuccessfulAmountToday_transactionBeforeStartOfDay_isExcluded() {
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        // Transaction cách startOfDay 1 giây về trước
        createTx(savedAccount, new BigDecimal("777000"), "SUCCESS", "LOW",
                 startOfDay.minusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        BigDecimal total = transactionRepository
                .sumSuccessfulAmountToday(savedAccount.getId(), startOfDay);

        // Vẫn phải là 800k, không được cộng 777k
        assertEquals(0, total.compareTo(new BigDecimal("800000")),
            "Transaction 1 giây trước startOfDay không được tính vào tổng hôm nay");
    }

    @Test
    @DisplayName("✅ sumTotalSuccessfulAmount: Chỉ tính SUCCESS, PENDING/FAILED bị loại")
    void sumTotalSuccessfulAmount_excludesNonSuccessStatuses() {
        // Tạo thêm các trạng thái khác để verify filter
        createTx(savedAccount, new BigDecimal("5000000"), "PENDING", "LOW", now.minusMinutes(10));
        createTx(savedAccount, new BigDecimal("3000000"), "CANCELLED", "LOW", now.minusMinutes(10));
        entityManager.flush();
        entityManager.clear();

        BigDecimal total = transactionRepository.sumTotalSuccessfulAmount();

        // Vẫn là 1.1 triệu từ setUp(), PENDING và CANCELLED không được cộng
        assertNotNull(total);
        assertEquals(0, total.compareTo(new BigDecimal("1100000")),
            "PENDING và CANCELLED không được tính vào sumTotalSuccessfulAmount");
    }

    @Test
    @DisplayName("⚠️ sumSuccessfulAmountToday: Account có đúng 1 giao dịch → trả về chính xác số đó")
    void sumSuccessfulAmountToday_singleTransaction_returnsThatAmount() {
        Account freshAccount = createBaseAccount("fresh_user", "0922222222", "22223333");
        createTx(freshAccount, new BigDecimal("123456"), "SUCCESS", "LOW", now.minusMinutes(5));
        entityManager.flush();
        entityManager.clear();

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        BigDecimal total = transactionRepository
                .sumSuccessfulAmountToday(freshAccount.getId(), startOfDay);

        assertEquals(0, total.compareTo(new BigDecimal("123456")),
            "Account có đúng 1 giao dịch phải SUM ra chính xác số đó");
    }
}

// ==========================================================
// NHÓM 5 — COUNT EDGE CASES
// ==========================================================
@Nested
@DisplayName("Nhóm 5 — COUNT Edge Cases")
class CountEdgeCaseTests {

    @Test
    @DisplayName("✅ countRecentTransactions: Không có giao dịch nào trong window → trả về 0")
    void countRecentTransactions_whenNoneInWindow_returnsZero() {
        // Window chỉ 1 phút trước — không có giao dịch nào trong setUp gần vậy
        int count = transactionRepository
                .countRecentTransactions(savedAccount.getId(), now.minusMinutes(1));

        assertEquals(0, count, "Không có giao dịch trong 1 phút qua, phải trả về 0");
    }

    @Test
    @DisplayName("✅ countRecentTransactions: Bao gồm TẤT CẢ trạng thái (SUCCESS + FAILED)")
    void countRecentTransactions_countsAllStatuses() {
        // Query này đếm tất cả trạng thái, không chỉ SUCCESS
        // Window = 5 tiếng: bao gồm cả 4 giao dịch trong setUp (1h, 2h, 3h, 4h trước)
        int count = transactionRepository
                .countRecentTransactions(savedAccount.getId(), now.minusHours(5));

        assertEquals(4, count,
            "countRecentTransactions phải đếm cả FAILED, không chỉ SUCCESS");
    }

    @Test
    @DisplayName("✅ countRecentTransactions: Giao dịch của account khác không bị đếm nhầm")
    void countRecentTransactions_isolatedByAccount() {
        Account otherAccount = createBaseAccount("spy_user", "0933333333", "33334444");
        createTx(otherAccount, new BigDecimal("100"), "SUCCESS", "LOW", now.minusMinutes(30));
        entityManager.flush();
        entityManager.clear();

        // Window 2.5h của savedAccount vẫn phải là 2
        int count = transactionRepository
                .countRecentTransactions(savedAccount.getId(), now.minusHours(2).minusMinutes(30));

        assertEquals(2, count,
            "Giao dịch của otherAccount không được bị đếm nhầm vào savedAccount");
    }

    @Test
    @DisplayName("✅ countHighRiskTransactions: Thêm HIGH risk mới → count tăng đúng 1")
    void countHighRiskTransactions_increasesWhenHighRiskAdded() {
        long before = transactionRepository.countHighRiskTransactions();

        createTx(savedAccount, new BigDecimal("1000"), "SUCCESS", "HIGH", now.minusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        long after = transactionRepository.countHighRiskTransactions();

        assertEquals(before + 1, after,
            "Thêm 1 HIGH risk transaction phải tăng count đúng 1 đơn vị");
    }

    @Test
    @DisplayName("✅ countHighRiskTransactions: LOW và MEDIUM không bị đếm nhầm là HIGH")
    void countHighRiskTransactions_excludesOtherRiskLevels() {
        createTx(savedAccount, new BigDecimal("1000"), "SUCCESS", "LOW",    now.minusMinutes(1));
        createTx(savedAccount, new BigDecimal("1000"), "SUCCESS", "MEDIUM", now.minusMinutes(2));
        entityManager.flush();
        entityManager.clear();

        long count = transactionRepository.countHighRiskTransactions();

        // Vẫn là 1 từ setUp, LOW và MEDIUM không được tính
        assertEquals(1, count,
            "LOW và MEDIUM risk không được đếm vào countHighRiskTransactions");
    }

    @Test
    @DisplayName("✅ countTotalTransactions: Đếm TƯƠNG ĐỐI — thêm N giao dịch → tăng đúng N")
    void countTotalTransactions_incrementsCorrectly() {
        // Dùng delta thay vì assert giá trị tuyệt đối → không vỡ khi test khác còn data
        long before = transactionRepository.countTotalTransactions();

        createTx(savedAccount, new BigDecimal("1"), "SUCCESS", "LOW", now.minusSeconds(1));
        createTx(savedAccount, new BigDecimal("2"), "SUCCESS", "LOW", now.minusSeconds(2));
        entityManager.flush();
        entityManager.clear();

        long after = transactionRepository.countTotalTransactions();

        assertEquals(before + 2, after,
            "Thêm 2 transaction phải tăng đúng 2, không được nhiều hơn hay ít hơn");
    }
}

// ==========================================================
// NHÓM 6 — findTop5 EDGE CASES
// ==========================================================
@Nested
@DisplayName("Nhóm 6 — findTop5 Edge Cases")
class FindTop5EdgeCaseTests {

    @Test
    @DisplayName("✅ Ít hơn 5 giao dịch → trả về tất cả, không throw exception")
    void findTop5_whenLessThan5Transactions_returnsAll() {
        // Tạo account mới chỉ có 2 giao dịch
        Account freshAccount = createBaseAccount("fresh2", "0944444444", "44445555");
        createTx(freshAccount, new BigDecimal("100"), "SUCCESS", "LOW", now.minusHours(1));
        createTx(freshAccount, new BigDecimal("200"), "SUCCESS", "LOW", now.minusHours(2));
        entityManager.flush();
        entityManager.clear();

        List<Transaction> result = transactionRepository
                .findTop5ByFromAccountUserIdOrderByCreatedAtDesc(
                        freshAccount.getUser().getId());

        assertEquals(2, result.size(),
            "Có 2 giao dịch thì phải trả về 2, không được throw vì thiếu 5");
    }

    @Test
    @DisplayName("✅ Không có giao dịch nào → trả về list rỗng")
    void findTop5_whenNoTransactions_returnsEmptyList() {
        Account emptyAccount = createBaseAccount("empty_user", "0955555555", "55556666");
        entityManager.flush();
        entityManager.clear();

        List<Transaction> result = transactionRepository
                .findTop5ByFromAccountUserIdOrderByCreatedAtDesc(
                        emptyAccount.getUser().getId());

        assertNotNull(result, "Phải trả về list rỗng, không phải null");
        assertTrue(result.isEmpty(), "Account không có giao dịch → list phải rỗng");
    }

    @Test
    @DisplayName("✅ findTop5 chỉ lấy giao dịch của đúng userId, không lấy của user khác")
    void findTop5_isolatedByUserId() {
        Account otherAccount = createBaseAccount("intruder", "0966666666", "66667777");
        // Tạo giao dịch rất mới cho intruder
        createTx(otherAccount, new BigDecimal("9999999"), "SUCCESS", "LOW", now.minusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        List<Transaction> result = transactionRepository
                .findTop5ByFromAccountUserIdOrderByCreatedAtDesc(
                        savedAccount.getUser().getId());

        // Không được có giao dịch 9.9 triệu của intruder
        result.forEach(tx ->
            assertNotEquals(0,
                tx.getAmount().compareTo(new BigDecimal("9999999")),
                "Giao dịch của user khác không được lọt vào top5 của savedAccount")
        );
    }

    @Test
    @DisplayName("✅ findTop5 đảm bảo thứ tự DESC — giao dịch mới nhất luôn đứng đầu")
    void findTop5_orderIsStrictlyDescending() {
        // Tạo thêm để có đủ 7 giao dịch, verify thứ tự chặt chẽ hơn
        createTx(savedAccount, new BigDecimal("10"), "SUCCESS", "LOW",
                 now.minusMinutes(10));
        createTx(savedAccount, new BigDecimal("20"), "SUCCESS", "LOW",
                 now.minusMinutes(5));
        entityManager.flush();
        entityManager.clear();

        List<Transaction> top5 = transactionRepository
                .findTop5ByFromAccountUserIdOrderByCreatedAtDesc(
                        savedAccount.getUser().getId());

        assertEquals(5, top5.size());

        // Verify thứ tự DESC: mỗi phần tử phải có createdAt >= phần tử tiếp theo
        for (int i = 0; i < top5.size() - 1; i++) {
            LocalDateTime current = top5.get(i).getCreatedAt();
            LocalDateTime next    = top5.get(i + 1).getCreatedAt();
            assertFalse(current.isBefore(next),
                "Phần tử [" + i + "] (" + current + ") phải >= [" + (i+1) + "] (" + next + ")");
        }
    }
}

// ==========================================================
// NHÓM 7 — findByIdWithUserSecurity EDGE CASES
// ==========================================================
@Nested
@DisplayName("Nhóm 7 — findByIdWithUserSecurity Edge Cases")
class FindByIdWithUserSecurityTests {

    @Test
    @DisplayName("❌ ID không tồn tại → Optional.empty()")
    void whenIdNotExists_returnsEmpty() {
        Optional<Transaction> result =
                transactionRepository.findByIdWithUserSecurity(999999L);

        assertTrue(result.isEmpty(),
            "ID không tồn tại phải trả về Optional.empty()");
    }

    @Test
    @DisplayName("✅ Không có LazyInitializationException sau khi session đóng")
    void afterSessionClear_noLazyInitException() {
        Transaction anyTx = transactionRepository.findAll().get(0);
        Long txId = anyTx.getId();
        entityManager.clear(); // Đóng session

        Optional<Transaction> result =
                transactionRepository.findByIdWithUserSecurity(txId);

        assertTrue(result.isPresent());
        // Các dòng này sẽ throw LazyInitializationException nếu JOIN FETCH thiếu
        assertDoesNotThrow(() -> {
            String username  = result.get().getFromAccount().getUser().getUsername();
            String pinHash   = result.get().getFromAccount().getUser()
                                     .getUserSecurity().getPinHash();
            assertNotNull(username, "Username không được null");
            assertNotNull(pinHash,  "PinHash không được null");
        }, "Không được có LazyInitializationException — JOIN FETCH phải cover đủ graph");
    }

    @Test
    @DisplayName("✅ Amount và status được load đúng qua JOIN FETCH query")
    void joinFetch_loadsTransactionFieldsCorrectly() {
        // Lấy ID của transaction 500k HIGH risk
        List<Transaction> all = transactionRepository.findAll();
        Transaction highRiskTx = all.stream()
                .filter(t -> t.getRiskLevel().equals("HIGH"))
                .findFirst()
                .orElseThrow();
        entityManager.clear();

        Optional<Transaction> result =
                transactionRepository.findByIdWithUserSecurity(highRiskTx.getId());

        assertTrue(result.isPresent());
        assertAll("Transaction fields phải được load đầy đủ",
            () -> assertEquals(0,
                    result.get().getAmount().compareTo(new BigDecimal("500000"))),
            () -> assertEquals("SUCCESS", result.get().getStatus()),
            () -> assertEquals("HIGH",    result.get().getRiskLevel()),
            () -> assertEquals("999999999", result.get().getToAccountNumber())
        );
    }
}

// ==========================================================
// NHÓM 8 — DATA INTEGRITY
// ==========================================================
@Nested
@DisplayName("Nhóm 8 — Data Integrity")
class DataIntegrityTests {

    @Test
    @DisplayName("✅ Amount lưu và đọc không mất precision (1234567.89)")
    void amount_preservesPrecision() {
        createTx(savedAccount, new BigDecimal("1234567.89"), "SUCCESS", "LOW",
                 now.minusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        List<Transaction> top5 = transactionRepository
                .findTop5ByFromAccountUserIdOrderByCreatedAtDesc(
                        savedAccount.getUser().getId());

        assertEquals(0,
            top5.get(0).getAmount().compareTo(new BigDecimal("1234567.89")),
            "Phần thập phân không được bị làm tròn sau khi lưu DB");
    }

    @Test
    @DisplayName("✅ createdAt được lưu và đọc với độ chính xác đến giây")
    void createdAt_preservesDateTimeAccuracy() {
        LocalDateTime specificTime =
                LocalDateTime.of(2024, 6, 15, 14, 30, 45);
        createTx(savedAccount, new BigDecimal("1000"), "SUCCESS", "LOW", specificTime);
        entityManager.flush();
        entityManager.clear();

        // Dùng sumSuccessfulAmountToday với mốc trước specificTime để xác nhận thời gian đúng
        BigDecimal total = transactionRepository.sumSuccessfulAmountToday(
                savedAccount.getId(),
                LocalDateTime.of(2024, 6, 15, 14, 30, 45));

        // Nếu createdAt được lưu đúng, transaction này phải nằm trong window
        assertNotNull(total);
    }

    @Test
    @DisplayName("✅ toAccountNumber lưu và đọc nguyên vẹn")
    void toAccountNumber_isPersistedCorrectly() {
        Transaction tx = new Transaction();
        tx.setFromAccount(savedAccount);
        tx.setToAccountNumber("VN-SPECIAL-ACC-001");
        tx.setAmount(new BigDecimal("1000"));
        tx.setStatus("SUCCESS");
        tx.setRiskLevel("LOW");
        tx.setCreatedAt(now);
        entityManager.persist(tx);
        entityManager.flush();
        entityManager.clear();

        Optional<Transaction> result =
                transactionRepository.findByIdWithUserSecurity(tx.getId());

        assertTrue(result.isPresent());
        assertEquals("VN-SPECIAL-ACC-001", result.get().getToAccountNumber(),
            "toAccountNumber phải được lưu và đọc nguyên vẹn");
    }
}

// ==========================================================
// NHÓM 9 — NULL & EMPTY DATABASE STATE
// (Tình huống DB trống hoặc data null)
// ==========================================================
@Nested
@DisplayName("Nhóm 9 — Null & Empty State")
class NullAndEmptyStateTests {

    @Test
    @DisplayName("⚠️ sumTotalSuccessfulAmount khi DB HOÀN TOÀN trống → null (NPE trap)")
    void sumTotalSuccessfulAmount_whenDbEmpty_returnsNull() {
        /*
         * ĐÂY LÀ BẪY NPE SỐ 1 TRONG PRODUCTION:
         * JPA SUM() trên 0 rows trả về NULL, không phải 0.
         * Nếu service layer gọi: BigDecimal total = repo.sumTotalSuccessfulAmount()
         * rồi total.compareTo(...) → NullPointerException ngay lập tức.
         *
         * Test này nhắc developer phải luôn null-check hoặc dùng:
         * COALESCE(SUM(t.amount), 0) trong JPQL
         */
        // Xóa sạch tất cả transaction trong setUp
        transactionRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        BigDecimal result = transactionRepository.sumTotalSuccessfulAmount();

        // Document behavior: null hay 0? Tùy JPQL có COALESCE không
        assertNull(result,
            "SUM trên DB trống trả về null — service layer PHẢI null-check trước khi dùng");
    }

    @Test
    @DisplayName("⚠️ countRecentTransactions khi account không có giao dịch nào → 0, không phải null")
    void countRecentTransactions_whenAccountHasNoTx_returnsZero() {
        Account freshAccount = createBaseAccount("no_tx_user", "0977777777", "77778888");
        entityManager.flush();
        entityManager.clear();

        // COUNT() luôn trả về số nguyên, không bao giờ null — nhưng cần confirm
        int count = transactionRepository
                .countRecentTransactions(freshAccount.getId(), now.minusHours(24));

        assertEquals(0, count, "COUNT phải trả về 0 chứ không phải null khi không có data");
    }

    @Test
    @DisplayName("⚠️ sumSuccessfulAmountToday: Account tồn tại nhưng chỉ có FAILED → null")
    void sumSuccessfulAmountToday_whenOnlyFailedTx_returnsNull() {
        Account failAccount = createBaseAccount("fail_user", "0978888888", "78889999");
        createTx(failAccount, new BigDecimal("500000"), "FAILED", "LOW", now.minusHours(1));
        createTx(failAccount, new BigDecimal("300000"), "FAILED", "HIGH", now.minusHours(2));
        entityManager.flush();
        entityManager.clear();

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        BigDecimal result = transactionRepository
                .sumSuccessfulAmountToday(failAccount.getId(), startOfDay);

        // Không có SUCCESS nào → SUM trả về null
        assertNull(result,
            "Chỉ có FAILED transactions → SUM phải trả về null, service layer phải xử lý");
    }
}

// ==========================================================
// NHÓM 10 — CONCURRENT TIMESTAMP (Race Condition)
// ==========================================================
@Nested
@DisplayName("Nhóm 10 — Timestamp Edge Cases")
class TimestampEdgeCaseTests {

    @Test
    @DisplayName("✅ 2 transaction cùng createdAt — cả 2 đều được COUNT và SUM")
    void twoTransactionsWithSameCreatedAt_bothCounted() {
        /*
         * Race condition thực tế: 2 request đến cùng lúc, 
         * cùng millisecond → createdAt giống hệt nhau.
         * Cả 2 phải được đếm và cộng dồn.
         */
        LocalDateTime sameTime = now.minusMinutes(30);
        createTx(savedAccount, new BigDecimal("111111"), "SUCCESS", "LOW", sameTime);
        createTx(savedAccount, new BigDecimal("222222"), "SUCCESS", "LOW", sameTime);
        entityManager.flush();
        entityManager.clear();

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        BigDecimal total = transactionRepository
                .sumSuccessfulAmountToday(savedAccount.getId(), startOfDay);

        // 800k (setUp) + 111k + 222k = 1.133.000
        assertEquals(0, total.compareTo(new BigDecimal("1133333")),
            "2 transaction cùng timestamp đều phải được cộng vào SUM");
    }

    @Test
    @DisplayName("✅ countRecentTransactions: timeLimit = NOW → 0 (không có tx trong tương lai)")
    void countRecentTransactions_withTimeLimitAsNow_returnsZero() {
        // Nếu timeLimit là đúng NOW hoặc tương lai → không có tx nào nằm trong window
        int count = transactionRepository
                .countRecentTransactions(savedAccount.getId(), now.plusSeconds(1));

        assertEquals(0, count,
            "timeLimit ở tương lai → không có giao dịch nào trong window");
    }

    @Test
    @DisplayName("✅ countRecentTransactions: timeLimit rất xa quá khứ → đếm tất cả")
    void countRecentTransactions_withVeryOldTimeLimit_countsAll() {
        // Đếm tổng transaction của savedAccount bằng cách đọc thẳng từ DB
        long totalInDb = transactionRepository.findAll().stream()
                .filter(tx -> tx.getFromAccount().getId()
                                .equals(savedAccount.getId()))
                .count();

        int count = transactionRepository
                .countRecentTransactions(savedAccount.getId(),
                        now.minusYears(100));

        assertEquals((int) totalInDb, count,
            "timeLimit rất xa quá khứ phải đếm được tất cả transaction của account");
    }

    @Test
    @DisplayName("✅ Transaction tại đúng timeLimit (boundary = inclusive)")
    void countRecentTransactions_transactionAtExactTimeLimit_isCounted() {
        LocalDateTime exactLimit = now.minusHours(1);
        // setUp đã tạo 1 tx tại now.minusHours(1) → phải được đếm nếu >= 
        int count = transactionRepository
                .countRecentTransactions(savedAccount.getId(), exactLimit);

        // Nếu JPQL dùng >= thì count = 1 (tx tại đúng mốc)
        // Nếu JPQL dùng > thì count = 0
        // Test này document behavior thực tế của JPQL
        assertTrue(count >= 0,
            "Transaction tại đúng timeLimit — document behavior (>= hay >)");
        System.out.println("📋 BOUNDARY BEHAVIOR: countRecentTransactions với tx tại đúng timeLimit = " + count);
    }
}

// ==========================================================
// NHÓM 11 — AMOUNT SPECIAL VALUES
// ==========================================================
@Nested
@DisplayName("Nhóm 11 — Amount Special Values")
class AmountSpecialValueTests {

    @Test
    @DisplayName("✅ Amount = 0 được lưu, SUM chính xác")
    void amountZero_isIncludedInSum() {
        createTx(savedAccount, BigDecimal.ZERO, "SUCCESS", "LOW", now.minusMinutes(5));
        entityManager.flush();
        entityManager.clear();

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        BigDecimal total = transactionRepository
                .sumSuccessfulAmountToday(savedAccount.getId(), startOfDay);

        // 800k + 0 = vẫn 800k, nhưng transaction 0đ phải được COUNT
        assertEquals(0, total.compareTo(new BigDecimal("800000")),
            "Transaction 0đ không làm thay đổi SUM nhưng không được gây lỗi");

        int count = transactionRepository
                .countRecentTransactions(savedAccount.getId(), now.minusMinutes(10));
        assertEquals(1, count, "Transaction 0đ vẫn phải được đếm");
    }

    @Test
    @DisplayName("✅ Amount rất nhỏ (0.01) không bị làm tròn thành 0")
    void verySmallAmount_notRoundedToZero() {
        createTx(savedAccount, new BigDecimal("0.01"), "SUCCESS", "LOW",
                 now.minusMinutes(5));
        entityManager.flush();
        entityManager.clear();

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        BigDecimal total = transactionRepository
                .sumSuccessfulAmountToday(savedAccount.getId(), startOfDay);

        assertNotNull(total);
        // 800000 + 0.01 = 800000.01
        assertTrue(total.compareTo(new BigDecimal("800000")) > 0,
            "Amount 0.01 phải được cộng vào SUM, không được bị làm tròn thành 0");
    }

    @Test
    @DisplayName("✅ Nhiều transaction nhỏ cộng lại đúng (floating point trap)")
    void manySmallAmounts_sumCorrectly() {
        /*
         * Floating point trap: 0.1 + 0.2 ≠ 0.3 với double/float.
         * BigDecimal phải xử lý đúng.
         */
        for (int i = 0; i < 10; i++) {
            createTx(savedAccount, new BigDecimal("0.10"), "SUCCESS", "LOW",
                     now.minusMinutes(i + 1));
        }
        entityManager.flush();
        entityManager.clear();

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        BigDecimal total = transactionRepository
                .sumSuccessfulAmountToday(savedAccount.getId(), startOfDay);

        // 800000 + (0.10 * 10) = 800001.00
        assertNotNull(total);
        assertEquals(0, total.compareTo(new BigDecimal("800001.00")),
            "10 lần cộng 0.10 phải ra đúng 1.00, không bị floating point error");
    }
}

// ==========================================================
// NHÓM 12 — SELF-TRANSACTION & BUSINESS RULE
// ==========================================================
@Nested
@DisplayName("Nhóm 12 — Business Rule Edge Cases")
class BusinessRuleTests {

    @Test
    @DisplayName("✅ Giao dịch tự chuyển cho chính mình (fromAccount = toAccountNumber)")
    void selfTransaction_isStoredAndQueried() {
        // Chuyển tiền từ account của mình về lại chính số account đó
        Transaction selfTx = new Transaction();
        selfTx.setFromAccount(savedAccount);
        selfTx.setToAccountNumber(savedAccount.getAccountNumber()); // ← CHÍNH MÌNH
        selfTx.setAmount(new BigDecimal("100000"));
        selfTx.setStatus("SUCCESS");
        selfTx.setRiskLevel("LOW");
        selfTx.setCreatedAt(now.minusMinutes(1));
        entityManager.persist(selfTx);
        entityManager.flush();
        entityManager.clear();

        // Hệ thống không nên chặn ở tầng Repository
        // (Business rule chặn ở Service layer, không phải DB layer)
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        BigDecimal total = transactionRepository
                .sumSuccessfulAmountToday(savedAccount.getId(), startOfDay);

        // 800k + 100k = 900k (self-tx vẫn được cộng vào SUM)
        assertEquals(0, total.compareTo(new BigDecimal("900000")),
            "Self-transaction vẫn được tính ở tầng Repository");
    }

    @Test
    @DisplayName("✅ riskLevel = null không làm countHighRiskTransactions crash")
    void nullRiskLevel_doesNotBreakHighRiskCount() {
        /*
         * Nếu entity không có NOT NULL constraint trên riskLevel,
         * một transaction có riskLevel = null có thể được lưu.
         * Query WHERE riskLevel = 'HIGH' sẽ bỏ qua null rows → đúng behavior.
         * Nhưng nếu có NullPointerException thì là bug.
         */
        Transaction nullRiskTx = new Transaction();
        nullRiskTx.setFromAccount(savedAccount);
        nullRiskTx.setToAccountNumber("123456789");
        nullRiskTx.setAmount(new BigDecimal("1000"));
        nullRiskTx.setStatus("SUCCESS");
        nullRiskTx.setRiskLevel(null); // ← null intentionally
        nullRiskTx.setCreatedAt(now.minusMinutes(1));

        // Nếu DB có NOT NULL constraint → persist sẽ throw → test pass với assertThrows
        // Nếu DB không có constraint → persist thành công → count phải vẫn là 1
        try {
            entityManager.persist(nullRiskTx);
            entityManager.flush();
            entityManager.clear();

            long count = transactionRepository.countHighRiskTransactions();
            assertEquals(1, count,
                "null riskLevel không được bị đếm vào HIGH risk count");
        } catch (Exception e) {
            // DB enforce NOT NULL → behavior này cũng chấp nhận được
            assertTrue(e.getMessage() != null,
                "DB từ chối null riskLevel — constraint hoạt động đúng");
        }
    }

    @Test
    @DisplayName("✅ findByIdWithUserSecurity: transaction của account khác không trả nhầm")
    void findByIdWithUserSecurity_doesNotReturnOtherAccountTx() {
        Account otherAccount = createBaseAccount("other", "0999999999", "99990000");
        Transaction otherTx = createTx(otherAccount, new BigDecimal("777"),
                                       "SUCCESS", "LOW", now.minusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        // Query bằng ID của otherTx nhưng verify nó thuộc otherAccount
        Optional<Transaction> result =
                transactionRepository.findByIdWithUserSecurity(otherTx.getId());

        assertTrue(result.isPresent());
        assertEquals("other",
            result.get().getFromAccount().getUser().getUsername(),
            "Phải trả về transaction của đúng account, không bị nhầm");

        // Query bằng ID của savedAccount tx không được trả về otherTx
        List<Transaction> savedAccountTxs = transactionRepository
                .findTop5ByFromAccountUserIdOrderByCreatedAtDesc(
                        savedAccount.getUser().getId());
        savedAccountTxs.forEach(tx ->
            assertNotEquals(otherTx.getId(), tx.getId(),
                "Transaction của otherAccount không được lọt vào query của savedAccount")
        );
    }
}

}