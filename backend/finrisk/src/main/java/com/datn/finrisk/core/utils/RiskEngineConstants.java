package com.datn.finrisk.core.utils;

import java.util.Map;

public final class RiskEngineConstants {

    private RiskEngineConstants() {}

    public static final Map<String, Integer> CATEGORY_CAPS = Map.of(
        "DEVICE",      30,
        "FINANCIAL",   40,
        "BIOMETRIC",   55,
        "VELOCITY",    30,
        "CONTEXTUAL",  20,
        "COMPOSITE",   40
    );

    public static final int AI_MAX_CONTRIBUTION = 35;
}
