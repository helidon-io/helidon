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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigValue;
import io.helidon.config.MissingValueException;
import io.helidon.config.spi.ConfigMapper;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.LazyConfigSource;

/**
 * Config delegate.
 * <p>
 * Also implements a {@link Config Helidon Config} delegate backed by a {@link LazyConfigSource}
 * to support "just in time" caching when using {@link io.helidon.config.Config Helidon Config}.
 */
abstract class HelidonTestConfigDelegate implements org.eclipse.microprofile.config.Config, Config {

    private final LazyValue<Config> hdelegate = LazyValue.create(this::delegate0);
    private final Map<org.eclipse.microprofile.config.Config, List<String>> cache = new HashMap<>();

    /*
     * Get the MicroProfile config delegate.
     *
     * @return delegate
     */
    abstract org.eclipse.microprofile.config.Config delegate();

    @Override
    public <T> T getValue(String propertyName, Class<T> propertyType) {
        return delegate().getValue(propertyName, propertyType);
    }

    @Override
    public org.eclipse.microprofile.config.ConfigValue getConfigValue(String propertyName) {
        return delegate().getConfigValue(propertyName);
    }

    @Override
    public <T> List<T> getValues(String propertyName, Class<T> propertyType) {
        return delegate().getValues(propertyName, propertyType);
    }

    @Override
    public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
        return delegate().getOptionalValue(propertyName, propertyType);
    }

    @Override
    public <T> Optional<List<T>> getOptionalValues(String propertyName, Class<T> propertyType) {
        return delegate().getOptionalValues(propertyName, propertyType);
    }

    @Override
    public Iterable<String> getPropertyNames() {
        return delegate().getPropertyNames();
    }

    @Override
    public Iterable<org.eclipse.microprofile.config.spi.ConfigSource> getConfigSources() {
        return delegate().getConfigSources();
    }

    @Override
    public <T> Optional<org.eclipse.microprofile.config.spi.Converter<T>> getConverter(Class<T> forType) {
        return delegate().getConverter(forType);
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        return delegate().unwrap(type);
    }

    @Override
    public Instant timestamp() {
        return hdelegate.get().timestamp();
    }

    @Override
    public Key key() {
        return hdelegate.get().key();
    }

    @Override
    public Config root() {
        return hdelegate.get().root();
    }

    @Override
    public Config get(Key key) {
        return hdelegate.get().get(key);
    }

    @Override
    public Config detach() {
        return hdelegate.get().detach();
    }

    @Override
    public Type type() {
        return hdelegate.get().type();
    }

    @Override
    public boolean hasValue() {
        return hdelegate.get().hasValue();
    }

    @Override
    public Stream<Config> traverse(Predicate<Config> predicate) {
        return hdelegate.get().traverse(predicate);
    }

    @Override
    public <T> T convert(Class<T> type, String value) throws ConfigMappingException {
        return hdelegate.get().convert(type, value);
    }

    @Override
    public ConfigMapper mapper() {
        return hdelegate.get().mapper();
    }

    @Override
    public <T> ConfigValue<T> as(GenericType<T> genericType) {
        return hdelegate.get().as(genericType);
    }

    @Override
    public <T> ConfigValue<T> as(Class<T> type) {
        return hdelegate.get().as(type);
    }

    @Override
    public <T> ConfigValue<T> as(Function<Config, T> mapper) {
        return hdelegate.get().as(mapper);
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException {
        return hdelegate.get().asList(type);
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
        return hdelegate.get().asList(mapper);
    }

    @Override
    public ConfigValue<List<Config>> asNodeList() throws ConfigMappingException {
        return hdelegate.get().asNodeList();
    }

    @Override
    public ConfigValue<Map<String, String>> asMap() throws MissingValueException {
        return hdelegate.get().asMap();
    }

    /**
     * Refresh the cached property names for the current delegate.
     */
    void refresh() {
        org.eclipse.microprofile.config.Config delegate = delegate();
        if (delegate != null) {
            cache.computeIfPresent(delegate, (k, v) -> {
                List<String> names = new ArrayList<>();
                for (String name : k.getPropertyNames()) {
                    names.add(name);
                }
                return names;
            });
        }
    }

    private List<String> propertyNames(org.eclipse.microprofile.config.Config config) {
        List<String> names = new ArrayList<>();
        for (String name : config.getPropertyNames()) {
            names.add(name);
        }
        return names;
    }

    private Config delegate0() {
        return Config.just((ConfigSource & LazyConfigSource) key -> {
            org.eclipse.microprofile.config.Config delegate = delegate();
            if (delegate != null) {
                String value = delegate.getConfigValue(key).getValue();
                if (value != null) {
                    // simple value
                    return Optional.of(ConfigNode.ValueNode.create(value));
                }
                // complex value
                List<String> propertyNames = cache.computeIfAbsent(delegate, this::propertyNames);
                ConfigNode.ObjectNode.Builder builder = ConfigNode.ObjectNode.builder();
                boolean hasEntries = false;
                for (String name : propertyNames) {
                    if (name.startsWith(key + ".")) {
                        String k = name.substring(key.length() + 1);
                        String v = delegate.getConfigValue(name).getValue();
                        builder.addValue(k, v);
                        hasEntries = true;
                    }
                }
                if (hasEntries) {
                    return Optional.of(builder.build());
                }
            }
            return Optional.empty();
        });
    }
}
