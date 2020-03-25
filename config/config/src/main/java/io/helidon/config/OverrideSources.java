/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.OverrideSource;

/**
 * Class provides access to built-in {@link io.helidon.config.spi.OverrideSource} implementations.
 *
 * @see io.helidon.config.spi.OverrideSource
 */
public final class OverrideSources {

    private OverrideSources() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * An empty implementation of {@code OverrideSource}.
     * <p>
     * A single instance is hold, so the return value is always the same.
     *
     * @return an empty implementation of {@code OverrideSource}
     */
    public static OverrideSource empty() {
        return OverridingSourceHolder.EMPTY;
    }

    /**
     * Creates a new instance od {@link OverrideSource} from a map of wildcards to values.
     * <p>
     * Note that {@link Map} does not guarantee the ordering of the items, but overrides are resolved in specified order. When
     * more than one of the overrides keys might match, the first of them will be applied.
     *
     * @param overrideValues a map of wildcards to values
     * @return a new instance of {@code OverrideSource}
     */
    public static OverrideSource create(Map<String, String> overrideValues) {
        return InMemoryOverrideSource.builder(overrideValues).build();
    }

    /**
     * Creates new instance of Classpath OverrideSource Builder to be used to bootstrap OverrideSource instance from
     * specified resource.
     * <p>
     * The name of a resource is a '{@code /}'-separated full path name that identifies the resource.
     * If the resource name has a leading slash then it is dropped before lookup.
     *
     * @param resourceName a name of the resource
     * @return new Builder instance
     */
    public static ClasspathOverrideSource.Builder classpath(String resourceName) {
        return ClasspathOverrideSource.builder().resource(resourceName);
    }

    /**
     * Creates new instance of the File OverrideSource Builder to be used to bootstrap an File OverrideSource instance.
     *
     * @param file a file with an override value map
     * @return an instance of builder
     */
    public static FileOverrideSource.Builder file(String file) {
        return FileOverrideSource.builder().path(Paths.get(file));
    }

    /**
     * Creates new instance of the URL OverrideSource Builder to be used to bootstrap an URL OverrideSource instance.
     *
     * @param url an URL with an override value map
     * @return an instance of builder
     */
    public static UrlOverrideSource.Builder url(URL url) {
        return UrlOverrideSource.builder().url(url);
    }

    /**
     * Holder of singleton instance of {@link OverrideSource}.
     *
     * @see OverrideSources#empty()
     */
    private static final class OverridingSourceHolder {

        /**
         * EMPTY singleton instance.
         */
        private static final OverrideSource EMPTY = new EmptyOverrideSource();

        private OverridingSourceHolder() {
            throw new AssertionError("Instantiation not allowed.");
        }
    }

    private static final class EmptyOverrideSource implements OverrideSource {
        @Override
        public Optional<ConfigContent.OverrideContent> load() throws ConfigException {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "EmptyOverrideSource";
        }

        @Override
        public boolean optional() {
            return true;
        }
    }
}
