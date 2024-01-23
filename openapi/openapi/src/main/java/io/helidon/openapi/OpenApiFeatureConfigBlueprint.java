/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.openapi;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.openapi.spi.OpenApiManagerProvider;
import io.helidon.openapi.spi.OpenApiServiceProvider;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * {@link OpenApiFeature} prototype.
 */
@Prototype.Blueprint
@Prototype.Configured("openapi")
@Prototype.Provides(ServerFeatureProvider.class)
interface OpenApiFeatureConfigBlueprint extends Prototype.Factory<OpenApiFeature> {
    /**
     * Weight of the OpenAPI feature. This is quite low, to be registered after routing.
     * {@value io.helidon.openapi.OpenApiFeature#WEIGHT}.
     *
     * @return weight of the feature
     */
    @Option.DefaultDouble(OpenApiFeature.WEIGHT)
    @Option.Configured
    double weight();

    /**
     * Sets whether the feature should be enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    @Option.Configured("enabled")
    @Option.DefaultBoolean(true)
    boolean isEnabled();

    /**
     * Web context path for the OpenAPI endpoint.
     *
     * @return webContext to use
     */
    @Option.Configured
    @Option.Default("/openapi")
    String webContext();

    /**
     * Path of the static OpenAPI document file. Default types are `json`, `yaml`, and `yml`.
     *
     * @return location of the static OpenAPI document file
     */
    @Option.Configured
    Optional<String> staticFile();

    /**
     * CORS config.
     *
     * @return CORS config
     */
    @Option.Configured
    Optional<CrossOriginConfig> cors();

    /**
     * OpenAPI services.
     *
     * @return the OpenAPI services
     */
    @Option.Configured
    @Option.Provider(OpenApiServiceProvider.class)
    @Option.Singular
    List<OpenApiService> services();

    /**
     * OpenAPI manager.
     *
     * @return the OpenAPI manager
     */
    @Option.Configured
    @Option.Provider(value = OpenApiManagerProvider.class, discoverServices = false)
    Optional<OpenApiManager<?>> manager();

    /**
     * Whether to allow anybody to access the endpoint.
     *
     * @return whether to permit access to metrics endpoint to anybody, defaults to {@code true}
     * @see #roles()
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean permitAll();

    /**
     * Hints for role names the user is expected to be in.
     *
     * @return list of hints
     */
    @Option.Configured
    @Option.Default("openapi")
    List<String> roles();

    /**
     * Name of this instance.
     *
     * @return instance name, used when discovered from configuration
     */
    @Option.Default(OpenApiFeature.OPENAPI_ID)
    String name();

    /**
     * List of sockets to register this feature on. If empty, it would get registered on all sockets.
     *
     * @return socket names to register on, defaults to empty (all available sockets)
     */
    @Option.Configured
    @Option.Singular
    Set<String> sockets();
}
