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
 * Tests the typed client properties owned by the JDBC provider.
 */
class JdbcProviderPropertiesConfigTest {

    /**
     * Verifies the public configuration preserves the established parameter
     * count cache defaults.
     */
    @Test
    void providesEstablishedDefaults() {
        JdbcPropertiesConfig properties = JdbcPropertiesConfig.create();

        assertCache(properties, 256, 4_096);
    }

    /**
     * Verifies file configuration and public builders produce equivalent
     * parameter count cache settings.
     */
    @Test
    void mapsConfiguredAndProgrammaticOverridesEquivalently() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "jdbc.parameter-count-cache.capacity", "17",
                "jdbc.parameter-count-cache.max-sql-length", "2048")));
        JdbcPropertiesConfig configured = JdbcPropertiesConfig.create(config);
        JdbcPropertiesConfig programmatic = JdbcPropertiesConfig.builder()
                .jdbc(JdbcProviderPropertiesConfig.builder()
                              .parameterCountCache(JdbcParameterCountCacheConfig.builder()
                                                           .capacity(17)
                                                           .maxSqlLength(2_048)
                                                           .buildPrototype())
                              .buildPrototype())
                .buildPrototype();
        JdbcClientConfig programmaticClient = JdbcClientConfig.builder()
                .dataSource("unused")
                .properties(programmatic)
                .buildPrototype();

        assertCache(configured, 17, 2_048);
        assertCache(programmatic, 17, 2_048);
        assertCache(programmaticClient.properties(), 17, 2_048);
    }

    /**
     * Verifies cache validation enforces the independent entry ceiling and
     * retained SQL payload ceiling.
     */
    @Test
    void validatesParameterCountCacheBounds() {
        JdbcParameterCountCacheConfig exactMaximum = JdbcParameterCountCacheConfig.builder()
                .capacity(4_096)
                .maxSqlLength(4_096)
                .buildPrototype();

        assertThat(exactMaximum.capacity(), is(4_096));
        assertThat(exactMaximum.maxSqlLength(), is(4_096));
        assertThrows(DataException.class,
                     () -> JdbcParameterCountCacheConfig.builder().capacity(-1).buildPrototype());
        assertThrows(DataException.class,
                     () -> JdbcParameterCountCacheConfig.builder()
                             .capacity(4_097)
                             .maxSqlLength(1)
                             .buildPrototype());
        assertThrows(DataException.class,
                     () -> JdbcParameterCountCacheConfig.builder().maxSqlLength(0).buildPrototype());
        assertThrows(DataException.class,
                     () -> JdbcParameterCountCacheConfig.builder()
                             .capacity(4_096)
                             .maxSqlLength(4_097)
                             .buildPrototype());
    }

    /**
     * Verifies unknown provider keys do not change recognized defaults.
     */
    @Test
    void ignoresUnknownProviderPropertyKeys() {
        Config config = Config.just(ConfigSources.create(Map.of("jdbc.unknown-setting", "ignored")));

        assertCache(JdbcPropertiesConfig.create(config), 256, 4_096);
    }

    private static void assertCache(JdbcPropertiesConfig properties, int capacity, int maxSqlLength) {
        JdbcParameterCountCacheConfig cache = properties.jdbc().parameterCountCache();
        assertThat(cache.capacity(), is(capacity));
        assertThat(cache.maxSqlLength(), is(maxSqlLength));
    }
}
