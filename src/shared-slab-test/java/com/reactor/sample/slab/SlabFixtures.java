package com.reactor.sample.slab;

import com.reactor.rust.cache.config.RustCacheConfig;
import com.reactor.rust.cache.internal.nativebridge.NativeRedisBridge;
import java.nio.charset.StandardCharsets;

/** Seed only the disposable Redis used by this example's verification. */
public final class SlabFixtures {
    public static void main(String[] args) throws Exception {
        int client = NativeRedisBridge.createClient(RustCacheConfig.builder().host("127.0.0.1").port(6379)
                .readConnections(1).writeConnections(1).maxReadInflight(1).maxWriteInflight(1).maxResponseBytes(65536).build());
        try {
            for (int n = 0; n < 100; n++) {
                try {
                    if (!NativeRedisBridge.set(client, "phase54:catalog:small", "{\"stock\":42}".getBytes(StandardCharsets.UTF_8), 3600000)
                            || !NativeRedisBridge.set(client, "phase54:catalog:heavy",
                            ("{\"description\":\"" + "inventory-".repeat(400) + "\",\"stock\":42}").getBytes(StandardCharsets.UTF_8), 3600000)) {
                        throw new IllegalStateException("Seeding rejected");
                    }
                    System.out.println("SLAB_FIXTURES_READY"); return;
                } catch (RuntimeException notReady) { if (n == 99) throw notReady; Thread.sleep(100); }
            }
        } finally { NativeRedisBridge.closeClient(client); }
    }
}
