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

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.config.internal.ConfigKeyImpl;
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
    public final <T> Optional<List<T>> asOptionalList(Class<? extends T> type) throws ConfigMappingException {
        try {
            return nodeList()
                    .map(list -> list.stream()
                            .map(config -> config.as(type))
                            .collect(Collectors.toList()));
        } catch (ConfigMappingException ex) {
            throw new ConfigMappingException(key(),
                                             "Error to map complex node item. " + ex.getLocalizedMessage(),
                                             ex);
        }
    }

    @Override
    public final Stream<Config> traverse(Predicate<Config> predicate) {
        return asNodeList().stream()
                .filter(predicate)
                .map(node -> traverseSubNodes(node, predicate))
                .reduce(Stream.empty(), Stream::concat);
    }

    private static Stream<Config> traverseSubNodes(Config config, Predicate<Config> predicate) {
        if (config.type().isLeaf()) {
            return Stream.of(config);
        } else {
            return config.asNodeList().stream()
                    .filter(predicate)
                    .map(node -> traverseSubNodes(node, predicate))
                    .reduce(Stream.of(config), Stream::concat);
        }
    }

}
