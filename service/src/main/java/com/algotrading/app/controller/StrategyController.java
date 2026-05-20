package com.algotrading.app.controller;

import com.algotrading.app.model.HistoricalDataRequest;
import com.algotrading.app.model.StrategyDecision;
import com.algotrading.app.service.TradingStrategyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Tag(
        name = "Strategies",
        description = "Strategy discovery and on-demand technical strategy evaluation using Kite historical candle data."
)
public class StrategyController {

    private static final Logger log = LoggerFactory.getLogger(StrategyController.class);

    private final TradingStrategyService tradingStrategyService;

    public StrategyController(TradingStrategyService tradingStrategyService) {
        this.tradingStrategyService = tradingStrategyService;
    }

    /**
     * List all registered strategy names.
     */
    @GetMapping
    @Operation(
            summary = "List registered strategies",
            description = "Returns the strategy names that can be passed to the evaluate endpoint."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registered strategy names returned",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(
                                    implementation = String.class,
                                    example = "SMA_CROSSOVER")))),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content)
    })
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
    @Operation(
            summary = "Evaluate a strategy",
            description = """
                    Evaluates the named strategy over Kite historical candles for the supplied instrument token,
                    date range, and interval. The response is a decision only; this endpoint never places orders.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Strategy evaluated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = StrategyDecision.class))),
            @ApiResponse(responseCode = "400", description = "Invalid token, date range, or interval",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "No active Kite session",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Strategy name is not registered",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "Kite market-data lookup failed",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content)
    })
    public ResponseEntity<StrategyDecision> evaluate(
            @Parameter(
                    description = "Registered strategy name returned by GET /api/v1/strategies.",
                    required = true,
                    example = "SMA_CROSSOVER"
            )
            @PathVariable String name,
            @Parameter(
                    description = "Kite instrument token to evaluate, for example 256265 for NIFTY 50.",
                    required = true,
                    example = "256265"
            )
            @RequestParam String token,
            @Parameter(
                    description = "Start date for historical candles in ISO-8601 yyyy-MM-dd format.",
                    required = true,
                    example = "2024-01-01"
            )
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(
                    description = "End date for historical candles in ISO-8601 yyyy-MM-dd format. Must be on or after from.",
                    required = true,
                    example = "2024-06-30"
            )
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(
                    description = "Kite candle interval. Defaults to day when omitted.",
                    example = "day",
                    schema = @Schema(allowableValues = {
                            "minute", "3minute", "5minute", "10minute", "15minute", "30minute",
                            "60minute", "day"
                    })
            )
            @RequestParam(defaultValue = "day") String interval) {

        HistoricalDataRequest request = new HistoricalDataRequest(token, from, to, interval);
        try {
            StrategyDecision decision = tradingStrategyService.evaluate(name, request);
            return ResponseEntity.ok(decision);
        } catch (RuntimeException ex) {
            log.error("Strategy evaluation failed strategy='{}' token='{}' from={} to={} interval={} error='{}'",
                    name,
                    request.instrumentToken(),
                    request.from(),
                    request.to(),
                    request.interval(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
