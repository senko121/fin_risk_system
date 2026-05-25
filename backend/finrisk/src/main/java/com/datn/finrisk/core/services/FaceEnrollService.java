package com.datn.finrisk.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gọi Python /api/ai/enroll-face để extract ArcFace embedding từ ảnh webcam.
 * Trả về embedding dưới dạng JSON string "[0.023, -0.182, ...]" để lưu vào DB.
 */
@Slf4j
@Service
public class FaceEnrollService {

    @Value("${ai.service.enroll-url:http://localhost:5000/api/ai/enroll-face}")
    private String enrollUrl;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Gọi Python enroll endpoint.
     *
     * @param userId        ID user để log
     * @param imageBase64   Ảnh webcam base64 (có hoặc không có prefix data:...)
     * @return              JSON string của embedding vector, hoặc null nếu thất bại
     */
    public String enrollFace(Long userId, String imageBase64) {
        long startMs = System.currentTimeMillis();
        log.info("[ENROLL][START] userId={}", userId);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("image_base64", imageBase64);
            body.put("user_id", String.valueOf(userId));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            JsonNode response = restTemplate.postForObject(enrollUrl, entity, JsonNode.class);

            long elapsed = System.currentTimeMillis() - startMs;

            if (response == null) {
                log.warn("[ENROLL][FAIL] userId={} elapsed={}ms — null response", userId, elapsed);
                return null;
            }

            boolean success = response.path("success").asBoolean(false);
            if (!success) {
                String error = response.path("error").asText("UNKNOWN_ERROR");
                log.warn("[ENROLL][FAIL] userId={} elapsed={}ms error={}", userId, elapsed, error);
                return null;
            }

            JsonNode embeddingNode = response.path("embedding");
            if (embeddingNode.isMissingNode() || !embeddingNode.isArray()) {
                log.warn("[ENROLL][FAIL] userId={} elapsed={}ms — missing embedding in response", userId, elapsed);
                return null;
            }

            // Serialize embedding array → JSON string để lưu vào DB column LONGTEXT
            String embeddingJson = objectMapper.writeValueAsString(embeddingNode);

            double qualityScore = response.path("quality_score").asDouble(0.0);
            log.info("[ENROLL][DONE] userId={} elapsed={}ms quality={} embedding_dim={}",
                    userId, elapsed, String.format("%.4f", qualityScore), embeddingNode.size());

            return embeddingJson;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            log.error("[ENROLL][ERROR] userId={} elapsed={}ms error={}", userId, elapsed, e.getMessage());
            return null;
        }
    }

    /**
     * Parse embedding JSON string từ DB → List<Double>.
     * Dùng trong verify flow để truyền sang Python.
     *
     * @param embeddingJson  JSON string "[0.023, -0.182, ...]"
     * @return               List<Double> hoặc null nếu parse lỗi
     */
    public List<Double> parseEmbedding(String embeddingJson) {
        try {
            return objectMapper.readValue(
                    embeddingJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
        } catch (Exception e) {
            log.error("[ENROLL][PARSE-ERROR] Failed to parse embedding JSON: {}", e.getMessage());
            return null;
        }
    }
}