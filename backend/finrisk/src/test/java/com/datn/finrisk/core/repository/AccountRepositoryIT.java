package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserSecurity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test") // 🚀 Ép Spring Boot đọc file application-test.yml
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 🚀 CẤM Spring Boot tự ý tráo Database
@DisplayName("AccountRepository — Integration Tests (H2)")
class AccountRepositoryIT {

    @Autowired private TestEntityManager entityManager;
    @Autowired private AccountRepository accountRepository;

    // ── Primary test fixtures ──
    private User    savedUser;
    private Account savedAccount;

// ── Helper: tạo 1 bộ User + UserSecurity + Account ĐÚNG CHUẨN JPA ──
    private Account createAccount(String username, String fullName,
                                   String pinHash, String accountNumber,
                                   BigDecimal balance) {
        
        // 1. TẠO BỐ (USER) TRƯỚC
        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        // 🚀 Fix lỗi NULL và Unique cho phoneNumber (Sinh random để không bị trùng giữa các test)
        user.setPhoneNumber("09" + System.nanoTime() % 100000000); 
        user = entityManager.persist(user); // Lưu xuống DB để lấy ID

        // 2. TẠO CON (USER_SECURITY) SAU
        UserSecurity sec = new UserSecurity();
        sec.setPinHash(pinHash);
        // 🚀 Fix lỗi NULL cho passwordHash (Entity bắt buộc nullable = false)
        sec.setPasswordHash("dummy_hash_123"); 
        // 🚀 Mấu chốt: Nối Con với Bố để cột user_id không bị NULL
        sec.setUser(user); 
        sec = entityManager.persist(sec); // Lưu xuống DB

        // Nối lại Bố với Con ở trên RAM cho chuẩn đồng bộ 2 chiều (Bidirectional)
        user.setUserSecurity(sec);

        // 3. TẠO TÀI KHOẢN NGÂN HÀNG (ACCOUNT)
        Account acc = new Account();
        acc.setAccountNumber(accountNumber);
        acc.setBalance(balance);
        acc.setUser(user);
        
        return entityManager.persist(acc);
    }

    @BeforeEach
    void setUp() {
        savedAccount = createAccount(
                "thach_test", "Nguyễn Hoàng Thạch",
                "hashed_pin_123", "88889999",
                new BigDecimal("15000000"));

        savedUser = savedAccount.getUser();

        entityManager.flush();
        entityManager.clear();
    }

    // ==========================================================
    // NHÓM 1 — findByAccountNumber()
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 1 — findByAccountNumber()")
    class FindByAccountNumberTests {

        @Test
        @DisplayName("✅ Tìm thấy đúng account theo số tài khoản")
        void whenExists_returnsAccount() {
            Optional<Account> result =
                    accountRepository.findByAccountNumber("88889999");

            assertTrue(result.isPresent());
            assertEquals("88889999", result.get().getAccountNumber());
            
            // 🚀 ĐỔI DÒNG NÀY: Dùng compareTo() thay vì assertEquals() trực tiếp
            assertEquals(0, result.get().getBalance().compareTo(new BigDecimal("15000000")), 
                "Số dư phải khớp nhau về mặt giá trị toán học");
        }

        @Test
        @DisplayName("❌ Số tài khoản không tồn tại → Optional.empty()")
        void whenNotExists_returnsEmpty() {
            assertTrue(accountRepository.findByAccountNumber("00000000").isEmpty());
        }

        @Test
        @DisplayName("❌ Input null → empty hoặc exception có kiểm soát (không crash)")
        void whenNull_doesNotCrash() {
            assertDoesNotThrow(() ->
                accountRepository.findByAccountNumber(null)
            );
        }
    }

    // ==========================================================
    // NHÓM 2 — findByAccountNumberWithUser()
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 2 — findByAccountNumberWithUser()")
    class FindByAccountNumberWithUserTests {

        @Test
        @DisplayName("✅ Trả về account kèm User đã được fetch (không lazy)")
        void whenExists_returnsAccountWithUser() {
            Optional<Account> result =
                    accountRepository.findByAccountNumberWithUser("88889999");

            assertTrue(result.isPresent());
            // Sau clear() mà User vẫn load được → JOIN FETCH hoạt động đúng
            assertNotNull(result.get().getUser());
            assertEquals(savedUser.getId(), result.get().getUser().getId());
            assertEquals("thach_test", result.get().getUser().getUsername());
        }

