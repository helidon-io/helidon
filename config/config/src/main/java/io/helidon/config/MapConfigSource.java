/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import io.helidon.config.internal.ConfigUtils;
import io.helidon.config.spi.BaseConfigSource;
import io.helidon.config.spi.BaseConfigSourceBuilder;
import io.helidon.config.spi.ConfigContent.NodeContent;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;

/**
 * {@link ConfigSource} implementation based on {@link Map Map&lt;String, String&gt;}.
 * <p>
 * Map key format must conform to {@link Config#key() Config key} format.
 *
 * @see io.helidon.config.ConfigSources.MapBuilder
 */
public final class MapConfigSource extends BaseConfigSource implements ConfigSource,
                                                                       NodeConfigSource,
                                                                       PollableSource<Map<?, ?>> {

    private final Map<?, ?> map;
    private final String mapSourceName;

    private MapConfigSource(Builder builder) {
        super(builder);

        // we intentionally keep the original instance, so we can watch for changes
        this.map = builder.map;
        this.mapSourceName = builder.sourceName;
    }

    /**
     * Create a new fluent API builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new config source from the provided map.
     *
     * @param map config properties
     * @return a new map config source
     */
    public static MapConfigSource create(Map<String, String> map) {
        Objects.requireNonNull(map);
        return builder().map(map).build();
    }

    /**
     * Create a new config source from the provided properties.
     * @param properties properties to serve as source of data
     * @return a new map config source
     */
    public static MapConfigSource create(Properties properties) {
        Objects.requireNonNull(properties);
        return builder().properties(properties).build();
    }

    @Override
    public boolean isModified(Map<?, ?> stamp) {
        return !this.map.equals(stamp);
    }

    @Override
    public Optional<PollingStrategy> pollingStrategy() {
        return super.pollingStrategy();
    }

    @Override
    public Optional<NodeContent> load() throws ConfigException {
        return Optional.of(NodeContent.builder()
                                   .node(ConfigUtils.mapToObjectNode(map, false))
                                   .build());
    }

    @Override
    protected String uid() {
        return mapSourceName.isEmpty() ? "" : mapSourceName;
    }

    /**
     * A fluent API builder for {@link MapConfigSource}.
     */
    public static final class Builder extends BaseConfigSourceBuilder<Builder, Void>
            implements io.helidon.common.Builder<MapConfigSource>,
                       PollableSource.Builder<Builder> {

        private Map<?, ?> map;
        private String sourceName = "";

        private Builder() {
        }

        @Override
        public MapConfigSource build() {
            return new MapConfigSource(this);
        }

        /**
         * Map to be used as config source underlying data.
         * The same instance is kept by the config source, to support polling.
         *
         * @param map map to use
         * @return updated builder instance
         */
        public Builder map(Map<String, String> map) {
            this.map = map;
            return this;
        }

        /**
         * Properties to be used as config source underlying data.
         * The same instance is kept by the config source, to support polling.
         *
         * @param properties properties to use
         * @return updated builder instance
         */
        public Builder properties(Properties properties) {
            this.map = properties;
            return this;
        }

        /**
         * Name of this source.
         * The following names are reserved (you can still use them, but you will impact
         *  config functionality):
         * <ul>
         *     <li>{@code system-properties} - used by System properties config source
         *     <li>{@code environment-variables} - used by Environment variables config source
         * </ul>
         *
         * @param sourceName name of this source
         * @return updated builder instance
         */
        public Builder sourceName(String sourceName) {
            this.sourceName = Objects.requireNonNull(sourceName);
            return this;
        }

        @Override
        public Builder pollingStrategy(PollingStrategy pollingStrategy) {
            return super.pollingStrategy(pollingStrategy);
        }
    }
}
