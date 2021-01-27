/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.microstream.cache;

import java.lang.reflect.Field;
import java.util.function.Predicate;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;

import io.helidon.config.Config;

import one.microstream.cache.types.CacheConfiguration;
import one.microstream.cache.types.CacheConfiguration.Builder;
import one.microstream.cache.types.CacheConfigurationBuilderConfigurationBased;
import one.microstream.cache.types.CacheConfigurationPropertyNames;
import one.microstream.cache.types.EvictionManager;
import one.microstream.configuration.types.Configuration;

/**
 * Builder for Microstream-CacheConfigurations.
 *
 * @param <K> type of the cache key
 * @param <V> type of the cache value
 */
public class MicrostreamCacheConfigurationBuilder<K, V> implements CacheConfigurationPropertyNames,
CacheConfiguration.Builder<K, V>, io.helidon.common.Builder<CacheConfiguration<K, V>> {

    private Builder<K, V> cacheConfigBuilder;

    protected MicrostreamCacheConfigurationBuilder(Class<K> keyType, Class<V> valueType) {
        super();
        cacheConfigBuilder = CacheConfiguration.Builder(keyType, valueType);
    }

    protected MicrostreamCacheConfigurationBuilder(Configuration configuration, Class<K> keyType, Class<V> valueType) {
        super();
        cacheConfigBuilder = CacheConfigurationBuilderConfigurationBased.New().buildCacheConfiguration(configuration,
                CacheConfiguration.Builder(keyType, valueType));
    }

    /**
     * creates a new MicrostreamCacheConfigurationBuilder using the supplied helidon configuration.
     *
     * @param config helidon configuration
     * @return a new MicrostreamCacheConfigurationBuilder
     */
    public static MicrostreamCacheConfigurationBuilder<?, ?> builder(Config config) {
        return builder(config, null, null);
    }

    /**
     * Create a CacheConfiguration builder with default values.
     *
     * @param <K> type of the cache key
     * @param <V> type of the cache value
     * @param keyType type of the cache key
     * @param valueType type of the cache value
     * @return a new CacheConfiguration builder
     */
    public static <K, V> MicrostreamCacheConfigurationBuilder<K, V> builder(Class<K> keyType, Class<V> valueType) {
        return new MicrostreamCacheConfigurationBuilder<>(keyType, valueType);
    }

    /**
     * Create a CacheConfiguration builder initialized from the supplied helidon
     * configuration node.
     *
     * @param <K> type of the cache key
     * @param <V> type of the cache value
     * @param config helidon configuation
     * @param keyType type of the cache key
     * @param valueType type of the cache value
     * @return a new CacheConfiguration builder
     */
    public static <K, V> MicrostreamCacheConfigurationBuilder<K, V> builder(Config config, Class<K> keyType,
            Class<V> valueType) {
        one.microstream.configuration.types.Configuration.Builder configurationBuilder = Configuration.Builder();
        if (config.exists()) {
            config.detach().asMap().get().forEach(configurationBuilder::set);
        }

        Configuration configuration = configurationBuilder.buildConfiguration();
        configuration.opt(KEY_TYPE).ifPresent((s) -> verifyType(s, keyType));
        configuration.opt(VALUE_TYPE).ifPresent((s) -> verifyType(s, valueType));

        return new MicrostreamCacheConfigurationBuilder<K, V>(configuration, keyType, valueType);
    }

    @Override
    public CacheConfiguration<K, V> build() {
        return cacheConfigBuilder.build();
    }

    @Override
    public MicrostreamCacheConfigurationBuilder<K, V> readThrough(boolean readTrough) {
        cacheConfigBuilder.readThrough(readTrough);
        return this;
    }

    @Override
    public MicrostreamCacheConfigurationBuilder<K, V> writeThrough(boolean writeThrough) {
        cacheConfigBuilder.writeThrough(writeThrough);
        return this;
    }

    @Override
    public MicrostreamCacheConfigurationBuilder<K, V> storeByValue(boolean storeByValue) {
        cacheConfigBuilder.storeByValue(storeByValue);
        return this;
    }

    @Override
    public MicrostreamCacheConfigurationBuilder<K, V> enableStatistics(boolean enableStatistics) {
        cacheConfigBuilder.enableStatistics(enableStatistics);
        return this;
    }

    @Override
    public MicrostreamCacheConfigurationBuilder<K, V> enableManagement(boolean enableManagement) {
        cacheConfigBuilder.enableManagement(enableManagement);
        return this;
    }

    @Override
    public MicrostreamCacheConfigurationBuilder<K, V> expiryPolicyFactory(Factory<ExpiryPolicy> expiryPolicyFactory) {
        cacheConfigBuilder.expiryPolicyFactory(expiryPolicyFactory);
        return this;
    }

    @Override
    public MicrostreamCacheConfigurationBuilder<K, V> evictionManagerFactory(
            Factory<EvictionManager<K, V>> evictionManagerFactory) {
        cacheConfigBuilder.evictionManagerFactory(evictionManagerFactory);
        return this;
    }

    @Override
    public MicrostreamCacheConfigurationBuilder<K, V> cacheLoaderFactory(
            Factory<CacheLoader<K, V>> cacheLoaderFactory) {
        cacheConfigBuilder.cacheLoaderFactory(cacheLoaderFactory);
        return this;
    }

    @Override
    public MicrostreamCacheConfigurationBuilder<K, V> cacheWriterFactory(
            Factory<CacheWriter<? super K, ? super V>> cacheWriterFactory) {
        cacheConfigBuilder.cacheWriterFactory(cacheWriterFactory);
        return this;
    }

    @Override
    public MicrostreamCacheConfigurationBuilder<K, V> serializerFieldPredicate(
            Predicate<? super Field> serializerFieldPredicate) {
        cacheConfigBuilder.serializerFieldPredicate(serializerFieldPredicate);
        return this;
    }

    @Override
    public Builder<K, V> addListenerConfiguration(CacheEntryListenerConfiguration<K, V> listenerConfiguration) {
        cacheConfigBuilder.addListenerConfiguration(listenerConfiguration);
        return this;
    }

    private static void verifyType(String typeName, Class<?> actualType) {
        if (!typeName.equals(actualType.getTypeName())) {
            throw new ConfigException("Microstream cache-config type missmatch, expected value from configuration: " + typeName
                    + " but got: " + actualType.getTypeName());
        }
    }
}
