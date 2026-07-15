package com.reactor.sample.cache.reader.service;

import com.reactor.rust.cache.api.CacheReadResult;
import com.reactor.rust.cache.config.CacheProperties;
import com.reactor.rust.cache.core.RustCache;
import com.reactor.rust.cache.projection.CacheReaderProjectionSettings;
import com.reactor.rust.cache.projection.VersionedJsonProjectionReaders;

import java.util.List;

public final class CustomerCacheService {

    private final RustCache cache;
    private final VersionedJsonProjectionReaders readers;

    public CustomerCacheService(RustCache cache, CacheProperties properties) {
        this(cache, properties, CacheReaderProjectionSettings.resolveAll(properties, "sample.cache.customer"));
    }

    public CustomerCacheService(
            RustCache cache,
            CacheProperties properties,
            List<CacheReaderProjectionSettings> projectionSettings) {
        this.cache = cache;
        long versionCacheMillis = properties.getLong("sample.cache.customer.version-cache-ms");
        this.readers = VersionedJsonProjectionReaders.create(cache, projectionSettings, versionCacheMillis);
    }

    public CacheReadResult customer(long id) {
        return readers.getById("detail", id);
    }

    public CacheReadResult customersBySegment(String segment) {
        return readers.getIndex(
                "segment",
                "segment",
                segment == null || segment.isBlank() ? "standard" : segment);
    }

    public CacheReadResult customerByCustomerNo(String customerNo) {
        return readers.getIndex("detail", "customer-no", customerNo == null ? "" : customerNo.trim());
    }

    public CacheReadResult customersByStatus(String status) {
        return readers.getIndex(
                "status",
                "status",
                status == null || status.isBlank() ? "active" : status);
    }

    public CacheReadResult campaignCandidates(String campaign) {
        return readers.getIndex(
                "campaign",
                "campaign",
                campaign == null || campaign.isBlank() ? "retention" : campaign);
    }

    public CacheReadResult meta() {
        return readers.getMeta("meta");
    }

    public String metricsJson() {
        return cache.metricsJson();
    }

}
