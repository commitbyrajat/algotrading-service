package com.algotrading.app.order;

import com.algotrading.app.config.MarketHoursProperties;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ZoneId marketZone;

    @Autowired
    public MarketHoursGuard(MarketHoursProperties properties) {
        this(Clock.system(properties.zone()), properties.zone(), properties.closeTime(), properties.enabled());
    }

    MarketHoursGuard(Clock clock) {
        this(clock, MARKET_ZONE, DEFAULT_MARKET_CLOSE_TIME, true);
    }

    MarketHoursGuard(Clock clock, LocalTime marketCloseTime) {
        this(clock, MARKET_ZONE, marketCloseTime, true);
    }

    private MarketHoursGuard(Clock clock, ZoneId marketZone, LocalTime marketCloseTime, boolean enabled) {
        this.clock = clock;
        this.marketZone = marketZone;
        this.marketCloseTime = marketCloseTime;
        this.enabled = enabled;
    }

    static MarketHoursGuard alwaysOpen() {
        return new MarketHoursGuard(Clock.system(MARKET_ZONE), MARKET_ZONE, DEFAULT_MARKET_CLOSE_TIME, false);
    }

    public void ensureMarketOpenForOrders() {
        if (!enabled) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(marketZone);
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException(
                    "Cannot place order because market is closed on weekends. Current market time="
                            + now.toLocalDate() + " " + now.toLocalTime()
                            + " " + marketZone
            );
        }

        if (!now.toLocalTime().isBefore(marketCloseTime)) {
            throw new IllegalArgumentException(
                    "Cannot place order because market is closed at or after " + marketCloseTime
                            + ". Current market time="
                            + now.toLocalDate() + " " + now.toLocalTime()
                            + " " + marketZone
            );
        }
    }
}
