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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.helidon.builder.api.Prototype;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.http.HttpToken;

final class StaticContentConfigSupport {
    private StaticContentConfigSupport() {
    }

    static List<PreCompressedEncodingConfig> defaultPreCompressedEncodings() {
        List<PreCompressedEncodingConfig> result = new ArrayList<>();
        result.add(PreCompressedEncodingConfig.create("br", "br"));
        result.add(PreCompressedEncodingConfig.create("gzip", "gz"));
        return result;
    }

    static Map<String, String> normalizePreCompressedEncodings(List<PreCompressedEncodingConfig> configured) {
        Map<String, String> result = new LinkedHashMap<>();
        for (PreCompressedEncodingConfig entry : configured) {
            String coding = normalizeCoding(entry.coding());
            String suffix = normalizeSuffix(entry.suffix());
            if (result.put(coding, suffix) != null) {
                throw new IllegalArgumentException("Duplicate pre-compressed content coding: " + coding);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static List<PreCompressedEncodingConfig> preCompressedEncodingConfigs(Map<String, String> configured) {
        List<PreCompressedEncodingConfig> result = new ArrayList<>(configured.size());
        configured.forEach((coding, suffix) -> result.add(PreCompressedEncodingConfig.create(coding, suffix)));
        return List.copyOf(result);
    }

    private static MediaType createContentTypes(Config config) {
        return config.asString()
                .map(MediaTypes::create)
                .get();
    }

    private static String normalizeCoding(String configured) {
        String coding = configured.trim().toLowerCase(Locale.ROOT);
        if (coding.isEmpty()) {
            throw new IllegalArgumentException("Pre-compressed content coding must not be empty");
        }
        if ("identity".equals(coding) || "*".equals(coding)) {
            throw new IllegalArgumentException("Pre-compressed content coding must not be " + configured);
        }
        try {
            HttpToken.validate(coding);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid pre-compressed content coding: " + configured, e);
        }
        return coding;
    }

    private static String normalizeSuffix(String configured) {
        String suffix = configured.trim();
        while (suffix.startsWith(".")) {
            suffix = suffix.substring(1);
        }
        if (suffix.isEmpty()) {
            throw new IllegalArgumentException("Pre-compressed suffix must not be empty");
        }
        if (suffix.indexOf('/') != -1 || suffix.indexOf('\\') != -1) {
            throw new IllegalArgumentException("Pre-compressed suffix must not contain path separators: " + configured);
        }
        return suffix;
    }

    static class BaseMethods {
        private BaseMethods() {
        }

        @Prototype.ConfigFactoryMethod("contentTypes")
        static MediaType createContentTypes(Config config) {
            return StaticContentConfigSupport.createContentTypes(config);
        }
    }

    static class PreCompressedEncodingMethods {
        private PreCompressedEncodingMethods() {
        }

        /**
         * Create a pre-compressed representation configuration.
         *
         * @param coding HTTP content coding
         * @param suffix sidecar file suffix
         * @return pre-compressed representation configuration
         */
        @Prototype.PrototypeFactoryMethod
        static PreCompressedEncodingConfig create(String coding, String suffix) {
            return PreCompressedEncodingConfig.builder()
                    .coding(coding)
                    .suffix(suffix)
                    .build();
        }
    }

    static class FileSystemMethods {
        private FileSystemMethods() {
        }

        /**
         * Create a new file system based static content configuration from the defined location.
         * All other configuration is default.
         *
         * @param location path on file system that is the root of static content (all files under it will be available!)
         * @return a new configuration for classpath static content handler
         */
        @Prototype.PrototypeFactoryMethod
        static FileSystemHandlerConfig create(Path location) {
            return FileSystemHandlerConfig.builder()
                    .location(location)
                    .build();
        }
    }

    static class ClasspathMethods {
        private ClasspathMethods() {
        }

        /**
         * Create a new classpath based static content configuration from the defined location.
         * All other configuration is default.
         *
         * @param location location on classpath
         * @return a new configuration for classpath static content handler
         */
        @Prototype.PrototypeFactoryMethod
        static ClasspathHandlerConfig create(String location) {
            return ClasspathHandlerConfig.builder()
                    .location(location)
                    .build();
        }
    }

    static class StaticContentMethods {
        private StaticContentMethods() {
        }

        @Prototype.ConfigFactoryMethod("contentTypes")
        static MediaType createContentTypes(Config config) {
            return StaticContentConfigSupport.createContentTypes(config);
        }
    }
}
