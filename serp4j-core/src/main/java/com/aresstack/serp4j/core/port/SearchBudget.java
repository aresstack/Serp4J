package com.aresstack.serp4j.core.port;

import com.aresstack.serp4j.core.model.id.ProviderId;

/**
 * A cost or volume limit the orchestrator checks before every paid call (WP2 §5.1). Implementations are mutable
 * and must be thread-safe; the core only defines the contract.
 */
public interface SearchBudget {

    /**
     * Tries to reserve {@code units} (requests, credits or similar, as defined by the budget) for a call of
     * {@code provider}.
     *
     * @return {@code true} if the units were reserved, {@code false} if the budget is exhausted
     */
    boolean tryAcquire(ProviderId provider, int units);
}
