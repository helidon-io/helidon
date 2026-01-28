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

package io.helidon.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.config.spi.ConfigMapper;

/**
 * Configuration that merges two {@code Config} instances, primary and fallback.
 * When a property is not present in the primary configuration, fallback is used.
 * <p>
 * When a key is present in both the {@code primary} and {@code fallback} configurations,
 * the value from the {@code primary} configuration takes precedence.
 * </p>
 */
public class MergedConfig implements Config {

    private final Config primaryDelegate;
    private final Config fallbackDelegate;

    private MergedConfig(Config primary, Config fallback) {
        this.primaryDelegate = primary;
        this.fallbackDelegate = fallback;
    }

    /**
     * Creates a new {@code MergedConfig} that merges the specified configurations.
     *
     * @param primary  the primary configuration, which has precedence in the case of duplicate keys
     * @param fallback the fallback configuration, queried for keys not found in the primary one
     * @return a merged {@code Config} instance
     */
    public static Config create(Config primary, Config fallback) {
        return new MergedConfig(primary, fallback);
    }

    @Override
    public Instant timestamp() {
        if (!primaryDelegate.exists() && fallbackDelegate.exists()) {
            return fallbackDelegate.timestamp();
        }
        return primaryDelegate.timestamp();
    }

    @Override
    public Key key() {
        if (!primaryDelegate.exists() && fallbackDelegate.exists()) {
            return fallbackDelegate.key();
        }
        return primaryDelegate.key();
    }

    @Override
    public Config root() {
        return new MergedConfig(primaryDelegate.root(), fallbackDelegate.root());
    }

    @Override
    public Config get(Key key) {
        return new MergedConfig(primaryDelegate.get(key), fallbackDelegate.get(key));
    }

    @Override
    public Config detach() {
        return new MergedConfig(primaryDelegate.detach(), fallbackDelegate.detach());
    }

    @Override
    public Type type() {
        if (!primaryDelegate.exists() && fallbackDelegate.exists()) {
            return fallbackDelegate.type();
        }
        return primaryDelegate.type();
    }

    @Override
    public boolean hasValue() {
        return primaryDelegate.hasValue() || fallbackDelegate.hasValue();
    }

    /**
     * Traverses over merged configs like {@link io.helidon.config.Config#traverse()}.
     * Every config node exising in both primary and fallback configs is merged.
     *
     * @param predicate predicate evaluated on each visited {@code Config} node
     *                  to continue or stop visiting the node
     * @return stream of deepening depth-first sub nodes
     */
    @Override
    public Stream<Config> traverse(Predicate<Config> predicate) {
        var existingKeys = primaryDelegate.traverse()
                .filter(Config::exists)
                .map(Config::key)
                .collect(Collectors.toSet());
        return Stream.concat(primaryDelegate.traverse(predicate)
                                     .map(c -> {
                                         // If a node exists in fallback config, create a merged node
                                         if (existingKeys.contains(c.key())) {
                                             return new MergedConfig(c, fallbackDelegate.get(c.key()));
                                         }
                                         return c;
                                     }),
                             fallbackDelegate.traverse(predicate)
                                     .filter(c -> !existingKeys.contains(c.key())));
    }

    @Override
    public <T> T convert(Class<T> type, String value) throws ConfigMappingException {
        return primaryDelegate.convert(type, value);
    }

    /**
     * Only mappers from the primary config are considered.
     *
     * @return mapper from primary config
     */
    @Override
    public ConfigMapper mapper() {
        return primaryDelegate.mapper();
    }

    @Override
    public <T> ConfigValue<T> as(GenericType<T> genericType) {
        var primaryValue = primaryDelegate.as(genericType);
        if (primaryValue.isPresent()) {
            return primaryValue;
        }
        var secondaryValue = fallbackDelegate.as(genericType);
        if (secondaryValue.isPresent()) {
            return secondaryValue;
        }
        return primaryValue;
    }

    @Override
    public <T> ConfigValue<T> as(Class<T> type) {
        var primaryValue = primaryDelegate.as(type);
        if (primaryValue.isPresent()) {
            return primaryValue;
        }
        var secondaryValue = fallbackDelegate.as(type);
        if (secondaryValue.isPresent()) {
            return secondaryValue;
        }
        return primaryValue;
    }

    @Override
    public <T> ConfigValue<T> as(Function<Config, T> mapper) {
        var primaryValue = primaryDelegate.as(mapper);
        if (primaryValue.isPresent()) {
            return primaryValue;
        }
        var secondaryValue = fallbackDelegate.as(mapper);
        if (secondaryValue.isPresent()) {
            return secondaryValue;
        }
        return primaryValue;
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException {
        var primaryValue = primaryDelegate.asList(type);
        if (primaryValue.isPresent()) {
            return primaryValue;
        }
        var secondaryValue = fallbackDelegate.asList(type);
        if (secondaryValue.isPresent()) {
            return secondaryValue;
        }
        return primaryValue;
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
        var primaryValue = primaryDelegate.asList(mapper);
        if (primaryValue.isPresent()) {
            return primaryValue;
        }
        var secondaryValue = fallbackDelegate.asList(mapper);
        if (secondaryValue.isPresent()) {
            return secondaryValue;
        }
        return primaryValue;
    }

    @Override
    public ConfigValue<List<Config>> asNodeList() throws ConfigMappingException {
        var primaryValue = primaryDelegate.asNodeList();
        if (primaryValue.isPresent()) {
            return primaryValue;
        }
        var secondaryValue = fallbackDelegate.asNodeList();
        if (secondaryValue.isPresent()) {
            return secondaryValue;
        }
        return primaryValue;
    }

    @Override
    public ConfigValue<Map<String, String>> asMap() throws MissingValueException {
        var primaryValue = primaryDelegate.asMap();
        if (primaryValue.isPresent()) {
            return primaryValue;
        }
        var secondaryValue = fallbackDelegate.asMap();
        if (secondaryValue.isPresent()) {
            return secondaryValue;
        }
        return primaryValue;
    }
}
