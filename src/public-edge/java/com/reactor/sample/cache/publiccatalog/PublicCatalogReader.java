package com.reactor.sample.cache.publiccatalog;

import com.reactor.rust.cache.api.CacheReadResult;
import com.reactor.rust.cache.projection.GenerateProjectionReader;
import com.reactor.rust.cache.projection.ProjectionIndexRead;

@GenerateProjectionReader(rootPrefix = "sample.public.catalog", restBean = true)
public interface PublicCatalogReader {
    @ProjectionIndexRead(projection = "product", index = "sku")
    CacheReadResult product(String sku);
}
