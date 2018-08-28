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
import java.util.stream.Collectors;

import io.helidon.config.internal.ConfigKeyImpl;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * Implementation of {@link Config} that represents {@link Config.Type#OBJECT branch} node.
 */
class ConfigObjectImpl extends ConfigComplexImpl<ObjectNode> {

    ConfigObjectImpl(ConfigKeyImpl prefix,
                     ConfigKeyImpl key,
                     ObjectNode objectNode,
                     ConfigFilter filter,
                     ConfigFactory factory,
                     ConfigMapperManager mapperManager) {
        super(Type.OBJECT, prefix, key, objectNode, filter, factory, mapperManager);
    }

    @Override
    public Optional<List<Config>> nodeList() {
        return Optional.of(
                getNode().entrySet()
                        .stream()
                        .map(e -> get(e.getKey()))
                        .collect(Collectors.toList())
        );
    }

    @Override
    public String toString() {
        return "[" + realKey() + "] OBJECT (members: " + getNode().size() + ")";
    }

}
