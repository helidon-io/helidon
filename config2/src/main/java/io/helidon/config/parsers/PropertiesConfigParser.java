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

package io.helidon.config.parsers;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.Priority;

import io.helidon.config.ConfigNode;
import io.helidon.config.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;
import io.helidon.config.spi.Content;

/**
 * {@link io.helidon.config.spi.ConfigParser} implementation that parses Java Properties content.
 * <p>
 * The parser implementation supports {@link java.util.ServiceLoader}, i.e. {@link io.helidon.config.Config.Builder}
 * can automatically load and register {@code PropertiesConfigParser} instance,
 * if not {@link io.helidon.config.Config.Builder#disableParserServices() disabled}.
 * And of course it can be {@link io.helidon.config.Config.Builder#addParser(io.helidon.config.spi.ConfigParser) registered programmatically}.
 * <p>
 * Priority of the {@code PropertiesConfigParser} to be used by {@link io.helidon.config.Config.Builder},
 * if loaded automatically as a {@link java.util.ServiceLoader service}, is {@value PRIORITY}.
 *
 * @see io.helidon.config.Config.Builder#addParser(io.helidon.config.spi.ConfigParser)
 * @see io.helidon.config.Config.Builder#disableParserServices()
 */
@Priority(PropertiesConfigParser.PRIORITY)
public class PropertiesConfigParser implements ConfigParser {

    /**
     * A String constant representing {@value} media type.
     */
    public static final String MEDIA_TYPE_TEXT_JAVA_PROPERTIES = "text/x-java-properties";

    /**
     * Priority of the parser used if registered by {@link io.helidon.config.Config.Builder} automatically.
     */
    public static final int PRIORITY = 200;

    private static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of(MEDIA_TYPE_TEXT_JAVA_PROPERTIES);

    @Override
    public Set<String> supportedMediaTypes() {
        return SUPPORTED_MEDIA_TYPES;
    }

    @Override
    public ObjectNode parse(Content.ParsableContent content) throws ConfigParserException {
        Properties properties = new Properties();

        try (InputStream readable = content.data()) {
            properties.load(readable);
        } catch (Exception e) {
            throw new ConfigParserException("Cannot read from source: " + e.getLocalizedMessage(), e);
        }
        return toNode(properties);
    }

    private ConfigNode.ObjectNode toNode(Map<?, ?> map) {
        ConfigNode.ObjectNode.Builder builder = ConfigNode.ObjectNode.builder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            builder.addValue(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return builder.build();
    }
}
