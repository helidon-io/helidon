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
import java.util.Properties;

import io.helidon.config.ConfigException;
import io.helidon.config.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;

public class SystemPropertiesConfigSource implements ConfigSource.EagerSource,
                                                     ConfigSource.StampPollingSource<Map<String, String>> {
    @Override
    public ConfigParser.Content load() throws ConfigException {
        Map<String, String> currentProperties = new HashMap<>();
        Properties sysProps = System.getProperties();
        sysProps
                .stringPropertyNames()
                .forEach(key -> currentProperties.put(key, sysProps.getProperty(key)));

        return ConfigParser.Content.builder()
                .pollingStamp(currentProperties)
                .node(toNode(currentProperties))
                .build();
    }

    private ConfigNode.ObjectNode toNode(Map<String, String> map) {
        ConfigNode.ObjectNode.Builder builder = ConfigNode.ObjectNode.builder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            builder.addValue(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    @Override
    public boolean isModified(Map<String, String> stamp) {
        return !System.getProperties().equals(stamp);
    }
}
