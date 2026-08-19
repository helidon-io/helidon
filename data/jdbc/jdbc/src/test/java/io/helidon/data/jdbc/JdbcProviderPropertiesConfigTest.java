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

import io.helidon.common.Size;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the typed persistence unit properties owned by the JDBC provider.
 */
class JdbcProviderPropertiesConfigTest {

    /**
     * Verifies the public configuration defaults preserve the established
     * cache and bootstrap limits.
     */
    @Test
    void providesEstablishedDefaults() {
        JdbcPropertiesConfig properties = JdbcPropertiesConfig.create();

        assertProviderProperties(properties, 256, 4_096, 8 * 1024 * 1024, 16 * 1024 * 1024, 10_000);
    }

    /**
     * Verifies file configuration and public builders produce equivalent
     * provider settings.
     */
    @Test
    void mapsConfiguredAndProgrammaticOverridesEquivalently() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "jdbc.parameter-count-cache.capacity", "17",
                "jdbc.parameter-count-cache.max-sql-length", "2048",
                "jdbc.scripts.max-resource-size", "4 MiB",
                "jdbc.scripts.max-total-size", "6 MiB",
                "jdbc.scripts.max-statements", "77")));
        JdbcPropertiesConfig configured = JdbcPropertiesConfig.create(config);
        JdbcPropertiesConfig programmatic = JdbcPropertiesConfig.builder()
                .jdbc(JdbcProviderPropertiesConfig.builder()
                              .parameterCountCache(JdbcParameterCountCacheConfig.builder()
                                                           .capacity(17)
                                                           .maxSqlLength(2 * 1024)
                                                           .buildPrototype())
                              .scripts(JdbcScriptConfig.builder()
                                               .maxResourceSize(Size.parse("4 MiB"))
                                               .maxTotalSize(Size.parse("6 MiB"))
                                               .maxStatements(77)
                                               .buildPrototype())
                              .buildPrototype())
                .buildPrototype();
        JdbcPersistenceUnitConfig programmaticUnit = JdbcPersistenceUnitConfig.builder()
                .dataSource("unused")
                .properties(programmatic)
                .buildPrototype();

        assertProviderProperties(configured, 17, 2 * 1024, 4 * 1024 * 1024, 6 * 1024 * 1024, 77);
        assertProviderProperties(programmatic, 17, 2 * 1024, 4 * 1024 * 1024, 6 * 1024 * 1024, 77);
        assertProviderProperties(programmaticUnit.properties(),
                                 17,
                                 2 * 1024,
                                 4 * 1024 * 1024,
                                 6 * 1024 * 1024,
                                 77);
    }

    /**
     * Verifies cache validation enforces the independent entry ceiling and
     * the retained SQL payload ceiling.
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
     * Verifies script validation accepts exact hard ceilings and a fractional
     * unit value that resolves to whole bytes.
     */
    @Test
    void acceptsValidScriptBoundaryValues() {
        JdbcScriptConfig exactMaximum = JdbcScriptConfig.builder()
                .maxResourceSize(Size.parse("64 MiB"))
                .maxTotalSize(Size.parse("128 MiB"))
                .maxStatements(100_000)
                .buildPrototype();
        JdbcScriptConfig fractionalUnit = JdbcScriptConfig.builder()
                .maxResourceSize(Size.parse("0.5 KiB"))
                .maxTotalSize(Size.parse("1 KiB"))
                .maxStatements(1)
                .buildPrototype();

        assertThat(exactMaximum.maxResourceSize().toBytes(), is(64L * 1024 * 1024));
        assertThat(exactMaximum.maxTotalSize().toBytes(), is(128L * 1024 * 1024));
        assertThat(exactMaximum.maxStatements(), is(100_000));
        assertThat(fractionalUnit.maxResourceSize().toBytes(), is(512L));
    }

    /**
     * Verifies script validation rejects invalid ranges, hard ceiling
     * violations, and values outside the bounded reader representation.
     */
    @Test
    void rejectsInvalidScriptBoundaryValues() {
        assertThrows(DataException.class,
                     () -> JdbcScriptConfig.builder().maxResourceSize(Size.ZERO).buildPrototype());
        assertThrows(DataException.class,
                     () -> JdbcScriptConfig.builder().maxResourceSize(Size.create(-1)).buildPrototype());
        assertThrows(DataException.class,
                     () -> JdbcScriptConfig.builder()
                             .maxResourceSize(Size.create(2))
                             .maxTotalSize(Size.create(1))
                             .buildPrototype());
        assertThrows(DataException.class,
                     () -> JdbcScriptConfig.builder()
                             .maxResourceSize(Size.parse("64 MiB")).maxTotalSize(Size.parse("128 MiB"))
                             .maxStatements(0)
                             .buildPrototype());
        assertThrows(DataException.class,
                     () -> JdbcScriptConfig.builder()
                             .maxResourceSize(Size.create(64L * 1024 * 1024 + 1))
                             .maxTotalSize(Size.create(64L * 1024 * 1024 + 1))
                             .buildPrototype());
        assertThrows(DataException.class,
                     () -> JdbcScriptConfig.builder()
                             .maxResourceSize(Size.create(1))
                             .maxTotalSize(Size.create(128L * 1024 * 1024 + 1))
                             .buildPrototype());
        assertThrows(DataException.class,
                     () -> JdbcScriptConfig.builder().maxStatements(100_001).buildPrototype());
        assertThrows(DataException.class,
                     () -> JdbcScriptConfig.builder()
                             .maxResourceSize(Size.create(Long.MAX_VALUE))
                             .maxTotalSize(Size.create(Long.MAX_VALUE))
                             .buildPrototype());
        assertThrows(DataException.class,
                     () -> new JdbcScriptRunner.BootstrapPolicy(Integer.MAX_VALUE, Integer.MAX_VALUE, 1));
        assertThrows(ArithmeticException.class, () -> Size.parse("0.5"));
    }

    /**
     * Verifies unknown keys retain the normal generated configuration behavior
     * and do not change recognized defaults.
     */
    @Test
    void ignoresUnknownProviderPropertyKeys() {
        Config config = Config.just(ConfigSources.create(Map.of("jdbc.unknown-setting", "ignored")));

        JdbcPropertiesConfig properties = JdbcPropertiesConfig.create(config);

        assertProviderProperties(properties, 256, 4_096, 8 * 1024 * 1024, 16 * 1024 * 1024, 10_000);
    }

    /**
     * Asserts every value in one public provider properties tree.
     *
     * @param properties provider properties
     * @param capacity expected cache capacity
     * @param maxSqlLength expected maximum cacheable SQL length
     * @param maxResourceBytes expected resource byte limit
     * @param maxTotalBytes expected total byte limit
     * @param maxStatements expected statement limit
     */
    private static void assertProviderProperties(JdbcPropertiesConfig properties,
                                                 int capacity,
                                                 int maxSqlLength,
                                                 long maxResourceBytes,
                                                 long maxTotalBytes,
                                                 int maxStatements) {
        JdbcProviderPropertiesConfig jdbc = properties.jdbc();
        assertThat(jdbc.parameterCountCache().capacity(), is(capacity));
        assertThat(jdbc.parameterCountCache().maxSqlLength(), is(maxSqlLength));
        assertThat(jdbc.scripts().maxResourceSize().toBytes(), is(maxResourceBytes));
        assertThat(jdbc.scripts().maxTotalSize().toBytes(), is(maxTotalBytes));
        assertThat(jdbc.scripts().maxStatements(), is(maxStatements));
    }
}
