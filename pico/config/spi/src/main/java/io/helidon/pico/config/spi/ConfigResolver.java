/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.config.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;

/**
 * Helps resolve resolving / mapping primitive and higher-level config bean types.
 */
public interface ConfigResolver {

    /**
     * Resolves a config key attribute using config (if available) for a particular config bean method/attribute.
     *
     * @param config                the optional config
     * @param configKey             the config key (relative to this config)
     * @param attrName              the attribute name
     * @param type                  the attribute type
     * @param configBeanType        the config bean type
     * @param metaAttributes        the meta attributes for every config
     * @param <T> the type being requested
     * @return the resolved value
     */
    <T> Optional<T> of(Config config,
                       String configKey,
                       String attrName,
                       Class<T> type,
                       Class<?> configBeanType,
                       Map<String, Map<String, Object>> metaAttributes);

    /**
     * Resolves a config key attribute using config (if available) for a particular config bean method/attribute of collection type.
     *
     * @param config                the optional config
     * @param configKey             the config key (relative to this config)
     * @param attrName              the attribute name
     * @param type                  the attribute value type (e.g., Optional<String> -> Optional)
     * @param componentType         the attribute value component type (e.g., Optional<String> -> String)
     * @param configBeanType        the config bean type
     * @param metaAttributes        the meta attributes for every config
     * @param <T> the type being requested
     * @param <V> the component type of type being requested
     * @return the resolved value
     */
    <T, V> Optional<Collection<V>> ofCollection(Config config,
                                                String configKey,
                                                String attrName,
                                                Class<T> type,
                                                Class<V> componentType,
                                                Class<?> configBeanType,
                                                Map<String, Map<String, Object>> metaAttributes);

    /**
     * Resolves a config key attribute using config (if available) for a particular config bean method/attribute of map type.
     *
     * @param config                the optional config
     * @param configKey             the config key (relative to this config)
     * @param attrName              the attribute name
     * @param keyType               the attribute key type (e.g., Optional<String> -> Optional)
     * @param keyComponentType      the attribute key component type (e.g., Optional<String> -> String)
     * @param type                  the attribute value type (e.g., Optional<String> -> Optional)
     * @param componentType         the attribute value component type (e.g., Optional<String> -> String)
     * @param configBeanType        the config bean type
     * @param metaAttributes        the meta attributes for every config
     * @param <K> the key type being requested
     * @param <T> the type being requested
     * @param <V> the component type of type being requested
     * @return the resolved value
     */
    <K, T, V> Optional<Map<K, V>> ofMap(Config config,
                                          String configKey,
                                          String attrName,
                                          Class<K> keyType,
                                          Class<?> keyComponentType,
                                          Class<T> type,
                                          Class<V> componentType,
                                          Class<?> configBeanType,
                                          Map<String, Map<String, Object>> metaAttributes);

}
