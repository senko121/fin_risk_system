package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.RiskPolicy;
import com.datn.finrisk.core.entities.SystemConfigLog;
import com.datn.finrisk.core.repository.RiskPolicyRepository;
import com.datn.finrisk.core.repository.SystemConfigLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RiskPolicyService — Unit Tests (Full Suite)")
class RiskPolicyServiceTest {

    @Mock private RiskPolicyRepository riskPolicyRepository;
    @Mock private SystemConfigLogRepository configLogRepository;

    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private RiskPolicyService riskPolicyService;

    private RiskPolicy mockPolicy;

    @BeforeEach
    void setUp() throws Exception {  
        mockPolicy = new RiskPolicy();
        mockPolicy.setId(1L);
        mockPolicy.setMinScore(0);
        mockPolicy.setMaxScore(40);
        mockPolicy.setActionBeanName("LOW_RISK_ACTION");  
 
        lenient().when(objectMapper.writeValueAsString(any())).thenAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg instanceof RiskPolicy) {
                RiskPolicy p = (RiskPolicy) arg;
 
                return "{\"minScore\":" + p.getMinScore() + "}";
            }
            return "{}";
        });
    }

    // ====================================================================
    // NHÓM 1: getAllPolicies
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 1 — getAllPolicies")
    class GetAllPoliciesTests {

        // ==========================================================
        // TEST 1: Happy path — trả về list từ repository
        // ==========================================================
        @Test
        @DisplayName("✅ DB có dữ liệu → trả về đúng danh sách policy")
        void getAllPolicies_hasData_returnsList() {
            RiskPolicy p1 = new RiskPolicy(); p1.setId(1L);
            RiskPolicy p2 = new RiskPolicy(); p2.setId(2L);
            when(riskPolicyRepository.findAll()).thenReturn(Arrays.asList(p1, p2));

            List<RiskPolicy> result = riskPolicyService.getAllPolicies();

            assertEquals(2, result.size());
            verify(riskPolicyRepository, times(1)).findAll();
        }

        // ==========================================================
        // TEST 2: DB rỗng → trả về list rỗng, không crash
        // Tại sao quan trọng: Caller thường .stream() hoặc forEach trên kết quả —
        //   list rỗng an toàn hơn null. Đảm bảo service không tự thêm null check
        //   hay ném exception khi chưa có policy nào.
        // ==========================================================
        @Test
        @DisplayName("✅ DB không có policy nào → trả về list rỗng, không ném exception")
        void getAllPolicies_emptyDb_returnsEmptyList() {
            when(riskPolicyRepository.findAll()).thenReturn(Collections.emptyList());

            List<RiskPolicy> result = riskPolicyService.getAllPolicies();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        // ==========================================================
        // TEST 3: Service delegate đúng sang repository, không xử lý gì thêm
        // Tại sao quan trọng: getAllPolicies phải là pure delegate — không filter,
        //   không sort, không transform. Nếu sau này ai thêm logic ẩn, test này fail.
        // ==========================================================
        @Test
        @DisplayName("✅ getAllPolicies chỉ delegate sang findAll(), không filter hay transform")
        void getAllPolicies_delegatesToRepository() {
            riskPolicyService.getAllPolicies();

            verify(riskPolicyRepository, times(1)).findAll();
            verifyNoMoreInteractions(riskPolicyRepository);
            verifyNoInteractions(configLogRepository);
        }

        // ==========================================================
        // TEST 4: Repository ném exception → exception nổi lên caller, không bị nuốt
        // Tại sao quan trọng: getAllPolicies không có try-catch — exception phải
        //   thoát ra để caller (controller) xử lý và trả về HTTP 500 đúng.
        // ==========================================================
        @Test
        @DisplayName("❌ Repository ném RuntimeException → exception thoát ra ngoài service")
        void getAllPolicies_repositoryThrows_propagatesException() {
            when(riskPolicyRepository.findAll()).thenThrow(new RuntimeException("DB connection lost"));

            assertThrows(RuntimeException.class, () -> riskPolicyService.getAllPolicies());
        }
    }

    // ====================================================================
    // NHÓM 2: updatePolicyThresholds — Happy Path
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 2 — updatePolicyThresholds: Happy Path")
    class UpdatePolicyHappyPathTests {

        @BeforeEach
        void setUpHappyPath() {
            when(riskPolicyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
            // save() trả về chính object đã được set min/max mới
            when(riskPolicyRepository.save(any(RiskPolicy.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        // ==========================================================
        // TEST 5: Cập nhật thành công → trả về savedPolicy với giá trị mới
        // ==========================================================
        @Test
        @DisplayName("✅ Cập nhật min/max hợp lệ → trả về policy đã được lưu")
        void updatePolicyThresholds_validInput_returnsSavedPolicy() {
            RiskPolicy result = riskPolicyService.updatePolicyThresholds(1L, 10, 50, "admin");

            assertNotNull(result);
            assertEquals(10, result.getMinScore());
            assertEquals(50, result.getMaxScore());
        }

        // ==========================================================
        // TEST 6: minScore và maxScore được set đúng lên entity trước khi save
        // Tại sao quan trọng: Tránh trường hợp code set sai field
        //   (ví dụ: setMinScore(newMax) nhầm) — lỗi im lặng không có exception.
        // ==========================================================
        @Test
        @DisplayName("✅ minScore và maxScore được ghi đúng lên entity trước khi save")
        void updatePolicyThresholds_setsCorrectFieldsOnEntity() {
            ArgumentCaptor<RiskPolicy> policyCaptor = ArgumentCaptor.forClass(RiskPolicy.class);

            riskPolicyService.updatePolicyThresholds(1L, 15, 60, "admin");

            verify(riskPolicyRepository).save(policyCaptor.capture());
            RiskPolicy saved = policyCaptor.getValue();
            assertEquals(15, saved.getMinScore(), "minScore phải là giá trị mới 15");
            assertEquals(60, saved.getMaxScore(), "maxScore phải là giá trị mới 60");
        }

        // ==========================================================
        // TEST 7: actionBeanName KHÔNG bị thay đổi sau update
        // Tại sao quan trọng: Comment trong code ghi rõ "cấm sửa actionBeanName".
        //   Test này làm cứng quy tắc đó — nếu ai sửa code thêm
        //   policy.setActionBeanName(...) thì test này fail ngay.
        // ==========================================================
        @Test
        @DisplayName("🔒 actionBeanName KHÔNG bị thay đổi sau khi update (chỉ được sửa min/max)")
        void updatePolicyThresholds_doesNotModifyActionBeanName() {
            String originalBeanName = mockPolicy.getActionBeanName();

            riskPolicyService.updatePolicyThresholds(1L, 10, 50, "admin");

            assertEquals(originalBeanName, mockPolicy.getActionBeanName(),
                    "actionBeanName phải giữ nguyên — đây là trường bảo vệ không cho sửa");
        }

        // ==========================================================
        // TEST 8: configLogRepository.save() được gọi đúng 1 lần
        // Tại sao quan trọng: Mỗi lần update phải tạo đúng 1 audit log.
        //   Gọi 0 lần = mất trace, gọi 2 lần = duplicate record.
        // ==========================================================
        @Test
        @DisplayName("✅ Audit log được ghi đúng 1 lần sau khi update thành công")
        void updatePolicyThresholds_savesAuditLogExactlyOnce() {
            riskPolicyService.updatePolicyThresholds(1L, 10, 50, "admin");

            verify(configLogRepository, times(1)).save(any(SystemConfigLog.class));
        }

        // ==========================================================
        // TEST 9: Log ghi đúng adminUsername và action
        // ==========================================================
        @Test
        @DisplayName("✅ Audit log chứa đúng adminUsername và action 'UPDATE_POLICY'")
        void updatePolicyThresholds_logContainsCorrectAdminAndAction() {
            ArgumentCaptor<SystemConfigLog> logCaptor = ArgumentCaptor.forClass(SystemConfigLog.class);

            riskPolicyService.updatePolicyThresholds(1L, 10, 50, "superadmin_01");

            verify(configLogRepository).save(logCaptor.capture());
            SystemConfigLog log = logCaptor.getValue();
            assertEquals("superadmin_01", log.getAdminUsername(),
                    "Log phải ghi đúng tên admin thực hiện thao tác");
            assertEquals("UPDATE_POLICY", log.getActionType(),
                    "Action trong log phải là 'UPDATE_POLICY'");
        }

        // ==========================================================
        // TEST 10: Log ghi đúng tableName và entityId
        // ==========================================================
        @Test
        @DisplayName("✅ Audit log chứa đúng tableName='risk_policies' và entityId")
        void updatePolicyThresholds_logContainsCorrectTableAndEntityId() {
            ArgumentCaptor<SystemConfigLog> logCaptor = ArgumentCaptor.forClass(SystemConfigLog.class);

            riskPolicyService.updatePolicyThresholds(1L, 10, 50, "admin");

            verify(configLogRepository).save(logCaptor.capture());
            SystemConfigLog log = logCaptor.getValue();
            assertEquals("risk_policies", log.getTargetTable());

            assertEquals(1L, log.getTargetId());
        }

        // ==========================================================
        // TEST 11: oldJson chứa data CŨ, newJson chứa data MỚI
        // Tại sao quan trọng: Đây là phần cốt lõi của audit log — nếu old/new bị swap
        //   hoặc cả hai đều là cùng một snapshot, admin không thể rollback được.
        // ==========================================================
        @Test
        @DisplayName("✅ oldJson chứa minScore CŨ (0), newJson chứa minScore MỚI (25)")
        void updatePolicyThresholds_logContainsCorrectOldAndNewJson() {
            ArgumentCaptor<SystemConfigLog> logCaptor = ArgumentCaptor.forClass(SystemConfigLog.class);
            mockPolicy.setMinScore(0); // Giá trị cũ

            riskPolicyService.updatePolicyThresholds(1L, 25, 80, "admin");

            verify(configLogRepository).save(logCaptor.capture());
            SystemConfigLog log = logCaptor.getValue();

            // oldJson phải chứa giá trị trước khi update
            assertNotNull(log.getOldValue(), "oldJson không được null");
            assertTrue(log.getOldValue().contains("0") || log.getOldValue().contains("\"minScore\":0"),
                    "oldJson phải chứa minScore cũ = 0");

            // newJson phải chứa giá trị sau khi update
            assertNotNull(log.getNewValue(), "newJson không được null");
            assertTrue(log.getNewValue().contains("25") || log.getNewValue().contains("\"minScore\":25"),
                    "newJson phải chứa minScore mới = 25");

            // oldJson và newJson phải khác nhau
            assertNotEquals(log.getOldValue(), log.getNewValue(),
                    "oldJson và newJson không được giống nhau — nếu giống nhau thì log vô nghĩa");
        }
    }

    // ====================================================================
    // NHÓM 3: updatePolicyThresholds — Validation
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 3 — updatePolicyThresholds: Validation")
    class UpdatePolicyValidationTests {

        // ==========================================================
        // TEST 12: Policy ID không tồn tại trong DB
        // ==========================================================
        @Test
        @DisplayName("❌ ID không tồn tại → ném RuntimeException với message chứa ID")
        void updatePolicyThresholds_idNotFound_throwsRuntimeException() {
            when(riskPolicyRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> riskPolicyService.updatePolicyThresholds(99L, 10, 50, "admin"));

            assertTrue(ex.getMessage().contains("99"),
                    "Message lỗi phải chứa ID để dễ debug: " + ex.getMessage());
            // Không được gọi save hay log khi không tìm thấy policy
            verify(riskPolicyRepository, never()).save(any());
            verify(configLogRepository, never()).save(any());
        }

        // ==========================================================
        // TEST 13: newMin == newMax (bằng nhau) → IllegalArgumentException
        // Tại sao quan trọng: Code dùng >= nên bằng nhau phải bị chặn.
        //   Min=50 Max=50 tạo ra policy với khoảng score = 0, vô nghĩa về logic.
        // ==========================================================
        @Test
        @DisplayName("❌ newMin == newMax (50 == 50) → IllegalArgumentException")
        void updatePolicyThresholds_minEqualsMax_throwsIllegalArgument() {
            when(riskPolicyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

            assertThrows(IllegalArgumentException.class,
                    () -> riskPolicyService.updatePolicyThresholds(1L, 50, 50, "admin"));
        }

        // ==========================================================
        // TEST 14: newMin > newMax (đảo ngược) → IllegalArgumentException
        // ==========================================================
        @Test
        @DisplayName("❌ newMin > newMax (80 > 40) → IllegalArgumentException")
        void updatePolicyThresholds_minGreaterThanMax_throwsIllegalArgument() {
            when(riskPolicyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> riskPolicyService.updatePolicyThresholds(1L, 80, 40, "admin"));

            assertNotNull(ex.getMessage(), "Exception message không được null");
        }

        // ==========================================================
        // TEST 15: Validation fail → save() và configLog KHÔNG được gọi
        // Tại sao quan trọng: Đảm bảo không có partial write — không lưu data
        //   sai vào DB, không tạo log giả cho transaction chưa xảy ra.
        // ==========================================================
        @Test
        @DisplayName("❌ Validation fail (min >= max) → KHÔNG gọi save() hay configLog")
        void updatePolicyThresholds_validationFails_noSaveOrLogCalled() {
            when(riskPolicyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

            assertThrows(IllegalArgumentException.class,
                    () -> riskPolicyService.updatePolicyThresholds(1L, 100, 50, "admin"));

            verify(riskPolicyRepository, never()).save(any());
            verify(configLogRepository, never()).save(any());
        }

        // ==========================================================
        // TEST 16: newMin = newMax - 1 (boundary hợp lệ nhỏ nhất) → thành công
        // Tại sao quan trọng: Boundary test — đúng 1 đơn vị dưới ngưỡng lỗi phải pass.
        //   Nếu code dùng > thay vì >= thì test 13 sẽ pass nhưng test này vẫn đúng.
        //   Cặp test 13+16 pin down chính xác behavior của operator >=.
        // ==========================================================
        @Test
        @DisplayName("✅ newMin = newMax - 1 (boundary hợp lệ nhỏ nhất: 49 < 50) → thành công")
        void updatePolicyThresholds_minIsMaxMinusOne_succeeds() {
            when(riskPolicyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
            when(riskPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() ->
                    riskPolicyService.updatePolicyThresholds(1L, 49, 50, "admin"));
        }

        // ==========================================================
        // TEST 17: newMin âm, newMax dương → hợp lệ (service không chặn giá trị âm)
        // Tại sao quan trọng: Document behavior — code hiện tại không có validation
        //   chặn min âm. Một số policy rủi ro có thể có điểm khởi đầu từ -∞ đến 0.
        //   Nếu sau này thêm validation chặn âm, test này sẽ fail và buộc review.
        // ==========================================================
        @Test
        @DisplayName("✅ newMin âm (-10), newMax dương (0) → hợp lệ, service không chặn giá trị âm")
        void updatePolicyThresholds_negativeMin_currentlyAllowed() {
            when(riskPolicyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
            when(riskPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() ->
                    riskPolicyService.updatePolicyThresholds(1L, -10, 0, "admin"),
                    "Service hiện tại không chặn min âm — đây là document behavior test");
        }
    }

    // ====================================================================
    // NHÓM 4: updatePolicyThresholds — Failure & Isolation
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 4 — updatePolicyThresholds: Failure & Isolation")
    class UpdatePolicyFailureTests {

        // ==========================================================
        // TEST 18: riskPolicyRepository.save() ném exception → wrap thành RuntimeException
        // Tại sao quan trọng: Code có catch(Exception e) { throw new RuntimeException(...) }
        //   bao bọc lỗi bên trong. Phải test để chắc message lỗi gốc được truyền vào.
        // ==========================================================
        @Test
        @DisplayName("❌ riskPolicyRepository.save() ném exception → wrap thành RuntimeException")
        void updatePolicyThresholds_saveFails_wrapsExceptionInRuntime() {
            when(riskPolicyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
            when(riskPolicyRepository.save(any())).thenThrow(new RuntimeException("DB write failed"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> riskPolicyService.updatePolicyThresholds(1L, 10, 50, "admin"));

            assertTrue(ex.getMessage().contains("Lỗi khi cập nhật Policy"),
                    "Exception phải được wrap lại với message rõ ràng, thực tế: " + ex.getMessage());
        }

        // ==========================================================
        // TEST 19: configLogRepository.save() ném exception → wrap thành RuntimeException
        // Tại sao quan trọng: @Transactional(rollbackFor=Exception.class) — nếu log save
        //   fail, toàn bộ transaction phải rollback (cả policy update lẫn log).
        //   Test đảm bảo exception từ configLog không bị nuốt im lặng.
        // ==========================================================
        @Test
        @DisplayName("❌ configLogRepository.save() ném exception → wrap RuntimeException, policy update rollback")
        void updatePolicyThresholds_configLogSaveFails_wrapsException() {
            when(riskPolicyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
            when(riskPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(configLogRepository.save(any())).thenThrow(new RuntimeException("Log table locked"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> riskPolicyService.updatePolicyThresholds(1L, 10, 50, "admin"));

            assertTrue(ex.getMessage().contains("Lỗi khi cập nhật Policy"),
                    "Exception từ configLog phải được wrap lại: " + ex.getMessage());
        }

        // ==========================================================
        // TEST 20: adminUsername = null → log vẫn được ghi, service không crash
        // Tại sao quan trọng: Caller (controller) có thể truyền null nếu SecurityContext
        //   không có principal (ví dụ: scheduled task hoặc internal API call không auth).
        //   Service không nên crash vì thiếu username — chỉ cần lưu null vào log.
        // ==========================================================
        @Test
        @DisplayName("⚠️ adminUsername = null → service không crash, log vẫn được ghi")
        void updatePolicyThresholds_nullAdminUsername_doesNotCrash() {
            when(riskPolicyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
            when(riskPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() ->
                    riskPolicyService.updatePolicyThresholds(1L, 10, 50, null));

            verify(configLogRepository, times(1)).save(any(SystemConfigLog.class));
        }

        // ==========================================================
        // TEST 21: adminUsername rỗng "" → log vẫn được ghi với chuỗi rỗng
        // Tại sao quan trọng: Khác null — empty string vẫn là giá trị hợp lệ trong DB.
        //   Log sẽ có adminUsername="" thay vì null — cả hai đều cần được ghi lại.
        // ==========================================================
        @Test
        @DisplayName("⚠️ adminUsername = \"\" → log ghi chuỗi rỗng, không crash")
        void updatePolicyThresholds_emptyAdminUsername_logsEmpty() {
            when(riskPolicyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
            when(riskPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<SystemConfigLog> logCaptor = ArgumentCaptor.forClass(SystemConfigLog.class);

            assertDoesNotThrow(() ->
                    riskPolicyService.updatePolicyThresholds(1L, 10, 50, ""));

            verify(configLogRepository).save(logCaptor.capture());
            assertEquals("", logCaptor.getValue().getAdminUsername());
        }

        // ==========================================================
        // TEST 22: riskPolicyRepository.save() trả về object khác với input
        //   → service trả về kết quả từ save(), không phải object ban đầu
        // Tại sao quan trọng: Một số JPA implementation (với @Version, @GeneratedValue)
        //   trả về managed entity khác với object đầu vào sau save().
        //   Service phải trả về kết quả của save(), không phải `policy` ban đầu.
        // ==========================================================
        @Test
        @DisplayName("✅ service trả về kết quả từ save(), không phải object policy gốc")
        void updatePolicyThresholds_returnsSavedEntityNotOriginal() {
            RiskPolicy savedEntity = new RiskPolicy();
            savedEntity.setId(1L);
            savedEntity.setMinScore(10);
            savedEntity.setMaxScore(50);
            savedEntity.setActionBeanName("LOW_RISK_ACTION");

            when(riskPolicyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
            when(riskPolicyRepository.save(any())).thenReturn(savedEntity); // Trả về object KHÁC

            RiskPolicy result = riskPolicyService.updatePolicyThresholds(1L, 10, 50, "admin");

            assertSame(savedEntity, result,
                    "Service phải trả về kết quả từ repository.save(), " +
                    "không phải object policy trước khi save");
        }
    }
}