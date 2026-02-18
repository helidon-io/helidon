/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.security;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.http.Method;
import io.helidon.webserver.WebServer;

/**
 * Configuration of a single path security setup.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.CustomMethods(SecurityConfigSupport.PathConfigCustomMethods.class)
interface PathsConfigBlueprint {
    /**
     * This method is internal only and was used from the builder and will be removed without replacement.
     * You can easily map method from config using {@code config.asString().map(Method::create)}.
     *
     * @param config config instance
     * @return method mapped
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    static Method createMethods(Config config) {
        return config.asString().map(Method::create).orElseThrow();
    }

    /**
     * HTTP methods to match when applying this configured path.
     *
     * @return list of methods to secure
     */
    @Option.Configured
    @Option.Singular
    List<Method> methods();

    /**
     * Path to secure.
     * Uses the same rules as Helidon WebServer.
     *
     * @return path to secure
     */
    @Option.Configured
    String path();

    /**
     * Named listeners that should be secured, defaults to the default listener.
     *
     * @return sockets to secure
     */
    @Option.Configured
    @Option.Default(WebServer.DEFAULT_SOCKET_NAME)
    List<String> sockets();

    /**
     * Security handler configuration for this protected path.
     *
     * @return security handler
     */
    @Option.Configured(merge = true)
    SecurityHandler handler();
}
