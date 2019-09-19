/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 * TODO javadoc.
 */
public class MediaTypes {
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

    public static Optional<String> detectType(URL url) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(url))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public static Optional<String> detectType(URI uri) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(uri))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public static Optional<String> detectType(Path file) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(file))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public static Optional<String> detectType(String fileName) {
        return DETECTORS.stream()
                .map(mtd -> mtd.detectType(fileName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public static Optional<String> detectFileType(String fileSuffix) {
        return CACHE.computeIfAbsent(fileSuffix, it ->
                DETECTORS.stream()
                        .map(mtd -> mtd.detectExtensionType(fileSuffix))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst());
    }
}
