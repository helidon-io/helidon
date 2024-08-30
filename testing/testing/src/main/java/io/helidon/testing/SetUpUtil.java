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

package io.helidon.testing;

import java.util.List;
import java.util.Map;

import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigSource;

final class SetUpUtil {
    private SetUpUtil() {
    }

    static List<TestConfig.File> file(Class<?> testClass) {
        TestConfig.Files annotation = testClass.getAnnotation(TestConfig.Files.class);
        if (annotation == null) {
            TestConfig.File file = testClass.getAnnotation(TestConfig.File.class);
            if (file == null) {
                return List.of();
            }
            return List.of(file);
        }
        return List.of(annotation.value());
    }

    static List<TestConfig.Value> value(Class<?> testClass) {
        TestConfig.Values annotation = testClass.getAnnotation(TestConfig.Values.class);
        if (annotation == null) {
            TestConfig.Value value = testClass.getAnnotation(TestConfig.Value.class);
            if (value == null) {
                return List.of();
            }
            return List.of(value);
        }
        return List.of(annotation.value());
    }

    static void profile(Class<?> testClass) {
        TestConfig.Profile annotation = testClass.getAnnotation(TestConfig.Profile.class);
        String profile = "test";

        boolean force = false;
        if (annotation != null) {
            profile = annotation.value();
            force = true;
        }

        boolean profileNotConfigured = System.getProperty("config.profile") == null
                && System.getProperty("helidon.config.profile") == null
                && System.getenv("HELIDON_CONFIG_PROFILE") == null;

        if (force || profileNotConfigured) {
            System.setProperty("helidon.config.profile", profile);
        }
    }

    static ConfigSource createValueSource(TestConfig.Value value) {
        return ConfigSources.create(Map.of(value.key(), value.value()))
                .name("From " + value.getClass().getName())
                .build();
    }

    static List<ConfigSource> createFileSource(TestConfig.File value) {
        return List.of(ConfigSources.file(value.value()).optional().build(),
                       ConfigSources.classpath(value.value()).optional().build());
    }
}
