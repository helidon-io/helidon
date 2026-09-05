/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.jdbc;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the parameter count cache configuration owned by the JDBC provider.
 */
class JdbcClientCacheConfigTest {

    /**
     * Verifies a client uses the established parameter count cache defaults.
     */
    @Test
    void providesEstablishedDefaults() {
        JdbcClientConfig config = JdbcClientConfig.builder()
                .dataSource("unused")
                .buildPrototype();

        assertCache(config, 256, 4_096);
    }

    /**
     * Verifies nested application configuration and the public builder
     * produce equivalent parameter count cache settings.
     */
    @Test
    void mapsConfiguredAndProgrammaticOverridesEquivalently() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data-source", "unused",
                "properties.jdbc.parameter-count-cache.capacity", "17",
                "properties.jdbc.parameter-count-cache.max-sql-length", "2048")));
        JdbcClientConfig configured = JdbcClientConfig.create(config);
        JdbcClientConfig programmatic = JdbcClientConfig.builder()
                .dataSource("unused")
                .parameterCountCacheCapacity(17)
                .parameterCountCacheMaxSqlLength(2_048)
                .buildPrototype();

        assertCache(configured, 17, 2_048);
        assertCache(programmatic, 17, 2_048);
    }

    /**
     * Verifies cache validation enforces the independent entry ceiling and
     * retained SQL payload ceiling.
     */
    @Test
    void validatesParameterCountCacheBounds() {
        JdbcClientConfig exactMaximum = cacheBuilder()
                .parameterCountCacheCapacity(4_096)
                .parameterCountCacheMaxSqlLength(4_096)
                .buildPrototype();

        assertCache(exactMaximum, 4_096, 4_096);
        assertThrows(DataException.class,
                     () -> cacheBuilder().parameterCountCacheCapacity(-1).buildPrototype());
        assertThrows(DataException.class,
                     () -> cacheBuilder()
                             .parameterCountCacheCapacity(4_097)
                             .parameterCountCacheMaxSqlLength(1)
                             .buildPrototype());
        assertThrows(DataException.class,
                     () -> cacheBuilder().parameterCountCacheMaxSqlLength(0).buildPrototype());
        assertThrows(DataException.class,
                     () -> cacheBuilder()
                             .parameterCountCacheCapacity(4_096)
                             .parameterCountCacheMaxSqlLength(4_097)
                             .buildPrototype());
    }

    /**
     * Verifies application configuration applies the same cross-field cache
     * validation as the public builder.
     */
    @Test
    void validatesConfiguredParameterCountCacheBounds() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data-source", "unused",
                "properties.jdbc.parameter-count-cache.capacity", "4096",
                "properties.jdbc.parameter-count-cache.max-sql-length", "4097")));

        assertThrows(DataException.class, () -> JdbcClientConfig.create(config));
    }

    /**
     * Verifies an unknown provider key does not change recognized defaults.
     */
    @Test
    void ignoresUnknownProviderPropertyKeys() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data-source", "unused",
                "properties.jdbc.unknown-setting", "ignored")));

        assertCache(JdbcClientConfig.create(config), 256, 4_096);
    }

    private static void assertCache(JdbcClientConfig config, int capacity, int maxSqlLength) {
        assertThat(config.parameterCountCacheCapacity(), is(capacity));
        assertThat(config.parameterCountCacheMaxSqlLength(), is(maxSqlLength));
    }

    private static JdbcClientConfig.Builder cacheBuilder() {
        return JdbcClientConfig.builder().dataSource("unused");
    }
}
