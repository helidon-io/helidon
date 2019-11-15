/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.AbstractMpSource;
import io.helidon.config.spi.AbstractSource;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;

/**
 * {@link ConfigSource} implementation based on {@link Map Map&lt;String, String&gt;}.
 * <p>
 * Map key format must conform to {@link Config#key() Config key} format.
 *
 * @see io.helidon.config.ConfigSources.MapBuilder
 */
public class MapConfigSource extends AbstractMpSource<Instant> {

    private final Map<String, String> map;
    private final String mapSourceName;
    private final boolean strict;

    /**
     * Initialize map config source.
     *
     * @param map           config properties
     * @param strict        strict mode flag
     * @param mapSourceName name of map source
     */
    protected MapConfigSource(Map<String, String> map, boolean strict, String mapSourceName) {
        super(new Builder());
        Objects.requireNonNull(map, "map cannot be null");
        Objects.requireNonNull(mapSourceName, "mapSourceName cannot be null");

        this.map = map;
        this.strict = strict;
        this.mapSourceName = mapSourceName;
    }

    private MapConfigSource(Builder builder) {
        super(builder);
        this.map = new HashMap<>();
        this.mapSourceName = "empty";
        this.strict = true;
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
     * Create a new config source from the provided map, with strict mode set to {@code false}.
     *
     * @param map config properties
     * @return a new map config source
     */
    public static MapConfigSource create(Map<String, String> map) {
        return create(map, false, "");
    }

    /**
     * Create a new config source from the provided map.
     *
     * @param map config properties
     * @param strict strict mode flag, if set to {@code true}, parsing would fail if a tree node and a leaf node conflict,
     *                  such as for {@code http.ssl=true} and {@code http.ssl.port=1024}.
     * @param mapSourceName name of map source (for debugging purposes)
     * @return a new map config source
     */
    public static MapConfigSource create(Map<String, String> map, boolean strict, String mapSourceName) {
        return new MapConfigSource(map, strict, mapSourceName);
    }

    @Override
    protected String uid() {
        return mapSourceName.isEmpty() ? "" : mapSourceName;
    }

    @Override
    protected Optional<Instant> dataStamp() {
        return Optional.of(Instant.EPOCH);
    }

    @Override
    protected Data<ObjectNode, Instant> loadData() throws ConfigException {
        return new Data<>(Optional.of(ConfigUtils.mapToObjectNode(map, strict)), Optional.of(Instant.EPOCH));
    }

    /**
     * A fluent API builder for {@link io.helidon.config.internal.MapConfigSource}.
     */
    public static final class Builder extends AbstractSource.Builder<Builder, String, MapConfigSource> {
        private Builder() {
            super(String.class);
        }

        @Override
        public MapConfigSource build() {
            return new MapConfigSource(this);
        }
    }
}
