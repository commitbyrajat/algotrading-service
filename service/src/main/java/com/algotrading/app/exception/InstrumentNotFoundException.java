package com.algotrading.app.exception;

import java.util.Collection;
import java.util.Optional;
import java.util.StringJoiner;

public class InstrumentNotFoundException extends RuntimeException {

    public InstrumentNotFoundException(Collection<String> identifiers, Optional<String> exchange) {
        super(message(identifiers, exchange));
    }

    private static String message(Collection<String> identifiers, Optional<String> exchange) {
        StringJoiner joiner = new StringJoiner(", ");
        identifiers.forEach(joiner::add);
        return "Instrument not found for identifiers=[" + joiner + "]"
                + exchange.map(value -> " exchange=" + value).orElse("");
    }
}
