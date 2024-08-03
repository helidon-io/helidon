/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import java.util.Properties;
import java.util.Set;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;
import io.helidon.metadata.compile.Spotbugs;

/**
 * {@link ConfigParser} implementation that parses Java Properties content.
 * <p>
 * The parser implementation supports {@link java.util.ServiceLoader}, i.e. {@link io.helidon.config.Config.Builder}
 * can automatically load and register {@code PropertiesConfigParser} instance,
 * if not {@link io.helidon.config.Config.Builder#disableParserServices() disabled}.
 * And of course it can be {@link io.helidon.config.Config.Builder#addParser(ConfigParser) registered programmatically}.
 * <p>
 * Weight of the {@code PropertiesConfigParser} to be used by {@link io.helidon.config.Config.Builder},
 * if loaded automatically as a {@link java.util.ServiceLoader service}, is {@value #WEIGHT}.
 *
 * @see io.helidon.config.Config.Builder#addParser(ConfigParser)
 * @see io.helidon.config.Config.Builder#disableParserServices()
 */
@Weight(PropertiesConfigParser.WEIGHT)
public class PropertiesConfigParser implements ConfigParser {

    /**
     * Java properties media type.
     */
    public static final MediaType MEDIA_TYPE_TEXT_JAVA_PROPERTIES = MediaTypes.create("text/x-java-properties");

    /**
     * Priority of the parser used if registered by {@link io.helidon.config.Config.Builder} automatically.
     */
    public static final double WEIGHT = Weighted.DEFAULT_WEIGHT - 10;

    private static final Set<MediaType> SUPPORTED_MEDIA_TYPES = Set.of(MEDIA_TYPE_TEXT_JAVA_PROPERTIES);

    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    public PropertiesConfigParser() {
    }

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return SUPPORTED_MEDIA_TYPES;
    }

    @Override
    public ConfigNode.ObjectNode parse(ConfigParser.Content content) throws ConfigParserException {
        Properties properties = new Properties();
        try {
            properties.load(content.data());
        } catch (Exception e) {
            throw new ConfigParserException("Cannot read from source: " + e.getLocalizedMessage(), e);
        }

        return ConfigUtils.mapToObjectNode(ConfigUtils.propertiesToMap(properties), true);
    }

}
