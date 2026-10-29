package com.reactor.sample.trace;

import com.reactor.rust.annotations.RequiresProperty;
import com.reactor.rust.http.client.HttpExchange;
import com.reactor.rust.http.client.ReactorHttpClient;
import java.util.concurrent.CompletionStage;

@ReactorHttpClient(baseUrlProperty = "sample.inventory.base-url")
@RequiresProperty(name = "sample.api.enabled", value = "true", matchIfMissing = true)
public interface InventoryClient {
    @HttpExchange(path = "/inventory/small", retries = 0, timeoutMs = 2000, idempotent = true)
    CompletionStage<byte[]> small();
    @HttpExchange(path = "/inventory/heavy", retries = 0, timeoutMs = 2000, idempotent = true)
    CompletionStage<byte[]> heavy();
    @HttpExchange(path = "/inventory/retry", retries = 2, timeoutMs = 2000, idempotent = true)
    CompletionStage<byte[]> retry();
}
