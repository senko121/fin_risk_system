package com.datn.finrisk.core.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FaceEnrollService — Unit Tests")
class FaceEnrollServiceTest {

    @Mock private RestTemplate restTemplate;
    @InjectMocks private FaceEnrollService faceEnrollService;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(faceEnrollService, "enrollBatchUrl",
                "http://localhost:5000/api/ai/enroll-face-batch");
    }

    // ── enrollFaceBatch ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("enrollFaceBatch()")
    class EnrollFaceBatch {

        @Test
        @DisplayName("successful response with embeddings → returns JSON string")
        void successResponse_returnsEmbeddingsJson() throws Exception {
            ObjectNode response = mapper.createObjectNode();
            response.put("success", true);
            response.put("embeddings_count", 1);
            // embeddings is an array of arrays
            response.set("embeddings", mapper.readTree("[[0.1, 0.2, 0.3]]"));

            when(restTemplate.postForObject(anyString(), any(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                    .thenReturn(response);

            List<String> images = Arrays.asList("base64img1", "base64img2");
            String result = faceEnrollService.enrollFaceBatch(1L, images);

            assertThat(result).isNotNull().contains("0.1");
        }

        @Test
        @DisplayName("success=false in response → returns null")
        void successFalse_returnsNull() {
            ObjectNode response = mapper.createObjectNode();
            response.put("success", false);

            when(restTemplate.postForObject(anyString(), any(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                    .thenReturn(response);

            String result = faceEnrollService.enrollFaceBatch(1L, List.of("img1"));
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("null response from AI service → returns null")
        void nullResponse_returnsNull() {
            when(restTemplate.postForObject(anyString(), any(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                    .thenReturn(null);

            String result = faceEnrollService.enrollFaceBatch(1L, List.of("img1"));
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("response with empty embeddings array → returns null")
        void emptyEmbeddings_returnsNull() throws Exception {
            ObjectNode response = mapper.createObjectNode();
            response.put("success", true);
            response.set("embeddings", mapper.readTree("[]"));

            when(restTemplate.postForObject(anyString(), any(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                    .thenReturn(response);

            String result = faceEnrollService.enrollFaceBatch(1L, List.of("img1"));
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("RestTemplate throws exception → returns null (no rethrow)")
        void restTemplateThrows_returnsNull() {
            when(restTemplate.postForObject(anyString(), any(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            String result = faceEnrollService.enrollFaceBatch(1L, List.of("img1"));
            assertThat(result).isNull();
        }
    }

    // ── parseEmbeddings ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("parseEmbeddings()")
    class ParseEmbeddings {

        @Test
        @DisplayName("valid JSON array of arrays → parsed correctly")
        void validJson_parsedCorrectly() {
            String json = "[[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]";
            List<List<Double>> result = faceEnrollService.parseEmbeddings(json);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsExactly(0.1, 0.2, 0.3);
            assertThat(result.get(1)).containsExactly(0.4, 0.5, 0.6);
        }

        @Test
        @DisplayName("single embedding → list with one entry")
        void singleEmbedding_parsedAsOneEntry() {
            String json = "[[1.0, 2.0]]";
            List<List<Double>> result = faceEnrollService.parseEmbeddings(json);
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly(1.0, 2.0);
        }

        @Test
        @DisplayName("null input → returns null (gracefully)")
        void nullInput_returnsNull() {
            assertThat(faceEnrollService.parseEmbeddings(null)).isNull();
        }

        @Test
        @DisplayName("invalid JSON → returns null (no exception)")
        void invalidJson_returnsNull() {
            assertThat(faceEnrollService.parseEmbeddings("not-json")).isNull();
        }

        @Test
        @DisplayName("empty JSON array → returns empty list")
        void emptyArray_returnsEmptyList() {
            List<List<Double>> result = faceEnrollService.parseEmbeddings("[]");
            assertThat(result).isEmpty();
        }
    }
}
