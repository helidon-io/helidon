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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;
import io.helidon.config.spi.ConfigSource;

/**
 * The runtime of a config source. For a single {@link io.helidon.config.Config}, there is one source runtime for each configured
 * config source.
 */
public class ConfigSourceRuntimeImpl implements ConfigSourceRuntime {
    private final AtomicReference<Map<String, String>> currentValues = new AtomicReference<>();
    private final ConfigSource configSource;

    @Override
    public void onChange(BiConsumer<Config.Key, ConfigNode> change) {

    }

    @Override
    public Optional<ConfigNode.ObjectNode> load() {
        return Optional.empty();
    }

    /*
     * MP Config related methods
     */

    @Override
    public Map<String, String> getProperties() {
        return currentValues.get();
    }

    @Override
    public String getValue(String propertyName) {
        return currentValues.get().get(propertyName);
    }

    @Override
    public String getName() {
        return configSource.description();
    }

    private synchronized void ensureCurrentValue() {
        if (null == currentValues.get()) {
            currentValues.set(loadMap(load()));
        }
    }

    private static Map<String, String> loadMap(Optional<ConfigNode.ObjectNode> item) {
        if (item.isPresent()) {
            ConfigNode.ObjectNode node = item.get();
            Map<String, String> values = new TreeMap<>();
            processNode(values, "", node);
            return values;
        } else {
            return Map.of();
        }
    }

    private static void processNode(Map<String, String> values, String keyPrefix, ConfigNode.ObjectNode node) {
        node.forEach((key, configNode) -> {
            switch (configNode.nodeType()) {
            case OBJECT:
                processNode(values, key(keyPrefix, key), (ConfigNode.ObjectNode) configNode);
                break;
            case LIST:
                processNode(values, key(keyPrefix, key), ((ConfigNode.ListNode) configNode));
                break;
            case VALUE:
                break;
            default:
                throw new IllegalStateException("Config node of type: " + configNode.nodeType() + " not supported");
            }

            String directValue = configNode.get();
            if (null != directValue) {
                values.put(key(keyPrefix, key), directValue);
            }
        });
    }

    private static void processNode(Map<String, String> values, String keyPrefix, ConfigNode.ListNode node) {
        List<String> directValue = new LinkedList<>();
        Map<String, String> thisListValues = new HashMap<>();
        boolean hasDirectValue = true;

        for (int i = 0; i < node.size(); i++) {
            ConfigNode configNode = node.get(i);
            String nextKey = key(keyPrefix, String.valueOf(i));
            switch (configNode.nodeType()) {
            case OBJECT:
                processNode(thisListValues, nextKey, (ConfigNode.ObjectNode) configNode);
                hasDirectValue = false;
                break;
            case LIST:
                processNode(thisListValues, nextKey, (ConfigNode.ListNode) configNode);
                hasDirectValue = false;
                break;
            case VALUE:
                String value = configNode.get();
                directValue.add(value);
                thisListValues.put(nextKey, value);
                break;
            default:
                throw new IllegalStateException("Config node of type: " + configNode.nodeType() + " not supported");
            }
        }

        if (hasDirectValue) {
            values.put(keyPrefix, String.join(",", directValue));
        } else {
            values.putAll(thisListValues);
        }
    }

    private static String key(String keyPrefix, String key) {
        if (keyPrefix.isEmpty()) {
            return key;
        }
        return keyPrefix + "." + key;
    }


    /*
     * Config source related methods
     */
    /**
     * Parser config source content into internal config structure.
     *
     * @param context config context built by {@link io.helidon.config.Config.Builder}
     * @param content content to be parsed
     * @return parsed configuration into internal structure. Never returns {@code null}.
     * @throws io.helidon.config.spi.ConfigParserException in case of problem to parse configuration from the source
     */
    private ConfigNode.ObjectNode parse(ConfigContext context, ConfigParser.Content content) throws ConfigParserException {
        return parser()
                .or(() -> context.findParser(content.mediaType()
                                                     .orElseThrow(() -> new ConfigException("Unknown media type."))))
                .map(parser -> parser.parse(content))
                .orElseThrow(() -> new ConfigException("Cannot find suitable parser for '"
                                                               + content.mediaType().orElse(null) + "' media type."));
    }
}
