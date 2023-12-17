/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.configdriven.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

import io.helidon.common.config.Config;
import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;

/**
 * Used from generated code.
 * Represents the required information to handle config beans, either from
 * {@link io.helidon.inject.configdriven.service.ConfigBean}
 * annotation, or from other means.
 *
 * @param <T> type of the config bean
 */
public interface ConfigBeanFactory<T> {
    /**
     * Create instances from configuration.
     *
     * @param config configuration to use (root configuration instance)
     * @return list of config bean instances
     */
    List<NamedInstance<T>> createConfigBeans(Config config);

    /**
     * Type of config bean.
     *
     * @return bean type
     */
    TypeName configBeanType();

    /**
     * Whether the discovered config beans drive activation of their associated service.
     *
     * @return whether the config beans drive activation
     */
    boolean drivesActivation();

    /**
     * Creates a list of named instances from the provided configuration instance (must exist).
     *
     * @param config      configuration to analyze and use to create beans
     * @param wantDefault whether a default instance is wanted (will be created only based on default values)
     * @param factory     factory to create an instance from a config node
     * @return list of created named instances
     */
    default List<NamedInstance<T>> createRepeatableBeans(Config config,
                                                         boolean wantDefault,
                                                         Function<Config, T> factory) {

        Objects.requireNonNull(config, "Config must not be null");
        Objects.requireNonNull(factory, "Config bean factory must not be null");

        Map<String, NamedInstance<T>> instances = new TreeMap<>(NamedInstance.nameComparator());

        List<Config> childNodes = config.asNodeList().orElseGet(List::of);
        boolean isList = config.isList();

        for (Config childNode : childNodes) {
            String name = childNode.name(); // by default use the current node name - for lists, this would be the index
            name = isList ? childNode.get("name").asString().orElse(name) : name; // use "name" node if list and present
            instances.put(name, new NamedInstance<>(factory.apply(childNode), name));
        }

        if (wantDefault && !instances.containsKey(Injection.Named.DEFAULT_NAME)) {
            instances.put(Injection.Named.DEFAULT_NAME,
                          new NamedInstance<>(factory.apply(Config.empty()), Injection.Named.DEFAULT_NAME));
        }

        return List.copyOf(instances.values());
    }
}
