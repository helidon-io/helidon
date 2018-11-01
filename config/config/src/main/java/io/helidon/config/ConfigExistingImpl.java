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
        super(type, prefix, key, factory);
        this.filter = filter;

        Objects.requireNonNull(node, "node argument is null.");
        Objects.requireNonNull(mapperManager, "mapperManager argument is null.");

        this.node = node;
        this.mapperManager = mapperManager;
    }

    @Override
    public final Optional<String> value() throws ConfigMappingException {
        String value = getNode().get();
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
        return null != getNode().get();
    }

    @Override
    public final <T> Optional<T> asOptional(Class<? extends T> type) throws ConfigMappingException {
        try {
            return Optional.ofNullable(mapperManager.map(type, this));
        } catch (MissingValueException ignored) {
            // if we are missing a value, we return empty, should not propagate it further!
            return Optional.empty();
        }
    }

    @Override
    public final Optional<Map<String, String>> asOptionalMap() {
        Map map = mapperManager.map(Map.class, this);
        if (map instanceof ConfigMappers.StringMap) {
            return Optional.of((ConfigMappers.StringMap) map);
        }
        return Optional.of(new ConfigMappers.StringMap(map));
    }

    protected final N getNode() {
        return node;
    }

}
