package com.reactor.sample.cache.reader.service;

import com.reactor.rust.cache.api.CacheReadResult;
import com.reactor.rust.cache.core.RustCache;
import com.reactor.rust.cache.versioned.VersionedJsonCacheReader;
import com.reactor.sample.cache.reader.config.CacheReaderProperties;

public final class CustomerCacheService {

    private final RustCache cache;
    private final VersionedJsonCacheReader details;
    private final VersionedJsonCacheReader segments;
    private final VersionedJsonCacheReader statuses;
    private final VersionedJsonCacheReader campaigns;
    private final VersionedJsonCacheReader meta;

    public CustomerCacheService(RustCache cache, CacheReaderProperties properties) {
        this.cache = cache;
        long versionCacheMillis = properties.getLong("sample.cache.customer.version-cache-ms");
        this.details = cache.versionedJsonReader(namespace(properties, "detail"), versionCacheMillis);
        this.segments = cache.versionedJsonReader(namespace(properties, "segment"), versionCacheMillis);
        this.statuses = cache.versionedJsonReader(namespace(properties, "status"), versionCacheMillis);
        this.campaigns = cache.versionedJsonReader(namespace(properties, "campaign"), versionCacheMillis);
        this.meta = cache.versionedJsonReader(namespace(properties, "meta"), versionCacheMillis);
    }

    public CacheReadResult customer(long id) {
        return details.getById(id);
    }

    public CacheReadResult customersBySegment(String segment) {
        return segments.getIndex("segment", segment == null || segment.isBlank() ? "standard" : segment);
    }

    public CacheReadResult customerByCustomerNo(String customerNo) {
        return details.getIndex("customer-no", customerNo == null ? "" : customerNo.trim());
    }

    public CacheReadResult customersByStatus(String status) {
        return statuses.getIndex("status", status == null || status.isBlank() ? "active" : status);
    }

    public CacheReadResult campaignCandidates(String campaign) {
        return campaigns.getIndex("campaign", campaign == null || campaign.isBlank() ? "retention" : campaign);
    }

    public CacheReadResult meta() {
        return meta.getMeta();
    }

    public String metricsJson() {
        return cache.metricsJson();
    }

    private static String namespace(CacheReaderProperties properties, String projection) {
        String specificKey = "sample.cache.customer." + projection + ".namespace";
        String specificRuntime = properties.getRuntimeOverride(specificKey);
        if (hasText(specificRuntime)) {
            return specificRuntime;
        }
        String baseRuntime = properties.getRuntimeOverride("sample.cache.customer.namespace");
        if (hasText(baseRuntime)) {
            return baseRuntime + "." + projection;
        }
        String specificFile = properties.getFileOptional(specificKey);
        if (hasText(specificFile)) {
            return specificFile;
        }
        return properties.get("sample.cache.customer.namespace") + "." + projection;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
