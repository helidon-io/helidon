/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.cors;

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import static io.helidon.webserver.cors.Cors.ALLOW_ALL;

/**
 * Configuration of CORS for a specific path.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.CustomMethods(CorsConfigSupport.PathCustomMethods.class)
@Prototype.Annotated({"io.helidon.service.registry.Service.Contract",
        "java.lang.SuppressWarnings(\"removal\")"
})
interface CorsPathConfigBlueprint {
    /**
     * Path pattern to apply this configuration for.
     * Note that paths are checked in sequence, and the first path that matches the request
     * will be used to configure CORS.
     * <p>
     * Always configure the most restrictive rules first.
     *
     * @return path pattern as understood by WebServer routing
     */
    @Option.Configured
    String pathPattern();

    /**
     * Whether this CORS configuration should be enabled or not.
     * If disabled, this configuration will be ignored, and the next path will be checked.
     *
     * @return whether this configuration is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Set of allowed origins, defaults to all.
     * <p>
     * If not empty, this will be used with {@value io.helidon.http.HeaderNames#ACCESS_CONTROL_ALLOW_ORIGIN_NAME} header.
     *
     * @return allowed origins
     */
    @Option.Singular
    @Option.Configured
    @Option.Default(ALLOW_ALL)
    Set<String> allowOrigins();

    /**
     * Set of allowed headers, defaults to all.
     * <p>
     * If not empty, this will be used in {@value io.helidon.http.HeaderNames#ACCESS_CONTROL_ALLOW_HEADERS_NAME} header.
     *
     * @return allowed headers
     */
    @Option.Singular
    @Option.Configured
    @Option.Default(ALLOW_ALL)
    Set<String> allowHeaders();

    /**
     * Set of allowed methods, defaults to all.
     *
     * @return allowed methods
     */
    @Option.Singular
    @Option.Configured
    @Option.Default(ALLOW_ALL)
    Set<String> allowMethods();

    /**
     * Set of exposed headers, defaults to none.
     * <p>
     * If not empty, this will be used in {@value io.helidon.http.HeaderNames#ACCESS_CONTROL_EXPOSE_HEADERS_NAME} header.
     *
     * @return exposed headers
     */
    @Option.Singular
    @Option.Configured
    Set<String> exposeHeaders();

    /**
     * Whether to allow credentials.
     * <p>
     * If enabled, this will be used in {@value io.helidon.http.HeaderNames#ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME} header.
     *
     * @return whether to allow credentials, defaults to {@code false}
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean allowCredentials();

    /**
     * Max age in seconds.
     * <p>
     * This value will be used in {@value io.helidon.http.HeaderNames#ACCESS_CONTROL_MAX_AGE_NAME} header.
     *
     * @return max age in seconds
     */
    @Option.Configured
    @Option.DefaultLong(Cors.DEFAULT_MAX_AGE)
    long maxAge();
}
