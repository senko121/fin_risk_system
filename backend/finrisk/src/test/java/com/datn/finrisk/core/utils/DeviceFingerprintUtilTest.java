package com.datn.finrisk.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeviceFingerprintUtil — Unit Tests")
class DeviceFingerprintUtilTest {

    // ── fromUserAgent ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromUserAgent()")
    class FromUserAgent {

        @Test
        @DisplayName("null UA → Unknown|Unknown")
        void nullUa() {
            assertThat(DeviceFingerprintUtil.fromUserAgent(null)).isEqualTo("Unknown|Unknown");
        }

        @Test
        @DisplayName("empty string → Unknown|Unknown")
        void emptyUa() {
            assertThat(DeviceFingerprintUtil.fromUserAgent("")).isEqualTo("Unknown|Unknown");
        }

        @Test
        @DisplayName("whitespace-only → Unknown|Unknown")
        void blankUa() {
            assertThat(DeviceFingerprintUtil.fromUserAgent("   ")).isEqualTo("Unknown|Unknown");
        }

        @Test
        @DisplayName("Windows + Chrome → Windows|Chrome")
        void windowsChrome() {
            String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
            assertThat(DeviceFingerprintUtil.fromUserAgent(ua)).isEqualTo("Windows|Chrome");
        }

        @Test
        @DisplayName("Windows + Edge → Windows|Edge  (Edge checked before Chrome)")
        void windowsEdge() {
            String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0";
            assertThat(DeviceFingerprintUtil.fromUserAgent(ua)).isEqualTo("Windows|Edge");
        }

        @Test
        @DisplayName("macOS + Safari → macOS|Safari")
        void macSafari() {
            String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) "
                    + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15";
            assertThat(DeviceFingerprintUtil.fromUserAgent(ua)).isEqualTo("macOS|Safari");
        }

        @Test
        @DisplayName("Android + Firefox → Android|Firefox")
        void androidFirefox() {
            String ua = "Mozilla/5.0 (Android 14; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0";
            assertThat(DeviceFingerprintUtil.fromUserAgent(ua)).isEqualTo("Android|Firefox");
        }

        @Test
        @DisplayName("iPhone Safari → iOS|Safari")
        void iphoneSafari() {
            String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
                    + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
            assertThat(DeviceFingerprintUtil.fromUserAgent(ua)).isEqualTo("iOS|Safari");
        }

        @Test
        @DisplayName("iPad → iOS OS family")
        void ipad_detectsIos() {
            String ua = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) "
                    + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
            assertThat(DeviceFingerprintUtil.fromUserAgent(ua)).startsWith("iOS|");
        }

        @Test
        @DisplayName("Linux + Chrome → Linux|Chrome")
        void linuxChrome() {
            String ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
            assertThat(DeviceFingerprintUtil.fromUserAgent(ua)).isEqualTo("Linux|Chrome");
        }

        @Test
        @DisplayName("Postman → Unknown|Postman")
        void postman() {
            assertThat(DeviceFingerprintUtil.fromUserAgent("PostmanRuntime/7.37.0"))
                    .isEqualTo("Unknown|Postman");
        }

        @Test
        @DisplayName("Unknown bot → Unknown|Unknown")
        void unknownBot() {
            assertThat(DeviceFingerprintUtil.fromUserAgent("SomeRandomBot/2.0"))
                    .isEqualTo("Unknown|Unknown");
        }

        @Test
        @DisplayName("very long unknown UA (500 chars) → Unknown|Unknown, no exception")
        void veryLongUnknownUa_noException() {
            String longUa = "A".repeat(500);
            assertThat(DeviceFingerprintUtil.fromUserAgent(longUa)).isEqualTo("Unknown|Unknown");
        }
    }

    // ── resolve ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolve()")
    class Resolve {

        @Test
        @DisplayName("frontend fingerprint present + short → used as-is")
        void frontendFp_short_usedAsIs() {
            assertThat(DeviceFingerprintUtil.resolve("any-ua", "fp-from-browser"))
                    .isEqualTo("fp-from-browser");
        }

        @Test
        @DisplayName("frontend fingerprint exactly 255 chars → unchanged")
        void frontendFp_exactly255_unchanged() {
            String fp = "X".repeat(255);
            assertThat(DeviceFingerprintUtil.resolve(null, fp)).isEqualTo(fp);
        }

        @Test
        @DisplayName("frontend fingerprint > 255 chars → truncated to 255")
        void frontendFp_over255_truncated() {
            String longFp = "Y".repeat(300);
            String result  = DeviceFingerprintUtil.resolve(null, longFp);
            assertThat(result).hasSize(255).isEqualTo(longFp.substring(0, 255));
        }

        @Test
        @DisplayName("null frontend fingerprint → falls back to UA parsing")
        void nullFp_fallsBackToUa() {
            String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0 Safari/537.36";
            assertThat(DeviceFingerprintUtil.resolve(ua, null)).isEqualTo("Windows|Chrome");
        }

        @Test
        @DisplayName("blank frontend fingerprint → falls back to UA parsing")
        void blankFp_fallsBackToUa() {
            String ua = "Mozilla/5.0 (Android 14; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0";
            assertThat(DeviceFingerprintUtil.resolve(ua, "  ")).isEqualTo("Android|Firefox");
        }

        @Test
        @DisplayName("both null → Unknown|Unknown")
        void bothNull() {
            assertThat(DeviceFingerprintUtil.resolve(null, null)).isEqualTo("Unknown|Unknown");
        }

        @Test
        @DisplayName("frontend fingerprint takes priority over UA")
        void fpPriorityOverUa() {
            // UA would resolve to Windows|Chrome, but fp should win
            String ua = "Mozilla/5.0 (Windows NT 10.0) Chrome/124.0.0.0";
            assertThat(DeviceFingerprintUtil.resolve(ua, "device-specific-id"))
                    .isEqualTo("device-specific-id");
        }
    }
}
