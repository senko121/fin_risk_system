package com.datn.finrisk.core.services.biometric;

import com.datn.finrisk.application.dtos.EmotionAIResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EmotionEvaluator {
 
    CompletableFuture<EmotionAIResponse> evaluateSequenceAsync(List<String> frames);
}