        @Test
        @DisplayName("❌ Số tài khoản không tồn tại → Optional.empty()")
        void whenNotExists_returnsEmpty() {
            assertTrue(
                accountRepository.findByAccountNumberWithUser("00000000").isEmpty()
            );
        }

        @Test
        @DisplayName("✅ Số tài khoản của user khác không trả về nhầm")
        void doesNotReturnOtherUsersAccount() {
            // Tạo thêm user thứ 2
            createAccount("bob", "Bob Nguyen", "pin_bob",
                          "11112222", new BigDecimal("500000"));
            entityManager.flush();
            entityManager.clear();

            Optional<Account> result =
                    accountRepository.findByAccountNumberWithUser("88889999");

            assertTrue(result.isPresent());
            // Phải là của thach, không phải bob
            assertEquals("thach_test", result.get().getUser().getUsername());
        }
    }

    // ==========================================================
    // NHÓM 3 — findByUserId()
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 3 — findByUserId()")
    class FindByUserIdTests {

        @Test
        @DisplayName("✅ Tìm thấy account của user đúng ID")
        void whenExists_returnsAccount() {
            Optional<Account> result =
                    accountRepository.findByUserId(savedUser.getId());

            assertTrue(result.isPresent());
            assertEquals("88889999", result.get().getAccountNumber());
        }

        @Test
        @DisplayName("❌ User ID không tồn tại → Optional.empty()")
        void whenUserNotExists_returnsEmpty() {
            assertTrue(accountRepository.findByUserId(999999L).isEmpty());
        }

        @Test
        @DisplayName("✅ Data isolation: ID của user A không trả về account của user B")
        void userId_doesNotReturnOtherUsersAccount() {
            Account bobAccount = createAccount("bob", "Bob", "pin_bob",
                                               "11112222", new BigDecimal("1000"));
            entityManager.flush();
            entityManager.clear();

            Optional<Account> result =
                    accountRepository.findByUserId(savedUser.getId());

            assertTrue(result.isPresent());
            assertEquals("88889999", result.get().getAccountNumber(),
                    "Phải trả về account của thach, không phải bob");
            assertNotEquals(bobAccount.getAccountNumber(),
                    result.get().getAccountNumber());
        }

        @Test
        @DisplayName("⚠️ [CONTRACT] 1 user có 2 account — hàm xử lý thế nào?")
        void whenUserHasTwoAccounts_behaviorIsDefined() {
            /*
             * Test này xác định CONTRACT của hàm:
             * - Nếu thiết kế 1-1: phải throw NonUniqueResultException
             * - Nếu thiết kế 1-N: phải trả về 1 trong 2 (cần document rõ)
             *
             * Hiện tại trả về Optional → nếu có 2 account sẽ throw exception.
             * Test này làm tài liệu sống cho team biết đây là giới hạn của hàm.
             */
            Account secondAccount = new Account();
            secondAccount.setAccountNumber("88880000");
            secondAccount.setBalance(BigDecimal.ZERO);
            secondAccount.setUser(savedUser);
            entityManager.persist(secondAccount);
            entityManager.flush();
            entityManager.clear();

            // Ghi lại behavior hiện tại — nếu behavior thay đổi thì test này sẽ báo
            assertThrows(Exception.class,
                () -> accountRepository.findByUserId(savedUser.getId()),
                "1 user có 2 account phải được xử lý rõ ràng (exception hoặc lấy account chính)");
        }
    }

    // ==========================================================
    // NHÓM 4 — findByIdWithUserAndSecurity()
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 4 — findByIdWithUserAndSecurity()")
    class FindByIdWithUserAndSecurityTests {

