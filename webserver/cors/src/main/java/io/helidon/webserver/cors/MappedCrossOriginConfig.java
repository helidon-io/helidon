/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import io.helidon.config.Config;

/**
 * Cross-origin {@link CrossOriginConfig} instances linked to paths, plus an {@code enabled} setting. Most developers will not
 * need to use this directly from their applications.
 */
public class MappedCrossOriginConfig implements Iterable<Map.Entry<String, CrossOriginConfig>> {

    private String name = "";
    private boolean isEnabled = true;

    private final Map<String, Buildable> buildables;

    /**
     * Holds both a builder for and, later, the built {@code CrossOriginConfig} instances each of which are mapped to
     * a path expression.
     */
    private static class Buildable {
        private final CrossOriginConfig.Builder builder;
        private CrossOriginConfig crossOriginConfig;

        Buildable(CrossOriginConfig.Builder builder) {
            this.builder = builder;
        }

        /**
         * Returns the instance, building it if needed.
         *
         * @return the built instance
         */
        CrossOriginConfig get() {
            if (crossOriginConfig == null) {
                crossOriginConfig = builder.build();
            }
           return crossOriginConfig;
        }

        @Override
        public String toString() {
            return String.format("Buildable{%s}", crossOriginConfig == null ? builder.toString() : crossOriginConfig.toString());
        }
    }

    private MappedCrossOriginConfig(Builder builder) {
        this.name = builder.nameOpt.orElse("");
        this.isEnabled = builder.enabledOpt.orElse(true);
        buildables = builder.builders;

        // Force building to prevent any changes to the underlying builders that could cause surprising behavior later.
        buildables.forEach((path, b) -> b.get());
    }

    /**
     * Returns a new builder for creating a {@code CrossOriginConfig.Mapped} instance.
     *
     * @return the new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new {@code Mapped.Builder} instance using the provided configuration.
     * <p>
     *     Although this method is equivalent to {@code builder().config(config)} it conveniently combines those two steps for
     *     use as a method reference.
     * </p>
     *
     * @param config node containing {@code Mapped} cross-origin information
     * @return new {@code Mapped.Builder} based on the config
     */
    public static Builder builder(Config config) {
        return builder().config(config);
    }

    /**
     * Creates a new {@code Mapped} instance using the provided configuration.
     *
     * @param config node containing {@code Mapped} cross-origin information
     * @return new {@code Mapped} instance based on the config
     */
    public static MappedCrossOriginConfig create(Config config) {
        return builder(config).build();
    }

    @Override
    public Iterator<Map.Entry<String, CrossOriginConfig>> iterator() {
        return new Iterator<>() {

            private final Iterator<Map.Entry<String, Buildable>> it = buildables.entrySet().iterator();

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Map.Entry<String, CrossOriginConfig> next() {
                Map.Entry<String, Buildable> next = it.next();
                return new AbstractMap.SimpleEntry<>(next.getKey(), next.getValue().get());
            }
        };
    }

    /**
     * Invokes the specified consumer for each entry in the mapped CORS config.
     * @param consumer accepts the path and the {@code CrossOriginConfig} for processing
     */
    public void forEach(BiConsumer<String, CrossOriginConfig> consumer) {
        buildables.forEach((path, buildable) -> consumer.accept(path, buildable.get()));
    }

    /**
     * Finds the {@code CrossOriginConfig} associated with the given path expression, if any.
     *
     * @param pathExpr path expression to match on
     * @return {@code Optional} of the corresponding basic cross-origin information
     */
    public CrossOriginConfig get(String pathExpr) {
        Buildable b = buildables.get(pathExpr);
        return b == null ? null : b.get();
    }

    /**
     *
     * @return the name set up for this CORS-enabled component or app
     */
    public String name() {
        return name;
    }

    /**
     * Reports whether this instance is enabled.
     * @return current enabled state
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public String toString() {
        return String.format("MappedCrossOriginConfig{name='%s', isEnabled=%b, buildables=%s}", name, isEnabled, buildables);
    }

    /**
     * Fluent builder for {@code Mapped} cross-origin config.
     */
    public static class Builder implements io.helidon.common.Builder<MappedCrossOriginConfig> {

        private Optional<String> nameOpt = Optional.empty();
        private Optional<Boolean> enabledOpt = Optional.empty();
        private final Map<String, Buildable> builders = new HashMap<>();

        private Builder() {
        }

        @Override
        public MappedCrossOriginConfig build() {
            return new MappedCrossOriginConfig(this);
        }

        /**
         * Sets the name for the CORS-enabled component or app (primarily for logging).
         *
         * @param name name for the component
         * @return updated builder
         */
        public Builder name(String name) {
            this.nameOpt = Optional.of(name);
            return this;
        }

        /**
         * Sets whether the resulting {@code Mapped} cross-origin config should be enabled.
         *
         * @param enabled true to enable; false to disable
         * @return updated builder
         */
        public Builder enabled(boolean enabled) {
            this.enabledOpt = Optional.of(enabled);
            return this;
        }

        /**
         * Adds a new builder to the collection, associating it with the given path.
         *
         * @param path the path to link with the builder
         * @param builder the builder to use in building the actual {@code CrossOriginConfig} instance
         * @return updated builder
         */
        public Builder put(String path, CrossOriginConfig.Builder builder) {
            builders.put(path, new Buildable(builder));
            return this;
        }

        /**
         * Applies data in the provided config node.
         *
         * @param corsConfig {@code Config} node containing CORS information
         * @return updated builder
         */
        public Builder config(Config corsConfig) {
            return Loader.Mapped.applyConfig(corsConfig);
        }
    }
}
