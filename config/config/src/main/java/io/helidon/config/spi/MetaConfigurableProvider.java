/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;

import io.helidon.config.Config;

/**
 * Configurable object of config that can be loaded using a Java service loader.
 * This is used by {@link io.helidon.config.MetaConfig}.
 */
interface MetaConfigurableProvider<T> {
    /**
     * Return true if this provider supports the type of meta-configurable object.
     * @param type type that is supported (such as {@code file} for {@link io.helidon.config.spi.ConfigSource} meta configurable)
     * @return {@code true} if this provider can create instances of the type
     */
    boolean supports(String type);

    /**
     * Create an instance of the meta configurable using the provided meta configuration.
     *
     * @param type type of the meta configurable
     * @param metaConfig meta configuration
     * @return meta configurable configured from the metaConfig
     */
    T create(String type, Config metaConfig);

    /**
     * Return a set of supported types. Used for error handling.
     *
     * @return a set of types supported by this provider
     */
    Set<String> supported();
}
