package com.reactor.sample.outbound;

import com.reactor.rust.http.client.HttpExchange;
import com.reactor.rust.http.client.ReactorHttpClient;
import com.reactor.rust.annotations.RequiresProperty;
import java.util.concurrent.CompletionStage;

@ReactorHttpClient(baseUrlProperty = "sample.stock.base-url")
@RequiresProperty(name = "sample.outbound.breaker.enabled", value = "false", matchIfMissing = true)
@RequiresProperty(name = "sample.outbound.enabled", value = "true", matchIfMissing = true)
public interface StockClient {
    @HttpExchange(path = "/stock/small", retries = 0, timeoutMs = 2000, idempotent = true)
    CompletionStage<byte[]> small();
    @HttpExchange(path = "/stock/heavy", retries = 0, timeoutMs = 2000, idempotent = true)
    CompletionStage<byte[]> heavy();
}
