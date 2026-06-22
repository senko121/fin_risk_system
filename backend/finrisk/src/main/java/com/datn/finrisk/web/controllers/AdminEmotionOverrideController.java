package com.datn.finrisk.web.controllers;

import com.datn.finrisk.core.services.biometric.EmotionOverrideProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/emotion-override")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmotionOverrideController {

    private static final Logger log = LoggerFactory.getLogger(AdminEmotionOverrideController.class);

    private static final java.util.Set<String> VALID_EMOTIONS = java.util.Set.of(
        "FEAR", "STRESS", "ANGRY", "HAPPY", "NEUTRAL",
        "SURPRISE", "SAD", "DISGUST", "CONTEMPT", "CALM"
    );

    private final EmotionOverrideProperties props;

    public AdminEmotionOverrideController(EmotionOverrideProperties props) {
        this.props = props;
    }

    @PostMapping("/enable")
    public ResponseEntity<?> enable(
            @RequestParam(defaultValue = "FEAR") String emotion,
            @RequestParam(defaultValue = "0.90") double confidence) {

        String upper = emotion.toUpperCase();
        if (!VALID_EMOTIONS.contains(upper)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Unknown emotion: " + emotion,
                "valid", VALID_EMOTIONS
            ));
        }
        if (confidence < 0.0 || confidence > 1.0) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "confidence must be between 0.0 and 1.0"
            ));
        }

        props.setEnabled(true);
        props.setEmotion(upper);
        props.setConfidence(confidence);

        log.warn("[EMOTION-OVERRIDE] Enabled by admin — emotion={} confidence={}", upper, confidence);
        return ResponseEntity.ok(Map.of(
            "status", "OVERRIDE_ENABLED",
            "emotion", upper,
            "confidence", confidence
        ));
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disable() {
        props.setEnabled(false);
        log.info("[EMOTION-OVERRIDE] Disabled by admin");
        return ResponseEntity.ok(Map.of("status", "OVERRIDE_DISABLED"));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of(
            "enabled",    props.isEnabled(),
            "emotion",    props.getEmotion(),
            "confidence", props.getConfidence()
        ));
    }
}
