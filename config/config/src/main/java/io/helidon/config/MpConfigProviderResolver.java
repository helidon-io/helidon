/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.GenericType;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Integration with microprofile config.
 * This class is an implementation of a java service obtained through ServiceLoader.
 */
public class MpConfigProviderResolver extends ConfigProviderResolver {
    private static final Map<ClassLoader, ConfigDelegate> CONFIGS = new IdentityHashMap<>();
    private static final ReadWriteLock RW_LOCK = new ReentrantReadWriteLock();
    // specific for native image - we want to replace config provided during build with runtime configuration
    private static final List<ConfigDelegate> BUILD_CONFIG = new LinkedList<>();

    @Override
    public Config getConfig() {
        return getConfig(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public Config getConfig(ClassLoader loader) {
        if (null == loader) {
            loader = ClassLoader.getSystemClassLoader();
        }
        Lock lock = RW_LOCK.readLock();
        try {
            lock.lock();
            Config config = CONFIGS.get(loader);

            if (null == config) {
                lock.unlock();
                lock = RW_LOCK.writeLock();
                lock.lock();
                Config c = buildConfig(loader);
                return doRegisterConfig(c, loader);
            } else {
                return config;
            }
        } finally {
            lock.unlock();
        }
    }

    private Config buildConfig(ClassLoader loader) {
        MpConfigBuilder builder = getBuilder();

        Optional<io.helidon.config.Config> meta = MetaConfig.metaConfig();

        meta.ifPresent(builder::metaConfig);

        builder.forClassLoader(loader);

        if (meta.isEmpty()) {
            builder.addDefaultSources();
        }

        return builder.addDiscoveredSources()
                .addDiscoveredConverters()
                .build();
    }

    @Override
    public MpConfigBuilder getBuilder() {
        return new MpConfigBuilder();
    }

    @Override
    public void registerConfig(Config config, ClassLoader classLoader) {
        Lock lock = RW_LOCK.writeLock();
        try {
            lock.lock();
            doRegisterConfig(config, classLoader);
        } finally {
            lock.unlock();
        }
    }

    /**
     * This method should only be called when running within native image, as soon as runtime configuration
     * is available.
     *
     * @param config configuration to use
     */
    public static void runtimeStart(io.helidon.config.Config config) {
        if (BUILD_CONFIG.isEmpty()) {
            return;
        }
        BUILD_CONFIG.forEach(it -> it.set(config));
        BUILD_CONFIG.clear();
    }

    /**
     * This method should only be called when generating native image, as late in the process as possible.
     */
    public static void buildTimeEnd() {
        Lock lock = RW_LOCK.writeLock();
        try {
            lock.lock();
            CONFIGS.forEach((key, value) -> BUILD_CONFIG.add(value));
            CONFIGS.clear();
        } finally {
            lock.unlock();
        }
    }

    private ConfigDelegate doRegisterConfig(Config config, ClassLoader classLoader) {
        ConfigDelegate currentConfig = CONFIGS.remove(classLoader);

        if (config instanceof ConfigDelegate) {
            config = ((ConfigDelegate) config).delegate();
        }

        io.helidon.config.Config helidonConfig = (io.helidon.config.Config) config;

        if (null != currentConfig) {
            currentConfig.set(helidonConfig);
        }

        ConfigDelegate newConfig = new ConfigDelegate(helidonConfig);
        CONFIGS.put(classLoader, newConfig);

        return newConfig;
    }

    @Override
    public void releaseConfig(Config config) {
        // first attempt to find it
        Lock lock = RW_LOCK.readLock();
        AtomicReference<ClassLoader> cl = new AtomicReference<>();

        try {
            lock.lock();
            for (Map.Entry<ClassLoader, ConfigDelegate> entry : CONFIGS.entrySet()) {
                Config configFromRef = entry.getValue().delegate();
                if (config == configFromRef) {
                    cl.set(entry.getKey());
                    break;
                }
            }
        } finally {
            lock.unlock();
        }

        // if found, remove it
        if (cl.get() != null) {
            lock = RW_LOCK.writeLock();
            try {
                lock.lock();
                CONFIGS.remove(cl.get());
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * A delegate used to allow replacing configuration at runtime for components
     * that hold a reference to configuration obtained at build time.
     */
    public static final class ConfigDelegate implements io.helidon.config.Config, Config {
        private AtomicReference<io.helidon.config.Config> delegate;

        private ConfigDelegate(io.helidon.config.Config delegate) {
            this.delegate = new AtomicReference<>(delegate);
        }

        private void set(io.helidon.config.Config newDelegate) {
            this.delegate.set(newDelegate);
        }

        @Override
        public Instant timestamp() {
            return delegate.get().timestamp();
        }

        @Override
        public Key key() {
            return delegate.get().key();
        }

        @Override
        public io.helidon.config.Config get(Key key) {
            return delegate.get().get(key);
        }

        @Override
        public io.helidon.config.Config detach() {
            return delegate.get().detach();
        }

        @Override
        public Type type() {
            return delegate.get().type();
        }

        @Override
        public boolean hasValue() {
            return delegate.get().hasValue();
        }

        @Override
        public Stream<io.helidon.config.Config> traverse(Predicate<io.helidon.config.Config> predicate) {
            return delegate.get().traverse();
        }

        @Override
        public <T> T convert(Class<T> type, String value) throws ConfigMappingException {
            return delegate.get().convert(type, value);
        }

        @Override
        public <T> ConfigValue<T> as(GenericType<T> genericType) {
            return delegate.get().as(genericType);
        }

        @Override
        public <T> ConfigValue<T> as(Class<T> type) {
            return delegate.get().as(type);
        }

        @Override
        public <T> ConfigValue<T> as(Function<io.helidon.config.Config, T> mapper) {
            return delegate.get().as(mapper);
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException {
            return delegate.get().asList(type);
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Function<io.helidon.config.Config, T> mapper) throws ConfigMappingException {
            return delegate.get().asList(mapper);
        }

        @Override
        public ConfigValue<List<io.helidon.config.Config>> asNodeList() throws ConfigMappingException {
            return delegate.get().asNodeList();
        }

        @Override
        public ConfigValue<Map<String, String>> asMap() throws MissingValueException {
            return delegate.get().asMap();
        }

        @Override
        public <T> T getValue(String propertyName, Class<T> propertyType) {
            return ((Config) delegate.get()).getValue(propertyName, propertyType);
        }

        @Override
        public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
            return ((Config) delegate.get()).getOptionalValue(propertyName, propertyType);
        }

        @Override
        public Iterable<String> getPropertyNames() {
            return ((Config) delegate.get()).getPropertyNames();
        }

        @Override
        public Iterable<ConfigSource> getConfigSources() {
            return ((Config) delegate.get()).getConfigSources();
        }

        /**
         * Get the underlying instance of this delegate pattern.
         *
         * @return the instance backing this config delegate
         */
        public Config delegate() {
            return (Config) delegate.get();
        }
    }
}
