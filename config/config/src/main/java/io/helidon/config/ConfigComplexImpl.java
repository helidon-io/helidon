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

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigNode;

/**
 * Implementation of {@link Config} that represents complex (object, list) nodes.
 */
abstract class ConfigComplexImpl<N extends ConfigNode> extends ConfigExistingImpl<N> {

    ConfigComplexImpl(Type type,
                      ConfigKeyImpl prefix,
                      ConfigKeyImpl key,
                      N node,
                      ConfigFilter filter,
                      ConfigFactory factory,
                      ConfigMapperManager mapperManager) {
        super(type, prefix, key, node, filter, factory, mapperManager);
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException {
        return ConfigValues.createList(this,
                                   config -> config.as(type),
                                   config -> config.asList(type));
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
        return ConfigValues.createList(this,
                                   config -> config.as(mapper),
                                   config -> config.asList(mapper));
    }

    @Override
    public final Stream<Config> traverse(Predicate<Config> predicate) {
        return asNodeList()
                .map(list -> list.stream()
                        .filter(predicate)
                        .map(node -> traverseSubNodes(node, predicate))
                        .reduce(Stream.empty(), Stream::concat))
                .orElseThrow(MissingValueException.createSupplier(key()));

    }

    private Stream<Config> traverseSubNodes(Config config, Predicate<Config> predicate) {
        if (config.type().isLeaf()) {
            return Stream.of(config);
        } else {
            return config.asNodeList()
                    .map(list -> list.stream()
                            .filter(predicate)
                            .map(node -> traverseSubNodes(node, predicate))
                            .reduce(Stream.of(config), Stream::concat))
                    .orElseThrow(MissingValueException.createSupplier(key()));
        }
    }

}
