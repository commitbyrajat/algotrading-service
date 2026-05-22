package com.algotrading.app.controller;

import com.algotrading.app.portfolio.HoldingResponse;
import com.algotrading.app.portfolio.HoldingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Portfolio holdings endpoints.
 */
@RestController
@RequestMapping("/api/v1/holdings")
@Tag(
        name = "Holdings",
        description = "Zerodha Kite portfolio holdings endpoints."
)
public class HoldingsController {

    private final HoldingsService holdingsService;

    public HoldingsController(HoldingsService holdingsService) {
        this.holdingsService = holdingsService;
    }

    /**
     * List long-term equity delivery holdings.
     *
     * <pre>GET /api/v1/holdings</pre>
     */
    @GetMapping
    @Operation(
            summary = "List Kite portfolio holdings",
            description = """
                    Returns long-term equity delivery holdings from Kite portfolio holdings.
                    This maps Kite Connect GET /portfolio/holdings and does not place orders.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Holdings returned successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = HoldingResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No active Kite session",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "Kite holdings lookup failed",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content)
    })
    public ResponseEntity<List<HoldingResponse>> listHoldings() {
        return ResponseEntity.ok(holdingsService.listHoldings());
    }
}
