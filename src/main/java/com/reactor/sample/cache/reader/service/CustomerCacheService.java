package com.reactor.sample.cache.reader.service;

import com.reactor.rust.cache.api.CacheReadResult;
import com.reactor.rust.cache.core.RustCache;
import com.reactor.rust.cache.versioned.VersionedJsonCacheReader;
import com.reactor.sample.cache.reader.config.CacheReaderProperties;
import com.reactor.sample.cache.reader.config.CacheReaderProjectionSettings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomerCacheService {

    private final RustCache cache;
    private final Map<String, VersionedJsonCacheReader> readers;

    public CustomerCacheService(RustCache cache, CacheReaderProperties properties) {
        this(cache, properties, CacheReaderProjectionSettings.resolveAll(properties));
    }

    public CustomerCacheService(
            RustCache cache,
            CacheReaderProperties properties,
            List<CacheReaderProjectionSettings> projectionSettings) {
        this.cache = cache;
        long versionCacheMillis = properties.getLong("sample.cache.customer.version-cache-ms");
        this.readers = createReaders(cache, projectionSettings, versionCacheMillis);
    }

    public CacheReadResult customer(long id) {
        VersionedJsonCacheReader reader = reader("detail");
        return reader == null ? CacheReadResult.cacheNotReady() : reader.getById(id);
    }

    public CacheReadResult customersBySegment(String segment) {
        VersionedJsonCacheReader reader = reader("segment");
        return reader == null
                ? CacheReadResult.cacheNotReady()
                : reader.getIndex("segment", segment == null || segment.isBlank() ? "standard" : segment);
    }

    public CacheReadResult customerByCustomerNo(String customerNo) {
        VersionedJsonCacheReader reader = reader("detail");
        return reader == null
                ? CacheReadResult.cacheNotReady()
                : reader.getIndex("customer-no", customerNo == null ? "" : customerNo.trim());
    }

    public CacheReadResult customersByStatus(String status) {
        VersionedJsonCacheReader reader = reader("status");
        return reader == null
                ? CacheReadResult.cacheNotReady()
                : reader.getIndex("status", status == null || status.isBlank() ? "active" : status);
    }

    public CacheReadResult campaignCandidates(String campaign) {
        VersionedJsonCacheReader reader = reader("campaign");
        return reader == null
                ? CacheReadResult.cacheNotReady()
                : reader.getIndex("campaign", campaign == null || campaign.isBlank() ? "retention" : campaign);
    }

    public CacheReadResult meta() {
        VersionedJsonCacheReader reader = reader("meta");
        return reader == null ? CacheReadResult.cacheNotReady() : reader.getMeta();
    }

    public String metricsJson() {
        return cache.metricsJson();
    }

    private static Map<String, VersionedJsonCacheReader> createReaders(
            RustCache cache,
            List<CacheReaderProjectionSettings> projectionSettings,
            long versionCacheMillis) {
        Map<String, VersionedJsonCacheReader> created = new LinkedHashMap<>();
        for (CacheReaderProjectionSettings settings : projectionSettings) {
            created.put(settings.name(), cache.versionedJsonReader(settings.namespace(), versionCacheMillis));
        }
        return Collections.unmodifiableMap(created);
    }

    private VersionedJsonCacheReader reader(String projection) {
        return readers.get(projection);
    }
}
