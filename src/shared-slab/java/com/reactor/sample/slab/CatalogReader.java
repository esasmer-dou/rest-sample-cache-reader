package com.reactor.sample.slab;

import com.reactor.rust.di.annotation.Component;
import com.reactor.rust.cache.api.CacheReadSnapshot;
import com.reactor.rust.cache.config.RustCacheConfig;
import com.reactor.rust.config.PropertiesLoader;
import com.reactor.rust.ffm.cache.SharedSlabCacheReader;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicLong;

@Component
public final class CatalogReader {
    private final SharedSlabCacheReader reader;
    private final AtomicLong reads = new AtomicLong();
    public CatalogReader() {
        RustCacheConfig config = RustCacheConfig.builder()
                .host(PropertiesLoader.require("sample.redis.host"))
                .port(PropertiesLoader.getInt("sample.redis.port", 6379)).accessMode("read-only")
                .readConnections(1).writeConnections(1).maxReadInflight(1).maxWriteInflight(1)
                .maxResponseBytes(PropertiesLoader.getInt("sample.redis.max-response-bytes", 65_536)).build();
        boolean enabled = PropertiesLoader.getBoolean("sample.slab.enabled", false);
        boolean required = PropertiesLoader.getBoolean("sample.slab.required", false);
        int slots = PropertiesLoader.getInt("sample.slab.slots", 4);
        int maxKey = PropertiesLoader.getInt("sample.slab.max-key-bytes", 256);
        reader = enabled && !PropertiesLoader.getBoolean("sample.slab.ffm", false)
                ? SharedSlabCacheReader.openNativeSlab(config, required, slots, maxKey)
                : SharedSlabCacheReader.open(config, enabled, required, slots, maxKey);
        System.out.println("Shared slab reader: backend=" + (reader.ffmActive() ? "FFM" : reader.slabActive() ? "JNI-SLAB" : "JNI")
                + ", payloadBytes=" + reader.slabPayloadBytes());
    }
    public CacheReadSnapshot read(String key) { reads.incrementAndGet(); return reader.read(key); }
    public long readCount() { return reads.get(); }
    public boolean ffmActive() { return reader.ffmActive(); }
    public boolean slabActive() { return reader.slabActive(); }
    public long slabBytes() { return reader.slabPayloadBytes(); }
    @PreDestroy public void close() { reader.close(); }
}
