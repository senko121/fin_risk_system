package com.datn.finrisk.core.services.biometric;

import com.datn.finrisk.application.dtos.EmotionAIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Decorator around BatchEmotionEvaluator that allows runtime emotion override.
 * When app.emotion.override.enabled=true, skips the AI call and returns the
 * configured emotion directly. Useful for dev/QA testing coercion flows.
 */
@Primary
@Service("batchEmotionEvaluator")
public class OverridableEmotionEvaluator implements EmotionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(OverridableEmotionEvaluator.class);

    private final EmotionEvaluator delegate;
    private final EmotionOverrideProperties overrideProps;

    public OverridableEmotionEvaluator(
            @Qualifier("realBatchEmotionEvaluator") EmotionEvaluator delegate,
            EmotionOverrideProperties overrideProps) {
        this.delegate = delegate;
        this.overrideProps = overrideProps;
    }

    @Override
    public CompletableFuture<EmotionAIResponse> evaluateSequenceAsync(List<String> frames) {
        if (overrideProps.isEnabled()) {
            String forcedEmotion = overrideProps.getEmotion().toUpperCase();
            double forcedConf    = overrideProps.getConfidence();
            log.warn("[EMOTION-OVERRIDE] ACTIVE — skipping AI call, returning emotion={} confidence={}",
                forcedEmotion, forcedConf);

            EmotionAIResponse fake = new EmotionAIResponse();
            fake.setEmotion(forcedEmotion);
            fake.setConfidence(forcedConf);
            fake.setProcessTimeMs(0.0);
            return CompletableFuture.completedFuture(fake);
        }

        return delegate.evaluateSequenceAsync(frames);
    }
}
