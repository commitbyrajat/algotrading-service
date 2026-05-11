package com.algotrading.app.controller;

import com.algotrading.app.model.HistoricalDataRequest;
import com.algotrading.app.model.StrategyDecision;
import com.algotrading.app.service.TradingStrategyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API for strategy listing and on-demand evaluation.
 *
 * <h3>Endpoints</h3>
 * <pre>
 * GET /api/v1/strategies
 *   → List all registered strategy names.
 *
 * GET /api/v1/strategies/{name}/evaluate
 *        ?token=256265
 *        &amp;from=2024-01-01
 *        &amp;to=2024-06-30
 *        &amp;interval=day
 *   → Evaluate the named strategy on live Kite data.
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyController {

    private final TradingStrategyService tradingStrategyService;

    public StrategyController(TradingStrategyService tradingStrategyService) {
        this.tradingStrategyService = tradingStrategyService;
    }

    /**
     * List all registered strategy names.
     */
    @GetMapping
    public ResponseEntity<List<String>> listStrategies() {
        return ResponseEntity.ok(tradingStrategyService.listStrategies());
    }

    /**
     * Evaluate a specific strategy using live Kite historical data.
     *
     * @param name     strategy name, e.g. {@code SMA_CROSSOVER} or {@code RSI_MEAN_REVERSION}
     * @param token    Kite instrument token (string), e.g. {@code 256265} for NIFTY50
     * @param from     start date (ISO-8601), e.g. {@code 2024-01-01}
     * @param to       end date (ISO-8601), e.g. {@code 2024-06-30}
     * @param interval candle interval, e.g. {@code day}, {@code 15minute}
     */
    @GetMapping("/{name}/evaluate")
    public ResponseEntity<StrategyDecision> evaluate(
            @PathVariable String name,
            @RequestParam String token,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String interval) {

        HistoricalDataRequest request = new HistoricalDataRequest(token, from, to, interval);
        StrategyDecision decision = tradingStrategyService.evaluate(name, request);
        return ResponseEntity.ok(decision);
    }
}