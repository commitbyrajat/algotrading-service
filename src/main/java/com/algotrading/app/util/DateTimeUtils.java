package com.algotrading.app.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtils {
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    public static Instant parse(String timeStamp) {
        return OffsetDateTime
                .parse(timeStamp, dateTimeFormatter)
                .toInstant();
    };
}
