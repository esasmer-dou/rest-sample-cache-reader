package com.reactor.sample.cache.reader.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheReaderPropertiesTest {

    @Test
    void loadsClasspathDefaults() {
        CacheReaderProperties properties = CacheReaderProperties.load();

        assertEquals("crm.customer", properties.get("sample.cache.customer.namespace"));
        assertEquals(8080, properties.getInt("server.port"));
    }
}
