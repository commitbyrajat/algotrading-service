package com.algotrading.app.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class MarketHoursGuard {

    static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    static final LocalTime DEFAULT_MARKET_CLOSE_TIME = LocalTime.of(15, 30);

    private final Clock clock;
    private final LocalTime marketCloseTime;
    private final boolean enabled;

    public MarketHoursGuard(@Value("${trading.market-hours.close-time:15:30}") String marketCloseTime) {
        this(Clock.system(MARKET_ZONE), parseMarketCloseTime(marketCloseTime), true);
    }

    MarketHoursGuard(Clock clock) {
        this(clock, DEFAULT_MARKET_CLOSE_TIME, true);
    }

    MarketHoursGuard(Clock clock, LocalTime marketCloseTime) {
        this(clock, marketCloseTime, true);
    }

    private MarketHoursGuard(Clock clock, LocalTime marketCloseTime, boolean enabled) {
        this.clock = clock;
        this.marketCloseTime = marketCloseTime;
        this.enabled = enabled;
    }

    static MarketHoursGuard alwaysOpen() {
        return new MarketHoursGuard(Clock.system(MARKET_ZONE), DEFAULT_MARKET_CLOSE_TIME, false);
    }

    public void ensureMarketOpenForOrders() {
        if (!enabled) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(MARKET_ZONE);
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException(
                    "Cannot place order because market is closed on weekends. Current market time="
                            + now.toLocalDate() + " " + now.toLocalTime()
                            + " " + MARKET_ZONE
            );
        }

        if (!now.toLocalTime().isBefore(marketCloseTime)) {
            throw new IllegalArgumentException(
                    "Cannot place order because market is closed at or after " + marketCloseTime
                            + ". Current market time="
                            + now.toLocalDate() + " " + now.toLocalTime()
                            + " " + MARKET_ZONE
            );
        }
    }

    private static LocalTime parseMarketCloseTime(String marketCloseTime) {
        if (marketCloseTime == null || marketCloseTime.isBlank()) {
            return DEFAULT_MARKET_CLOSE_TIME;
        }
        return LocalTime.parse(marketCloseTime.trim());
    }
}
