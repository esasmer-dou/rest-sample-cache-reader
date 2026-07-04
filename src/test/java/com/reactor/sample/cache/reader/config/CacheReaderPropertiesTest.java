package com.reactor.sample.cache.reader.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheReaderPropertiesTest {

    @Test
    void loadsClasspathDefaults() {
        CacheReaderProperties properties = CacheReaderProperties.load();

        assertEquals("crm.customer", properties.get("sample.cache.customer.namespace"));
        assertEquals("crm.customer.detail", properties.get("sample.cache.customer.detail.namespace"));
        assertEquals("crm.customer.segment", properties.get("sample.cache.customer.segment.namespace"));
        assertEquals("crm.customer.status", properties.get("sample.cache.customer.status.namespace"));
        assertEquals("crm.customer.campaign", properties.get("sample.cache.customer.campaign.namespace"));
        assertEquals("crm.customer.meta", properties.get("sample.cache.customer.meta.namespace"));
        assertEquals(8080, properties.getInt("server.port"));
    }
}
