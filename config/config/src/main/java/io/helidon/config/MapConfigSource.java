/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 * @see io.helidon.config.MapConfigSource.Builder
 */
public class MapConfigSource extends AbstractConfigSource implements ConfigSource,
                                                                     NodeConfigSource,
                                                                     PollableSource<Map<?, ?>> {

    private final Map<?, ?> map;
    private final String mapSourceName;

    MapConfigSource(MapBuilder<?> builder) {
        super(builder);

        // we intentionally keep the original instance, so we can watch for changes
        this.map = builder.map();
        this.mapSourceName = builder.sourceName();
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
     * Fluent API builder for {@link io.helidon.config.MapConfigSource}.
     */
    public static final class Builder extends MapBuilder<Builder> {
        private Builder() {
        }

        @Override
        public MapConfigSource build() {
            return new MapConfigSource(this);
        }
    }

    /**
     * An abstract fluent API builder for {@link MapConfigSource}.
     * If you want to extend {@link io.helidon.config.MapConfigSource}, you can use this class as a base for
     * your own builder.
     *
     * @param <T> type of the implementing builder
     */
    public abstract static class MapBuilder<T extends MapBuilder<T>> extends AbstractConfigSourceBuilder<T, Void>
            implements io.helidon.common.Builder<MapConfigSource>,
                       PollableSource.Builder<T> {

        private Map<?, ?> map;
        private String sourceName = "";
        @SuppressWarnings("unchecked")
        private final T me = (T) this;

        /**
         * Creat a new builder instance.
         */
        protected MapBuilder() {
        }

        /**
         * Map to be used as config source underlying data.
         * The same instance is kept by the config source, to support polling.
         *
         * @param map map to use
         * @return updated builder instance
         */
        public T map(Map<String, String> map) {
            this.map = map;
            return me;
        }

        /**
         * Properties to be used as config source underlying data.
         * The same instance is kept by the config source, to support polling.
         *
         * @param properties properties to use
         * @return updated builder instance
         */
        public T properties(Properties properties) {
            this.map = properties;
            return me;
        }

        /**
         * Name of this source.
         *
         * @param sourceName name of this source
         * @return updated builder instance
         */
        public T name(String sourceName) {
            this.sourceName = Objects.requireNonNull(sourceName);
            return me;
        }

        @Override
        public T pollingStrategy(PollingStrategy pollingStrategy) {
            return super.pollingStrategy(pollingStrategy);
        }

        /**
         * Map used as data of this config source.
         *
         * @return map with the data
         */
        protected Map<?, ?> map() {
            return map;
        }

        /**
         * Name of the source.
         *
         * @return name
         */
        protected String sourceName() {
            return sourceName;
        }
    }
}
