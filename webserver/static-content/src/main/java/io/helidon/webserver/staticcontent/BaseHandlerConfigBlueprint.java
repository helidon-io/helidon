/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.media.type.MediaType;

/**
 * Configuration of static content handlers that is common for classpath and file system based handlers.
 */
@Prototype.Blueprint(createEmptyPublic = false, createFromConfigPublic = false)
@Prototype.Configured
@Prototype.CustomMethods(StaticContentConfigSupport.BaseMethods.class)
interface BaseHandlerConfigBlueprint {
    /**
     * Whether this handle is enabled, defaults to {@code true}.
     *
     * @return whether enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Context that will serve this handler's static resources, defaults to {@code /}.
     *
     * @return context under webserver
     */
    @Option.Configured
    @Option.Default("/")
    String context();

    /**
     * Sockets names (listeners) that will host this static content handler, defaults to all configured sockets.
     * Default socket name is {@code @default}.
     *
     * @return sockets to register this handler on
     */
    @Option.Configured
    @Option.Singular
    Set<String> sockets();

    /**
     * Welcome-file name. In case a directory is requested, this file would be served if present.
     * There is no welcome file by default.
     *
     * @return welcome-file name, such as {@code index.html}
     */
    @Option.Configured
    Optional<String> welcome();

    /**
     * A set of files that are cached in memory at startup. These files are never removed from the in-memory cache, though
     * their overall size is added to the memory cache used bytes.
     * When using classpath, the set must contain explicit list of all files that should be cached, when using file system,
     * it can contain a directory, and all files under that directory (recursive) would be cached as well.
     * <p>
     * Note that files cached through this method may use more than the max-bytes configured for the in-memory cache (i.e.
     * this option wins over the maximal size in bytes), so kindly be careful with what is pushed to the cache.
     * <p>
     * <i>Files cached in memory are snapshots. They remain available and are never re-loaded, even if their source is changed
     * or removed, until server restart!</i>
     *
     * @return set of file names (or directory names if not using classpath) to cache in memory on startup
     */
    @Option.Configured
    @Option.Singular
    Set<String> cachedFiles();

    /**
     * Handles will use memory cache configured on {@link StaticContentConfig#memoryCache()} by default.
     * In case a memory cache is configured here, it will replace the memory cache used by the static content feature, and this
     * handle will use a dedicated memory cache instead.
     * <p>
     * To disable memory caching for a single handler, create the configuration, and set {@code enabled: false}.
     *
     * @return memory cache to use with this handler
     */
    @Option.Configured
    Optional<MemoryCache> memoryCache();

    /**
     * Maps a filename extension to the response content type.
     * To have a system-wide configuration, you can use the service loader SPI
     * {@link io.helidon.common.media.type.spi.MediaTypeDetector}.
     * <p>
     * This method can override {@link io.helidon.common.media.type.MediaTypes} detection
     * for a specific static content handler.
     * <p>
     * Handler will use a union of configuration on the {@link io.helidon.webserver.staticcontent.StaticContentConfig} and
     * here when used from configuration.
     *
     * @return map of file extensions to associated media type
     */
    @Option.Configured
    @Option.Singular
    Map<String, MediaType> contentTypes();

    /**
     * Map request path to resource path. Default uses the same path as requested.
     * This can be used to resolve all paths to a single file, or to filter out files.
     *
     * @return function to map request path to resource path
     */
    @Option.DefaultMethod("identity")
    Function<String, String> pathMapper();

    /**
     * Configures the capacity of the cache used for resource metadata.
     * Pre-compressed sidecar lookup state is attached to the corresponding normal resource record.
     * <p>
     * To cache content (bytes) in memory, use {@link io.helidon.webserver.staticcontent.BaseHandlerConfig#memoryCache()}
     *
     * @return maximal number of cached records; records do not contain the content bytes
     */
    @Option.Configured
    Optional<Integer> recordCacheCapacity();

    /**
     * Whether pre-compressed sidecar resources should be selected for this handler; feature-registered handlers inherit
     * the feature value when absent, and direct service creation defaults to enabled.
     *
     * @return whether pre-compressed sidecar resources should be used
     */
    @Option.Configured
    Optional<Boolean> preCompressedEnabled();

    /**
     * Pre-compressed content coding to file suffix mappings; handler mappings replace inherited feature-level mappings
     * rather than merging with them, an explicit empty map disables sidecar lookups for this handler, codings must be
     * unique concrete valid HTTP tokens other than {@code identity} and {@code *}, and suffixes have leading dots ignored
     * and must not contain path separators.
     *
     * @return content coding to file suffix mappings
     */
    @Option.Configured
    @Option.Singular("preCompressedEncoding")
    Optional<Map<String, String>> preCompressedEncodings();
}
