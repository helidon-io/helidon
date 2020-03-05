/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.common.media.type;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import io.helidon.common.media.type.spi.MediaTypeDetector;
import io.helidon.common.serviceloader.HelidonServiceLoader;

/**
 * Media type detection based on a resource.
 * <p>The media type detection uses the following algorithm:
 * <ul>
 *     <li>Queries {@link io.helidon.common.media.type.spi.MediaTypeDetector} services in priority order</li>
 *     <li>Checks all {@code META-INF/media-types.properties} files on classpath for a mapping (suffix=media type)</li>
 *     <li>Checks built-in mapping provided by Helidon (with the usual the web relevant media types)</li>
 * </ul>
 */
public final class MediaTypes {
    private static final Logger LOGGER = Logger.getLogger(MediaTypes.class.getName());
    private static final List<MediaTypeDetector> DETECTORS;
    private static final ConcurrentHashMap<String, Optional<String>> CACHE = new ConcurrentHashMap<>();

    static {
        // and load media type detectors
        DETECTORS = HelidonServiceLoader.builder(ServiceLoader.load(MediaTypeDetector.class))
                .addService(new BuiltInsDetector(), 100100)
                .addService(new CustomDetector(), 100000)
                .build()
                .asList();
    }

    // prevent instantiation of utility class
    private MediaTypes() {
    }

    /**
     * Detect media type based on URL.
     * As there may be an infinite number of urls used in a system, the results are NOT cached.
     *
     * @param url to determine media type for
     * @return media type or empty if none found
     */
    public static Optional<String> detectType(URL url) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(url))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Detect media type based on URI.
     * Results may not be cached.
     *
     * @param uri to determine media type for
     * @return media type or empty if none found
     */
    public static Optional<String> detectType(URI uri) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(uri))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Detect media type for a file on file system.
     * Results may not be cached.
     *
     * @param file file on a file system
     * @return media type or empty if none found
     */
    public static Optional<String> detectType(Path file) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(file))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Detect media type for a path (may be URL, URI, path on a file system).
     * Results may not be cached. If you have {@link java.net.URL}, {@link java.net.URI}, or {@link java.nio.file.Path} please
     * use the other methods on this class.
     *
     * @param fileName any string that has a file name as its last element
     * @return media type or empty if none found
     * @see #detectType(java.net.URI)
     * @see #detectType(java.net.URL)
     * @see #detectType(java.nio.file.Path)
     */
    public static Optional<String> detectType(String fileName) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(fileName))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Detecd media type for a specific file extension.
     * Results are cached.
     *
     * @param fileSuffix suffix of a file, such as {@code txt}, {@code properties}, or {@code jpeg}. Without the leading dot.
     * @return media type for the file suffix or empty if none found
     */
    public static Optional<String> detectExtensionType(String fileSuffix) {
        return CACHE.computeIfAbsent(fileSuffix, it ->
                DETECTORS.stream()
                        .map(mtd -> mtd.detectExtensionType(fileSuffix))
                        .flatMap(Optional::stream)
                        .findFirst());
    }
}
