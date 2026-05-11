package com.algotrading.app.strategy;

import com.algotrading.app.exception.StrategyNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of all {@link TechnicalStrategy} implementations.
 *
 * <p>Spring auto-injects every {@code @Component} that implements
 * {@link TechnicalStrategy} via the {@code List} constructor parameter.
 * The registry key is {@link TechnicalStrategy#name()}.</p>
 */
@Component
public class StrategyRegistry {

    private final Map<String, TechnicalStrategy> registry;

    public StrategyRegistry(List<TechnicalStrategy> strategies) {
        this.registry = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(
                        TechnicalStrategy::name,
                        Function.identity()
                ));
    }

    /**
     * Retrieve a strategy by its exact registered name.
     *
     * @param name the strategy name
     * @return the matching strategy
     * @throws StrategyNotFoundException if no strategy with that name is registered
     */
    public TechnicalStrategy get(String name) {
        TechnicalStrategy strategy = registry.get(name);
        if (strategy == null) {
            throw new StrategyNotFoundException(name);
        }
        return strategy;
    }

    /**
     * Returns an unmodifiable snapshot of all registered strategy names.
     *
     * @return list of strategy names; order is not guaranteed
     */
    public List<String> listNames() {
        return Collections.unmodifiableList(List.copyOf(registry.keySet()));
    }
}