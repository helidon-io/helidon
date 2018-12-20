/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.config.internal.ConfigKeyImpl;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigNode;

/**
 * Implementation of {@link Config} that represents NOT {@link Type#MISSING missing} node.
 *
 * @param <N> type of node
 */
abstract class ConfigExistingImpl<N extends ConfigNode> extends AbstractConfigImpl {

    private final N node;
    private final ConfigMapperManager mapperManager;
    private final ConfigFilter filter;

    ConfigExistingImpl(Type type,
                       ConfigKeyImpl prefix,
                       ConfigKeyImpl key,
                       N node,
                       ConfigFilter filter,
                       ConfigFactory factory,
                       ConfigMapperManager mapperManager) {
        super(type, prefix, key, factory, mapperManager);
        this.filter = filter;

        Objects.requireNonNull(node, "node argument is null.");
        Objects.requireNonNull(mapperManager, "mapperManager argument is null.");

        this.node = node;
        this.mapperManager = mapperManager;
    }

    @Override
    public final Optional<String> value() throws ConfigMappingException {
        String value = node().get();
        if (null != value) {
            return Optional.ofNullable(filter.apply(realKey(), value));
        } else {
            // even if this is a tree node, we want to return empty, as this node does not have a value
            // and that is a good state (as complex nodes are allowed to have a direct value)
            return Optional.empty();
        }
    }

    @Override
    public boolean hasValue() {
        return null != node().get();
    }

    @Override
    public <T> ConfigValue<T> as(GenericType<T> genericType) {
        return ConfigValues.create(this, genericType, mapperManager);
    }

    @Override
    public <T> ConfigValue<T> as(Class<T> type) {
        return ConfigValues.create(this, type, mapperManager);
    }

    @Override
    public <T> ConfigValue<T> as(Function<Config, T> mapper) {
        return ConfigValues.create(this, mapper);
    }

    @Override
    public ConfigValue<Map<String, String>> asMap() {
        return ConfigValues.createMap(this, mapperManager);
    }

    protected final N node() {
        return node;
    }


}
