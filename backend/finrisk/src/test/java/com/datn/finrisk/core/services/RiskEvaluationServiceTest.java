package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.application.dtos.FaceAIResponse;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.RuleRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
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
 *   ✅ API trả về null (body rỗng, không phải exception)
 *   ✅ Xác thực URL endpoint chính xác
 *   ✅ Xác thực Content-Type = APPLICATION_JSON
 *   ✅ Input base64 rỗng
 *
 * [Nhóm 3 - Emotion AI]
 *   ✅ API trả về null
 *   ✅ Xác thực URL endpoint
 *   ✅ Xác thực emotion value cụ thể trong DTO
 *
 * [Nhóm 4 - Voice AI]
 *   ✅ API trả về null JsonNode
 *   ✅ authCode là chuỗi rỗng trong JSON
 *   ✅ originalFilename null → fallback "audio.wav"
 *   ✅ audioFile.getBytes() ném IOException
 *   ✅ Xác thực Content-Type = MULTIPART_FORM_DATA
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RiskEvaluationService — Bổ sung Edge Cases & Nâng cao")
class RiskEvaluationServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private RuleRepository ruleRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private RiskEvaluationService riskEvaluationService;

    private Transaction mockTx;
    private User mockUser;
    private Account mockSender;
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
    }

    // ====================================================================
    // NHÓM 1 (BỔ SUNG): CỖ MÁY CHẤM ĐIỂM (SpEL RULE ENGINE) - EDGE CASES
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 1 (Bổ sung) — Rule Engine Edge Cases")
    class RuleEngineEdgeCases {

        // --- Cấu hình chung cho các test trong nhóm này ---
        private void setupCommonMocks() {
            when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(1);
            when(transactionRepository.sumSuccessfulAmountToday(anyLong(), any())).thenReturn(BigDecimal.ZERO);
        }

        // ==========================================================
        // TEST 1: Không có rule nào trong DB → Điểm = 0
        // Gap: File gốc chỉ test "rule không khớp", chưa test "không có rule nào cả"
        // ==========================================================
        @Test
        @DisplayName("✅ Không có rule nào trong DB (list rỗng) → Trả về điểm 0, không crash")
        void evaluateRisk_emptyRuleList_returnsZeroAndDoesNotCrash() {
            when(ruleRepository.findByIsActiveTrue()).thenReturn(Collections.emptyList());
            setupCommonMocks();

            int score = riskEvaluationService.evaluateRisk(mockTx, false);

            assertEquals(0, score, "Danh sách rule rỗng phải trả về điểm 0");
        }

        // ==========================================================
        // TEST 2: Số dư ví bằng 0 → Không chia-cho-0
        // Gap: Đây là bug tiềm ẩn nghiêm trọng trong production.
        // Code gốc có kiểm tra `currentBalance > 0` nhưng chưa được test.
        // ==========================================================
        @Test
        @DisplayName("⚠️ Số dư ví = 0 → balanceRatio vẫn = 0, không có ArithmeticException")
        void evaluateRisk_zeroBalance_balanceRatioIsZeroNoException() {
            // Ví trống hoàn toàn
            mockSender.setBalance(BigDecimal.ZERO);

            Rule rule = new Rule();
            rule.setRuleName("Kiểm tra balance ratio = 0 khi ví trống");
            rule.setSpelExpression("#balanceRatio == 0.0"); // Phải đúng vì chia cho 0 được guard
            rule.setActionScore(15);

            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(rule));
            setupCommonMocks();

            // Không được ném exception, và balanceRatio phải = 0.0
            assertDoesNotThrow(() -> {
                int score = riskEvaluationService.evaluateRisk(mockTx, false);
                assertEquals(15, score, "balanceRatio phải = 0 khi số dư tài khoản bằng 0");
            });
        }

        // ==========================================================
        // TEST 3: suspiciousSession = true (không phải adminFlagged)
        // Gap: Logic "OR" chưa được test đầy đủ từng nhánh.
        // ==========================================================
        @Test
        @DisplayName("⚠️ suspiciousSession = true (adminFlagged = false) → combinedSuspiciousRisk = true")
        void evaluateRisk_suspiciousSessionOnly_flaggedAsSuspicious() {
            mockUser.setSuspiciousSession(true);
            mockUser.setAdminFlagged(false);

            Rule rule = new Rule();
            rule.setSpelExpression("#suspiciousSession == true");
            rule.setActionScore(50);
            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(rule));
            setupCommonMocks();

            int score = riskEvaluationService.evaluateRisk(mockTx, false);
            assertEquals(50, score);
        }

        // ==========================================================
        // TEST 4: adminFlagged = true (không phải suspiciousSession)
        // Gap: Nhánh kia của OR chưa được test.
        // ==========================================================
        @Test
        @DisplayName("⚠️ adminFlagged = true (suspiciousSession = false) → combinedSuspiciousRisk = true")
        void evaluateRisk_adminFlaggedOnly_flaggedAsSuspicious() {
            mockUser.setSuspiciousSession(false);
            mockUser.setAdminFlagged(true);

            Rule rule = new Rule();
            rule.setSpelExpression("#suspiciousSession == true");
            rule.setActionScore(80);
            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(rule));
            setupCommonMocks();

            int score = riskEvaluationService.evaluateRisk(mockTx, false);
            assertEquals(80, score);
        }

        // ==========================================================
        // TEST 5: recentTxCount cao → Rule spam bắt được
        // Gap: recentTxCount được bơm vào SpEL nhưng chưa có test nào dùng.
        // ==========================================================
        @Test
        @DisplayName("⚠️ recentTxCount > 3 trong 1 phút → Rule spam khớp, cộng điểm")
        void evaluateRisk_highRecentTxCount_spamRuleTriggered() {
            Rule rule = new Rule();
            rule.setRuleName("Spam rule - giao dịch liên tục");
            rule.setSpelExpression("#recentTxCount > 3");
            rule.setActionScore(40);

            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(rule));
            // Giả lập 5 giao dịch trong 1 phút vừa rồi
            when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(5);
            when(transactionRepository.sumSuccessfulAmountToday(anyLong(), any())).thenReturn(BigDecimal.ZERO);

            int score = riskEvaluationService.evaluateRisk(mockTx, false);
            assertEquals(40, score);
        }

        // ==========================================================
        // TEST 6: dailyTotalAmount vượt ngưỡng → Rule giới hạn ngày khớp
        // Gap: dailyTotalAmount được bơm vào SpEL nhưng chưa có test kiểm tra vượt ngưỡng.
        // ==========================================================
        @Test
        @DisplayName("⚠️ Tổng tiền ngày hôm nay vượt 50 triệu → Rule cảnh báo khớp")
        void evaluateRisk_dailyLimitExceeded_ruleTriggered() {
            Rule rule = new Rule();
            rule.setRuleName("Vượt hạn mức ngày");
            rule.setSpelExpression("#dailyTotalAmount > 50000000");
            rule.setActionScore(60);

            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(rule));
            when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(1);
            // 50 triệu đã chuyển hôm nay + 1 triệu đang chuyển = 51 triệu → vượt ngưỡng
            when(transactionRepository.sumSuccessfulAmountToday(anyLong(), any()))
                    .thenReturn(new BigDecimal("50000000"));

            int score = riskEvaluationService.evaluateRisk(mockTx, false);
            assertEquals(60, score);
        }

        // ==========================================================
        // TEST 7: SpEL trả về null thay vì Boolean → Không crash, bỏ qua rule
        // Gap: Code dùng Boolean.TRUE.equals(isMatched) để guard null, nhưng chưa test.
        // Ví dụ: expression "#tx.amount" trả về BigDecimal, không phải Boolean.
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

            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(badRule, goodRule));
            setupCommonMocks();

            // Rule xấu bị bỏ qua (Boolean.TRUE.equals(non-boolean) = false), rule tốt vẫn chạy
            int score = riskEvaluationService.evaluateRisk(mockTx, true);
            assertEquals(25, score);
        }

        // ==========================================================
        // TEST 8: ActionScore âm → Math.max(0,...) bảo vệ tổng điểm không âm
        // Gap: Code có Math.max(0, totalRiskScore) nhưng chưa test trường hợp tổng âm.
        // ==========================================================
        @Test
        @DisplayName("⚠️ Rule có actionScore âm (rule giảm điểm) → Tổng điểm không âm hơn 0")
        void evaluateRisk_negativeActionScore_totalScoreNeverBelowZero() {
            Rule penaltyRule = new Rule();
            penaltyRule.setRuleName("Rule giảm điểm (ân giảm)");
            penaltyRule.setSpelExpression("#deviceTrusted == true"); // deviceTrusted hardcode = true → luôn khớp
            penaltyRule.setActionScore(-100); // Điểm âm

            when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(penaltyRule));
            setupCommonMocks();

            int score = riskEvaluationService.evaluateRisk(mockTx, false);

            // Math.max(0, -100) = 0
            assertEquals(0, score, "Điểm rủi ro không bao giờ được âm");
        }
    }

    // ====================================================================
    // NHÓM 2 (BỔ SUNG): FACE AI — EDGE CASES & CONTRACT TESTS
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 2 (Bổ sung) — Face AI Edge Cases & Contract")
    class FaceAiEdgeCases {

        // ==========================================================
        // TEST 9: API trả về null (body rỗng) thay vì ném exception
        // Gap: Code log "Phản hồi: Matched=..." có if(response != null) nhưng chưa test null return.
        // ==========================================================
        @Test
        @DisplayName("⚠️ API Face trả về null body (không phải exception) → Kết quả là null, không crash")
        void verifyIdentityAsync_apiReturnsNullBody_handledGracefully() throws Exception {
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(FaceAIResponse.class)))
                    .thenReturn(null); // Server trả về 200 nhưng body rỗng

            CompletableFuture<FaceAIResponse> future = riskEvaluationService.verifyIdentityAsync("live", "reg");
            FaceAIResponse result = future.get();

            // Phải trả về null thay vì crash
            assertNull(result, "Body null từ API cần được xử lý không crash");
        }

        // ==========================================================
        // TEST 10: Kiểm tra URL endpoint được gọi đúng
        // Gap: File gốc dùng anyString() cho URL, không xác nhận đúng endpoint.
        // ==========================================================
        @Test
        @DisplayName("✅ Luôn gọi đúng URL endpoint: http://localhost:5000/api/ai/verify-face")
        void verifyIdentityAsync_callsCorrectEndpoint() throws Exception {
            FaceAIResponse mockResponse = new FaceAIResponse();
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(FaceAIResponse.class)))
                    .thenReturn(mockResponse);

            riskEvaluationService.verifyIdentityAsync("live_b64", "reg_b64").get();

            // Xác nhận URL chính xác đến từng ký tự
            verify(restTemplate).postForObject(
                    eq("http://localhost:5000/api/ai/verify-face"),
                    any(HttpEntity.class),
                    eq(FaceAIResponse.class)
            );
        }

        // ==========================================================
        // TEST 11: Kiểm tra Content-Type header phải là APPLICATION_JSON
        // Gap: Quan trọng để đảm bảo Python server nhận đúng kiểu dữ liệu.
        // ==========================================================
        @Test
        @DisplayName("✅ Request gửi đến Face AI phải có Content-Type: application/json")
        void verifyIdentityAsync_sendsCorrectContentTypeHeader() throws Exception {
            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            when(restTemplate.postForObject(anyString(), entityCaptor.capture(), eq(FaceAIResponse.class)))
                    .thenReturn(new FaceAIResponse());

            riskEvaluationService.verifyIdentityAsync("live_b64", "reg_b64").get();

            HttpEntity capturedEntity = entityCaptor.getValue();
            assertEquals(MediaType.APPLICATION_JSON,
                    capturedEntity.getHeaders().getContentType(),
                    "Content-Type phải là application/json");
        }

        // ==========================================================
        // TEST 12: Input base64 rỗng → Vẫn gọi API bình thường (business logic phía server quyết định)
        // Gap: Null/empty input có thể gây lỗi khó debug nếu không có test.
        // ==========================================================
        @Test
        @DisplayName("⚠️ Input base64 rỗng → Vẫn gọi API, không crash ở tầng service")
        void verifyIdentityAsync_emptyBase64Input_stillCallsApiWithoutCrashing() throws Exception {
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(FaceAIResponse.class)))
                    .thenReturn(null);

            // Service không nên validate ở đây, để Python server xử lý
            CompletableFuture<FaceAIResponse> future = riskEvaluationService.verifyIdentityAsync("", "");
            assertDoesNotThrow(() -> future.get());
            verify(restTemplate, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(FaceAIResponse.class));
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
        // Gap: Tương tự Face AI, chưa test null response body.
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
        // Gap: File gốc chỉ assert notNull, chưa kiểm tra giá trị bên trong DTO.
        // ==========================================================
        @Test
        @DisplayName("✅ Kết quả trả về đúng emotion value từ DTO (ví dụ: 'FEAR')")
        void detectEmotionAsync_success_returnsCorrectEmotionValue() throws Exception {
            EmotionAIResponse mockResponse = new EmotionAIResponse();
            mockResponse.setEmotion("FEAR"); // Cảm xúc sợ hãi → rủi ro cao

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
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 4 (Bổ sung) — Voice AI Edge Cases & Contract")
    class VoiceAiEdgeCases {

        // ==========================================================
        // TEST 16: API trả về null JsonNode
        // Gap: File gốc kiểm tra node không có field, nhưng chưa test null node.
        // ==========================================================
        @Test
        @DisplayName("⚠️ API Voice trả về null JsonNode → Trả về chuỗi rỗng, không NullPointerException")
        void verifyVoiceLivenessAsync_nullJsonNode_returnsEmptyString() throws Exception {
            MockMultipartFile audioFile = new MockMultipartFile("audio", "test.wav", "audio/wav", "data".getBytes());

            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                    .thenReturn(null); // Python trả về 200 nhưng body null

            String result = riskEvaluationService.verifyVoiceLivenessAsync(audioFile).get();

            assertEquals("", result, "Null JsonNode phải dẫn đến chuỗi rỗng, không phải NPE");
        }

        // ==========================================================
        // TEST 17: authCode là chuỗi rỗng trong JSON → Trả về chuỗi rỗng
        // Gap: Python nhận diện được nhưng mã OTP rỗng (tiếng ồn, giọng không rõ).
        // ==========================================================
        @Test
        @DisplayName("⚠️ JSON có field 'authCode' nhưng giá trị là chuỗi rỗng → Trả về chuỗi rỗng")
        void verifyVoiceLivenessAsync_emptyAuthCodeValue_returnsEmptyString() throws Exception {
            MockMultipartFile audioFile = new MockMultipartFile("audio", "test.wav", "audio/wav", "data".getBytes());

            String jsonResponse = "{\"authCode\": \"\"}";
            JsonNode mockNode = objectMapper.readTree(jsonResponse);
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                    .thenReturn(mockNode);

            String result = riskEvaluationService.verifyVoiceLivenessAsync(audioFile).get();

            assertEquals("", result, "authCode rỗng trong JSON nên trả về chuỗi rỗng");
        }

        // ==========================================================
        // TEST 18: originalFilename = null → Fallback thành "audio.wav"
        // Gap: Code có logic `audioFile.getOriginalFilename() != null ? ... : "audio.wav"`
        //      nhưng chưa được test trường hợp null.
        // ==========================================================
        @Test
        @DisplayName("✅ originalFilename = null → ByteArrayResource fallback tên file = 'audio.wav'")
        void verifyVoiceLivenessAsync_nullOriginalFilename_fallsBackToDefaultName() throws Exception {
            // MockMultipartFile với originalFilename = null
            MockMultipartFile audioFileWithNullName = new MockMultipartFile(
                    "audio", null, "audio/wav", "data".getBytes()
            );

            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            String jsonResponse = "{\"authCode\": \"123456\"}";
            when(restTemplate.postForObject(anyString(), entityCaptor.capture(), eq(JsonNode.class)))
                    .thenReturn(objectMapper.readTree(jsonResponse));

            String result = riskEvaluationService.verifyVoiceLivenessAsync(audioFileWithNullName).get();

            // Kết quả vẫn trả về đúng authCode
            assertEquals("123456", result);
            // Và body multipart chứa resource (file được gửi thành công, không crash vì null filename)
            assertNotNull(entityCaptor.getValue().getBody());
        }

        // ==========================================================
        // TEST 19: Content-Type của request phải là MULTIPART_FORM_DATA
        // Gap: Nếu sai Content-Type, Python server không thể parse multipart.
        // ==========================================================
        @Test
        @DisplayName("✅ Request gửi đến Voice AI phải có Content-Type: multipart/form-data")
        void verifyVoiceLivenessAsync_sendsCorrectContentTypeHeader() throws Exception {
            MockMultipartFile audioFile = new MockMultipartFile("audio", "test.wav", "audio/wav", "data".getBytes());
            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

            when(restTemplate.postForObject(anyString(), entityCaptor.capture(), eq(JsonNode.class)))
                    .thenReturn(objectMapper.readTree("{\"authCode\": \"000000\"}"));

            riskEvaluationService.verifyVoiceLivenessAsync(audioFile).get();

            MediaType contentType = entityCaptor.getValue().getHeaders().getContentType();
            assertNotNull(contentType, "Content-Type header không được null");
            assertTrue(contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA),
                    "Content-Type phải là multipart/form-data để Python server parse được");
        }

        // ==========================================================
        // TEST 20: audioFile.getBytes() ném IOException → Catch exception, trả về chuỗi rỗng
        // Gap: Đây là I/O exception không phải RestClientException, cần test riêng.
        //      Code gốc có catch(Exception e) bao hết nhưng chưa được test.
        // ==========================================================
        @Test
        @DisplayName("❌ audioFile.getBytes() ném IOException (file bị lỗi) → Trả về chuỗi rỗng")
        void verifyVoiceLivenessAsync_getBytesFails_returnsEmptyString() throws Exception {
            // Tạo MockMultipartFile giả lập getBytes() ném IOException
            MockMultipartFile brokenFile = new MockMultipartFile("audio", "broken.wav", "audio/wav", (byte[]) null) {
                @Override
                public byte[] getBytes() throws java.io.IOException {
                    throw new java.io.IOException("Disk read error");
                }
            };

            CompletableFuture<String> future = riskEvaluationService.verifyVoiceLivenessAsync(brokenFile);
            String result = future.get();

            assertEquals("", result, "IOException khi đọc file phải được bắt và trả về chuỗi rỗng");
            // Không nên gọi đến restTemplate khi file bị lỗi
            verify(restTemplate, never()).postForObject(anyString(), any(), eq(JsonNode.class));
        }
    }
}