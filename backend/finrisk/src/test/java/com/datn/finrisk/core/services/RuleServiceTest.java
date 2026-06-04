package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.SystemConfigLog;
import com.datn.finrisk.core.repository.RuleRepository;
import com.datn.finrisk.core.repository.SystemConfigLogRepository;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleService - Full Test Suite")
class RuleServiceTest {

    @Mock
    private RuleRepository ruleRepository;

    @Mock
    private SystemConfigLogRepository configLogRepository;

    @InjectMocks
    private RuleService ruleService;

    // ─────────────────────────────────────────────────────────────
    // Helper: tạo Rule mẫu
    // ─────────────────────────────────────────────────────────────
    private Rule buildRule(Long id, String name, String conditions, int score, boolean isActive) {
        Rule r = new Rule();
        r.setId(id);
        r.setRuleName(name);
        r.setConditions(conditions);
        r.setActionScore(score);
        r.setIsActive(isActive);
        return r;
    }

    // ══════════════════════════════════════════════════════════════
    // 1. getAllRules()
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getAllRules()")
    class GetAllRulesTests {

        @Test
        @DisplayName("Trả về danh sách đầy đủ cả active và inactive")
        void shouldReturnAllRules() {
            Rule active   = buildRule(1L, "Rule A", "{}", 10, true);
            Rule inactive = buildRule(2L, "Rule B", "{}", 20, false);
            when(ruleRepository.findAll()).thenReturn(Arrays.asList(active, inactive));

            List<Rule> result = ruleService.getAllRules();

            assertThat(result).hasSize(2).containsExactly(active, inactive);
            verify(ruleRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Trả về danh sách rỗng khi không có rule nào")
        void shouldReturnEmptyListWhenNoRules() {
            when(ruleRepository.findAll()).thenReturn(Collections.emptyList());

            List<Rule> result = ruleService.getAllRules();

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2. updateRule()
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateRule()")
    class UpdateRuleTests {

        private Rule existing;
        private final String ADMIN = "admin01";

        @BeforeEach
        void setUp() {
            existing = buildRule(1L, "Old Name", "{\"field\":\"amount\",\"operator\":\">\",\"value\":\"1000000\"}", 30, true);
        }

        // ── Happy path – các field phổ biến ──────────────────────

        @Test
        @DisplayName("Update field=amount: sinh SpEL '#tx.amount > value'")
        void shouldGenerateSpelForAmountField() {
            String conditions = "{\"field\":\"amount\",\"operator\":\">\",\"value\":\"5000000\"}";
            Rule ruleData = buildRule(null, "New Name", conditions, 50, true);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

            Rule result = ruleService.updateRule(1L, ruleData, ADMIN);

            assertThat(result.getRuleName()).isEqualTo("New Name");
            assertThat(result.getActionScore()).isEqualTo(50);
            assertThat(result.getSpelExpression()).isEqualTo("#tx.amount > 5000000");
        }

        @Test
        @DisplayName("Update field=emotion: sinh SpEL với dấu nháy đơn quanh value")
        void shouldGenerateSpelForEmotionField() {
            String conditions = "{\"field\":\"emotion\",\"operator\":\"==\",\"value\":\"PANIC\"}";
            Rule ruleData = buildRule(null, "Emotion Rule", conditions, 40, true);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

            Rule result = ruleService.updateRule(1L, ruleData, ADMIN);

            assertThat(result.getSpelExpression()).isEqualTo("#tx.emotionSignal == 'PANIC'");
        }

        @Test
        @DisplayName("Update field=history với value=NEW_RECIPIENT: sinh SpEL isNewRecipient")
        void shouldGenerateSpelForHistoryNewRecipient() {
            String conditions = "{\"field\":\"history\",\"operator\":\"==\",\"value\":\"NEW_RECIPIENT\"}";
            Rule ruleData = buildRule(null, "History Rule", conditions, 20, true);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

            Rule result = ruleService.updateRule(1L, ruleData, ADMIN);

            assertThat(result.getSpelExpression()).isEqualTo("#isNewRecipient == true");
        }

        @Test
        @DisplayName("Update field=history với value khác NEW_RECIPIENT: ném RuntimeException (validation bác bỏ)")
        void shouldGenerateEmptySpelForHistoryOtherValue() {
            String conditions = "{\"field\":\"history\",\"operator\":\"==\",\"value\":\"OLD_RECIPIENT\"}";
            Rule ruleData = buildRule(null, "History Rule 2", conditions, 10, true);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> ruleService.updateRule(1L, ruleData, ADMIN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OLD_RECIPIENT");
        }

        @Test
        @DisplayName("Update field=dailyTotal: sinh SpEL '#dailyTotalAmount'")
        void shouldGenerateSpelForDailyTotal() {
            String conditions = "{\"field\":\"dailyTotal\",\"operator\":\">\",\"value\":\"10000000\"}";
            Rule ruleData = buildRule(null, "Daily Rule", conditions, 35, true);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

            Rule result = ruleService.updateRule(1L, ruleData, ADMIN);

            assertThat(result.getSpelExpression()).isEqualTo("#dailyTotalAmount > 10000000");
        }

        @Test
        @DisplayName("Update field=isNightTime: sinh SpEL đúng")
        void shouldGenerateSpelForIsNightTime() {
            String conditions = "{\"field\":\"isNightTime\",\"operator\":\"==\",\"value\":\"true\"}";
            Rule ruleData = buildRule(null, "Night Rule", conditions, 15, true);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

            Rule result = ruleService.updateRule(1L, ruleData, ADMIN);

            assertThat(result.getSpelExpression()).isEqualTo("#isNightTime == true");
        }

        @Test
        @DisplayName("Update field không tồn tại: ném RuntimeException (validation bác bỏ unknown field)")
        void shouldGenerateEmptySpelForUnknownField() {
            String conditions = "{\"field\":\"unknownField\",\"operator\":\">\",\"value\":\"99\"}";
            Rule ruleData = buildRule(null, "Unknown Rule", conditions, 5, true);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> ruleService.updateRule(1L, ruleData, ADMIN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknownField");
        }

        // ── Ghi log ──────────────────────────────────────────────

        @Test
        @DisplayName("Phải ghi SystemConfigLog với action=UPDATE_RULE")
        void shouldSaveConfigLogOnUpdate() {
            String conditions = "{\"field\":\"amount\",\"operator\":\">\",\"value\":\"100\"}";
            Rule ruleData = buildRule(null, "Log Test", conditions, 10, true);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

            ruleService.updateRule(1L, ruleData, ADMIN);

            ArgumentCaptor<SystemConfigLog> captor = ArgumentCaptor.forClass(SystemConfigLog.class);
            verify(configLogRepository).save(captor.capture());

            SystemConfigLog log = captor.getValue();
            assertThat(log.getActionType()).isEqualTo("UPDATE_RULE");
            assertThat(log.getTargetTable()).isEqualTo("rules");
            assertThat(log.getTargetId()).isEqualTo(1L);
            assertThat(log.getAdminUsername()).isEqualTo(ADMIN);
        }

        // ── Error cases ──────────────────────────────────────────

        @Test
        @DisplayName("Ném RuntimeException khi Rule ID không tồn tại")
        void shouldThrowWhenRuleNotFound() {
            when(ruleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                ruleService.updateRule(999L, new Rule(), ADMIN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("999");
        }

        @Test
        @DisplayName("Ném RuntimeException khi conditions là JSON không hợp lệ (vẫn save được, SpEL bị bỏ qua)")
        void shouldContinueEvenIfConditionsJsonIsInvalid() {
            // JSON invalid chỉ khiến SpEL bị skip, không rollback update
            Rule ruleData = buildRule(null, "Bad JSON", "NOT_VALID_JSON", 10, true);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

            // Không ném exception – lỗi dịch SpEL được bắt nội bộ
            assertThatCode(() -> ruleService.updateRule(1L, ruleData, ADMIN))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Ném RuntimeException khi ruleRepository.save() thất bại")
        void shouldThrowWhenSaveFails() {
            String conditions = "{\"field\":\"amount\",\"operator\":\">\",\"value\":\"100\"}";
            Rule ruleData = buildRule(null, "Fail", conditions, 10, true);

            when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any(Rule.class))).thenThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() -> ruleService.updateRule(1L, ruleData, ADMIN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Lỗi khi cập nhật luật");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. toggleRuleStatus()
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("toggleRuleStatus()")
    class ToggleRuleStatusTests {

        private final String ADMIN = "adminToggle";

        @Test
        @DisplayName("Bật → Tắt: isActive phải chuyển từ true sang false")
        void shouldTurnOffActiveRule() {
            Rule rule = buildRule(1L, "Active Rule", "{}", 10, true);
            when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

            Rule result = ruleService.toggleRuleStatus(1L, ADMIN);

            assertThat(result.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("Tắt → Bật: isActive phải chuyển từ false sang true")
        void shouldTurnOnInactiveRule() {
            Rule rule = buildRule(2L, "Inactive Rule", "{}", 10, false);
            when(ruleRepository.findById(2L)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

            Rule result = ruleService.toggleRuleStatus(2L, ADMIN);

            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("Phải ghi SystemConfigLog với action=TOGGLE_RULE_STATUS")
        void shouldSaveConfigLogOnToggle() {
            Rule rule = buildRule(3L, "Toggle Rule", "{}", 10, true);
            when(ruleRepository.findById(3L)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

            ruleService.toggleRuleStatus(3L, ADMIN);

            ArgumentCaptor<SystemConfigLog> captor = ArgumentCaptor.forClass(SystemConfigLog.class);
            verify(configLogRepository).save(captor.capture());

            SystemConfigLog log = captor.getValue();
            assertThat(log.getActionType()).isEqualTo("TOGGLE_RULE_STATUS");
            assertThat(log.getTargetTable()).isEqualTo("rules");
            assertThat(log.getAdminUsername()).isEqualTo(ADMIN);
        }

        @Test
        @DisplayName("Ném RuntimeException khi Rule ID không tồn tại")
        void shouldThrowWhenRuleNotFound() {
            when(ruleRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ruleService.toggleRuleStatus(404L, ADMIN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("404");
        }

        @Test
        @DisplayName("Ném RuntimeException khi save() lỗi")
        void shouldThrowWhenSaveFails() {
            Rule rule = buildRule(5L, "Rule", "{}", 10, true);
            when(ruleRepository.findById(5L)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(any(Rule.class))).thenThrow(new RuntimeException("DB down"));

            assertThatThrownBy(() -> ruleService.toggleRuleStatus(5L, ADMIN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Lỗi khi bật/tắt luật");
        }

        @Test
        @DisplayName("Toggle 2 lần liên tiếp: phải trở về trạng thái ban đầu")
        void shouldReturnToOriginalStateAfterDoubleToggle() {
            Rule rule = buildRule(6L, "Rule", "{}", 10, true);
            when(ruleRepository.findById(6L)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));

            // Toggle lần 1
            Rule after1 = ruleService.toggleRuleStatus(6L, ADMIN);
            assertThat(after1.getIsActive()).isFalse();

            // Toggle lần 2 (rule hiện đang false)
            rule.setIsActive(false); // cập nhật lại trạng thái như sau lần 1
            Rule after2 = ruleService.toggleRuleStatus(6L, ADMIN);
            assertThat(after2.getIsActive()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 4. createRule()
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("createRule()")
    class CreateRuleTests {

        private final String ADMIN = "adminCreate";

        // ── SpEL generation – đủ 9 case field ───────────────────

        @Test
        @DisplayName("Tạo rule field=amount: SpEL '#tx.amount'")
        void shouldCreateRuleWithAmountSpel() {
            String cond = "{\"field\":\"amount\",\"operator\":\"<\",\"value\":\"500000\"}";
            Rule data = buildRule(null, "Amount Rule", cond, 25, true);
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> {
                Rule r = inv.getArgument(0); r.setId(10L); return r;
            });

            Rule result = ruleService.createRule(data, ADMIN);

            assertThat(result.getSpelExpression()).isEqualTo("#tx.amount < 500000");
        }

        @Test
        @DisplayName("Tạo rule field=suspiciousSession: SpEL '#suspiciousSession'")
        void shouldCreateRuleWithSuspiciousSessionSpel() {
            String cond = "{\"field\":\"suspiciousSession\",\"operator\":\"==\",\"value\":\"true\"}";
            Rule data = buildRule(null, "Session Rule", cond, 40, true);
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> {
                Rule r = inv.getArgument(0); r.setId(11L); return r;
            });

            Rule result = ruleService.createRule(data, ADMIN);

            assertThat(result.getSpelExpression()).isEqualTo("#suspiciousSession == true");
        }

        @Test
        @DisplayName("Tạo rule field=deviceTrusted: SpEL '#deviceTrusted'")
        void shouldCreateRuleWithDeviceTrustedSpel() {
            String cond = "{\"field\":\"deviceTrusted\",\"operator\":\"==\",\"value\":\"false\"}";
            Rule data = buildRule(null, "Device Rule", cond, 30, true);
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> {
                Rule r = inv.getArgument(0); r.setId(12L); return r;
            });

            Rule result = ruleService.createRule(data, ADMIN);

            assertThat(result.getSpelExpression()).isEqualTo("#deviceTrusted == false");
        }

        @Test
        @DisplayName("Tạo rule field=recentTxCount: SpEL '#recentTxCount'")
        void shouldCreateRuleWithRecentTxCountSpel() {
            String cond = "{\"field\":\"recentTxCount\",\"operator\":\">\",\"value\":\"5\"}";
            Rule data = buildRule(null, "TxCount Rule", cond, 20, true);
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> {
                Rule r = inv.getArgument(0); r.setId(13L); return r;
            });

            Rule result = ruleService.createRule(data, ADMIN);

            assertThat(result.getSpelExpression()).isEqualTo("#recentTxCount > 5");
        }

        @Test
        @DisplayName("Tạo rule field=balanceRatio: SpEL '#balanceRatio'")
        void shouldCreateRuleWithBalanceRatioSpel() {
            String cond = "{\"field\":\"balanceRatio\",\"operator\":\">\",\"value\":\"0.8\"}";
            Rule data = buildRule(null, "Balance Rule", cond, 35, true);
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> {
                Rule r = inv.getArgument(0); r.setId(14L); return r;
            });

            Rule result = ruleService.createRule(data, ADMIN);

            assertThat(result.getSpelExpression()).isEqualTo("#balanceRatio > 0.8");
        }

        @Test
        @DisplayName("Tạo rule field=emotion: SpEL với nháy đơn quanh value")
        void shouldCreateRuleWithEmotionSpel() {
            String cond = "{\"field\":\"emotion\",\"operator\":\"==\",\"value\":\"FEAR\"}";
            Rule data = buildRule(null, "Emotion Rule", cond, 45, true);
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> {
                Rule r = inv.getArgument(0); r.setId(15L); return r;
            });

            Rule result = ruleService.createRule(data, ADMIN);

            assertThat(result.getSpelExpression()).isEqualTo("#tx.emotionSignal == 'FEAR'");
        }

        // ── isActive mặc định = true ──────────────────────────────

        @Test
        @DisplayName("Rule mới tạo phải mặc định isActive = true dù input là false")
        void shouldDefaultIsActiveToTrue() {
            String cond = "{\"field\":\"amount\",\"operator\":\">\",\"value\":\"1\"}";
            Rule data = buildRule(null, "Default Active", cond, 5, false); // input false
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> {
                Rule r = inv.getArgument(0); r.setId(20L); return r;
            });

            Rule result = ruleService.createRule(data, ADMIN);

            assertThat(result.getIsActive()).isTrue();
        }

        // ── Ghi log ──────────────────────────────────────────────

        @Test
        @DisplayName("Phải ghi SystemConfigLog với action=CREATE_RULE và oldJson='{}'")
        void shouldSaveConfigLogOnCreate() {
            String cond = "{\"field\":\"amount\",\"operator\":\">\",\"value\":\"100\"}";
            Rule data = buildRule(null, "Log Rule", cond, 10, true);
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> {
                Rule r = inv.getArgument(0); r.setId(99L); return r;
            });

            ruleService.createRule(data, ADMIN);

            ArgumentCaptor<SystemConfigLog> captor = ArgumentCaptor.forClass(SystemConfigLog.class);
            verify(configLogRepository).save(captor.capture());

            SystemConfigLog log = captor.getValue();
            assertThat(log.getActionType()).isEqualTo("CREATE_RULE");
            assertThat(log.getTargetTable()).isEqualTo("rules");
            assertThat(log.getOldValue()).isEqualTo("{}");
            assertThat(log.getAdminUsername()).isEqualTo(ADMIN);
        }

        // ── Error / Edge cases ───────────────────────────────────

        @Test
        @DisplayName("JSON conditions rỗng '{}': validateCondition ném exception vì thiếu field")
        void shouldHandleEmptyConditionsGracefully() {
            Rule data = buildRule(null, "Empty Cond", "{}", 10, true);

            assertThatThrownBy(() -> ruleService.createRule(data, ADMIN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("field");
        }

        @Test
        @DisplayName("conditions là null: ném RuntimeException (vì readTree(null) lỗi)")
        void shouldThrowWhenConditionsNull() {
            Rule data = buildRule(null, "Null Cond", null, 10, true);

            assertThatThrownBy(() -> ruleService.createRule(data, ADMIN))
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("save() thất bại: ném RuntimeException wrap 'Lỗi khi tạo luật mới'")
        void shouldThrowWhenSaveFails() {
            String cond = "{\"field\":\"amount\",\"operator\":\">\",\"value\":\"100\"}";
            Rule data = buildRule(null, "Fail Rule", cond, 10, true);
            when(ruleRepository.save(any(Rule.class))).thenThrow(new RuntimeException("DB crashed"));

            assertThatThrownBy(() -> ruleService.createRule(data, ADMIN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Lỗi khi tạo luật mới");
        }

        @Test
        @DisplayName("configLogRepository.save() thất bại: RuntimeException được ném ra")
        void shouldThrowWhenLogSaveFails() {
            String cond = "{\"field\":\"amount\",\"operator\":\">\",\"value\":\"100\"}";
            Rule data = buildRule(null, "Log Fail", cond, 10, true);
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> {
                Rule r = inv.getArgument(0); r.setId(23L); return r;
            });
            doThrow(new RuntimeException("Log DB error")).when(configLogRepository).save(any());

            assertThatThrownBy(() -> ruleService.createRule(data, ADMIN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Lỗi khi tạo luật mới");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 5. Edge cases – SpEL dùng chung (cả create & update)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("SpEL Edge Cases")
    class SpelEdgeCases {

        private final String ADMIN = "edgeAdmin";

        @Test
        @DisplayName("Operator là '>=' phải xuất hiện đúng trong SpEL")
        void shouldHandleGreaterOrEqualOperator() {
            String cond = "{\"field\":\"recentTxCount\",\"operator\":\">=\",\"value\":\"10\"}";
            Rule data = buildRule(null, "GTE Rule", cond, 15, true);
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> {
                Rule r = inv.getArgument(0); r.setId(30L); return r;
            });

            Rule result = ruleService.createRule(data, ADMIN);
            assertThat(result.getSpelExpression()).isEqualTo("#recentTxCount >= 10");
        }

        @Test
        @DisplayName("Value chứa số thập phân: SpEL giữ nguyên")
        void shouldHandleDecimalValue() {
            String cond = "{\"field\":\"balanceRatio\",\"operator\":\"<\",\"value\":\"0.5\"}";
            Rule data = buildRule(null, "Decimal Rule", cond, 20, true);
            when(ruleRepository.save(any(Rule.class))).thenAnswer(inv -> {
                Rule r = inv.getArgument(0); r.setId(31L); return r;
            });

            Rule result = ruleService.createRule(data, ADMIN);
            assertThat(result.getSpelExpression()).isEqualTo("#balanceRatio < 0.5");
        }

        @Test
        @DisplayName("Conditions JSON thiếu field 'field': ném RuntimeException (validation yêu cầu 'field')")
        void shouldHandleMissingFieldKey() {
            String cond = "{\"operator\":\">\",\"value\":\"100\"}"; // thiếu "field"
            Rule data = buildRule(null, "Missing Field", cond, 5, true);

            assertThatThrownBy(() -> ruleService.createRule(data, ADMIN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("field");
        }

        @Test
        @DisplayName("Conditions JSON thiếu 'operator': validateCondition ném exception vì operator không hợp lệ")
        void shouldHandleMissingOperatorAndValue() {
            String cond = "{\"field\":\"amount\"}"; // thiếu operator và value
            Rule data = buildRule(null, "Missing Op", cond, 5, true);

            assertThatThrownBy(() -> ruleService.createRule(data, ADMIN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("amount");
        }
    }
}