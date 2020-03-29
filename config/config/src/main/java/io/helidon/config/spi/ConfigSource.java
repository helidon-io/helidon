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

package io.helidon.config.spi;

import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

/**
 * {@link Source} of configuration.
 *
 * There is a set of interfaces that you can implement to support various aspect of a config source.
 * <p>
 * Config sources by "eagerness" of loading of data. The config source either loads all data when asked to (and this is
 * the preferred way for Helidon Config), or loads each key separately.
 * <ul>
 *     <li>{@link io.helidon.config.spi.ParsableSource} - an eager source that provides an input stream with data to be
 *          parsed based on its content type</li>
 *     <li>{@link io.helidon.config.spi.NodeConfigSource} - an eager source that provides a
 *     {@link io.helidon.config.spi.ConfigNode.ObjectNode} with its configuration tree</li>
 *     <li>{@link io.helidon.config.spi.LazyConfigSource} - a lazy source that provides values key by key</li>
 * </ul>
 *
 * <p>
 * Config sources by "mutability" of data. The config source may be immutable (default), or provide a means for
 * change support
 * <ul>
 *     <li>{@link io.helidon.config.spi.PollableSource} - a source that can generate a "stamp" of the data that can
 *      be used to check for possible changes in underlying data (such as file digest, a timestamp, data version)</li>
 *     <li>{@link io.helidon.config.spi.WatchableSource} - a source that is based on data that have a specific change
 *     watcher that can notify the config framework of changes without the need for regular polling (such as file)</li>
 *     <li>{@link io.helidon.config.spi.EventConfigSource} - a source that can directly notify about changes</li>
 * </ul>
 *
 * Each of the interfaces mentioned above also has an inner class with a builder interface, if any configuration is needed.
 * The {@link io.helidon.config.AbstractConfigSource} implements a super set of all the configuration methods from all interfaces
 * as {@code protected}, so you can use them in your implementation.
 * <p>
 * {@link io.helidon.config.AbstractConfigSourceBuilder} implements the configuration methods, so you can simply extend it with
 * your builder and implement all the builders that make sense for your config source type.
 *
 *
 * @see Config.Builder#sources(Supplier)
 * @see Config.Builder#sources(Supplier, Supplier)
 * @see Config.Builder#sources(Supplier, Supplier, Supplier)
 * @see Config.Builder#sources(java.util.List)
 * @see io.helidon.config.AbstractConfigSource
 * @see ConfigSources ConfigSources - access built-in implementations.
 */
public interface ConfigSource extends Supplier<ConfigSource>, Source {
    @Override
    default ConfigSource get() {
        return this;
    }

    /**
     * Initialize the config source with a {@link ConfigContext}.
     * <p>
     * The method is executed during {@link Config} bootstrapping by {@link Config.Builder}.
     *
     * @param context a config context
     */
    default void init(ConfigContext context) {
    }
}
