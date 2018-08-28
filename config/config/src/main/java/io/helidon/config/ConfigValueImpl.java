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

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.internal.ConfigKeyImpl;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigNode.ValueNode;

/**
 * Implementation of {@link Config} that represents a
 * {@link Config.Type#VALUE single value} node.
 */
class ConfigValueImpl extends ConfigExistingImpl<ValueNode> {
    private static final Pattern SPLIT_PATTERN = Pattern.compile("(?<!\\\\),");

    private final ConfigMapperManager mapperManager;

    ConfigValueImpl(ConfigKeyImpl prefix,
                    ConfigKeyImpl key,
                    ValueNode valueNode,
                    ConfigFilter filter,
                    ConfigFactory factory,
                    ConfigMapperManager mapperManager) {
        super(Type.VALUE, prefix, key, valueNode, filter, factory, mapperManager);
        this.mapperManager = mapperManager;

        Objects.requireNonNull(filter, "filter argument is null.");

    }

    @Override
    public Optional<List<Config>> nodeList() {
        throw new ConfigMappingException(key(), "The Config node represents single value.");
    }

    @Override
    public final <T> Optional<List<T>> asOptionalList(Class<? extends T> type) throws ConfigMappingException {
        if (type == Config.class) {
            throw new ConfigMappingException(key(), "The Config node represents single value.");
        }

        Optional<String> value = value();
        if (!value.isPresent()) {
            return Optional.empty();
        }

        String stringValue = value.get();

        if (stringValue.contains(",")) {
            // the value may be a comma separated string, with optional escape of commas with backslash
            String[] parts = toArray(stringValue);

            List<T> result = new LinkedList<>();
            for (String part : parts) {
                result.add(mapperManager.map(type, createConfig(this, part)));
            }
            return Optional.of(result);
        } else {
            return Optional.of(CollectionsHelper.listOf(mapperManager.map(type, createConfig(this, stringValue))));
        }
    }

    static String[] toArray(String stringValue) {
        String[] values = SPLIT_PATTERN.split(stringValue, -1);

        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            values[i] = value.replace("\\,", ",");
        }
        return values;
    }

    private Config createConfig(ConfigValueImpl configValue, String stringValue) {
        return new Config() {
            @Override
            public Instant timestamp() {
                return null;
            }

            @Override
            public Key key() {
                return configValue.key();
            }

            @Override
            public Config get(Key key) {
                return null;
            }

            @Override
            public Config detach() {
                return null;
            }

            @Override
            public Type type() {
                return Type.VALUE;
            }

            @Override
            public boolean hasValue() {
                return true;
            }

            @Override
            public Stream<Config> traverse(Predicate<Config> predicate) {
                return null;
            }

            @Override
            public Optional<String> value() throws ConfigMappingException {
                return Optional.of(stringValue);
            }

            @Override
            public Optional<List<Config>> nodeList() throws ConfigMappingException {
                return Optional.empty();
            }

            @Override
            public Optional<Map<String, String>> asOptionalMap() {
                return Optional.empty();
            }

            @Override
            public <T> Optional<T> asOptional(Class<? extends T> type) throws ConfigMappingException {
                return Optional.empty();
            }

            @Override
            public <T> Optional<List<T>> asOptionalList(Class<? extends T> type) throws ConfigMappingException {
                return Optional.empty();
            }
        };
    }

    @Override
    public Stream<Config> traverse(Predicate<Config> predicate) {
        return Stream.empty();
    }

    @Override
    public String toString() {
        return "[" + realKey() + "] VALUE '" + getNode().get() + "'";
    }

}
