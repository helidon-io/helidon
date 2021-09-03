/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.function.BiConsumer;

import io.helidon.config.spi.ConfigNode;

/**
 * The runtime of a config source. For a single {@link Config}, there is one source runtime for each configured
 * config source.
 */
public interface ConfigSourceRuntime {
    /**
     * Change support for a runtime.
     *
     * @param change change listener
     */
    void onChange(BiConsumer<String, ConfigNode> change);

    /**
     * Load the config source if it is eager (such as {@link io.helidon.config.spi.ParsableSource} or
     *  {@link io.helidon.config.spi.NodeConfigSource}.
     * <p>
     * For {@link io.helidon.config.spi.LazyConfigSource}, this
     * method may return an empty optional (if no key was yet requested), or a node with currently known keys and values.
     *
     * @return loaded data
     */
    Optional<ConfigNode.ObjectNode> load();

    /**
     * Get a single config node based on the key.
     * Use this method if you are interested in a specific key, as it works both for eager and lazy config sources.
     *
     * @param key key of the node to retrieve
     * @return value on the key, or empty if not present
     */
    Optional<ConfigNode> node(String key);

    /**
     * Description of the underlying config source.
     * @return description of the source
     */
    String description();

    /**
     * If a config source is lazy, its {@link #load()} method always returns empty and you must use
     * {@link #node(String)} methods to retrieve its values.
     *
     * @return {@code true} if the underlying config source cannot load whole configuration tree
     */
    boolean isLazy();
}
