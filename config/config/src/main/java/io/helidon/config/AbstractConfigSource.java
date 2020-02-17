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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;

/**
 * A base implementation for config sources, that combines configuration from any type of a config source.
 * This class does not directly implement the interfaces - this is left to the implementer of the config source.
 * This class provides configuration methods as {@code protected}, so you can make them public in your implementation, to only
 * expose methods that must be implemented.
 * <p>
 * Other methods of the config source interfaces must be implemented by each source as they are data specific,
 * such as {@link io.helidon.config.spi.PollableSource#isModified(Object)}.
 *
 * All other methods return reasonable defaults.
 * Config framework analyzes the config source based on interfaces it implements.
 *
 * @see io.helidon.config.spi.ConfigSource
 * @see io.helidon.config.spi.WatchableSource
 * @see io.helidon.config.spi.PollableSource
 * @see io.helidon.config.spi.ParsableSource
 */
public abstract class AbstractConfigSource extends AbstractSource implements ConfigSource {
    private final Optional<String> mediaType;
    private final Optional<ConfigParser> parser;
    private final Optional<Function<Config.Key, Optional<String>>> mediaTypeMapping;
    private final Optional<Function<Config.Key, Optional<ConfigParser>>> parserMapping;
    private final boolean mediaMappingSupported;

    protected AbstractConfigSource(AbstractConfigSourceBuilder<?, ?> builder) {
        super(builder);

        this.mediaType = builder.mediaType();
        this.parser = builder.parser();
        this.mediaTypeMapping = builder.mediaTypeMapping();
        this.parserMapping = builder.parserMapping();

        this.mediaMappingSupported = mediaTypeMapping.isPresent() || parserMapping.isPresent();
    }

    protected Optional<String> mediaType() {
        return mediaType;
    }

    protected Optional<ConfigParser> parser() {
        return parser;
    }

    ConfigNode.ObjectNode processNodeMapping(Function<String, Optional<ConfigParser>> mediaToParser,
                                             ConfigKeyImpl configKey,
                                             ConfigNode.ObjectNode loaded) {

        if (!mediaMappingSupported) {
            return loaded;
        }

        return processObject(mediaToParser, configKey, loaded);
    }

    private ConfigNode.ObjectNode processObject(Function<String, Optional<ConfigParser>> mediaToParser,
                                                ConfigKeyImpl key,
                                                ConfigNode.ObjectNode objectNode) {
        ObjectNodeBuilderImpl builder = (ObjectNodeBuilderImpl) ConfigNode.ObjectNode.builder();

        objectNode.forEach((name, node) -> builder.addNode(name, processNode(mediaToParser, key.child(name), node)));

        return builder.build();
    }

    private ConfigNode processNode(Function<String, Optional<ConfigParser>> mediaToParser,
                                   ConfigKeyImpl key,
                                   ConfigNode node) {
        switch (node.nodeType()) {
        case OBJECT:
            return processObject(mediaToParser, key, (ConfigNode.ObjectNode) node);
        case LIST:
            return processList(mediaToParser, key, (ConfigNode.ListNode) node);
        case VALUE:
            return processValue(mediaToParser, key, (ConfigNode.ValueNode) node);
        default:
            throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getName());
        }
    }

    private ConfigNode.ListNode processList(Function<String, Optional<ConfigParser>> mediaToParser,
                                            ConfigKeyImpl key,
                                            ConfigNode.ListNode listNode) {
        ListNodeBuilderImpl builder = (ListNodeBuilderImpl) ConfigNode.ListNode.builder();

        for (int i = 0; i < listNode.size(); i++) {
            builder.addNode(processNode(mediaToParser, key.child(Integer.toString(i)), listNode.get(i)));
        }

        return builder.build();
    }

    private ConfigNode processValue(Function<String, Optional<ConfigParser>> mediaToParser,
                                    Config.Key key,
                                    ConfigNode.ValueNode valueNode) {

        Optional<ConfigParser> parser = findParserForKey(mediaToParser, key);

        if (parser.isEmpty()) {
            return valueNode;
        }

        ConfigParser found = parser.get();

        return found.parse(ConfigParser.Content.builder()
                // value node must have a value
                .data(toStream(valueNode.get()))
                .charset(StandardCharsets.UTF_8)
                .build());
    }

    private InputStream toStream(String string) {
        return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }

    private Optional<ConfigParser> findParserForKey(Function<String, Optional<ConfigParser>> mediaToParser,
                                                    Config.Key key) {

        // try to find it in parser mapping (explicit parser for a key)
        Optional<ConfigParser> parser = parserMapping.flatMap(it -> it.apply(key));

        if (parser.isPresent()) {
            return parser;
        }

        // now based on media type
        Optional<String> maybeMedia = mediaTypeMapping.flatMap(it -> it.apply(key));

        if (maybeMedia.isEmpty()) {
            // no media type configured, return empty
            return Optional.empty();
        }

        String mediaType = maybeMedia.get();

        // if media is explicit, parser is required
        return Optional.of(mediaToParser.apply(mediaType)
                .orElseThrow(() -> new ConfigException("Cannot find parser for media type "
                                                               + mediaType
                                                               + " for key "
                                                               + key
                                                               + " in config source "
                                                               + description())));

    }
}
