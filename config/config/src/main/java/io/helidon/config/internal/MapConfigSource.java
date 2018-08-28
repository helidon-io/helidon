/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;

/**
 * {@link ConfigSource} implementation based on {@link Map Map&lt;String, String&gt;}.
 * <p>
 * Map key format must conform to {@link Config#key() Config key} format.
 *
 * @see io.helidon.config.ConfigSources.MapBuilder
 */
public class MapConfigSource implements ConfigSource {

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
    public MapConfigSource(Map<String, String> map, boolean strict, String mapSourceName) {
        Objects.requireNonNull(map, "map cannot be null");
        Objects.requireNonNull(mapSourceName, "mapSourceName cannot be null");

        this.map = map;
        this.strict = strict;
        this.mapSourceName = mapSourceName;
    }

    @Override
    public String description() {
        return ConfigSource.super.description() + "[" + mapSourceName + "]";
    }

    @Override
    public Optional<ObjectNode> load() {
        return Optional.of(ConfigUtils.mapToObjectNode(map, strict));
    }

}
