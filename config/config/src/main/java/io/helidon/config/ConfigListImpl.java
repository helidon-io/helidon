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
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigNode.ListNode;

/**
 * Implementation of {@link Config} that represents a list of nodes.
 */
class ConfigListImpl extends ConfigComplexImpl<ListNode> {

    ConfigListImpl(ConfigKeyImpl prefix,
                   ConfigKeyImpl key,
                   ListNode listNode,
                   ConfigFilter filter,
                   ConfigFactory factory,
                   ConfigMapperManager mapperManager) {
        super(Type.LIST, prefix, key, listNode, filter, factory, mapperManager);
    }

    @Override
    public ConfigValue<List<Config>> asNodeList() throws ConfigMappingException {
        return ConfigValues.create(this,
                                   () -> Optional.of(
                                           IntStream.range(0, node().size())
                                                   .boxed()
                                                   .map(index -> get(Integer.toString(index)))
                                                   .collect(Collectors.toList())),
                                   Config::asNodeList);
    }


    @Override
    public String toString() {
        return "[" + realKey() + "] LIST (elements: " + node().size() + ")";
    }

}
