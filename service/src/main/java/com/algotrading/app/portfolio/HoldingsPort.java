package com.algotrading.app.portfolio;

import java.util.List;

/**
 * Port for retrieving broker portfolio holdings.
 */
public interface HoldingsPort {

    List<HoldingResponse> listHoldings();
}
