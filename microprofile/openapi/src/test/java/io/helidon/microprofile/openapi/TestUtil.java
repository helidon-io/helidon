/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Utility to query a tree structure using a dotted path notation.
 */
class TestUtil {

    private TestUtil() {
    }

    /**
     * Get a class-path resource.
     * @param path resource path
     * @return resource content as a string
     */
    static String resource(String path) {
        try (InputStream is = TestUtil.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Create a new instance of {@link Config} with the given maps as config sources.
     *
     * @param configSources config sources
     * @return config
     */
    @SafeVarargs
    static Config config(Map<String, String>... configSources) {
        return ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(configSources(configSources))
                .build();
    }

    @SafeVarargs
    private static ConfigSource[] configSources(Map<String, String>... configSources) {
        return Arrays.stream(configSources)
                .map(MpConfigSources::create)
                .toArray(ConfigSource[]::new);
    }

    /**
     * Treats the provided {@code Map} as a tree and navigates through it
     * using the dotted-name convention as expressed in the {@code dottedPath}
     * argument, finally casting the value retrieved from the last segment of
     * the path as the specified type and returning that cast value.
     *
     * @param <T>        type to which the final value will be cast
     * @param map        the tree
     * @param dottedPath navigation path to the item of interest ;
     *                   note that the {@code dottedPath} must not use dots except as path segment separators
     * @param cl         {@code Class} for the return type {@code <T>}
     * @return value from the lowest-level map retrieved using the last path segment, cast to the specified type
     */
    @SuppressWarnings(value = "unchecked")
    public static <T> T query(Map<String, Object> map, String dottedPath, Class<T> cl) {
        Map<String, Object> originalMap = map;
        String[] segments = dottedPath.split("\\.");
        for (int i = 0; i < segments.length - 1; i++) {
            map = (Map<String, Object>) map.get(segments[i]);
            if (map == null) {
                throw new AssertionError(String.format(
                        "Traversing dotted path %s segment %s not found in parsed map %s",
                        dottedPath, segments[i], originalMap));
            }
        }
        return cl.cast(map.get(segments[segments.length - 1]));
    }
}
