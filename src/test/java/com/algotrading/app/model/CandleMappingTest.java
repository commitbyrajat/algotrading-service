package com.algotrading.app.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Candle} domain record – construction,
 * field storage, and compact-constructor validation.
 */
class CandleMappingTest {

    private static final Instant FIXED_TS = Instant.parse("2024-06-01T09:15:00Z");

    @Test
    void candle_storesAllFieldsCorrectly() {
        Candle c = new Candle(FIXED_TS, 100.0, 105.5, 98.0, 102.5, 75_000L);

        assertThat(c.timestamp()).isEqualTo(FIXED_TS);
        assertThat(c.open()).isEqualTo(100.0);
        assertThat(c.high()).isEqualTo(105.5);
        assertThat(c.low()).isEqualTo(98.0);
        assertThat(c.close()).isEqualTo(102.5);
        assertThat(c.volume()).isEqualTo(75_000L);
    }

    @Test
    void candle_throwsNPE_whenTimestampIsNull() {
        assertThatThrownBy(() -> new Candle(null, 100, 105, 98, 102, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void candle_throwsException_whenHighLessThanLow() {
        assertThatThrownBy(() -> new Candle(FIXED_TS, 100, 90.0, 95.0, 92.0, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("high")
                .hasMessageContaining("low");
    }

    @Test
    void candle_throwsException_whenVolumeIsNegative() {
        assertThatThrownBy(() -> new Candle(FIXED_TS, 100, 105, 98, 102, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("volume");
    }

    @Test
    void candle_throwsException_whenOpenIsZero() {
        assertThatThrownBy(() -> new Candle(FIXED_TS, 0.0, 105, 98, 102, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OHLC");
    }

    @Test
    void candle_zeroVolumeIsAllowed() {
        // Zero volume is valid (e.g. pre-market bars with no trades)
        Candle c = new Candle(FIXED_TS, 100, 105, 98, 102, 0L);
        assertThat(c.volume()).isZero();
    }
}