package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.FaceRegisterBatchRequest;
import com.datn.finrisk.application.dtos.IContactLastTransaction;
import com.datn.finrisk.application.dtos.PinSetupRequest;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.UserContactRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.repository.UserSecurityRepository;
import com.datn.finrisk.core.services.FaceEnrollService;
import com.datn.finrisk.core.services.PinService;
import com.datn.finrisk.core.services.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController - Full Test Suite")
class UserControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private UserContactRepository contactRepo;
    @Mock private AccountRepository accountRepository;
    @Mock private PinService pinService;
    @Mock private UserSecurityRepository userSecurityRepository;
    @Mock private FaceEnrollService faceEnrollService;

    @InjectMocks
    private UserController userController;

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────
    private User buildUser(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setFullName("Full " + username);
        return u;
    }

    private Account buildAccount(String accountNumber, User user) {
        Account a = new Account();
        a.setAccountNumber(accountNumber);
        a.setUser(user);
        return a;
    }

    // ══════════════════════════════════════════════════════════════
    // API 1: POST /api/users/register-face-batch
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /register-face-batch")
    class RegisterFaceTests {

        @Test
        @DisplayName("200 OK: đăng ký khuôn mặt đa góc thành công")
        void shouldReturn200WhenFaceRegisteredSuccessfully() {
            User user = buildUser(1L, "alice");
            FaceRegisterBatchRequest req = new FaceRegisterBatchRequest();
            req.setUserId(1L);
            req.setImagesBase64(List.of("img_front", "img_left", "img_right"));

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(faceEnrollService.enrollFaceBatch(any(), any())).thenReturn("[[0.1,0.2,0.3]]");
            when(userRepository.save(any())).thenReturn(user);

            ResponseEntity<?> response = userController.registerFaceBatch(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("status")).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("faceEmbeddings được lưu đúng sau khi enroll")
        void shouldSaveFaceEmbeddingsToUser() {
            User user = buildUser(1L, "alice");
            FaceRegisterBatchRequest req = new FaceRegisterBatchRequest();
            req.setUserId(1L);
            req.setImagesBase64(List.of("img_front", "img_left"));

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(faceEnrollService.enrollFaceBatch(any(), any())).thenReturn("[[0.1,0.2]]");
            when(userRepository.save(any())).thenReturn(user);

            userController.registerFaceBatch(req);

            verify(userRepository).save(user);
            assertThat(user.getFaceEmbeddings()).isEqualTo("[[0.1,0.2]]");
        }

        @Test
        @DisplayName("400 Bad Request: imagesBase64 là null")
        void shouldReturn400WhenImagesListIsNull() {
            FaceRegisterBatchRequest req = new FaceRegisterBatchRequest();
            req.setUserId(1L);
            req.setImagesBase64(null);

            ResponseEntity<?> response = userController.registerFaceBatch(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("400 Bad Request: imagesBase64 là danh sách rỗng")
        void shouldReturn400WhenImagesListIsEmpty() {
            FaceRegisterBatchRequest req = new FaceRegisterBatchRequest();
            req.setUserId(1L);
            req.setImagesBase64(List.of());

            ResponseEntity<?> response = userController.registerFaceBatch(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("400 Bad Request: user không tồn tại")
        void shouldReturn400WhenUserNotFound() {
            FaceRegisterBatchRequest req = new FaceRegisterBatchRequest();
            req.setUserId(999L);
            req.setImagesBase64(List.of("img1", "img2"));

            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            ResponseEntity<?> response = userController.registerFaceBatch(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().toString()).contains("Lỗi:");
        }

        @Test
        @DisplayName("400 Bad Request: AI service ném exception → trả lỗi")
        void shouldReturn400WhenEnrollFails() {
            User user = buildUser(1L, "alice");
            FaceRegisterBatchRequest req = new FaceRegisterBatchRequest();
            req.setUserId(1L);
            req.setImagesBase64(List.of("img1", "img2"));

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(faceEnrollService.enrollFaceBatch(any(), any()))
                    .thenThrow(new RuntimeException("AI service unreachable"));

            ResponseEntity<?> response = userController.registerFaceBatch(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().toString()).contains("Lỗi:");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // API 3: POST /api/users/{id}/change-password
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /{id}/change-password")
    class ChangePasswordTests {

        @Test
        @DisplayName("200 OK: đổi mật khẩu thành công")
        void shouldReturn200WhenPasswordChangedSuccessfully() throws Exception {
            Map<String, String> body = Map.of("oldPassword", "old123", "newPassword", "new456");
            doNothing().when(userService).changePassword(1L, "old123", "new456");

            ResponseEntity<?> response = userController.changePassword(1L, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("Cập nhật mật khẩu thành công!");
        }

        @Test
        @DisplayName("userService.changePassword() được gọi với đúng id, oldPass, newPass")
        void shouldDelegateToUserServiceWithCorrectArgs() throws Exception {
            Map<String, String> body = Map.of("oldPassword", "oldP@ss", "newPassword", "newP@ss");
            doNothing().when(userService).changePassword(5L, "oldP@ss", "newP@ss");

            userController.changePassword(5L, body);

            verify(userService).changePassword(5L, "oldP@ss", "newP@ss");
        }

        @Test
        @DisplayName("400 Bad Request: service ném exception mật khẩu cũ sai")
        void shouldReturn400WhenOldPasswordWrong() throws Exception {
            Map<String, String> body = Map.of("oldPassword", "wrong", "newPassword", "new");
            doThrow(new Exception("Mật khẩu hiện tại không chính xác!"))
                    .when(userService).changePassword(1L, "wrong", "new");

            ResponseEntity<?> response = userController.changePassword(1L, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isEqualTo("Mật khẩu hiện tại không chính xác!");
        }

        @Test
        @DisplayName("400 Bad Request: service ném exception mật khẩu mới trùng cũ")
        void shouldReturn400WhenNewPasswordSameAsOld() throws Exception {
            Map<String, String> body = Map.of("oldPassword", "same", "newPassword", "same");
            doThrow(new Exception("Mật khẩu mới không được trùng với mật khẩu cũ!"))
                    .when(userService).changePassword(1L, "same", "same");

            ResponseEntity<?> response = userController.changePassword(1L, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isEqualTo("Mật khẩu mới không được trùng với mật khẩu cũ!");
        }

        @Test
        @DisplayName("400 Bad Request: service ném exception user không tồn tại")
        void shouldReturn400WhenUserNotFound() throws Exception {
            Map<String, String> body = Map.of("oldPassword", "old", "newPassword", "new");
            doThrow(new Exception("Không tìm thấy người dùng!"))
                    .when(userService).changePassword(999L, "old", "new");

            ResponseEntity<?> response = userController.changePassword(999L, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isEqualTo("Không tìm thấy người dùng!");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // API: GET /api/users/{userId}/contacts
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /{userId}/contacts")
    class GetContactsTests {

        @Test
        @DisplayName("200 OK: trả về danh sách contacts từ repo")
        void shouldReturn200WithContactList() {

            List<IContactLastTransaction> mockContacts = List.of(
                mock(IContactLastTransaction.class),
                mock(IContactLastTransaction.class)
            );

            when(contactRepo.findContactsWithLastTransaction(1L))
                    .thenReturn(mockContacts);

            ResponseEntity<?> response = userController.getContacts(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(mockContacts);
        }

        @Test
        @DisplayName("200 OK: trả về danh sách rỗng khi không có contacts")
        void shouldReturn200WithEmptyListWhenNoContacts() {
            when(contactRepo.findContactsWithLastTransaction(2L)).thenReturn(List.of());

            ResponseEntity<?> response = userController.getContacts(2L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat((List<?>) response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("contactRepo.findContactsWithLastTransaction() được gọi với đúng userId")
        void shouldCallContactRepoWithCorrectUserId() {
            when(contactRepo.findContactsWithLastTransaction(7L)).thenReturn(List.of());

            userController.getContacts(7L);

            verify(contactRepo).findContactsWithLastTransaction(7L);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // API 4: GET /api/users/{userId}/generate-qr
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /{userId}/generate-qr")
    class GenerateQRCodeTests {

        @Test
        @DisplayName("200 OK: trả về qrCodeBase64 và content đúng format")
        void shouldReturn200WithQRCodeData() {
            User user = buildUser(1L, "alice");
            Account account = buildAccount("9999001234", user);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(accountRepository.findByUser(user)).thenReturn(Optional.of(account));

            ResponseEntity<?> response = userController.generateMyQRCode(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("qrCodeBase64");
            assertThat(body).containsKey("content");
        }

        @Test
        @DisplayName("QR content đúng format: 'FINRISK|accountNumber|fullName'")
        void shouldGenerateCorrectQRContent() {
            User user = buildUser(1L, "alice");
            user.setFullName("Nguyen Van A");
            Account account = buildAccount("123456789", user);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(accountRepository.findByUser(user)).thenReturn(Optional.of(account));

            ResponseEntity<?> response = userController.generateMyQRCode(1L);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("content")).isEqualTo("FINRISK|123456789|Nguyen Van A");
        }

        @Test
        @DisplayName("qrCodeBase64 phải có prefix 'data:image/png;base64,'")
        void shouldReturnBase64WithPngPrefix() {
            User user = buildUser(1L, "alice");
            Account account = buildAccount("111222333", user);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(accountRepository.findByUser(user)).thenReturn(Optional.of(account));

            ResponseEntity<?> response = userController.generateMyQRCode(1L);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("qrCodeBase64").toString())
                    .startsWith("data:image/png;base64,");
        }

        @Test
        @DisplayName("400 Bad Request: user không tồn tại")
        void shouldReturn400WhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            ResponseEntity<?> response = userController.generateMyQRCode(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("error");
        }

        @Test
        @DisplayName("400 Bad Request: user chưa có tài khoản ngân hàng")
        void shouldReturn400WhenNoAccountFound() {
            User user = buildUser(1L, "alice");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(accountRepository.findByUser(user)).thenReturn(Optional.empty());

            ResponseEntity<?> response = userController.generateMyQRCode(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("error").toString()).contains("Lỗi tạo mã QR");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // API 5: POST /api/users/security/pin
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /security/pin")
    class SetupOrChangePinTests {

        @Test
        @DisplayName("200 OK: cài PIN thành công, trả về status=SUCCESS")
        void shouldReturn200WhenPinSetupSuccessful() throws Exception {
            PinSetupRequest req = new PinSetupRequest();
            req.setUserId(1L);
            req.setOldPin(null);
            req.setNewPin("123456");

            when(pinService.setupOrChangePin(1L, null, "123456"))
                    .thenReturn("Cài đặt PIN thành công!");

            ResponseEntity<?> response = userController.setupOrChangePin(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("status")).isEqualTo("SUCCESS");
            assertThat(body.get("message")).isEqualTo("Cài đặt PIN thành công!");
        }

        @Test
        @DisplayName("200 OK: đổi PIN thành công")
        void shouldReturn200WhenPinChangedSuccessfully() throws Exception {
            PinSetupRequest req = new PinSetupRequest();
            req.setUserId(1L);
            req.setOldPin("111111");
            req.setNewPin("222222");

            when(pinService.setupOrChangePin(1L, "111111", "222222"))
                    .thenReturn("Đổi PIN thành công!");

            ResponseEntity<?> response = userController.setupOrChangePin(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("pinService.setupOrChangePin() được gọi đúng tham số")
        void shouldDelegateToPinServiceWithCorrectArgs() throws Exception {
            PinSetupRequest req = new PinSetupRequest();
            req.setUserId(3L);
            req.setOldPin("000000");
            req.setNewPin("999999");

            when(pinService.setupOrChangePin(3L, "000000", "999999")).thenReturn("OK");

            userController.setupOrChangePin(req);

            verify(pinService).setupOrChangePin(3L, "000000", "999999");
        }

        @Test
        @DisplayName("400 Bad Request: pinService ném exception, trả về status=FAILED")
        void shouldReturn400WhenPinServiceThrows() {
            PinSetupRequest req = new PinSetupRequest();
            req.setUserId(1L);
            req.setOldPin("wrong");
            req.setNewPin("999999");

            when(pinService.setupOrChangePin(1L, "wrong", "999999"))
                    .thenThrow(new RuntimeException("PIN cũ không chính xác!"));

            ResponseEntity<?> response = userController.setupOrChangePin(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();

            assertThat(body.get("status")).isEqualTo("FAILED");
            assertThat(body.get("message")).isEqualTo("PIN cũ không chính xác!");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // API: GET /api/users/{userId}/security-status
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /{userId}/security-status")
    class GetSecurityStatusTests {

        @Test
        @DisplayName("200 OK: trả về isPinSetup và isFaceSetup đúng khi cả 2 đã setup")
        void shouldReturnTrueForBothFlagsWhenFullySetup() {
            User user = buildUser(1L, "alice");
            user.setFaceEmbeddings("[[0.1,0.2,0.3]]");

            UserSecurity security = new UserSecurity();
            security.setIsPinSetup(true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));

            ResponseEntity<?> response = userController.getSecurityStatus(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("isPinSetup")).isEqualTo(true);
            assertThat(body.get("isFaceSetup")).isEqualTo(true);
        }

        @Test
        @DisplayName("isFaceSetup = false khi chưa đăng ký face embeddings")
        void shouldReturnFalseForFaceSetupWhenFaceImageNull() {
            User user = buildUser(1L, "alice");
            // faceEmbeddings = null by default → hasFaceEmbeddings() = false

            UserSecurity security = new UserSecurity();
            security.setIsPinSetup(false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));

            ResponseEntity<?> response = userController.getSecurityStatus(1L);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("isFaceSetup")).isEqualTo(false);
        }

        @Test
        @DisplayName("isFaceSetup = false khi faceEmbeddings là chuỗi rỗng")
        void shouldReturnFalseForFaceSetupWhenFaceImageEmpty() {
            User user = buildUser(1L, "alice");
            user.setFaceEmbeddings(""); // blank → hasFaceEmbeddings() = false

            UserSecurity security = new UserSecurity();
            security.setIsPinSetup(true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));

            ResponseEntity<?> response = userController.getSecurityStatus(1L);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("isFaceSetup")).isEqualTo(false);
        }

        @Test
        @DisplayName("isPinSetup = false khi chưa cài PIN")
        void shouldReturnFalseForPinSetupWhenNotConfigured() {
            User user = buildUser(1L, "alice");
            user.setFaceEmbeddings("[[0.1]]");

            UserSecurity security = new UserSecurity();
            security.setIsPinSetup(false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));

            ResponseEntity<?> response = userController.getSecurityStatus(1L);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("isPinSetup")).isEqualTo(false);
        }

        @Test
        @DisplayName("400 Bad Request: user không tồn tại")
        void shouldReturn400WhenUserNotFound() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            ResponseEntity<?> response = userController.getSecurityStatus(404L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().toString()).contains("Không tìm thấy người dùng!");
        }

        @Test
        @DisplayName("400 Bad Request: hồ sơ bảo mật không tồn tại")
        void shouldReturn400WhenSecurityProfileNotFound() {
            User user = buildUser(1L, "alice");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.empty());

            ResponseEntity<?> response = userController.getSecurityStatus(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().toString()).contains("Không tìm thấy hồ sơ bảo mật!");
        }

        @Test
        @DisplayName("Response body phải chứa đúng 3 key: isPinSetup, isFaceSetup, faceMode")
        void shouldContainExactlyTwoKeys() {
            User user = buildUser(1L, "alice");
            user.setFaceEmbeddings("[[0.1]]");

            UserSecurity security = new UserSecurity();
            security.setIsPinSetup(true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userSecurityRepository.findByUserId(1L)).thenReturn(Optional.of(security));

            ResponseEntity<?> response = userController.getSecurityStatus(1L);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKeys("isPinSetup", "isFaceSetup", "faceMode");
            assertThat(body.get("faceMode")).isEqualTo("MULTI_ANGLE");
        }
    }
}