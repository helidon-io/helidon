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

import java.nio.file.Path;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;

final class StaticContentConfigSupport {
    private StaticContentConfigSupport() {
    }

    static class BaseMethods {
        private BaseMethods() {
        }

        @Prototype.FactoryMethod
        static MediaType createContentTypes(Config config) {
            return StaticContentConfigSupport.createContentTypes(config);
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
        @Prototype.FactoryMethod
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
        @Prototype.FactoryMethod
        static ClasspathHandlerConfig create(String location) {
            return ClasspathHandlerConfig.builder()
                    .location(location)
                    .build();
        }
    }

    static class StaticContentMethods {
        private StaticContentMethods() {
        }

        @Prototype.FactoryMethod
        static MediaType createContentTypes(Config config) {
            return StaticContentConfigSupport.createContentTypes(config);
        }
    }

    private static MediaType createContentTypes(Config config) {
        return config.asString()
                .map(MediaTypes::create)
                .get();
    }
}
