/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.config;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.spi.ConfigSource;

/**
 * Utility class that locates the meta configuration source.
 */
final class MetaConfigFinder {
    /**
     * System property used to set a file with meta configuration.
     */
    public static final String META_CONFIG_SYSTEM_PROPERTY = "io.helidon.config.meta-config";

    private static final Logger LOGGER = Logger.getLogger(MetaConfigFinder.class.getName());
    private static final List<String> CONFIG_SUFFIXES = List.of("yaml", "conf", "json", "properties");
    private static final String META_CONFIG_PREFIX = "meta-config.";
    private static final String CONFIG_PREFIX = "application.";

    private MetaConfigFinder() {
    }

    static Optional<Config> findMetaConfig(Function<String, Boolean> supportedMediaType) {
        return findMetaConfigSource(supportedMediaType)
                .map(source -> Config.builder(source).build());
    }

    static Optional<ConfigSource> findConfigSource(Function<String, Boolean> supportedMediaType) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return findSource(supportedMediaType, cl, CONFIG_PREFIX, "config source");
    }

    private static Optional<ConfigSource> findMetaConfigSource(Function<String, Boolean> supportedMediaType) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Optional<ConfigSource> source;

        // check if meta configuration is configured using system property
        String property = System.getProperty(META_CONFIG_SYSTEM_PROPERTY);
        if (null != property) {
            // is it a file
            source = findFile(property, "meta configuration");
            if (source.isPresent()) {
                return source;
            }
            // so it is a classpath resource?
            source = findClasspath(cl, property, "meta configuration");
            if (source.isPresent()) {
                return source;
            }

            LOGGER.info("Meta configuration file not found: " + property);
        }

        return findSource(supportedMediaType, cl, META_CONFIG_PREFIX, "meta configuration");
    }

    private static Optional<ConfigSource> findSource(Function<String, Boolean> supportedMediaType,
                                                     ClassLoader cl,
                                                     String configPrefix,
                                                     String type) {
        Optional<ConfigSource> source;

        List<String> validSuffixes = CONFIG_SUFFIXES.stream()
                .filter(suffix -> supportedMediaType.apply(MediaTypes.detectExtensionType(suffix).orElse("unknown/unknown")))
                .collect(Collectors.toList());

        //  look into the file system - in current user directory
        source = validSuffixes.stream()
                .map(suf -> configPrefix + suf)
                .map(it -> findFile(it, type))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        if (source.isPresent()) {
            return source;
        }

        // and finally try to find meta configuration on classpath
        return validSuffixes.stream()
                .map(suf -> configPrefix + suf)
                .map(resource -> MetaConfigFinder.findClasspath(cl, resource, type))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private static Optional<ConfigSource> findFile(String name, String type) {
        Path path = Paths.get(name);
        if (Files.exists(path) && Files.isReadable(path) && !Files.isDirectory(path)) {
            LOGGER.info("Found " + type + " file: " + path.toAbsolutePath());
            return Optional.of(ConfigSources.file(path).build());
        }
        return Optional.empty();
    }

    private static Optional<ConfigSource> findClasspath(ClassLoader cl, String name, String type) {
        // so it is a classpath resource?
        URL resource = cl.getResource(name);
        if (null != resource) {
            LOGGER.fine(() -> "Found " + type + " resource: " + resource.getPath());
            return Optional.of(ConfigSources.classpath(name).build());
        }
        return Optional.empty();
    }
}
