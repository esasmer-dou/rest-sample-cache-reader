package com.reactor.sample.outbound;

import com.reactor.rust.http.client.HttpCircuitBreaker;
import com.reactor.rust.http.client.HttpExchange;
import com.reactor.rust.http.client.ReactorHttpClient;
import com.reactor.rust.annotations.RequiresProperty;
import java.util.concurrent.CompletionStage;

@ReactorHttpClient(baseUrlProperty = "sample.stock.base-url")
@RequiresProperty(name = "sample.outbound.breaker.enabled", value = "true")
@RequiresProperty(name = "sample.outbound.enabled", value = "true", matchIfMissing = true)
@HttpCircuitBreaker(windowSize = 64, minimumCalls = 16, failureRateThreshold = 50, openDurationMs = 1000, halfOpenMaxCalls = 2)
public interface ProtectedStockClient {
    @HttpExchange(path = "/stock/small", retries = 0, timeoutMs = 2000, idempotent = true)
    CompletionStage<byte[]> small();
    @HttpExchange(path = "/stock/heavy", retries = 0, timeoutMs = 2000, idempotent = true)
    CompletionStage<byte[]> heavy();
    @HttpCircuitBreaker(windowSize = 1, minimumCalls = 1, failureRateThreshold = 100, openDurationMs = 100, halfOpenMaxCalls = 1)
    @HttpExchange(path = "/stock/error", retries = 0, timeoutMs = 2000, idempotent = true)
    CompletionStage<byte[]> unavailable();
}
