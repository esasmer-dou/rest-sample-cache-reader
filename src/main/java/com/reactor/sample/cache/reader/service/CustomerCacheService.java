package com.reactor.sample.cache.reader.service;

import com.reactor.rust.cache.api.CacheReadResult;
import com.reactor.rust.cache.api.CacheReadSnapshot;
import com.reactor.rust.cache.api.CacheReadValue;
import com.reactor.rust.cache.projection.CacheMetricsRead;
import com.reactor.rust.cache.projection.GenerateProjectionReader;
import com.reactor.rust.cache.projection.ProjectionIdRead;
import com.reactor.rust.cache.projection.ProjectionIndexRead;
import com.reactor.rust.cache.projection.ProjectionMetaRead;

@GenerateProjectionReader(
        rootPrefix = "sample.cache.customer",
        generatedName = "CustomerCacheReader",
        allowL1 = true,
        restBean = true)
public interface CustomerCacheService {

    @ProjectionIdRead(projection = "detail")
    CacheReadResult customer(long id);

    @ProjectionIdRead(projection = "detail")
    CacheReadSnapshot customerSnapshot(long id);

    @ProjectionIdRead(projection = "detail")
    CacheReadValue customerValue(long id);

    @ProjectionIndexRead(projection = "segment", index = "segment", defaultValue = "standard")
    CacheReadResult customersBySegment(String segment);

    @ProjectionIndexRead(projection = "segment", index = "segment", defaultValue = "standard")
    CacheReadSnapshot segmentSnapshot(String segment);

    @ProjectionIndexRead(projection = "segment", index = "segment", defaultValue = "standard")
    CacheReadValue segmentValue(String segment);

    @ProjectionIndexRead(projection = "detail", index = "customer-no")
    CacheReadResult customerByCustomerNo(String customerNo);

    @ProjectionIndexRead(projection = "status", index = "status", defaultValue = "active")
    CacheReadResult customersByStatus(String status);

    @ProjectionIndexRead(projection = "campaign", index = "campaign", defaultValue = "retention")
    CacheReadResult campaignCandidates(String campaign);

    @ProjectionMetaRead(projection = "meta")
    CacheReadResult meta();

    @CacheMetricsRead
    String metricsJson();
}