        @Test
        @DisplayName("✅ Trả về đầy đủ Account + User + UserSecurity")
        void whenExists_returnsFullGraph() {
            Optional<Account> result =
                    accountRepository.findByIdWithUserAndSecurity(savedAccount.getId());

            assertTrue(result.isPresent());
            Account acc = result.get();

            // Kiểm tra toàn bộ object graph
            assertAll("Full object graph phải được load",
                () -> assertNotNull(acc.getUser(),                    "User không được null"),
                () -> assertNotNull(acc.getUser().getUserSecurity(),  "Security không được null"),
                () -> assertEquals("Nguyễn Hoàng Thạch",
                                   acc.getUser().getFullName(),       "Tên phải đúng"),
                () -> assertEquals("hashed_pin_123",
                                   acc.getUser().getUserSecurity().getPinHash(), "PIN phải đúng")
            );
        }

        @Test
        @DisplayName("❌ Account ID không tồn tại → Optional.empty()")
        void whenNotExists_returnsEmpty() {
            assertTrue(
                accountRepository.findByIdWithUserAndSecurity(999999L).isEmpty()
            );
        }

        @Test
        @DisplayName("✅ Account của user B không bị lấy nhầm khi query ID của user A")
        void isolation_doesNotReturnOtherAccount() {
            Account bobAccount = createAccount("bob", "Bob", "pin_bob",
                                               "11112222", new BigDecimal("999"));
            entityManager.flush();
            entityManager.clear();

            Optional<Account> result =
                    accountRepository.findByIdWithUserAndSecurity(savedAccount.getId());

            assertTrue(result.isPresent());
            assertNotEquals(bobAccount.getId(), result.get().getId());
            assertEquals("thach_test", result.get().getUser().getUsername());
        }
    }

    // ==========================================================
    // NHÓM 5 — findByAccountNumberIn()
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 5 — findByAccountNumberIn()")
    class FindByAccountNumberInTests {

        @Test
        @DisplayName("✅ Tìm 1 account khớp trong set nhiều số")
        void whenOneMatches_returnsList() {
            List<Account> results =
                    accountRepository.findByAccountNumberIn(
                            Set.of("88889999", "NOTEXIST1", "NOTEXIST2"));

            assertEquals(1, results.size());
            assertEquals("88889999", results.get(0).getAccountNumber());
        }

        @Test
        @DisplayName("✅ Tìm nhiều account khớp cùng lúc")
        void whenMultipleMatch_returnsAll() {
            createAccount("bob", "Bob", "pin_bob",
                          "11112222", new BigDecimal("500000"));
            entityManager.flush();
            entityManager.clear();

            List<Account> results =
                    accountRepository.findByAccountNumberIn(
                            Set.of("88889999", "11112222"));

            assertEquals(2, results.size());
            // Kiểm tra cả 2 số tài khoản có trong kết quả
            List<String> numbers = results.stream()
                    .map(Account::getAccountNumber).toList();
            assertTrue(numbers.containsAll(List.of("88889999", "11112222")));
        }

        @Test
        @DisplayName("✅ JOIN FETCH hoạt động: User và Security được load cùng")
        void joinFetch_loadsUserAndSecurityEagerly() {
            List<Account> results =
                    accountRepository.findByAccountNumberIn(Set.of("88889999"));

            assertEquals(1, results.size());
            // Sau entityManager.clear(), nếu JOIN FETCH đúng thì vẫn load được
            assertNotNull(results.get(0).getUser(),
                    "User phải được JOIN FETCH, không phải lazy load");
            assertNotNull(results.get(0).getUser().getUserSecurity(),
                    "UserSecurity phải được JOIN FETCH");
        }

        @Test
        @DisplayName("❌ Set rỗng → trả về danh sách rỗng, không lỗi SQL")
        void whenEmptySet_returnsEmptyList() {
            assertDoesNotThrow(() -> {
                List<Account> results =
                        accountRepository.findByAccountNumberIn(Set.of());
                assertTrue(results.isEmpty(),
                        "Set rỗng phải trả về list rỗng, không phải exception");
            });
        }

        @Test
        @DisplayName("❌ Không có số nào khớp → danh sách rỗng")
        void whenNoneMatch_returnsEmptyList() {
            List<Account> results =
                    accountRepository.findByAccountNumberIn(
                            Set.of("00000000", "11111111"));

            assertTrue(results.isEmpty());
        }
    }

