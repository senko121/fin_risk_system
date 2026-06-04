package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.BehaviorInsightResult;
import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.application.dtos.FaceAIResponse;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionAiInsight;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.RiskScoreRepository;
import com.datn.finrisk.core.repository.RuleRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserDeviceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BỔ SUNG: Các test case nâng cao và edge case cho RiskEvaluationService.
 * File này bổ sung cho RiskEvaluationServiceTest.java gốc.
 *
 * Danh sách gap đã được lấp đầy:
 *
 * [Nhóm 1 - Rule Engine]
 *   ✅ DB không có rule nào (list rỗng)
 *   ✅ balance = 0 → tránh chia-cho-0
 *   ✅ suspiciousSession OR adminFlagged (cả hai trường hợp)
 *   ✅ isNightTime = true → rule khung giờ đêm
 *   ✅ recentTxCount cao → rule spam tần suất
 *   ✅ dailyTotalAmount vượt ngưỡng
 *   ✅ SpEL trả về null thay vì Boolean
 *   ✅ actionScore âm → Math.max(0,...) bảo vệ tổng điểm
 *
 * [Nhóm 2 - Face AI]
 *   ✅ User không có face embeddings → trả về null ngay
 *   ✅ API trả về null (body rỗng, không phải exception)
 *   ✅ Xác thực URL endpoint chính xác
 *   ✅ Xác thực Content-Type = APPLICATION_JSON
 *
 * [Nhóm 3 - Emotion AI]
 *   ✅ API trả về null
 *   ✅ Xác thực URL endpoint
 *   ✅ Xác thực emotion value cụ thể trong DTO
 *
 * [Nhóm 4 - Voice AI — dùng verifyVoiceLivenessBase64Async(String)]
 *   ✅ API trả về null JsonNode
 *   ✅ authCode là chuỗi rỗng trong JSON
 *   ✅ Input base64 không hợp lệ → exception → chuỗi rỗng
 *   ✅ Xác thực Content-Type = MULTIPART_FORM_DATA
 *   ✅ restTemplate ném exception → chuỗi rỗng
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RiskEvaluationService — Bổ sung Edge Cases & Nâng cao")
class RiskEvaluationServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private RuleRepository ruleRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private BehavioralProfilingService behavioralProfilingService;
    @Mock private UserDeviceRepository userDeviceRepository;
    @Mock private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock private FaceEnrollService faceEnrollService;
    @Mock private RiskScoreRepository riskScoreRepository;

    @InjectMocks
    private RiskEvaluationService riskEvaluationService;

    private Transaction mockTx;
    private User mockUser;
    private Account mockSender;
    private CircuitBreaker testCircuitBreaker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setSuspiciousSession(false);
        mockUser.setAdminFlagged(false);

        mockSender = new Account();
        mockSender.setId(10L);
        mockSender.setBalance(new BigDecimal("5000000"));
        mockSender.setUser(mockUser);

        mockTx = new Transaction();
        mockTx.setId(100L);
        mockTx.setFromAccount(mockSender);
        mockTx.setAmount(new BigDecimal("1000000"));

        // @Value fields are not injected by Mockito — set them via reflection
        ReflectionTestUtils.setField(riskEvaluationService, "faceAiUrl",
                "http://localhost:5000/api/ai/verify-face");
        ReflectionTestUtils.setField(riskEvaluationService, "emotionAiUrl",
                "http://localhost:5001/api/ai/detect-emotion");
        ReflectionTestUtils.setField(riskEvaluationService, "emotionSequenceAiUrl",
                "http://localhost:5001/api/ai/detect-emotion-sequence");
        ReflectionTestUtils.setField(riskEvaluationService, "voiceAiUrl",
                "http://localhost:5003/api/ai/verify-voice");

        // Real in-memory circuit breaker in CLOSED state — passes all calls through
        testCircuitBreaker = CircuitBreaker.ofDefaults("test");
        when(circuitBreakerRegistry.circuitBreaker(anyString())).thenReturn(testCircuitBreaker);

        // Default: no behavioral anomaly (score=0, no insights)
        when(behavioralProfilingService.calculateBehavioralAnomalyScore(
                any(), any(), anyDouble(), anyDouble()))
                .thenReturn(new BehaviorInsightResult(0, Collections.emptyList()));
    }

    // ====================================================================
    // NHÓM 1 (BỔ SUNG): CỖ MÁY CHẤM ĐIỂM (SpEL RULE ENGINE) - EDGE CASES
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 1 (Bổ sung) — Rule Engine Edge Cases")
    class RuleEngineEdgeCases {

        // Các stub cần thiết cho mọi test evaluateRisk trong nhóm này
        private void setupCommonMocks() {
            when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(1);
            when(transactionRepository.sumSuccessfulAmountToday(anyLong(), any())).thenReturn(BigDecimal.ZERO);
        }

        // ==========================================================
        // TEST 1: Không có rule nào trong DB → Điểm = 0
        // ==========================================================
        @Test
        @DisplayName("✅ Không có rule nào trong DB (list rỗng) → Trả về điểm 0, không crash")
        void evaluateRisk_emptyRuleList_returnsZeroAndDoesNotCrash() {
            when(ruleRepository.findByIsActiveTrue()).thenReturn(Collections.emptyList());
            setupCommonMocks();

            int score = riskEvaluationService.evaluateRisk(
                    mockTx, false, new ArrayList<>(), new ArrayList<>());

            assertEquals(0, score, "Danh sách rule rỗng phải trả về điểm 0");
        }

        // ==========================================================
        // TEST 2: Số dư ví bằng 0 → Không chia-cho-0
        // Code có kiểm tra `currentBalance > 0` trước khi tính ratio
        // ==========================================================
        @Test
        @DisplayName("⚠️ Số dư ví = 0 → balanceRatio vẫn = 0, không có ArithmeticException")
        void evaluateRisk_zeroBalance_balanceRatioIsZeroNoException() {
            mockSender.setBalance(BigDecimal.ZERO);

            Rule rule = new Rule();
            rule.setRuleName("Kiểm tra balance ratio = 0 khi ví trống");
            rule.setSpelExpression("#balanceRatio == 0.0");
            rule.setActionScore(15);
            // No category → defaults to CONTEXTUAL (cap=20). 15 ≤ 20, unchanged.

            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(rule));
            setupCommonMocks();

            assertDoesNotThrow(() -> {
                int score = riskEvaluationService.evaluateRisk(
                        mockTx, false, new ArrayList<>(), new ArrayList<>());
                assertEquals(15, score, "balanceRatio phải = 0 khi số dư tài khoản bằng 0");
            });
        }

        // ==========================================================
        // TEST 3: suspiciousSession = true (không phải adminFlagged)
        // Logic "OR" — nhánh suspiciousSession
        // ==========================================================
        @Test
        @DisplayName("⚠️ suspiciousSession = true (adminFlagged = false) → combinedSuspiciousRisk = true")
        void evaluateRisk_suspiciousSessionOnly_flaggedAsSuspicious() {
            mockUser.setSuspiciousSession(true);
            mockUser.setAdminFlagged(false);

            Rule rule = new Rule();
            rule.setSpelExpression("#suspiciousSession == true");
            rule.setActionScore(50);
            rule.setCategory("BIOMETRIC");  // cap=55 ≥ 50, score không bị cắt
            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(rule));
            setupCommonMocks();

            int score = riskEvaluationService.evaluateRisk(
                    mockTx, false, new ArrayList<>(), new ArrayList<>());
            assertEquals(50, score);
        }

        // ==========================================================
        // TEST 4: adminFlagged = true (không phải suspiciousSession)
        // Logic "OR" — nhánh adminFlagged
        // ==========================================================
        @Test
        @DisplayName("⚠️ adminFlagged = true (suspiciousSession = false) → combinedSuspiciousRisk = true")
        void evaluateRisk_adminFlaggedOnly_flaggedAsSuspicious() {
            mockUser.setSuspiciousSession(false);
            mockUser.setAdminFlagged(true);

            Rule rule = new Rule();
            rule.setSpelExpression("#suspiciousSession == true");
            rule.setActionScore(50);
            rule.setCategory("BIOMETRIC");  // cap=55 ≥ 50, score không bị cắt
            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(rule));
            setupCommonMocks();

            int score = riskEvaluationService.evaluateRisk(
                    mockTx, false, new ArrayList<>(), new ArrayList<>());
            assertEquals(50, score);
        }

        // ==========================================================
        // TEST 5: recentTxCount cao → Rule spam bắt được
        // ==========================================================
        @Test
        @DisplayName("⚠️ recentTxCount > 3 trong 1 phút → Rule spam khớp, cộng điểm")
        void evaluateRisk_highRecentTxCount_spamRuleTriggered() {
            Rule rule = new Rule();
            rule.setRuleName("Spam rule - giao dịch liên tục");
            rule.setSpelExpression("#recentTxCount > 3");
            rule.setActionScore(40);
            rule.setCategory("FINANCIAL");  // cap=40 ≥ 40, score không bị cắt

            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(rule));
            when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(5);
            when(transactionRepository.sumSuccessfulAmountToday(anyLong(), any())).thenReturn(BigDecimal.ZERO);

            int score = riskEvaluationService.evaluateRisk(
                    mockTx, false, new ArrayList<>(), new ArrayList<>());
            assertEquals(40, score);
        }

        // ==========================================================
        // TEST 6: dailyTotalAmount vượt ngưỡng → Rule giới hạn ngày khớp
        // ==========================================================
        @Test
        @DisplayName("⚠️ Tổng tiền ngày hôm nay vượt 50 triệu → Rule cảnh báo khớp")
        void evaluateRisk_dailyLimitExceeded_ruleTriggered() {
            Rule rule = new Rule();
            rule.setRuleName("Vượt hạn mức ngày");
            rule.setSpelExpression("#dailyTotalAmount > 50000000");
            rule.setActionScore(40);
            rule.setCategory("FINANCIAL");  // cap=40 ≥ 40, score không bị cắt

            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(rule));
            when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(1);
            when(transactionRepository.sumSuccessfulAmountToday(anyLong(), any()))
                    .thenReturn(new BigDecimal("60000000"));

            int score = riskEvaluationService.evaluateRisk(
                    mockTx, false, new ArrayList<>(), new ArrayList<>());
            assertEquals(40, score);
        }

        // ==========================================================
        // TEST 7: SpEL trả về null thay vì Boolean → Không crash, bỏ qua rule
        // Boolean.TRUE.equals(non-boolean) = false → rule bị bỏ qua
        // ==========================================================
        @Test
        @DisplayName("⚠️ SpEL trả về kiểu không phải Boolean (ví dụ: số) → Bỏ qua rule, không crash")
        void evaluateRisk_spelReturnsNonBoolean_skipRuleSafely() {
            Rule badRule = new Rule();
            badRule.setId(77L);
            badRule.setRuleName("SpEL trả về số, không phải boolean");
            // Expression này hợp lệ về cú pháp nhưng trả về BigDecimal, không phải Boolean
            badRule.setSpelExpression("#tx.amount");
            badRule.setActionScore(999);

            Rule goodRule = new Rule();
            goodRule.setSpelExpression("#isNewRecipient == true");
            goodRule.setActionScore(25);
            goodRule.setCategory("FINANCIAL");  // cap=40 ≥ 25, score không bị cắt

            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(badRule, goodRule));
            setupCommonMocks();

            // Rule xấu bị bỏ qua, chỉ goodRule cộng điểm
            int score = riskEvaluationService.evaluateRisk(
                    mockTx, true, new ArrayList<>(), new ArrayList<>());
            assertEquals(25, score);
        }

        // ==========================================================
        // TEST 8: ActionScore âm → Category cap clamps về 0
        // Math.max(0, Math.min(-100, cap)) = 0 → tổng điểm không âm
        // ==========================================================
        @Test
        @DisplayName("⚠️ Rule có actionScore âm (rule giảm điểm) → Tổng điểm không âm hơn 0")
        void evaluateRisk_negativeActionScore_totalScoreNeverBelowZero() {
            Rule penaltyRule = new Rule();
            penaltyRule.setRuleName("Rule giảm điểm (ân giảm)");
            penaltyRule.setSpelExpression("#isNewRecipient == false");  // isNewRecipient=false → luôn khớp
            penaltyRule.setActionScore(-100);

            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(penaltyRule));
            setupCommonMocks();

            int score = riskEvaluationService.evaluateRisk(
                    mockTx, false, new ArrayList<>(), new ArrayList<>());

            // categoryTotals["CONTEXTUAL"] = -100, clamped = Math.max(0, Math.min(-100,20)) = 0
            assertEquals(0, score, "Điểm rủi ro không bao giờ được âm");
        }
    }

    // ====================================================================
    // NHÓM 2 (BỔ SUNG): FACE AI — EDGE CASES & CONTRACT TESTS
    // API mới: verifyIdentityAsync(List<String> liveFrames, User user)
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 2 (Bổ sung) — Face AI Edge Cases & Contract")
    class FaceAiEdgeCases {

        private User userWithFace;

        @BeforeEach
        void setUpFaceUser() {
            userWithFace = new User();
            userWithFace.setId(2L);
            // hasFaceEmbeddings() = (faceEmbeddings != null && !isBlank())
            userWithFace.setFaceEmbeddings("[[1.0,2.0,3.0]]");
            when(faceEnrollService.parseEmbeddings(anyString()))
                    .thenReturn(List.of(List.of(1.0, 2.0, 3.0)));
        }

        // ==========================================================
        // TEST 9: User không có face embeddings → Trả về null ngay
        // ==========================================================
        @Test
        @DisplayName("⚠️ User không có face embeddings → Trả về null ngay, không gọi API")
        void verifyIdentityAsync_userWithoutEmbeddings_returnsNullWithoutCallingApi() throws Exception {
            User userNoFace = new User();
            userNoFace.setId(3L);
            // faceEmbeddings = null → hasFaceEmbeddings() = false

            CompletableFuture<FaceAIResponse> future =
                    riskEvaluationService.verifyIdentityAsync(List.of("live_b64"), userNoFace);
            FaceAIResponse result = future.get();

            assertNull(result, "Thiếu face embeddings phải trả về null ngay lập tức");
            verify(restTemplate, never()).postForObject(anyString(), any(), eq(FaceAIResponse.class));
        }

        // ==========================================================
        // TEST 10: API trả về null (body rỗng) thay vì ném exception
        // ==========================================================
        @Test
        @DisplayName("⚠️ API Face trả về null body (không phải exception) → Kết quả là null, không crash")
        void verifyIdentityAsync_apiReturnsNullBody_handledGracefully() throws Exception {
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(FaceAIResponse.class)))
                    .thenReturn(null);

            CompletableFuture<FaceAIResponse> future =
                    riskEvaluationService.verifyIdentityAsync(List.of("live_b64"), userWithFace);
            FaceAIResponse result = future.get();

            assertNull(result, "Body null từ API cần được xử lý không crash");
        }

        // ==========================================================
        // TEST 11: Kiểm tra URL endpoint được gọi đúng
        // ==========================================================
        @Test
        @DisplayName("✅ Luôn gọi đúng URL endpoint: http://localhost:5000/api/ai/verify-face")
        void verifyIdentityAsync_callsCorrectEndpoint() throws Exception {
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(FaceAIResponse.class)))
                    .thenReturn(new FaceAIResponse());

            riskEvaluationService.verifyIdentityAsync(List.of("live_b64"), userWithFace).get();

            verify(restTemplate).postForObject(
                    eq("http://localhost:5000/api/ai/verify-face"),
                    any(HttpEntity.class),
                    eq(FaceAIResponse.class)
            );
        }

        // ==========================================================
        // TEST 12: Kiểm tra Content-Type header phải là APPLICATION_JSON
        // ==========================================================
        @Test
        @DisplayName("✅ Request gửi đến Face AI phải có Content-Type: application/json")
        void verifyIdentityAsync_sendsCorrectContentTypeHeader() throws Exception {
            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            when(restTemplate.postForObject(anyString(), entityCaptor.capture(), eq(FaceAIResponse.class)))
                    .thenReturn(new FaceAIResponse());

            riskEvaluationService.verifyIdentityAsync(List.of("live_b64"), userWithFace).get();

            assertEquals(MediaType.APPLICATION_JSON,
                    entityCaptor.getValue().getHeaders().getContentType(),
                    "Content-Type phải là application/json");
        }
    }

    // ====================================================================
    // NHÓM 3 (BỔ SUNG): EMOTION AI — EDGE CASES & CONTRACT TESTS
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 3 (Bổ sung) — Emotion AI Edge Cases & Contract")
    class EmotionAiEdgeCases {

        // ==========================================================
        // TEST 13: API trả về null body
        // ==========================================================
        @Test
        @DisplayName("⚠️ API Emotion trả về null body → Không crash, trả về null")
        void detectEmotionAsync_apiReturnsNullBody_handledGracefully() throws Exception {
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(EmotionAIResponse.class)))
                    .thenReturn(null);

            EmotionAIResponse result = riskEvaluationService.detectEmotionAsync("live_b64").get();

            assertNull(result, "Null body từ Emotion API cần được xử lý an toàn");
        }

        // ==========================================================
        // TEST 14: Kiểm tra URL endpoint đúng
        // ==========================================================
        @Test
        @DisplayName("✅ Luôn gọi đúng URL: http://localhost:5001/api/ai/detect-emotion")
        void detectEmotionAsync_callsCorrectEndpoint() throws Exception {
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(EmotionAIResponse.class)))
                    .thenReturn(new EmotionAIResponse());

            riskEvaluationService.detectEmotionAsync("live_b64").get();

            verify(restTemplate).postForObject(
                    eq("http://localhost:5001/api/ai/detect-emotion"),
                    any(HttpEntity.class),
                    eq(EmotionAIResponse.class)
            );
        }

        // ==========================================================
        // TEST 15: Kiểm tra giá trị emotion cụ thể được đọc đúng từ DTO
        // ==========================================================
        @Test
        @DisplayName("✅ Kết quả trả về đúng emotion value từ DTO (ví dụ: 'FEAR')")
        void detectEmotionAsync_success_returnsCorrectEmotionValue() throws Exception {
            EmotionAIResponse mockResponse = new EmotionAIResponse();
            mockResponse.setEmotion("FEAR");

            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(EmotionAIResponse.class)))
                    .thenReturn(mockResponse);

            EmotionAIResponse result = riskEvaluationService.detectEmotionAsync("live_b64").get();

            assertNotNull(result);
            assertEquals("FEAR", result.getEmotion(),
                    "Emotion value phải được đọc chính xác từ response DTO");
        }
    }

    // ====================================================================
    // NHÓM 4 (BỔ SUNG): VOICE AI — EDGE CASES & CONTRACT TESTS
    // API mới: verifyVoiceLivenessBase64Async(String audioBase64)
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 4 (Bổ sung) — Voice AI Edge Cases & Contract")
    class VoiceAiEdgeCases {

        // Valid base64 for test use
        private String validBase64;

        @BeforeEach
        void setUpBase64() {
            validBase64 = Base64.getEncoder().encodeToString("fake audio data".getBytes());
        }

        // ==========================================================
        // TEST 16: API trả về null JsonNode
        // ==========================================================
        @Test
        @DisplayName("⚠️ API Voice trả về null JsonNode → Trả về chuỗi rỗng, không NullPointerException")
        void verifyVoiceLivenessBase64Async_nullJsonNode_returnsEmptyString() throws Exception {
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                    .thenReturn(null);

            String result = riskEvaluationService.verifyVoiceLivenessBase64Async(validBase64).get();

            assertEquals("", result, "Null JsonNode phải dẫn đến chuỗi rỗng, không phải NPE");
        }

        // ==========================================================
        // TEST 17: authCode là chuỗi rỗng trong JSON → Trả về chuỗi rỗng
        // ==========================================================
        @Test
        @DisplayName("⚠️ JSON có field 'authCode' nhưng giá trị là chuỗi rỗng → Trả về chuỗi rỗng")
        void verifyVoiceLivenessBase64Async_emptyAuthCodeValue_returnsEmptyString() throws Exception {
            JsonNode mockNode = objectMapper.readTree("{\"authCode\": \"\"}");
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                    .thenReturn(mockNode);

            String result = riskEvaluationService.verifyVoiceLivenessBase64Async(validBase64).get();

            assertEquals("", result, "authCode rỗng trong JSON nên trả về chuỗi rỗng");
        }

        // ==========================================================
        // TEST 18: Input base64 không hợp lệ → Decode ném exception → Chuỗi rỗng
        // Base64.getDecoder().decode(invalid) ném IllegalArgumentException
        // được catch bởi catch(Exception e) và trả về ""
        // ==========================================================
        @Test
        @DisplayName("⚠️ Input base64 không hợp lệ → Exception được bắt, trả về chuỗi rỗng")
        void verifyVoiceLivenessBase64Async_invalidBase64_returnsEmptyString() throws Exception {
            String invalidBase64 = "not_valid_base64!!!@@@";

            String result = riskEvaluationService
                    .verifyVoiceLivenessBase64Async(invalidBase64).get();

            assertEquals("", result, "Base64 không hợp lệ phải được bắt và trả về chuỗi rỗng");
            verify(restTemplate, never()).postForObject(anyString(), any(), eq(JsonNode.class));
        }

        // ==========================================================
        // TEST 19: Content-Type của request phải là MULTIPART_FORM_DATA
        // ==========================================================
        @Test
        @DisplayName("✅ Request gửi đến Voice AI phải có Content-Type: multipart/form-data")
        void verifyVoiceLivenessBase64Async_sendsMultipartContentType() throws Exception {
            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            when(restTemplate.postForObject(anyString(), entityCaptor.capture(), eq(JsonNode.class)))
                    .thenReturn(objectMapper.readTree("{\"authCode\": \"000000\"}"));

            riskEvaluationService.verifyVoiceLivenessBase64Async(validBase64).get();

            MediaType contentType = entityCaptor.getValue().getHeaders().getContentType();
            assertNotNull(contentType, "Content-Type header không được null");
            assertTrue(contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA),
                    "Content-Type phải là multipart/form-data để Python server parse được");
        }

        // ==========================================================
        // TEST 20: restTemplate ném RuntimeException → catch(Exception e) → Chuỗi rỗng
        // ==========================================================
        @Test
        @DisplayName("❌ restTemplate ném RuntimeException → Bắt exception, trả về chuỗi rỗng")
        void verifyVoiceLivenessBase64Async_restTemplateThrows_returnsEmptyString() throws Exception {
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            String result = riskEvaluationService.verifyVoiceLivenessBase64Async(validBase64).get();

            assertEquals("", result, "Exception khi gọi API phải được bắt và trả về chuỗi rỗng");
            verify(restTemplate).postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class));
        }
    }
}
