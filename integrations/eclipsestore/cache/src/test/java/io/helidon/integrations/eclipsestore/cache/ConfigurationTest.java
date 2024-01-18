/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.eclipsestore.cache;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import org.eclipse.store.cache.types.CacheConfiguration;
import org.eclipse.store.cache.types.EvictionManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.cache.configuration.Factory;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertAll;

class ConfigurationTest {

    /**
     * Test if all default properties are set.
     */
    @Test
    void defaultValuesTest() {
        CacheConfiguration<Integer, String> cacheConfig = EclipseStoreCacheConfigurationBuilder
                .builder(Integer.class, String.class).build();

        assertAll(() -> assertThat("getKeyType", cacheConfig.getKeyType(), typeCompatibleWith(Integer.class)),
                () -> assertThat("getValueType", cacheConfig.getValueType(), typeCompatibleWith(String.class)),
                () -> assertThat("isManagementEnabled", cacheConfig.isManagementEnabled(), is(false)),
                () -> assertThat("isStatisticsEnabled", cacheConfig.isStatisticsEnabled(), is(false)),
                () -> assertThat("isReadThrough", cacheConfig.isReadThrough(), is(false)),
                () -> assertThat("isWriteThrough", cacheConfig.isWriteThrough(), is(false)),
                () -> assertThat("isStoreByValue", cacheConfig.isStoreByValue(), is(false)),
                () -> assertThat("getExpiryPolicyFactory", cacheConfig.getExpiryPolicyFactory(),
                        is(CacheConfiguration.DefaultExpiryPolicyFactory())),
                () -> assertThat("getEvictionManagerFactory", cacheConfig.getEvictionManagerFactory(),
                        is(CacheConfiguration.DefaultEvictionManagerFactory())),
                () -> assertThat("getCacheLoaderFactory", cacheConfig.getCacheLoaderFactory(), nullValue()),
                () -> assertThat("getCacheWriterFactory", cacheConfig.getCacheWriterFactory(), nullValue()),
                () -> assertThat("getCacheEntryListenerConfigurations",
                        cacheConfig.getCacheEntryListenerConfigurations(), emptyIterable()));
    }

    /**
     * Test if simple configuration values are applied. This test does not check all
     * values
     */
    @Test
    void configValuesTest() {
        Map<String, String> source = Map.of("cache.management-enabled", "true", "cache.statistics-enabled", "true",
                "cache.store-by-value", "true");

        Config config = Config.builder().addSource(ConfigSources.create(source).build()).build();

        CacheConfiguration<Long, String> cacheConfig = EclipseStoreCacheConfigurationBuilder
                .builder(config.get("cache"), Long.class, String.class).build();

        assertAll(() -> assertThat("getKeyType", cacheConfig.getKeyType(), typeCompatibleWith(Long.class)),
                () -> assertThat("getValueType", cacheConfig.getValueType(), typeCompatibleWith(String.class)),
                () -> assertThat("isManagementEnabled", cacheConfig.isManagementEnabled(), is(true)),
                () -> assertThat("isStatisticsEnabled", cacheConfig.isStatisticsEnabled(), is(true)),
                () -> assertThat("isStoreByValue", cacheConfig.isStoreByValue(), is(true)));
    }

    /**
     * Test if settings from config can be altered by code
     */
    @Test
    void applyChangeTest() {

        Map<String, String> source = Map.of("cache.management-enabled", "true", "cache.statistics-enabled", "true",
                "cache.store-by-value", "true");

        Config config = Config.builder().addSource(ConfigSources.create(source).build()).build();

        CacheConfiguration<Long, String> cacheConfig = EclipseStoreCacheConfigurationBuilder
                .builder(config.get("cache"), Long.class, String.class).disableManagement().storeByValue(false).build();

        assertAll(() -> assertThat("isManagementEnabled", cacheConfig.isManagementEnabled(), is(false)),
                () -> assertThat("isStatisticsEnabled", cacheConfig.isStatisticsEnabled(), is(true)),
                () -> assertThat("isStoreByValue", cacheConfig.isStoreByValue(), is(false)));
    }

    @Test
    void cacheLoaderFactoryTest() {
        @SuppressWarnings("unchecked")
        Factory<CacheLoader<Integer, String>> cacheLoaderFactory = Mockito.mock(Factory.class);

        CacheConfiguration<Integer, String> cacheConfig = EclipseStoreCacheConfigurationBuilder
                .builder(Integer.class, String.class).cacheLoaderFactory(cacheLoaderFactory).build();

        assertThat(cacheConfig.getCacheLoaderFactory(), sameInstance(cacheLoaderFactory));
    }

    @Test
    void cacheWriterFactoryTest() {
        @SuppressWarnings("unchecked")
        Factory<CacheWriter<? super Integer, ? super String>> cacheWriterFactory = Mockito.mock(Factory.class);

        CacheConfiguration<Integer, String> cacheConfig = EclipseStoreCacheConfigurationBuilder
                .builder(Integer.class, String.class).cacheWriterFactory(cacheWriterFactory).build();

        assertThat(cacheConfig.getCacheWriterFactory(), sameInstance(cacheWriterFactory));
    }

    @Test
    void expiryPolicyFactoryTest() {
        @SuppressWarnings("unchecked")
        Factory<ExpiryPolicy> expiryPolicyFactory = Mockito.mock(Factory.class);

        CacheConfiguration<Integer, String> cacheConfig = EclipseStoreCacheConfigurationBuilder
                .builder(Integer.class, String.class).expiryPolicyFactory(expiryPolicyFactory).build();

        assertThat(cacheConfig.getExpiryPolicyFactory(), sameInstance(expiryPolicyFactory));
    }

    @Test
    void evictionManagerFactoryTest() {
        @SuppressWarnings("unchecked")
        Factory<EvictionManager<Integer, String>> evictionManagerFactory = Mockito.mock(Factory.class);

        CacheConfiguration<Integer, String> cacheConfig = EclipseStoreCacheConfigurationBuilder
                .builder(Integer.class, String.class).evictionManagerFactory(evictionManagerFactory).build();

        assertThat(cacheConfig.getEvictionManagerFactory(), sameInstance(evictionManagerFactory));
    }

}
