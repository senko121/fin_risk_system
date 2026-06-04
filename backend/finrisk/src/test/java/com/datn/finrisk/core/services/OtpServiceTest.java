package com.datn.finrisk.core.services;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;   
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService — Bộ Test Toàn Diện")
class OtpServiceTest {

    // ========== CONSTANTS ==========
    private static final Long   TX_ID       = 999L;
    private static final String REDIS_KEY   = "otp_tx:999";
    private static final String USER_EMAIL  = "thach@example.com";
    private static final int    OTP_TTL_SEC = 180;

    // ========== MOCKS ==========
    @Mock private StringRedisTemplate            redisTemplate;
    @Mock private EmailService                   emailService;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "twilioAccountSid",  "fake_sid");
        ReflectionTestUtils.setField(otpService, "twilioAuthToken",   "fake_token");
        ReflectionTestUtils.setField(otpService, "twilioPhoneNumber", "+1000000000");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==========================================================
    // NHÓM 1 — SAVE OTP VÀO REDIS
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 1 — saveOtp()")
    class SaveOtpTests {

        @Test
        @DisplayName("✅ Lưu đúng key, value và TTL = 180s")
        void saveOtp_storesCorrectKeyValueAndTtl() {
            otpService.saveOtp(TX_ID, "123456");

            verify(valueOperations).set(eq(REDIS_KEY), eq("123456"), eq(Duration.ofSeconds(OTP_TTL_SEC)));
        }

        @Test
        @DisplayName("✅ Key phải có prefix 'otp_tx:' + transactionId")
        void saveOtp_keyFormatIsCorrect() {
            otpService.saveOtp(42L, "111111");

            // Đảm bảo format key đúng với bất kỳ ID nào
            verify(valueOperations).set(eq("otp_tx:42"), eq("111111"), any(Duration.class));
        }

        @Test
        @DisplayName("✅ OTP '000000' (edge case min) vẫn được lưu đúng")
        void saveOtp_withAllZerosOtp_storesCorrectly() {
            // nextInt(999999) có thể trả 0 -> format %06d -> "000000"
            otpService.saveOtp(TX_ID, "000000");

            verify(valueOperations).set(eq(REDIS_KEY), eq("000000"), eq(Duration.ofSeconds(OTP_TTL_SEC)));
        }

        @Test
        @DisplayName("⚠️ [BUG DETECTION] saveOtp KHÔNG được lưu key 'compare_key' không có TTL lên Redis")
        void saveOtp_mustNotSaveDebugKeyWithoutTtl() {
            /*
             * BUG: Trong code gốc có dòng:
             *   redisTemplate.opsForValue().set("compare_key", "from_spring");
             * Đây là debug code thừa, lưu key KHÔNG CÓ TTL lên Redis production.
             * Key này sẽ tồn tại vĩnh viễn và gây memory leak.
             * Test này sẽ FAIL với code hiện tại, nhắc developer xóa dòng đó đi.
             */
            otpService.saveOtp(TX_ID, "123456");

            // 2-arg set (không có TTL) không được gọi với bất kỳ key nào
            verify(valueOperations, never()).set(anyString(), anyString());
        }

        @Test
        @DisplayName("❌ Redis lỗi khi save — Không throw exception ra ngoài (fail-safe)")
        void saveOtp_whenRedisThrows_doesNotPropagateException() {
            doThrow(new RuntimeException("Redis connection refused"))
                    .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            // Hệ thống có catch bên trong, không được để exception leak ra
            assertDoesNotThrow(() -> otpService.saveOtp(TX_ID, "123456"));
        }
    }

    // ==========================================================
    // NHÓM 2 — GENERATE VOICE OTP
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 2 — generateVoiceOtp()")
    class GenerateVoiceOtpTests {

        @Test
        @DisplayName("✅ Tạo OTP đúng 6 chữ số")
        void generateVoiceOtp_producesExactly6Digits() {
            String otp = otpService.generateVoiceOtp(TX_ID);

            assertNotNull(otp);
            assertEquals(6, otp.length(), "OTP phải đúng 6 ký tự");
            assertTrue(otp.matches("\\d{6}"), "OTP chỉ được chứa chữ số");
        }

        @Test
        @DisplayName("✅ OTP được lưu vào Redis sau khi tạo")
        void generateVoiceOtp_savesToRedis() {
            String otp = otpService.generateVoiceOtp(TX_ID);

            verify(valueOperations).set(eq("voice_otp_tx:" + TX_ID), eq(otp), eq(Duration.ofSeconds(OTP_TTL_SEC)));
        }

        @Test
        @DisplayName("✅ generateVoiceOtp KHÔNG gửi email, KHÔNG gọi Twilio")
        void generateVoiceOtp_neverSendsEmailOrSms() {
            otpService.generateVoiceOtp(TX_ID);

            verify(emailService, never()).sendOtpEmail(anyString(), anyString());
            // Twilio không có mock nên nếu bị gọi thật sẽ throw -> test sẽ fail tự nhiên
        }

        @Test
        @DisplayName("✅ Hai lần gọi liên tiếp trả về giá trị (có thể) khác nhau")
        void generateVoiceOtp_twoCallsProduceDifferentOtps() {
            /*
             * Về mặt lý thuyết có xác suất cực nhỏ ra OTP trùng (1/1.000.000).
             * Chạy 5 lần để xác suất trùng hoàn toàn < 1 phần nghìn tỷ.
             */
            java.util.Set<String> results = new java.util.HashSet<>();
            for (int i = 0; i < 5; i++) {
                results.add(otpService.generateVoiceOtp(TX_ID));
            }
            assertTrue(results.size() > 1, "5 lần sinh OTP không thể ra cùng 1 mã");
        }
    }

    // ==========================================================
    // NHÓM 3 — GENERATE AND SEND OTP ASYNC (FALLBACK EMAIL)
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 3 — generateAndSendOtpAsync() + Fallback")
    class GenerateAndSendOtpTests {

        @Test
        @DisplayName("⚠️ SMS thất bại → Fallback gửi đúng email của user trong transaction")
        void generateAndSendOtp_whenSmsFails_sendsEmailToCorrectAddress()
                throws ExecutionException, InterruptedException {
            // Twilio chưa được init thật -> Message.creator() sẽ throw -> kích hoạt fallback email
            CompletableFuture<Void> future = otpService.generateAndSendOtpAsync(TX_ID, null, USER_EMAIL);
            future.get(); // Chờ async hoàn thành

            verify(emailService, times(1)).sendOtpEmail(eq(USER_EMAIL), anyString());
        }

        @Test
        @DisplayName("⚠️ SMS thất bại → OTP gửi qua email phải đúng 6 chữ số")
        void generateAndSendOtp_whenSmsFails_emailReceivesValid6DigitOtp()
                throws ExecutionException, InterruptedException {
            ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);

            otpService.generateAndSendOtpAsync(TX_ID, null, USER_EMAIL).get();

            verify(emailService).sendOtpEmail(anyString(), otpCaptor.capture());
            String sentOtp = otpCaptor.getValue();
            assertTrue(sentOtp.matches("\\d{6}"), "OTP gửi qua email phải là 6 chữ số, thực tế: " + sentOtp);
        }

        @Test
        @DisplayName("⚠️ SMS thất bại → OTP fallback email phải khớp OTP đã lưu trong Redis")
        void generateAndSendOtp_whenSmsFails_emailOtpMatchesRedisOtp()
                throws ExecutionException, InterruptedException {
            /*
             * Đây là test quan trọng nhất của nhóm này:
             * Đảm bảo OTP gửi đi qua email là CÙNG MÃ với OTP đã lưu vào Redis.
             * Nếu 2 mã khác nhau -> user không thể verify được dù nhập đúng email.
             */
            ArgumentCaptor<String> redisCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);

            otpService.generateAndSendOtpAsync(TX_ID, null, USER_EMAIL).get();

            // Lấy OTP đã lưu Redis
            verify(valueOperations).set(eq(REDIS_KEY), redisCaptor.capture(), any(Duration.class));
            // Lấy OTP đã gửi Email
            verify(emailService).sendOtpEmail(anyString(), emailCaptor.capture());

            assertEquals(redisCaptor.getValue(), emailCaptor.getValue(),
                    "OTP lưu Redis và OTP gửi Email PHẢI giống nhau!");
        }

        @Test
        @DisplayName("❌ Cả SMS và Email đều lỗi — Không throw exception, hệ thống vẫn sống")
        void generateAndSendOtp_whenBothSmsAndEmailFail_doesNotCrash()
                throws ExecutionException, InterruptedException {
            doThrow(new RuntimeException("SMTP server down"))
                    .when(emailService).sendOtpEmail(anyString(), anyString());

            CompletableFuture<Void> future = otpService.generateAndSendOtpAsync(TX_ID, null, USER_EMAIL);

  
            assertDoesNotThrow(() -> future.get());
        }

        @Test
        @DisplayName("❌ Transaction có email null — Fallback email không crash hệ thống")
        void generateAndSendOtp_whenUserEmailIsNull_doesNotCrash() {
            // NullPointerException trong block catch không được lan ra ngoài
            assertDoesNotThrow(() -> {
                try {
                    otpService.generateAndSendOtpAsync(TX_ID, null, null).get();
                } catch (ExecutionException e) {
                    fail("Không được throw ExecutionException: " + e.getCause());
                }
            });
        }

        @Test
        @DisplayName("✅ OTP luôn được lưu Redis TRƯỚC KHI gửi tin nhắn")
        void generateAndSendOtp_savesToRedisBeforeSending()
                throws ExecutionException, InterruptedException {
            // Dùng InOrder để kiểm tra thứ tự gọi
            var inOrder = inOrder(valueOperations, emailService);

            otpService.generateAndSendOtpAsync(TX_ID, null, USER_EMAIL).get();

            inOrder.verify(valueOperations).set(eq(REDIS_KEY), anyString(), any(Duration.class));
            inOrder.verify(emailService).sendOtpEmail(anyString(), anyString());
        }
    }

    // ==========================================================
    // NHÓM 4 — VERIFY OTP
    // ==========================================================
    @Nested
    @DisplayName("Nhóm 4 — verifyOtp()")
    class VerifyOtpTests {

        // verifyOtp() reads attemptsKey before otpKey; stub it so STRICT_STUBS does not
        // raise PotentialStubbingProblem when a different stub exists for the otp key.
        @BeforeEach
        void stubAttempts() {
            lenient().when(valueOperations.get("otp_attempts_tx:" + TX_ID)).thenReturn(null);
        }

        @Test
        @DisplayName("✅ OTP khớp → true + XÓA key khỏi Redis ngay lập tức")
        void verifyOtp_whenMatch_returnsTrueAndDeletesKey() {
            when(valueOperations.get(REDIS_KEY)).thenReturn("123456");

            assertTrue(otpService.verifyOtp(TX_ID, "123456"));
            verify(redisTemplate).delete(REDIS_KEY);
        }

        @Test
        @DisplayName("❌ OTP sai → false + GIỮ NGUYÊN key (để user nhập lại)")
        void verifyOtp_whenMismatch_returnsFalseAndKeepsKey() {
            when(valueOperations.get(REDIS_KEY)).thenReturn("123456");

            assertFalse(otpService.verifyOtp(TX_ID, "654321"));
            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("❌ Redis trả null (OTP hết hạn) → false")
        void verifyOtp_whenExpired_returnsFalse() {
            when(valueOperations.get(REDIS_KEY)).thenReturn(null);

            assertFalse(otpService.verifyOtp(TX_ID, "123456"));
            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("❌ Redis timeout/exception → false (fail-safe, không crash app)")
        void verifyOtp_whenRedisThrows_returnsFalseSafely() {
            when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis Timeout"));

            assertFalse(otpService.verifyOtp(TX_ID, "123456"));
        }

        @ParameterizedTest(name = "Input OTP = [{0}] → false")
        @NullAndEmptySource
        @ValueSource(strings = {"      ", "12345", "1234567", "abcdef", "12 345"})
        @DisplayName("❌ Input OTP không hợp lệ (null, rỗng, sai format) → false")
        void verifyOtp_withInvalidInput_returnsFalse(String badInput) {
            when(valueOperations.get(REDIS_KEY)).thenReturn("123456");

            assertFalse(otpService.verifyOtp(TX_ID, badInput),
                    "Input không hợp lệ '" + badInput + "' không được pass verify");
        }

        @Test
        @DisplayName("❌ OTP đã dùng không thể dùng lại (key đã bị xóa sau lần verify đầu)")
        void verifyOtp_afterSuccessfulVerify_cannotBeReused() {
            when(valueOperations.get(REDIS_KEY))
                    .thenReturn("123456")  // Lần gọi 1: còn tồn tại
                    .thenReturn(null);     // Lần gọi 2: đã bị xóa

            // Lần 1: thành công
            assertTrue(otpService.verifyOtp(TX_ID, "123456"));
            // Lần 2: dùng lại cùng OTP → phải fail
            assertFalse(otpService.verifyOtp(TX_ID, "123456"), "OTP không được dùng lại sau khi đã verify");
        }

        @Test
        @DisplayName("❌ Case-sensitive: '123456' ≠ '123456 ' (có space)")
        void verifyOtp_withTrailingSpace_returnsFalse() {
            when(valueOperations.get(REDIS_KEY)).thenReturn("123456");

            assertFalse(otpService.verifyOtp(TX_ID, "123456 "));
        }
    }
}