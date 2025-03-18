/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;

import static io.helidon.microprofile.testing.ReflectionHelper.invoke;
import static io.helidon.microprofile.testing.ReflectionHelper.isDefaultMethod;
import static io.helidon.microprofile.testing.ReflectionHelper.requireStatic;

/**
 * The synthetic test configuration that is expressed with annotations.
 * <p>
 * The delegate is initialized with the annotations extracted via {@link HelidonTestInfo}.
 * <p>
 * The delegate is re-built when the definitions are updated via the {@code update} methods.
 */
class HelidonTestConfigSynthetic extends HelidonTestConfigDelegate {

    private final Map<String, String> map = new HashMap<>();
    private final Map<String, Set<String>> blocks = new HashMap<>();
    private final Set<Method> methods = new HashSet<>();
    private final Set<String> resources = new HashSet<>();
    private final HelidonTestInfo<?> testInfo;
    private final ReentrantLock lock = new ReentrantLock();
    private final Runnable onUpdate;
    private boolean useExisting;
    private Config config;

    HelidonTestConfigSynthetic(HelidonTestInfo<?> testInfo, Runnable onUpdate) {
        this.testInfo = testInfo;
        this.onUpdate = onUpdate;
        map.put(ConfigSource.CONFIG_ORDINAL, "1000");
        map.put("server.port", "0");
        map.put("mp.config.profile", "test");
        testInfo.addConfigs().forEach(this::update);
        testInfo.addConfigBlocks().forEach(this::update);
        testInfo.addConfigSources().forEach(this::update);
        testInfo.configuration().ifPresent(this::update);
    }

    @Override
    Config delegate() {
        if (config == null) {
            try {
                lock.lock();
                if (config == null) {
                    config = buildConfig();
                    onUpdate.run();
                }
            } finally {
                lock.unlock();
            }
        }
        return config;
    }

    /**
     * Update.
     *
     * @param annotation annotation
     */
    void update(Configuration annotation) {
        map.put("mp.config.profile", annotation.profile());
        useExisting = annotation.useExisting();
        List<String> sources = List.of(annotation.configSources());
        if (!resources.containsAll(sources)) {
            resources.addAll(sources);
            config = null;
        }
    }

    /**
     * Update.
     *
     * @param annotations annotations
     */
    void update(AddConfig... annotations) {
        for (AddConfig annotation : annotations) {
            map.put(annotation.key(), annotation.value());
        }
    }

    /**
     * Update.
     *
     * @param annotations annotations
     */
    void update(AddConfigBlock... annotations) {
        for (AddConfigBlock annotation : annotations) {
            if (blocks.computeIfAbsent(annotation.type(), t -> new HashSet<>())
                    .add(annotation.value())) {
                config = null;
            }
        }
    }

    /**
     * Update.
     *
     * @param method method
     */
    void update(Method method) {
        if (methods.add(requireStatic(method))) {
            config = null;
        }
    }

    /**
     * Get the effective value of {@link Configuration#useExisting()}.
     *
     * @return useExisting
     */
    boolean useExisting() {
        return useExisting;
    }

    private Config buildConfig() {
        List<ConfigSource> configSources = new ArrayList<>();
        configSources.add(MpConfigSources.create(testInfo.id(), map));
        blocks.forEach((type, values) -> {
            for (String value : values) {
                ConfigSource config = MpConfigSources.create(type, new StringReader(value));
                configSources.add(addConfigOrdinal(config, type, "900"));
            }
        });
        for (Method m : methods) {
            ConfigSource config = invoke(ConfigSource.class, requireStatic(m), null);
            configSources.add(new ConfigSourceWrapper(config));
        }
        for (String source : resources) {
            String filename = source.trim();
            for (URL url : resources(filename)) {
                String type = extension(filename);
                ConfigSource config = MpConfigSources.create(type, url);
                configSources.add(addConfigOrdinal(config, type, "700"));
            }
        }
        ConfigBuilder builder = ConfigProviderResolver.instance()
                .getBuilder()
                .addDefaultSources()
                .addDiscoveredSources()
                .addDiscoveredConverters();
        configSources.forEach(builder::withSources);
        return builder.build();
    }

    private ConfigSource addConfigOrdinal(ConfigSource config, String type, String ordinal) {
        Map<String, String> properties = new HashMap<>(config.getProperties());
        properties.putIfAbsent(ConfigSource.CONFIG_ORDINAL, ordinal);
        return MpConfigSources.create(type, properties);
    }

    private static String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx > -1 ? filename.substring(idx + 1) : "properties";
    }

    private static Collection<URL> resources(String name) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Map<String, URL> urls = new HashMap<>();
            cl.getResources(name).asIterator()
                    .forEachRemaining(u -> urls.put(u.toString(), u));
            return urls.values();
        } catch (IOException e) {
            throw new UncheckedIOException(String.format(
                    "Failed to read '%s' from classpath", name), e);
        }
    }

    private record ConfigSourceWrapper(ConfigSource delegate) implements ConfigSource {
        @Override
        public Set<String> getPropertyNames() {
            return delegate.getPropertyNames();
        }

        @Override
        public String getValue(String propertyName) {
            return delegate.getValue(propertyName);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Map<String, String> getProperties() {
            return delegate.getProperties();
        }

        @Override
        public int getOrdinal() {
            boolean isDefault = isDefaultMethod(delegate, "getOrdinal");
            boolean isDefaultOrdinal = delegate.getOrdinal() == ConfigSource.DEFAULT_ORDINAL;
            return isDefault && isDefaultOrdinal ? 800 : delegate.getOrdinal();
        }
    }
}
