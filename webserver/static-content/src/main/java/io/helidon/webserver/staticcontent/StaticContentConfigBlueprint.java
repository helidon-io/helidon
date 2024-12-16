/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.staticcontent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.media.type.MediaType;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * Configuration of Static content feature.
 * <p>
 * Minimal example configuring a single classpath resource (properties):
 * <pre>
 * server.features.static-content.classpath.0.context=/static
 * server.features.static-content.classpath.0.location=/web
 * </pre>
 * and using yaml:
 * <pre>
 * server:
 *   features:
 *     static-content:
 *       classpath:
 *         - context: "/static"
 *           location: "/web"
 * </pre>
 */
@Prototype.Blueprint
@Prototype.Configured(value = StaticContentFeature.STATIC_CONTENT_ID, root = false)
@Prototype.Provides(ServerFeatureProvider.class)
@Prototype.CustomMethods(StaticContentConfigSupport.StaticContentMethods.class)
interface StaticContentConfigBlueprint extends Prototype.Factory<StaticContentFeature> {
    /**
     * Whether this feature is enabled, defaults to {@code true}.
     *
     * @return whether this feature is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Weight of the static content feature. Defaults to
     * {@value StaticContentFeature#WEIGHT}.
     *
     * @return weight of the feature
     */
    @Option.DefaultDouble(StaticContentFeature.WEIGHT)
    @Option.Configured
    double weight();


    /**
     * Name of this instance.
     *
     * @return instance name
     */
    @Option.Default(StaticContentFeature.STATIC_CONTENT_ID)
    String name();

    /**
     * Memory cache shared by the whole feature.
     * If not configured, files are not cached in memory (except for explicitly marked files/resources in each section).
     *
     * @return memory cache, if configured
     */
    @Option.Configured
    Optional<MemoryCache> memoryCache();

    /**
     * Temporary storage to use across all classpath handlers.
     * If not defined, a default one will be created.
     *
     * @return temporary storage
     */
    @Option.Configured
    Optional<TemporaryStorage> temporaryStorage();

    /**
     * List of classpath based static content handlers.
     *
     * @return classpath handlers
     */
    @Option.Configured
    @Option.Singular
    List<ClasspathHandlerConfig> classpath();

    /**
     * List of file system based static content handlers.
     *
     * @return path handlers
     */
    @Option.Configured
    @Option.Singular
    List<FileSystemHandlerConfig> path();

    /**
     * Maps a filename extension to the response content type.
     * To have a system-wide configuration, you can use the service loader SPI
     * {@link io.helidon.common.media.type.spi.MediaTypeDetector}.
     * <p>
     * This method can override {@link io.helidon.common.media.type.MediaTypes} detection
     * for a specific static content handler.
     * <p>
     * Handler will use a union of configuration defined here, and on the handler
     * here when used from configuration.
     *
     * @return map of file extensions to associated media type
     */
    @Option.Configured
    @Option.Singular
    Map<String, MediaType> contentTypes();

    /**
     * Welcome-file name. Default for all handlers.
     * By default, we do not serve default files.
     *
     * @return welcome-file name, such as {@code index.html}
     */
    @Option.Configured
    Optional<String> welcome();

    /**
     * Sockets names (listeners) that will host static content handlers, defaults to all configured sockets.
     * Default socket name is {@code @default}.
     * <p>
     * This configures defaults for all handlers.
     *
     * @return sockets to register this handler on
     */
    @Option.Configured
    @Option.Singular
    Set<String> sockets();

}
