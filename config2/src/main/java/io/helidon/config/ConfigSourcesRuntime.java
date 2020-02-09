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

package io.helidon.config;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import io.helidon.config.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;

class ConfigSourcesRuntime {
    private final List<ConfigSourceRuntime> runtimes = new LinkedList<>();
    private final ConfigSetup configSetup;
    private boolean hasMutable;
    private boolean hasLazy;

    ConfigSourcesRuntime(ConfigSetup configSetup, List<ConfigSourceSetup> sources) {
        this.configSetup = configSetup;
        for (ConfigSourceSetup source : sources) {
            runtimes.add(new ConfigSourceRuntime(configSetup, source));
        }

        for (ConfigSourceRuntime runtime : runtimes) {
            if (runtime.isLazy()) {
                hasLazy = true;
            }
            if (runtime.isMutable) {
                hasMutable = true;
            }
        }
    }

    public Optional<ConfigNode> getValue(Key key) {
        List<ConfigNode> nodes = new LinkedList<>();

        for (ConfigSourceRuntime runtime : runtimes) {
            runtime.getValue(key)
                    .ifPresent(nodes::add);
        }

        if (nodes.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(configSetup.mergingStrategy().merge(nodes));
    }

    public void init() {
        for (ConfigSourceRuntime runtime : runtimes) {
            if (runtime.isEager()) {
                runtime.load();
            }
        }
    }

    private static class ConfigSourceRuntime {
        private static final Logger LOGGER = Logger.getLogger(ConfigSourceRuntime.class.getName());

        private ConfigSetup configSetup;
        private final ConfigSourceSetup setup;
        private final ConfigSource theSource;
        private final boolean isLazy;
        private final boolean isEager;
        private final boolean isMutable;
        private final boolean isEvent;
        private final boolean isPollingStamp;
        private final boolean isPollingTarget;

        private final AtomicReference<ObjectNode> lastLoadedEagerNode = new AtomicReference<>();

        ConfigSourceRuntime(ConfigSetup configSetup, ConfigSourceSetup sourceSetup) {
            this.configSetup = configSetup;
            this.setup = sourceSetup;
            this.theSource = sourceSetup.theSource();
            this.isLazy = theSource instanceof ConfigSource.LazySource;
            this.isEager = theSource instanceof ConfigSource.EagerSource;

            this.isEvent = theSource instanceof ConfigSource.EventSource;
            this.isPollingStamp = theSource instanceof ConfigSource.StampPollingSource;
            this.isPollingTarget = theSource instanceof ConfigSource.TargetPollingSource;
            this.isMutable = isPollingTarget || isPollingStamp || isEvent;

            if (isLazy && isEager) {
                LOGGER.fine("Config source " + theSource + " is both eager and lazy, which is slightly confusing.");
            }
        }

        boolean isMutable() {
            return isMutable;
        }

        boolean isLazy() {
            return isLazy;
        }

        boolean isEager() {
            return isEager;
        }

        ConfigSource unwrap() {
            return theSource;
        }

        void load() {
            Optional<ObjectNode> result = doLoad();

            result.ifPresent(lastLoadedEagerNode::set);
        }

        private Optional<ObjectNode> doLoad() {
            ConfigParser.Content content = theSource.exists() ? loadEager() : null;

            if (null == content || !content.exists()) {
                if (setup.isOptional()) {
                    return Optional.empty();
                } else {
                    throw new ConfigException("Mandatory config source " + theSource + " is not available");
                }
            }

            try {
                if (content.hasData()) {
                    // parsable
                    ConfigParser parser = content.parser()
                            .orElseGet(() -> locateParser(content.mediaType().orElseThrow(() -> new ConfigException(
                                    "Neither media type nor parser is defined on a parsable config source " + theSource))));

                    return Optional.ofNullable(parser.parse(content));
                } else if (content.hasNode()) {
                    return Optional.ofNullable(content.node());
                } else {
                    throw new ConfigException("Config source  " + theSource + " provided neither data, nor config node.");
                }
            } finally {
                content.close();
            }
        }

        private ConfigParser locateParser(String mediaType) {
            return configSetup.findParser(mediaType)
                    .orElseThrow(() -> new ConfigException("Failed to find config parser for media type \"" + mediaType + "\""));
        }

        private ConfigParser.Content loadEager() {
            return ((ConfigSource.EagerSource) theSource).load();
        }

        public Optional<ConfigNode> getValue(Key key) {
            if (isLazy) {
                return ((ConfigSource.LazySource) theSource).node(key);
            }
            ObjectNode objectNode = lastLoadedEagerNode.get();
            if (null == objectNode) {
                return Optional.empty();
            }

            ObjectNode result = objectNode;
            ConfigNode.ListNode lastList = null;
            ConfigNode.ValueNode lastValue = null;
            for (String element : key.elements()) {
                if (null == result) {
                    return Optional.empty();
                }
                ConfigNode child = result.get(element);
                if (null == child) {
                    return Optional.empty();
                }
                switch (child.nodeType()) {
                case OBJECT:
                    result = (ObjectNode) child;
                    break;
                case LIST:
                    result = null;
                    lastList = (ConfigNode.ListNode) child;
                    break;
                case VALUE:
                    result = null;
                    lastValue = (ConfigNode.ValueNode) child;
                    break;
                default:
                    throw new IllegalStateException("Unsupported node type: " + child.nodeType());
                }
            }

            if (null != result) {
                return Optional.of(result);
            }

            if (null != lastValue) {
                return Optional.of(lastValue);
            }

            if (null != lastList) {
                return Optional.of(lastList);
            }

            throw new IllegalStateException("Bug in code");
        }
    }
}
