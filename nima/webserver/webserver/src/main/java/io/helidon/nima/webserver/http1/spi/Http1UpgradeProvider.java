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

package io.helidon.nima.webserver.http1.spi;

import java.util.Set;
import java.util.function.Function;

import io.helidon.common.config.Config;

/**
 * {@link java.util.ServiceLoader} provider interface for HTTP/1.1 connection upgrade provider.
 * This interface serves as {@link Http1Upgrader} builder
 * which receives requested configuration nodes from the server configuration when server builder
 * is running.
 */
public interface Http1UpgradeProvider {

    /**
     * Provider's specific configuration node name.
     *
     * @return name of the node to request
     */
    Set<String> configKeys();

    /**
     * Creates an instance of HTTP/HTTP/1.1 connection upgrader.
     *
     * @param config {@link io.helidon.common.config.Config;} configuration function that provides a value for any {@link #configKeys()}
     * @return new server HTTP/1.1 connection upgrade selector
     */
    Http1Upgrader create(Function<String, Config> config);

}
