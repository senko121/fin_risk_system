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

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gọi Python /api/ai/enroll-face-batch để extract multi-angle InsightFace embeddings.
 * Trả về JSON string "[[...],[...]]" để lưu vào face_embeddings.
 */
@Slf4j
@Service
public class FaceEnrollService {

    @Value("${ai.service.enroll-batch-url:http://localhost:5000/api/ai/enroll-face-batch}")
    private String enrollBatchUrl;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Gọi Python /api/ai/enroll-face-batch với nhiều góc mặt.
     * Trả về JSON string của list embeddings "[[...],[...],...]" để lưu vào face_embeddings.
     *
     * @param userId        ID user để log
     * @param imagesBase64  Danh sách ảnh base64 (front, left, right, up, down)
     * @return              JSON string "[[...],[...]]" hoặc null nếu thất bại
     */
    public String enrollFaceBatch(Long userId, List<String> imagesBase64) {
        long startMs = System.currentTimeMillis();
        log.info("[ENROLL-BATCH][START] userId={} images={}", userId, imagesBase64.size());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("images_base64", imagesBase64);
            body.put("user_id", String.valueOf(userId));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            JsonNode response = restTemplate.postForObject(enrollBatchUrl, entity, JsonNode.class);

            long elapsed = System.currentTimeMillis() - startMs;

            if (response == null) {
                log.warn("[ENROLL-BATCH][FAIL] userId={} elapsed={}ms — null response", userId, elapsed);
                return null;
            }

            boolean success = response.path("success").asBoolean(false);
            if (!success) {
                log.warn("[ENROLL-BATCH][FAIL] userId={} elapsed={}ms errors={}",
                        userId, elapsed, response.path("errors"));
                return null;
            }

            JsonNode embeddingsNode = response.path("embeddings");
            if (embeddingsNode.isMissingNode() || !embeddingsNode.isArray()
                    || embeddingsNode.isEmpty()) {
                log.warn("[ENROLL-BATCH][FAIL] userId={} elapsed={}ms — no embeddings returned",
                        userId, elapsed);
                return null;
            }

            String embeddingsJson = objectMapper.writeValueAsString(embeddingsNode);
            int count = response.path("embeddings_count").asInt(embeddingsNode.size());
            log.info("[ENROLL-BATCH][DONE] userId={} elapsed={}ms accepted={}/{}",
                    userId, elapsed, count, imagesBase64.size());

            return embeddingsJson;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            log.error("[ENROLL-BATCH][ERROR] userId={} elapsed={}ms error={}",
                    userId, elapsed, e.getMessage());
            return null;
        }
    }

    /**
     * Parse multi-angle embeddings JSON string từ DB → List<List<Double>>.
     *
     * @param embeddingsJson  "[[0.02, ...], [0.05, ...], ...]"
     * @return                List<List<Double>> hoặc null nếu parse lỗi
     */
    public List<List<Double>> parseEmbeddings(String embeddingsJson) {
        try {
            return objectMapper.readValue(
                    embeddingsJson, new TypeReference<List<List<Double>>>() {});
        } catch (Exception e) {
            log.error("[ENROLL][PARSE-EMBEDDINGS-ERROR] {}", e.getMessage());
            return null;
        }
    }
}