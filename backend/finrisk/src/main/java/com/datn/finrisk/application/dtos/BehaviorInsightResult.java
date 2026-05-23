package com.datn.finrisk.application.dtos;

import com.datn.finrisk.core.entities.TransactionAiInsight;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class BehaviorInsightResult {
    private final int totalScore;
    private final List<TransactionAiInsight> insights;
}