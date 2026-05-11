# Project Context

## Current State

This repository contains a Java 21 Spring Boot service for Zerodha Kite-backed strategy evaluation and manual order execution. The implementation is organized around domain strategies, ports, and Kite adapters.

Important entry points:

- App bootstrap: `src/main/java/com/algotrading/app/App.java`
- Auth API: `src/main/java/com/algotrading/app/controller/KiteAuthController.java`
- Strategy API: `src/main/java/com/algotrading/app/controller/StrategyController.java`
- Order API: `src/main/java/com/algotrading/app/controller/OrderController.java`
- Strategy orchestration: `src/main/java/com/algotrading/app/service/TradingStrategyService.java`
- Kite market data adapter: `src/main/java/com/algotrading/app/market/KiteMarketDataAdapter.java`
- Kite order adapter: `src/main/java/com/algotrading/app/order/KiteOrderAdapter.java`

## Implementation Review

### Strengths

- Strategy evaluation and order placement are explicitly decoupled.
- Domain strategy code does not import the Kite SDK.
- The `TechnicalStrategy` registry makes strategy additions straightforward.
- Strategy tests are mostly direct unit tests and avoid infrastructure dependencies.
- Market order protection is handled in the Kite adapter instead of leaking to API clients.
- Redis token restore is non-fatal, which keeps the service bootable when Redis is unavailable.

### Findings

1. `mvn test` is not green on the current machine.
   - `TradingStrategyServiceTest` fails because Mockito inline mock maker cannot self-attach to the current Oracle JDK 21 runtime.
   - Main compilation succeeds, and the non-Mockito tests pass.
   - Practical fix: configure Mockito as a Java agent in Surefire or avoid Mockito inline mocking for these tests.

2. Auth path documentation is inconsistent.
   - Runtime warning in `KiteAuthService` points to `/api/v1/kite/login`, but the implemented endpoint is `/api/v1/kite/login-url`.
   - `GlobalExceptionHandler` also hints at `/kite/login`.
   - `application.yml` defaults `KITE_REDIRECT_URL` to `http://127.0.0.1:8080/kite/callback`, while the controller mapping is `/api/v1/kite/callback`.

3. README has stale implementation details.
   - Project structure lists `AlgoTradingApplication.java`, but the actual bootstrap class is `App.java`.
   - Testing section expects 44 passing tests, while this run discovered 46 tests with 3 Mockito errors.
   - GAINZ_ALPHA_V2 README wording still describes bounded RSI bands, while implementation uses single-sided thresholds.

4. `OrderRequest` imports `JsonSetter` and `Nulls` but does not use them.
   - This is harmless but should be cleaned up.

5. Order validation is incomplete for live trading safety.
   - The request validates nulls, quantity, and non-negative prices.
   - It does not restrict `exchange`, `transactionType`, `orderType`, or `product` to known Kite-supported values.
   - It does not require positive `price` for `LIMIT` or trigger price for `SL` / `SL-M`.

6. Session handling is single-user.
   - `KiteSessionStore` holds one session in an `AtomicReference`.
   - This is acceptable for a local single-account service, but it is not multi-tenant.

## Verification

Command run:

```bash
mvn test
```

Result:

- Main source compiled successfully.
- Total tests discovered: 46.
- Passing before failure summary: 43.
- Errors: 3, all in `TradingStrategyServiceTest`.
- Root cause: Mockito inline Byte Buddy mock maker could not self-attach to the JVM.

## Development Notes

- Prefer adding strategy behavior through `TechnicalStrategy` implementations rather than branching inside `TradingStrategyService`.
- Keep Kite SDK types isolated to adapters/config/auth.
- Treat `StrategyDecision` as advisory only. Do not wire it directly to order placement without a separate risk design.
- Use `mvn test -Dtest=ClassName` for targeted strategy and utility tests.
- Redis is required for token restart resilience, but not for application startup.

## Recommended Next Steps

1. Fix Mockito/Surefire setup so `mvn test` is stable on JDK 21.
2. Align all auth URLs to `/api/v1/kite/login-url` and `/api/v1/kite/callback`.
3. Update README to match current class names, strategy logic, and test count.
4. Replace stringly typed order fields with enums or explicit allow-list validation.
5. Add controller-level tests for error responses and order validation.
6. Add an integration-test profile for Redis and Kite adapter boundaries using fakes or test containers.
