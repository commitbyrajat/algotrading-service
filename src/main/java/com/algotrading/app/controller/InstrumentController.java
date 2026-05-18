package com.algotrading.app.controller;

import com.algotrading.app.model.InstrumentResponse;
import com.algotrading.app.service.InstrumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Kite instrument master endpoints.
 *
 * <pre>
 * GET /api/v1/instruments                                      → all tradable instruments
 * GET /api/v1/instruments?exchange=NSE                         → instruments for one exchange
 * GET /api/v1/instruments/by-symbols?tradingSymbols=INFY,TCS   → instruments for trading symbols
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/instruments")
@Tag(
        name = "Instruments",
        description = "Kite instrument-master lookup endpoints used to find tradable symbols, exchanges, and instrument tokens."
)
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    /**
     * Retrieves Kite's instrument master.
     *
     * <p>Kite returns a large gzipped CSV dump once per day. The SDK parses it
     * and this endpoint exposes the rows as JSON.</p>
     */
    @GetMapping
    @Operation(
            summary = "List Kite instruments",
            description = """
                    Returns Kite's instrument master as JSON. Use the optional exchange parameter
                    to restrict results to one exchange such as NSE, BSE, NFO, BFO, CDS, or MCX.
                    The response can be large when exchange is omitted.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Instrument master returned successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = InstrumentResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No active Kite session",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "Kite instrument service failed",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content)
    })
    public ResponseEntity<List<InstrumentResponse>> listInstruments(
            @Parameter(
                    description = "Optional Kite exchange filter. Omit it to return all instruments.",
                    example = "NSE",
                    schema = @Schema(allowableValues = {"NSE", "BSE", "NFO", "BFO", "CDS", "MCX"})
            )
            @RequestParam Optional<String> exchange) {
        return ResponseEntity.ok(instrumentService.listInstruments(exchange));
    }

    /**
     * Retrieves instruments matching one or more trading symbols.
     */
    @GetMapping("/by-symbols")
    @Operation(
            summary = "List Kite instruments by trading symbols",
            description = """
                    Returns instrument-master rows whose tradingSymbol matches one of the requested
                    symbols. The tradingSymbols parameter accepts comma-delimited values or repeated
                    query parameters. Use the optional exchange parameter to narrow duplicate symbols
                    across exchanges or segments.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching instruments returned successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = InstrumentResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No active Kite session",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "Kite instrument service failed",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content)
    })
    public ResponseEntity<List<InstrumentResponse>> listInstrumentsByTradingSymbols(
            @Parameter(
                    description = "Trading symbols to look up. Repeat this parameter or pass comma-delimited values.",
                    example = "INFY,TCS"
            )
            @RequestParam List<String> tradingSymbols,
            @Parameter(
                    description = "Optional Kite exchange filter. Omit it to search all instruments.",
                    example = "NSE",
                    schema = @Schema(allowableValues = {"NSE", "BSE", "NFO", "BFO", "CDS", "MCX"})
            )
            @RequestParam Optional<String> exchange) {
        return ResponseEntity.ok(instrumentService.listInstrumentsByTradingSymbols(exchange, tradingSymbols));
    }
}
