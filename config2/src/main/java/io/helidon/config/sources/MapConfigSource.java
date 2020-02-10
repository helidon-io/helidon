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
 */

package io.helidon.config.sources;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import io.helidon.config.ConfigException;
import io.helidon.config.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ConfigSourceBase;
import io.helidon.config.spi.ConfigSourceBuilderBase;
import io.helidon.config.spi.Content;
import io.helidon.config.spi.PollingStrategy;

public class MapConfigSource extends ConfigSourceBase implements ConfigSource.NodeSource,
                                                                 ConfigSource.PollableSource<Map<?, ?>> {
    private final Map<?, ?> theMap;

    public MapConfigSource(Builder builder) {
        super(builder);
        // we intentionally keep the original instance, so we can watch for changes
        this.theMap = builder.map;
    }

    public static MapConfigSource create(Map<String, String> theMap) {
        Objects.requireNonNull(theMap);
        return builder().map(theMap).build();
    }

    public static MapConfigSource create(Properties properties) {
        Objects.requireNonNull(properties);
        return builder().properties(properties).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Content.NodeContent load() throws ConfigException {
        Map<?, ?> copyOfMap = new HashMap<>(theMap);

        return Content.nodeBuilder()
                .pollingStamp(copyOfMap)
                .node(toNode(copyOfMap))
                .build();
    }

    private ConfigNode.ObjectNode toNode(Map<?, ?> map) {
        ConfigNode.ObjectNode.Builder builder = ConfigNode.ObjectNode.builder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            builder.addValue(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return builder.build();
    }

    @Override
    public boolean isModified(Map<?, ?> stamp) {
        return !theMap.equals(stamp);
    }

    public static class Builder extends ConfigSourceBuilderBase<Builder, Void>
                    implements PollableSource.Builder<Builder> {
        private Map<?, ?> map;

        @Override
        public MapConfigSource build() {
            return new MapConfigSource(this);
        }

        public Builder map(Map<String, String> map) {
            this.map = map;
            return this;
        }

        public Builder properties(Properties properties) {
            this.map = properties;
            return this;
        }

        @Override
        public Builder pollingStrategy(PollingStrategy pollingStrategy) {
            return super.pollingStrategy(pollingStrategy);
        }
    }
}
