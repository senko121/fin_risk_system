package com.datn.finrisk.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RiskEngineConstants — Unit Tests")
class RiskEngineConstantsTest {

    @Test
    @DisplayName("CATEGORY_CAPS contains all 6 required categories")
    void categoryCaps_containsAllCategories() {
        assertThat(RiskEngineConstants.CATEGORY_CAPS)
                .containsOnlyKeys("DEVICE", "FINANCIAL", "BIOMETRIC", "VELOCITY", "CONTEXTUAL", "COMPOSITE");
    }

    @Test
    @DisplayName("DEVICE cap is 30")
    void deviceCap_is30() {
        assertThat(RiskEngineConstants.CATEGORY_CAPS.get("DEVICE")).isEqualTo(30);
    }

    @Test
    @DisplayName("FINANCIAL cap is 40")
    void financialCap_is40() {
        assertThat(RiskEngineConstants.CATEGORY_CAPS.get("FINANCIAL")).isEqualTo(40);
    }

    @Test
    @DisplayName("BIOMETRIC cap is 55 (highest category)")
    void biometricCap_is55() {
        assertThat(RiskEngineConstants.CATEGORY_CAPS.get("BIOMETRIC")).isEqualTo(55);
    }

    @Test
    @DisplayName("VELOCITY cap is 30")
    void velocityCap_is30() {
        assertThat(RiskEngineConstants.CATEGORY_CAPS.get("VELOCITY")).isEqualTo(30);
    }

    @Test
    @DisplayName("CONTEXTUAL cap is 20 (lowest category)")
    void contextualCap_is20() {
        assertThat(RiskEngineConstants.CATEGORY_CAPS.get("CONTEXTUAL")).isEqualTo(20);
    }

    @Test
    @DisplayName("COMPOSITE cap is 40")
    void compositeCap_is40() {
        assertThat(RiskEngineConstants.CATEGORY_CAPS.get("COMPOSITE")).isEqualTo(40);
    }

    @Test
    @DisplayName("AI_MAX_CONTRIBUTION is 35")
    void aiMaxContribution_is35() {
        assertThat(RiskEngineConstants.AI_MAX_CONTRIBUTION).isEqualTo(35);
    }

    @Test
    @DisplayName("map is immutable — put throws UnsupportedOperationException")
    void categoryCapMap_isImmutable() {
        assertThat(RiskEngineConstants.CATEGORY_CAPS)
                .satisfies(map -> {
                    try {
                        map.put("HACK", 999);
                        // If we get here the map was mutable — fail loudly
                        assertThat(true).as("Expected UnsupportedOperationException").isFalse();
                    } catch (UnsupportedOperationException expected) {
                        // correct
                    }
                });
    }
}
