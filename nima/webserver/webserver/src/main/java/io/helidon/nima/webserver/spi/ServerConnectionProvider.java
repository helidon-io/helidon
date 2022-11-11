/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.spi;

import java.util.function.Function;

import io.helidon.config.Config;

/**
 * {@link java.util.ServiceLoader} provider interface for server connection providers.
 * This interface serves as {@link ServerConnectionSelector} builder
 * which receives requested configuration nodes from the server configuration when server builder
 * is running.
 */
public interface ServerConnectionProvider {
    /**
     * Provider's specific configuration nodes names.
     *
     * @return names of the nodes to request
     */
    Iterable<String> configKeys();

    /**
     * Creates an instance of server connection selector.
     *
     * @param configs configuration for each {@link #configKeys()}, the config may be empty, but it will be present
     *                for each value
     * @return new server connection selector
     */
    ServerConnectionSelector create(Function<String, Config> configs);

}
