/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.media.type.spi.MediaTypeDetector;

final class Detectors {
    private static final List<MediaTypeDetector> DETECTORS;
    private static final ConcurrentHashMap<String, Optional<MediaType>> CACHE = new ConcurrentHashMap<>();

    static {
        // and load media type detectors
        DETECTORS = HelidonServiceLoader.builder(ServiceLoader.load(MediaTypeDetector.class))
                .addService(new BuiltInsDetector(), 0)
                .addService(new CustomDetector(), 1)
                .build()
                .asList();
    }

    private Detectors() {
    }

    static Optional<MediaType> detectExtensionType(String fileSuffix) {
        return CACHE.computeIfAbsent(fileSuffix, it ->
                DETECTORS.stream()
                        .map(mtd -> mtd.detectExtensionType(fileSuffix))
                        .flatMap(Optional::stream)
                        .findFirst());
    }

    static Optional<MediaType> detectType(String fileName) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(fileName))
                .flatMap(Optional::stream)
                .findFirst();
    }

    static Optional<MediaType> detectType(Path file) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(file))
                .flatMap(Optional::stream)
                .findFirst();
    }

    static Optional<MediaType> detectType(URI uri) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(uri))
                .flatMap(Optional::stream)
                .findFirst();
    }

    static Optional<MediaType> detectType(URL url) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(url))
                .flatMap(Optional::stream)
                .findFirst();
    }
}