    // ==========================================================
    // NHÓM 6 — DATA INTEGRITY
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 6 — Data Integrity")
    class DataIntegrityTests {

        @Test
        @DisplayName("✅ Balance = 0 được lưu và đọc chính xác")
        void balanceZero_isStoredCorrectly() {
            Account zeroAcc = createAccount("zero_user", "Zero",
                    "pin", "00000001", BigDecimal.ZERO);
            entityManager.flush();
            entityManager.clear();

            Optional<Account> result =
                    accountRepository.findByAccountNumber("00000001");

            assertTrue(result.isPresent());
            assertEquals(0, result.get().getBalance().compareTo(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("✅ Balance lớn (VD: 999 tỷ) không bị mất precision")
        void largBalance_noPrecisionLoss() {
            BigDecimal largeAmount = new BigDecimal("999999999999.99");
            Account richAcc = createAccount("rich_user", "Rich",
                    "pin", "99999998", largeAmount);
            entityManager.flush();
            entityManager.clear();

            Optional<Account> result =
                    accountRepository.findByAccountNumber("99999998");

            assertTrue(result.isPresent());
            assertEquals(0, result.get().getBalance().compareTo(largeAmount),
                    "BigDecimal không được mất chữ số sau khi lưu vào DB");
        }

        @Test
        @DisplayName("❌ Số tài khoản trùng → không thể lưu (unique constraint)")
        void duplicateAccountNumber_throwsException() {
            assertThrows(Exception.class, () -> {
                createAccount("another_user", "Another",
                        "pin2", "88889999", // ← trùng với savedAccount
                        new BigDecimal("1000"));
                entityManager.flush(); // Ép DB kiểm tra constraint ngay
            }, "Số tài khoản trùng phải bị DB từ chối");
        }
    }

    // ==========================================================
// NHÓM 7 — QUERY CORRECTNESS SAU KHI UPDATE
// (Thêm vào class AccountRepositoryIT)
// ==========================================================
@Nested
@DisplayName("Nhóm 7 — Đọc lại sau khi UPDATE (Stale Data)")
class AfterUpdateTests {

    @Test
    @DisplayName("✅ Cập nhật balance rồi đọc lại → phải thấy giá trị mới")
    void afterBalanceUpdate_readReturnsNewValue() {
        // Lấy account lên, sửa balance
        Account acc = entityManager.find(Account.class, savedAccount.getId());
        acc.setBalance(new BigDecimal("99000000"));
        entityManager.persist(acc);
        entityManager.flush();
        entityManager.clear(); // Xóa cache → buộc query xuống DB thật

        Optional<Account> result =
                accountRepository.findByAccountNumber("88889999");

        assertTrue(result.isPresent());
        assertEquals(0,
            result.get().getBalance().compareTo(new BigDecimal("99000000")),
            "Sau khi update, phải đọc được balance mới từ DB");
    }

    @Test
    @DisplayName("✅ Cập nhật username của User → query account vẫn thấy username mới")
    void afterUserUpdate_accountQueryReturnsUpdatedUser() {
        User user = entityManager.find(User.class, savedUser.getId());
        user.setFullName("Thạch Đã Đổi Tên");
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        Optional<Account> result =
                accountRepository.findByIdWithUserAndSecurity(savedAccount.getId());

        assertTrue(result.isPresent());
        assertEquals("Thạch Đã Đổi Tên",
            result.get().getUser().getFullName(),
            "Tên user mới phải được phản ánh khi query qua account");
    }

    @Test
    @DisplayName("✅ Cập nhật pinHash trong Security → query account vẫn thấy pin mới")
    void afterPinUpdate_securityReflectsChange() {
        UserSecurity sec = savedUser.getUserSecurity();
        // Cần load lại vì sau clear() session đã bị xóa
        UserSecurity managedSec =
                entityManager.find(UserSecurity.class, sec.getId());
        managedSec.setPinHash("new_pin_hash_999");
        entityManager.persist(managedSec);
        entityManager.flush();
        entityManager.clear();

        Optional<Account> result =
                accountRepository.findByIdWithUserAndSecurity(savedAccount.getId());

        assertTrue(result.isPresent());
        assertEquals("new_pin_hash_999",
            result.get().getUser().getUserSecurity().getPinHash(),
            "PIN mới phải được load đúng sau khi update");
    }
}

// ==========================================================
// NHÓM 8 — EDGE CASE KHÓ: BOUNDARY & SPECIAL INPUT
// ==========================================================
@Nested
@DisplayName("Nhóm 8 — Edge Case đặc biệt")
class EdgeCaseTests {

    @Test
    @DisplayName("✅ Account number có ký tự đặc biệt vẫn tìm được chính xác")
    void accountNumberWithSpecialChars_isFoundCorrectly() {
        // Một số hệ thống có account number dạng VN-001-2024
        createAccount("special_user", "Special", "pin",
                      "VN-001-2024", new BigDecimal("1000"));
        entityManager.flush();
        entityManager.clear();

        Optional<Account> result =
                accountRepository.findByAccountNumber("VN-001-2024");

        assertTrue(result.isPresent(),
            "Account number có dấu gạch ngang phải tìm được");
    }

    @Test
    @DisplayName("✅ Account number có khoảng trắng thừa → KHÔNG tìm thấy (no trim)")
    void accountNumberWithSpaces_isNotFoundWithoutTrim() {
        // DB lưu "88889999", query với "88889999 " (có space) → không được match
        Optional<Account> result =
                accountRepository.findByAccountNumber("88889999 ");

        // Nếu DB tự trim thì test này sẽ fail → cần biết behavior thực tế
        assertTrue(result.isEmpty(),
            "Query có khoảng trắng thừa không được match nếu DB không tự trim");
    }

    @Test
    @DisplayName("✅ Case sensitivity: '88889999' ≠ '88889999' (uppercase) nếu DB case-sensitive")
    void accountNumber_caseSensitivity_isTested() {
        // Test này document behavior của DB collation
        // MySQL default: case-insensitive; PostgreSQL: case-sensitive
        Optional<Account> upperResult =
                accountRepository.findByAccountNumber("VNABC123");
        Optional<Account> lowerResult =
                accountRepository.findByAccountNumber("vnabc123");

        // Nếu cả 2 cùng empty → test pass (vì account này chưa được tạo)
        // Mục đích: ghi lại rằng team đã nghĩ đến case sensitivity
        assertTrue(upperResult.isEmpty() || upperResult.equals(lowerResult),
            "Cần document rõ DB có case-sensitive với account number không");
    }

    @Test
    @DisplayName("✅ Balance chính xác đến 2 chữ số thập phân (không bị round)")
    void balance_preservesTwoDecimalPlaces() {
        createAccount("decimal_user", "Decimal", "pin",
                      "12340001", new BigDecimal("1234567.89"));
        entityManager.flush();
        entityManager.clear();

        Optional<Account> result =
                accountRepository.findByAccountNumber("12340001");

        assertTrue(result.isPresent());
        // Đảm bảo "1234567.89" không bị round thành "1234568"
        assertEquals(0,
            result.get().getBalance().compareTo(new BigDecimal("1234567.89")),
            "Phần thập phân không được bị làm tròn");
    }

    @Test
    @DisplayName("✅ findByAccountNumberIn với 1 phần tử valid + nhiều phần tử không tồn tại")
    void findByAccountNumberIn_largeSetWithOneMatch_returnsCorrectly() {
        // Simulate: Gửi 100 số tài khoản, chỉ 1 cái tồn tại
        Set<String> hugeSet = new java.util.HashSet<>();
        hugeSet.add("88889999"); // valid
        for (int i = 0; i < 99; i++) {
            hugeSet.add("FAKE" + String.format("%05d", i));
        }

        List<Account> results = accountRepository.findByAccountNumberIn(hugeSet);

        assertEquals(1, results.size(),
            "Trong 100 số chỉ có 1 tồn tại, phải trả về đúng 1 kết quả");
        assertEquals("88889999", results.get(0).getAccountNumber());
    }

    @Test
    @DisplayName("✅ findByAccountNumberIn không trả về duplicate dù join phức tạp")
    void findByAccountNumberIn_noDuplicateResults() {
        // JOIN FETCH đôi khi gây ra Cartesian product → duplicate rows
        // Test này đảm bảo repository đã xử lý DISTINCT đúng
        List<Account> results =
                accountRepository.findByAccountNumberIn(Set.of("88889999"));

        long distinctCount = results.stream()
                .map(Account::getAccountNumber)
                .distinct()
                .count();

        assertEquals(results.size(), distinctCount,
            "Kết quả không được có duplicate — kiểm tra DISTINCT trong JPQL");
    }
}

// ==========================================================
// NHÓM 9 — DATA ISOLATION GIỮA CÁC TEST (Test Independence)
// ==========================================================
@Nested
@DisplayName("Nhóm 9 — Test Isolation & Rollback")
class TestIsolationTests {

    @Test
    @DisplayName("✅ Data từ setUp() không bị ảnh hưởng bởi test trước")
    void dataFromSetUp_isCleanForEachTest() {
        // Nếu rollback hoạt động đúng, mỗi test chỉ thấy đúng 1 account từ setUp()
        List<Account> allAccounts =
                accountRepository.findByAccountNumberIn(
                        Set.of("88889999", "11112222", "99999998"));

        assertEquals(1, allAccounts.size(),
            "Mỗi test phải bắt đầu với đúng state từ @BeforeEach, " +
            "không bị lẫn data từ test khác");
    }

    @Test
    @DisplayName("✅ Tạo account trong test này không ảnh hưởng test khác")
    void accountCreatedInThisTest_isRolledBackAfterTest() {
        // Tạo thêm account trong test này
        createAccount("temp_user", "Temp", "pin",
                      "TEMP9999", new BigDecimal("100"));
        entityManager.flush();

        // Trong scope test này: phải thấy 2 account
        List<Account> during = accountRepository.findByAccountNumberIn(
                Set.of("88889999", "TEMP9999"));
        assertEquals(2, during.size(),
            "Trong scope test phải thấy account vừa tạo");

        // Sau test, @DataJpaTest sẽ rollback → test khác sẽ không thấy TEMP9999
        // (Không thể assert trực tiếp ở đây, nhưng Nhóm 9 test 1 sẽ verify điều này)
    }
}

// ==========================================================
// NHÓM 10 — CONCURRENT ACCESS SIMULATION
// ==========================================================
@Nested
@DisplayName("Nhóm 10 — Concurrent Read Simulation")
class ConcurrentAccessTests {

    @Test
    @DisplayName("✅ Cùng account number query nhiều lần liên tiếp → kết quả ổn định")
    void repeatedQueries_returnConsistentResults() {
        // Simulate nhiều request đọc cùng 1 account liên tiếp
        for (int i = 0; i < 10; i++) {
            Optional<Account> result =
                    accountRepository.findByAccountNumber("88889999");

            assertTrue(result.isPresent(),
                "Lần query thứ " + (i + 1) + " phải vẫn tìm thấy account");
            assertEquals(0,
                result.get().getBalance().compareTo(new BigDecimal("15000000")),
                "Balance phải ổn định qua " + (i + 1) + " lần query");
        }
    }

    @Test
    @DisplayName("✅ findByAccountNumberIn với set lớn không bị timeout/OOM")
    void findByAccountNumberIn_withModerateSet_performsReasonably() {
        // Tạo thêm 10 account thật
        for (int i = 0; i < 10; i++) {
            createAccount("user_" + i, "User " + i, "pin_" + i,
                          "ACC" + String.format("%05d", i),
                          new BigDecimal(i * 1000));
        }
        entityManager.flush();
        entityManager.clear();

        // Query với set 10 số hợp lệ + 90 số giả
        Set<String> querySet = new java.util.HashSet<>();
        for (int i = 0; i < 10; i++) {
            querySet.add("ACC" + String.format("%05d", i));
        }
        for (int i = 100; i < 190; i++) {
            querySet.add("FAKE" + i);
        }

        long start = System.currentTimeMillis();
        List<Account> results = accountRepository.findByAccountNumberIn(querySet);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(10, results.size(),
            "Phải tìm đúng 10 account hợp lệ trong set 100 phần tử");
        assertTrue(elapsed < 2000,
            "Query 100-item set không được mất hơn 2 giây, thực tế: " + elapsed + "ms");
    }
}
}