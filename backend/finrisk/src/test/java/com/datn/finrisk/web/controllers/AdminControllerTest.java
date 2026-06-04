package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.AdminUserDTO;
import com.datn.finrisk.core.entities.RiskPolicy;
import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.repository.BiometricSessionRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.security.JwtUtils;
import com.datn.finrisk.core.services.AdminUserService;
import com.datn.finrisk.core.services.JwtBlocklistService;
import com.datn.finrisk.core.services.RiskPolicyService;
import com.datn.finrisk.core.services.RuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminController — Unit Tests")
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TransactionRepository      transactionRepository;
    @MockBean private UserRepository             userRepository;
    @MockBean private RuleService                ruleService;
    @MockBean private AdminUserService           adminUserService;
    @MockBean private RiskPolicyService          riskPolicyService;

    // Security beans required by JwtAuthFilter even when filters are disabled
    @MockBean private JwtUtils                   jwtUtils;
    @MockBean private JwtBlocklistService        jwtBlocklistService;
    @MockBean private BiometricSessionRepository biometricSessionRepository;

    private static RequestPostProcessor asAdmin() {
        return request -> { request.setUserPrincipal(() -> "testAdmin"); return request; };
    }

    private Rule rule(Long id, String name) {
        Rule r = new Rule();
        r.setId(id);
        r.setRuleName(name);
        r.setIsActive(true);
        return r;
    }

    // ── GET /api/admin/rules ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/rules")
    class GetAllRules {

        @Test
        @DisplayName("returns 200 with list of rules")
        void returns200WithRules() throws Exception {
            when(ruleService.getAllRules()).thenReturn(List.of(rule(1L, "Rule A")));

            mockMvc.perform(get("/api/admin/rules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].ruleName").value("Rule A"));
        }

        @Test
        @DisplayName("returns 200 with empty list when no rules exist")
        void returns200EmptyList() throws Exception {
            when(ruleService.getAllRules()).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/rules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("returns 400 when service throws")
        void returns400WhenServiceThrows() throws Exception {
            when(ruleService.getAllRules()).thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/admin/rules"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PUT /api/admin/rules/{id} ─────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/admin/rules/{id}")
    class UpdateRule {

        @Test
        @DisplayName("returns 200 with updated rule")
        void returns200() throws Exception {
            Rule updated = rule(1L, "Updated Rule");
            when(ruleService.updateRule(eq(1L), any(Rule.class), eq("testAdmin")))
                    .thenReturn(updated);

            mockMvc.perform(put("/api/admin/rules/1")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updated)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ruleName").value("Updated Rule"));
        }

        @Test
        @DisplayName("returns 400 when service throws")
        void returns400WhenServiceThrows() throws Exception {
            when(ruleService.updateRule(any(), any(), any()))
                    .thenThrow(new RuntimeException("Rule not found"));

            mockMvc.perform(put("/api/admin/rules/99")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PATCH /api/admin/rules/{id}/toggle ────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/rules/{id}/toggle")
    class ToggleRule {

        @Test
        @DisplayName("returns 200 with rule toggled to inactive")
        void returns200Inactive() throws Exception {
            Rule toggled = rule(5L, "Some Rule");
            toggled.setIsActive(false);
            when(ruleService.toggleRuleStatus(5L, "testAdmin")).thenReturn(toggled);

            mockMvc.perform(patch("/api/admin/rules/5/toggle").with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false));
        }

        @Test
        @DisplayName("returns 400 when service throws")
        void returns400WhenServiceThrows() throws Exception {
            when(ruleService.toggleRuleStatus(anyLong(), anyString()))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(patch("/api/admin/rules/999/toggle").with(asAdmin()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── POST /api/admin/rules ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/admin/rules")
    class CreateRule {

        @Test
        @DisplayName("returns 200 with newly created rule")
        void returns200WithCreatedRule() throws Exception {
            Rule created = rule(10L, "New Rule");
            when(ruleService.createRule(any(Rule.class), eq("testAdmin"))).thenReturn(created);

            mockMvc.perform(post("/api/admin/rules")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(created)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        @DisplayName("returns 400 when service throws validation error")
        void returns400OnValidationError() throws Exception {
            when(ruleService.createRule(any(), any()))
                    .thenThrow(new RuntimeException("Invalid conditions"));

            mockMvc.perform(post("/api/admin/rules")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/admin/dashboard-stats ────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/dashboard-stats")
    class DashboardStats {

        @Test
        @DisplayName("returns 200 with correct stats fields")
        void returns200WithStats() throws Exception {
            when(transactionRepository.countTotalTransactions()).thenReturn(100L);
            when(transactionRepository.countHighRiskTransactions()).thenReturn(20L);
            when(transactionRepository.sumTotalSuccessfulAmount())
                    .thenReturn(new BigDecimal("5000000000"));

            mockMvc.perform(get("/api/admin/dashboard-stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTransactions").value(100))
                    .andExpect(jsonPath("$.highRiskBlocked").value(20))
                    .andExpect(jsonPath("$.totalMoneyTransferred").exists())
                    .andExpect(jsonPath("$.riskPercentage").value(20.0));
        }

        @Test
        @DisplayName("null totalAmount treated as ZERO")
        void nullAmountIsZero() throws Exception {
            when(transactionRepository.countTotalTransactions()).thenReturn(10L);
            when(transactionRepository.countHighRiskTransactions()).thenReturn(2L);
            when(transactionRepository.sumTotalSuccessfulAmount()).thenReturn(null);

            mockMvc.perform(get("/api/admin/dashboard-stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalMoneyTransferred").value(0));
        }

        @Test
        @DisplayName("zero total transactions → riskPercentage is 0")
        void zeroTransactions_riskPercentageZero() throws Exception {
            when(transactionRepository.countTotalTransactions()).thenReturn(0L);
            when(transactionRepository.countHighRiskTransactions()).thenReturn(0L);
            when(transactionRepository.sumTotalSuccessfulAmount()).thenReturn(BigDecimal.ZERO);

            mockMvc.perform(get("/api/admin/dashboard-stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.riskPercentage").value(0.0));
        }

        @Test
        @DisplayName("returns 400 when repository throws")
        void returns400WhenRepoThrows() throws Exception {
            when(transactionRepository.countTotalTransactions())
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/admin/dashboard-stats"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/admin/users ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/users")
    class GetUsers {

        @Test
        @DisplayName("returns 200 with page of users (defaults)")
        void returns200WithDefaults() throws Exception {
            when(adminUserService.getUsers("", 0, 10))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("passes search/page/size params to service")
        void passesQueryParams() throws Exception {
            when(adminUserService.getUsers("alice", 1, 5))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/admin/users")
                            .param("search", "alice")
                            .param("page", "1")
                            .param("size", "5"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("returns 400 when service throws")
        void returns400WhenServiceThrows() throws Exception {
            when(adminUserService.getUsers(anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PATCH /api/admin/users/{id}/toggle-status ─────────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/toggle-status")
    class ToggleUserStatus {

        @Test
        @DisplayName("returns 200 with updated DTO")
        void returns200() throws Exception {
            when(adminUserService.toggleUserStatus(2L, "testAdmin"))
                    .thenReturn(new AdminUserDTO());

            mockMvc.perform(patch("/api/admin/users/2/toggle-status").with(asAdmin()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("returns 400 when user not found")
        void returns400WhenUserNotFound() throws Exception {
            when(adminUserService.toggleUserStatus(anyLong(), anyString()))
                    .thenThrow(new RuntimeException("Không tìm thấy User!"));

            mockMvc.perform(patch("/api/admin/users/999/toggle-status").with(asAdmin()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PATCH /api/admin/users/{id}/toggle-suspicious ─────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/toggle-suspicious")
    class ToggleSuspicious {

        @Test
        @DisplayName("returns 200 with updated DTO")
        void returns200() throws Exception {
            when(adminUserService.toggleSuspicious(3L, "testAdmin"))
                    .thenReturn(new AdminUserDTO());

            mockMvc.perform(patch("/api/admin/users/3/toggle-suspicious").with(asAdmin()))
                    .andExpect(status().isOk());
        }
    }

    // ── GET /api/admin/users/{id}/recent-transactions ─────────────────────────

    @Nested
    @DisplayName("GET /api/admin/users/{id}/recent-transactions")
    class RecentTransactions {

        @Test
        @DisplayName("returns 200 with transaction list")
        void returns200() throws Exception {
            when(adminUserService.getRecentTransactionsByUserId(4L)).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/users/4/recent-transactions"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("returns 200 with non-empty map list")
        void returns200WithData() throws Exception {
            Map<String, Object> tx = Map.of("id", "TX42", "amount", 500000);
            when(adminUserService.getRecentTransactionsByUserId(7L)).thenReturn(List.of(tx));

            mockMvc.perform(get("/api/admin/users/7/recent-transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("TX42"));
        }

        @Test
        @DisplayName("returns 400 when service throws")
        void returns400WhenServiceThrows() throws Exception {
            when(adminUserService.getRecentTransactionsByUserId(anyLong()))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(get("/api/admin/users/999/recent-transactions"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PATCH /api/admin/users/{id}/reset-face ────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/reset-face")
    class ResetFace {

        @Test
        @DisplayName("returns 200 on successful biometric reset")
        void returns200() throws Exception {
            when(adminUserService.resetFaceBiometric(5L, "testAdmin"))
                    .thenReturn(new AdminUserDTO());

            mockMvc.perform(patch("/api/admin/users/5/reset-face").with(asAdmin()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("returns 400 when service throws")
        void returns400WhenServiceThrows() throws Exception {
            when(adminUserService.resetFaceBiometric(anyLong(), anyString()))
                    .thenThrow(new RuntimeException("User not found"));

            mockMvc.perform(patch("/api/admin/users/999/reset-face").with(asAdmin()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/admin/policies ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/policies")
    class GetPolicies {

        @Test
        @DisplayName("returns 200 with list of policies")
        void returns200() throws Exception {
            RiskPolicy p = new RiskPolicy();
            p.setId(1L);
            p.setRiskLevel("HIGH");
            p.setMinScore(60);
            p.setMaxScore(100);
            when(riskPolicyService.getAllPolicies()).thenReturn(List.of(p));

            mockMvc.perform(get("/api/admin/policies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].riskLevel").value("HIGH"))
                    .andExpect(jsonPath("$[0].minScore").value(60));
        }

        @Test
        @DisplayName("returns 400 when service throws")
        void returns400WhenServiceThrows() throws Exception {
            when(riskPolicyService.getAllPolicies())
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/admin/policies"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PUT /api/admin/policies/{id} ──────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/admin/policies/{id}")
    class UpdatePolicy {

        @Test
        @DisplayName("returns 200 with updated policy")
        void returns200() throws Exception {
            RiskPolicy updated = new RiskPolicy();
            updated.setId(1L);
            updated.setMinScore(40);
            updated.setMaxScore(80);
            when(riskPolicyService.updatePolicyThresholds(eq(1L), any(), any(), eq("testAdmin")))
                    .thenReturn(updated);

            mockMvc.perform(put("/api/admin/policies/1")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updated)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.minScore").value(40));
        }

        @Test
        @DisplayName("returns 400 when policy not found")
        void returns400WhenNotFound() throws Exception {
            when(riskPolicyService.updatePolicyThresholds(anyLong(), any(), any(), anyString()))
                    .thenThrow(new RuntimeException("Policy not found"));

            mockMvc.perform(put("/api/admin/policies/999")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
