/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * Configuration of CORS feature.
 */
@Prototype.Blueprint(decorator = CorsConfigSupport.BuilderDecorator.class)
@Prototype.Configured(CorsFeature.CORS_ID)
@Prototype.Provides(ServerFeatureProvider.class)
@Prototype.IncludeDefaultMethods
@Prototype.Annotated("java.lang.SuppressWarnings(\"removal\")")
interface CorsConfigBlueprint extends Prototype.Factory<CorsFeature>, Weighted {

    /**
     * Weight of the CORS feature. As it is used by other features, the default is quite high:
     * {@value CorsFeature#WEIGHT}.
     *
     * @return weight of the feature
     */
    @SuppressWarnings("deprecation")
    @Option.DefaultDouble(CorsFeature.WEIGHT)
    @Option.Configured
    double weight();

    /**
     * List of sockets to register this feature on. If empty, it would get registered on all sockets.
     *
     * @return socket names to register on, defaults to empty (all available sockets)
     */
    @Option.Configured
    @Option.Singular
    Set<String> sockets();

    /**
     * Name of this instance.
     *
     * @return instance name
     */
    @Option.Default(CorsFeature.CORS_ID)
    String name();

    /**
     * This feature can be disabled.
     * This feature is automatically enabled if there is at least one {@link #paths()} defined.
     *
     * @return whether the feature is enabled
     */
    @Option.Configured
    @Option.Required
    boolean enabled();

    /**
     * Per path configuration.
     * Default path is added, unless {@link #addDefaults()} is set to {@code false}.
     *
     * @return per path configurations
     */
    @Option.Singular
    @Option.Configured
    @Option.RegistryService
    default List<CorsPathConfig> paths() {
        return List.of();
    }

    /**
     * Whether to add a default path configuration, that matches all paths, {@code GET, HEAD, POST} methods, and allows
     * all origins, methods, and headers. This is always added as a last path.
     *
     * @return whether to add defaults as the last path, defaults to {@code true}
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    default boolean addDefaults() {
        return true;
    }

    /**
     * Access to config that was used to create this feature.
     *
     * @return configuration
     * @deprecated this method will be removed without a replacement, path based configuration is now handled by
     *         {@link #paths()}
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "4.4.0")
    Optional<Config> config();
}
