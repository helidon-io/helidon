/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * {@link Source} of configuration.
 *
 * @see Config.Builder#sources(Supplier)
 * @see Config.Builder#sources(Supplier, Supplier)
 * @see Config.Builder#sources(Supplier, Supplier, Supplier)
 * @see Config.Builder#sources(java.util.List)
 * @see AbstractConfigSource
 * @see AbstractParsableConfigSource
 * @see ConfigSources ConfigSources - access built-in implementations.
 */
@FunctionalInterface
public interface ConfigSource extends Source<ObjectNode>, Supplier<ConfigSource> {
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
