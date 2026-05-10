package com.datn.finrisk.core.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BỔ SUNG edge case & case quan trọng cho EmailService.
 * File này bổ sung cho EmailServiceTest.java gốc, không trùng lặp test cũ.
 *
 * Danh sách gap đã lấp:
 *
 * [sendOtpEmail — contract & idempotency]
 *   ✅ send() gọi đúng 1 lần khi thành công (không double-send)
 *   ✅ To field: chỉ có đúng 1 recipient, không có cc/bcc thêm
 *   ✅ Subject KHÔNG chứa OTP (bảo mật: tiêu đề không được lộ mã)
 *
 * [sendOtpEmail — input validation & null safety]
 *   ✅ toEmail = null → không crash (try-catch bao hết)
 *   ✅ toEmail = "" (rỗng) → không crash
 *   ✅ otp = null → body không in chuỗi "null" literal
 *   ✅ otp = "" → body không để trống chỗ mã OTP một cách im lặng
 *
 * [sendOtpEmail — exception variety]
 *   ✅ RuntimeException thường (khác MailException) → vẫn không crash
 *   ✅ NullPointerException từ mailSender → vẫn không crash
 *
 * [sendOtpEmail — OTP format edge cases]
 *   ✅ OTP toàn số 0 ("000000") → body in đúng, không bị trim hay falsy
 *   ✅ OTP dạng ngắn 1 ký tự → body vẫn chứa đúng
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService — Bổ sung Edge Cases")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    // Helper: gửi và bắt message đã được capture
    private SimpleMailMessage captureMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        return captor.getValue();
    }

    // ====================================================================
    // NHÓM 1: CONTRACT & IDEMPOTENCY
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 1 — Contract & Idempotency")
    class ContractTests {

        // ==========================================================
        // TEST 1: send() chỉ gọi đúng 1 lần — không double-send
        // Tại sao quan trọng: Nếu code có bug gọi mailSender.send() 2 lần,
        //   user nhận 2 email OTP cùng lúc → confusing và tốn quota SMTP.
        //   Test gốc dùng times(1) trong verify nhưng chỉ trong test exception path,
        //   chưa verify trong happy path.
        // ==========================================================
        @Test
        @DisplayName("✅ Gửi email thành công → send() được gọi đúng 1 lần, không gửi trùng")
        void sendOtpEmail_success_sendCalledExactlyOnce() {
            emailService.sendOtpEmail("user@gmail.com", "112233");

            verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        }

        // ==========================================================
        // TEST 2: To field chỉ có đúng 1 recipient
        // Tại sao quan trọng: SimpleMailMessage.setTo() nhận varargs — nếu ai đó
        //   sửa code thành setTo(email, "debug@internal.com") thì OTP bị leak sang
        //   địa chỉ nội bộ mà không có test nào phát hiện ra.
        // ==========================================================
        @Test
        @DisplayName("✅ To field chỉ có đúng 1 recipient — không có cc/bcc ẩn nào thêm vào")
        void sendOtpEmail_success_exactlyOneRecipient() {
            emailService.sendOtpEmail("user@gmail.com", "112233");

            SimpleMailMessage msg = captureMessage();
            String[] toList = msg.getTo();

            assertNotNull(toList, "To list không được null");
            assertEquals(1, toList.length, "Chỉ được phép có đúng 1 địa chỉ nhận, không cc thêm bất kỳ ai");
            assertEquals("user@gmail.com", toList[0]);
        }

        // ==========================================================
        // TEST 3: Subject KHÔNG chứa OTP
        // Tại sao quan trọng: Một số email client hiển thị subject trong notification
        //   preview — nếu subject chứa OTP thì mã bị lộ ngay cả khi user chưa mở email.
        //   Đây là lỗi bảo mật thực tế trong nhiều hệ thống ngân hàng.
        // ==========================================================
        @Test
        @DisplayName("🔒 Subject email KHÔNG chứa mã OTP (bảo mật: push notification không lộ mã)")
        void sendOtpEmail_success_subjectDoesNotContainOtp() {
            String otp = "998877";
            emailService.sendOtpEmail("user@gmail.com", otp);

            SimpleMailMessage msg = captureMessage();
            String subject = msg.getSubject();

            assertNotNull(subject);
            assertFalse(subject.contains(otp),
                    "Subject không được chứa mã OTP — lộ mã qua push notification preview là lỗ hổng bảo mật");
        }
    }

    // ====================================================================
    // NHÓM 2: NULL & EMPTY INPUT SAFETY
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 2 — Null & Empty Input Safety")
    class NullInputTests {

        // ==========================================================
        // TEST 4: toEmail = null → không crash ra ngoài service
        // Tại sao quan trọng: Caller có thể truyền null nếu DB không có email user.
        //   Code có try-catch(Exception e) bao hết → phải test để chắc catch đủ rộng.
        // ==========================================================
        @Test
        @DisplayName("⚠️ toEmail = null → try-catch bắt được, không ném exception ra ngoài service")
        void sendOtpEmail_nullEmail_doesNotThrow() {
            // mailSender.send() sẽ nhận message với To=null và có thể ném exception nội bộ
            // Service phải bắt lại, không để exception nổi lên caller
            assertDoesNotThrow(() -> emailService.sendOtpEmail(null, "112233"));
        }

        // ==========================================================
        // TEST 5: toEmail = "" → không crash
        // Tại sao quan trọng: Email rỗng khác null — SMTP server sẽ từ chối
        //   với một loại exception khác (IllegalArgumentException hoặc MailException).
        //   Cả hai đều phải được bắt.
        // ==========================================================
        @Test
        @DisplayName("⚠️ toEmail = \"\" (rỗng) → không crash, lỗi SMTP được nuốt trong try-catch")
        void sendOtpEmail_emptyEmail_doesNotThrow() {
            // Giả lập SMTP từ chối địa chỉ rỗng
            doThrow(new MailSendException("Invalid address"))
                    .when(mailSender).send(any(SimpleMailMessage.class));

            assertDoesNotThrow(() -> emailService.sendOtpEmail("", "112233"));
        }

        // ==========================================================
        // TEST 6: otp = null → body KHÔNG được in chuỗi "null" literal
        // Tại sao quan trọng: Java string concatenation "Mã OTP là: " + null = "Mã OTP là: null"
        //   → User nhận email với nội dung "Mã OTP là: null" rất mất uy tín và confusing.
        //   Nếu code không guard null OTP, bug này im lặng hoàn toàn (không có exception).
        // ==========================================================
        @Test
        @DisplayName("⚠️ otp = null → body email KHÔNG được chứa chuỗi \"null\" literal")
        void sendOtpEmail_nullOtp_bodyDoesNotContainNullString() {
            emailService.sendOtpEmail("user@gmail.com", null);

            SimpleMailMessage msg = captureMessage();
            String body = msg.getText();

            assertNotNull(body);
            assertFalse(body.contains("null"),
                    "Body email không được chứa chuỗi 'null' literal — " +
                    "user sẽ thấy 'Mã OTP: null' nếu không guard. " +
                    "Cần thêm null check hoặc default value cho OTP.");
        }

        // ==========================================================
        // TEST 7: otp = "" → body KHÔNG để trống im lặng (phải xử lý hoặc báo lỗi)
        // Tại sao quan trọng: OTP rỗng nghĩa là code generate OTP bị lỗi trước đó.
        //   Email gửi đi với mã trống khiến user không thể giao dịch nhưng lại không
        //   biết lý do. Cần document behavior hiện tại — hoặc không gửi, hoặc ghi log rõ.
        // ==========================================================
        @Test
        @DisplayName("⚠️ otp = \"\" (rỗng) → send() vẫn được gọi (document behavior hiện tại: không guard otp rỗng)")
        void sendOtpEmail_emptyOtp_sendIsStillCalled() {
            emailService.sendOtpEmail("user@gmail.com", "");

            // Document behavior hiện tại: service không validate OTP trước khi gửi.
            // Nếu muốn chặn case này, cần thêm guard và đổi test thành verify(mailSender, never()).send(...)
            verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        }
    }

    // ====================================================================
    // NHÓM 3: EXCEPTION VARIETY
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 3 — Exception Variety")
    class ExceptionVarietyTests {

        // ==========================================================
        // TEST 8: RuntimeException thường (không phải MailException) → không crash
        // Tại sao quan trọng: Code catch (Exception e) — nhưng nếu ai đó sửa thành
        //   catch (MailException e) thì RuntimeException sẽ lọt ra ngoài và crash cả
        //   transaction đang gọi service này. Test này bảo vệ độ rộng của catch.
        // ==========================================================
        @Test
        @DisplayName("❌ RuntimeException thông thường từ mailSender → vẫn không crash service")
        void sendOtpEmail_runtimeExceptionFromMailSender_caughtSafely() {
            doThrow(new RuntimeException("Unexpected internal error"))
                    .when(mailSender).send(any(SimpleMailMessage.class));

            assertDoesNotThrow(() -> emailService.sendOtpEmail("user@gmail.com", "112233"),
                    "catch(Exception e) phải bắt được RuntimeException, không chỉ MailException");
        }

        // ==========================================================
        // TEST 9: NullPointerException từ mailSender → không crash
        // Tại sao quan trọng: Nếu mailSender bean bị misconfigure trong môi trường
        //   staging/production (ví dụ thiếu SMTP password), Spring có thể inject một
        //   proxy lỗi và ném NPE thay vì MailException. catch(Exception) phải bắt được.
        // ==========================================================
        @Test
        @DisplayName("❌ NullPointerException từ mailSender (misconfigure) → vẫn không crash service")
        void sendOtpEmail_nullPointerFromMailSender_caughtSafely() {
            doThrow(new NullPointerException("Mail session is null"))
                    .when(mailSender).send(any(SimpleMailMessage.class));

            assertDoesNotThrow(() -> emailService.sendOtpEmail("user@gmail.com", "112233"));
        }
    }

    // ====================================================================
    // NHÓM 4: OTP FORMAT EDGE CASES
    // ====================================================================
    @Nested
    @DisplayName("Nhóm 4 — OTP Format Edge Cases")
    class OtpFormatTests {

        // ==========================================================
        // TEST 10: OTP = "000000" (toàn số 0) → body in đúng, không bị falsy/trim
        // Tại sao quan trọng: Một số implementation dùng if (otp) hay otp.isEmpty()
        //   để guard — "000000" qua một vài ngôn ngữ bị coi là falsy. Java không có
        //   vấn đề này, nhưng nếu code từng được port từ JS/Python thì cần verify.
        //   Ngoài ra cần đảm bảo "000000" không bị trim thành "0" hay bị drop leading zeros.
        // ==========================================================
        @Test
        @DisplayName("✅ OTP = \"000000\" (toàn số 0) → body chứa đúng \"000000\", không bị trim hay drop")
        void sendOtpEmail_allZeroOtp_bodyContainsExactOtp() {
            String otp = "000000";
            emailService.sendOtpEmail("user@gmail.com", otp);

            SimpleMailMessage msg = captureMessage();
            String body = msg.getText();

            assertNotNull(body);
            assertTrue(body.contains("000000"),
                    "OTP '000000' phải xuất hiện nguyên vẹn trong body, không bị trim hay convert sang số 0");
        }

        // ==========================================================
        // TEST 11: OTP dạng ngắn 1 ký tự → body vẫn chứa đúng
        // Tại sao quan trọng: Nếu OTP generator bị lỗi và trả về OTP quá ngắn,
        //   service vẫn gửi nhưng body phải chứa đúng string đó để dễ debug.
        //   Không nên có logic padding/trim ẩn nào làm thay đổi OTP trước khi gửi.
        // ==========================================================
        @Test
        @DisplayName("✅ OTP dạng ngắn 1 ký tự → body chứa đúng ký tự đó, không bị padding hay biến đổi")
        void sendOtpEmail_singleCharOtp_bodyContainsExactOtp() {
            String otp = "7";
            emailService.sendOtpEmail("user@gmail.com", otp);

            SimpleMailMessage msg = captureMessage();
            assertNotNull(msg.getText());
            assertTrue(msg.getText().contains("7"),
                    "OTP ngắn '7' phải xuất hiện nguyên vẹn trong body");
        }

        // ==========================================================
        // TEST 12: OTP chứa ký tự đặc biệt (nếu generator bị lỗi sinh ra)
        // Tại sao quan trọng: OTP chuẩn là 6 chữ số, nhưng nếu upstream bị lỗi
        //   và truyền xuống một string không chuẩn, service không được crash.
        //   Chỉ cần gửi đi đúng những gì nhận được — validation là việc của caller.
        // ==========================================================
        @Test
        @DisplayName("⚠️ OTP chứa ký tự đặc biệt (lỗi upstream) → body chứa đúng string, service không crash")
        void sendOtpEmail_specialCharOtp_doesNotCrashAndContainsOtp() {
            String malformedOtp = "ERR-01"; // Generator bị lỗi, trả về error code thay vì OTP

            assertDoesNotThrow(() -> emailService.sendOtpEmail("user@gmail.com", malformedOtp));

            SimpleMailMessage msg = captureMessage();
            assertTrue(Objects.requireNonNull(msg.getText()).contains(malformedOtp),
                    "Body phải chứa đúng string OTP nhận được, dù nó không đúng format chuẩn");
        }
    }
}