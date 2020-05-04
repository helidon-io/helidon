/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigNode.ValueNode;

/**
 * Implementation of {@link Config} that represents a
 * {@link Type#VALUE single value} node.
 */
class ConfigLeafImpl extends ConfigExistingImpl<ValueNode> {
    private static final Pattern SPLIT_PATTERN = Pattern.compile("(?<!\\\\),");
    private static final Pattern ESCAPED_COMMA_PATTERN = Pattern.compile("\\,", Pattern.LITERAL);

    private final ConfigMapperManager mapperManager;

    ConfigLeafImpl(ConfigKeyImpl prefix,
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
    public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException {
        if (Config.class.equals(type)) {
            throw new ConfigMappingException(key(), "The Config node represents single value.");
        }

        Optional<String> value = value();
        if (value.isEmpty()) {
            return ConfigValues.create(this, Optional::empty, aConfig -> aConfig.asList(type));
        }

        String stringValue = value.get();
        if (stringValue.contains(",")) {
            return ConfigValues.create(this,
                                       () -> {
                                           // the value may be a comma separated string, with optional escape of commas with
                                           // backslash
                                           String[] parts = toArray(stringValue);
                                           List<T> result = new LinkedList<>();
                                           for (String part : parts) {
                                               result.add(mapperManager.map(part, type, name()));
                                           }
                                           return Optional.of(result);
                                       },
                                       aConfig -> aConfig.asList(type));
        } else {
            return ConfigValues.create(this,
                                       () -> Optional.of(List.of(mapperManager.map(stringValue, type, name()))),
                                       aConfig -> aConfig.asList(type));

        }

    }

    @Override
    public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
        Optional<String> value = value();
        if (value.isEmpty()) {
            return ConfigValues.create(this, Optional::empty, aConfig -> aConfig.asList(mapper));
        }

        String stringValue = value.get();
        if (stringValue.contains(",")) {
            return ConfigValues.create(this,
                                       () -> {
                                           // the value may be a comma separated string, with optional escape of commas with
                                           // backslash
                                           String[] parts = toArray(stringValue);
                                           List<T> result = new LinkedList<>();
                                           for (String part : parts) {
                                               result.add(mapper.apply(mapperManager.simpleConfig(name(), part)));
                                           }
                                           return Optional.of(result);
                                       },
                                       aConfig -> aConfig.asList(mapper));
        } else {
            return ConfigValues.create(this,
                                       () -> Optional.of(List.of(mapper.apply(mapperManager.simpleConfig(name(), stringValue)))),
                                       aConfig -> aConfig.asList(mapper));

        }
    }

    static String[] toArray(String stringValue) {
        String[] values = SPLIT_PATTERN.split(stringValue, -1);

        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            values[i] = ESCAPED_COMMA_PATTERN.matcher(value).replaceAll(Matcher.quoteReplacement(","));
        }
        return values;
    }

    @Override
    public Stream<Config> traverse(Predicate<Config> predicate) {
        return Stream.empty();
    }

    @Override
    public String toString() {
        return "[" + realKey() + "] VALUE '" + node().get() + "'";
    }

}
