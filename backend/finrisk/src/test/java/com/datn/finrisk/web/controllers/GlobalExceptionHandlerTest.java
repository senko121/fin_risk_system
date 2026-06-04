package com.datn.finrisk.web.controllers;

import com.datn.finrisk.core.exceptions.BusinessLogicException;
import com.datn.finrisk.core.repository.BiometricSessionRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.security.JwtUtils;
import com.datn.finrisk.core.services.JwtBlocklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests GlobalExceptionHandler using a minimal stub controller
 * to trigger each exception branch.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.StubController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GlobalExceptionHandler — Unit Tests")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // Security beans required by JwtAuthFilter
    @MockBean private JwtUtils                   jwtUtils;
    @MockBean private JwtBlocklistService        jwtBlocklistService;
    @MockBean private UserRepository             userRepository;
    @MockBean private BiometricSessionRepository biometricSessionRepository;

    // ── Stub controller to trigger each exception ─────────────────────────────

    @RestController
    @RequestMapping("/test-errors")
    static class StubController {

        @GetMapping("/business")
        public String businessError() {
            throw new BusinessLogicException("ERR_TEST_CODE", "Something went wrong");
        }

        @GetMapping("/illegal-argument")
        public String illegalArgument() {
            throw new IllegalArgumentException("Bad argument value");
        }

        @GetMapping("/general")
        public String generalError() {
            throw new RuntimeException("Unexpected crash");
        }

        @PostMapping("/validation")
        public String validationError(@RequestBody @jakarta.validation.Valid ValidatedBody body) {
            return "ok";
        }

        record ValidatedBody(
                @jakarta.validation.constraints.NotBlank String name) {}
    }

    // ── BusinessLogicException → 400 ─────────────────────────────────────────

    @Nested
    @DisplayName("BusinessLogicException → 400 Bad Request")
    class BusinessLogicExceptionTests {

        @Test
        @DisplayName("returns 400 with errorCode from exception")
        void returns400WithErrorCode() throws Exception {
            mockMvc.perform(get("/test-errors/business"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR_TEST_CODE"));
        }

        @Test
        @DisplayName("response message matches exception message")
        void responseMessageMatchesException() throws Exception {
            mockMvc.perform(get("/test-errors/business"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Something went wrong"));
        }

        @Test
        @DisplayName("response status field is 400")
        void responseStatusIs400() throws Exception {
            mockMvc.perform(get("/test-errors/business"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("response includes timestamp field")
        void responseIncludesTimestamp() throws Exception {
            mockMvc.perform(get("/test-errors/business"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // ── IllegalArgumentException → 400 ───────────────────────────────────────

    @Nested
    @DisplayName("IllegalArgumentException → 400 Bad Request")
    class IllegalArgumentExceptionTests {

        @Test
        @DisplayName("returns 400")
        void returns400() throws Exception {
            mockMvc.perform(get("/test-errors/illegal-argument"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("errorCode is ERR_BAD_REQUEST")
        void errorCodeIsErrBadRequest() throws Exception {
            mockMvc.perform(get("/test-errors/illegal-argument"))
                    .andExpect(jsonPath("$.errorCode").value("ERR_BAD_REQUEST"));
        }

        @Test
        @DisplayName("message matches exception message")
        void messageMatchesExceptionMessage() throws Exception {
            mockMvc.perform(get("/test-errors/illegal-argument"))
                    .andExpect(jsonPath("$.message").value("Bad argument value"));
        }
    }

    // ── Generic Exception → 500 ───────────────────────────────────────────────

    @Nested
    @DisplayName("Unhandled Exception → 500 Internal Server Error")
    class GeneralExceptionTests {

        @Test
        @DisplayName("returns 500")
        void returns500() throws Exception {
            mockMvc.perform(get("/test-errors/general"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("errorCode is ERR_SYSTEM_UNKNOWN")
        void errorCodeIsErrSystemUnknown() throws Exception {
            mockMvc.perform(get("/test-errors/general"))
                    .andExpect(jsonPath("$.errorCode").value("ERR_SYSTEM_UNKNOWN"));
        }

        @Test
        @DisplayName("message is generic (does not leak internal details)")
        void messageIsGeneric() throws Exception {
            mockMvc.perform(get("/test-errors/general"))
                    .andExpect(jsonPath("$.message").value(
                            containsString("Hệ thống gặp sự cố")));
        }
    }

    // ── MethodArgumentNotValidException → 400 ────────────────────────────────

    @Nested
    @DisplayName("MethodArgumentNotValidException → 400 Validation Failed")
    class ValidationExceptionTests {

        @Test
        @DisplayName("returns 400 with ERR_VALIDATION_FAILED")
        void returns400WithValidationCode() throws Exception {
            String body = objectMapper.writeValueAsString(new java.util.HashMap<String, String>() {{
                put("name", "");  // blank → NotBlank violation
            }});

            mockMvc.perform(post("/test-errors/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("details map contains field-level validation errors")
        void detailsContainFieldErrors() throws Exception {
            String body = "{\"name\":\"\"}";

            mockMvc.perform(post("/test-errors/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.name").exists());
        }
    }
}
