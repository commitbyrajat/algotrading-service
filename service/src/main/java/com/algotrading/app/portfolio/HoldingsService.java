package com.algotrading.app.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service for portfolio holdings.
 */
@Service
public class HoldingsService {

    private static final Logger log = LoggerFactory.getLogger(HoldingsService.class);

    private final HoldingsPort holdingsPort;

    public HoldingsService(HoldingsPort holdingsPort) {
        this.holdingsPort = holdingsPort;
    }

    public List<HoldingResponse> listHoldings() {
        log.debug("HoldingsService.listHoldings");
        return holdingsPort.listHoldings();
    }
}
