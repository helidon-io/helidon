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

package io.helidon.config.hocon;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;

/**
 * Typesafe (Lightbend) Config (HOCON) {@link ConfigParser} implementation that supports following media types:
 * {@value #MEDIA_TYPE_APPLICATION_HOCON} and
 * {@value #MEDIA_TYPE_APPLICATION_JSON}.
 * <p>
 * The parser implementation supports {@link java.util.ServiceLoader}, i.e. {@link io.helidon.config.Config.Builder}
 * can automatically load and register {@code HoconConfigParser} instance,
 * if not {@link io.helidon.config.Config.Builder#disableParserServices() disabled}.
 * And of course it can be {@link io.helidon.config.Config.Builder#addParser(ConfigParser) registered programmatically}.
 * <p>
 * Priority of the {@code HoconConfigParser} to be used by {@link io.helidon.config.Config.Builder},
 * if loaded automatically as a {@link java.util.ServiceLoader service}, is {@value WEIGHT}.
 *
 * @see io.helidon.config.Config.Builder#addParser(ConfigParser)
 * @see io.helidon.config.Config.Builder#disableParserServices()
 */
@Weight(HoconConfigParser.WEIGHT)
public class HoconConfigParser implements ConfigParser {

    /**
     * A String constant representing {@value} media type.
     */
    public static final String MEDIA_TYPE_APPLICATION_HOCON = "application/hocon";
    /**
     * A String constant representing {@value} media type.
     */
    public static final String MEDIA_TYPE_APPLICATION_JSON = "application/json";
    /**
     * Priority of the parser used if registered by {@link io.helidon.config.Config.Builder} automatically.
     */
    public static final double WEIGHT = Weighted.DEFAULT_WEIGHT - 10;

    private static final List<String> SUPPORTED_SUFFIXES = List.of("json", "conf");
    private static final Set<String> SUPPORTED_MEDIA_TYPES =
            Set.of(MEDIA_TYPE_APPLICATION_HOCON, MEDIA_TYPE_APPLICATION_JSON);

    private final boolean resolvingEnabled;
    private final ConfigResolveOptions resolveOptions;
    private final ConfigParseOptions parseOptions;
    private final HoconConfigIncluder includer;

    HoconConfigParser(HoconConfigParserBuilder builder) {
        this.resolvingEnabled = builder.resolvingEnabled();
        this.resolveOptions = builder.resolveOptions();
        this.parseOptions = Objects.requireNonNull(builder.parseOptions());
        this.includer = builder.includer();

        if (resolvingEnabled) {
            Objects.requireNonNull(resolveOptions, "resolveOptions parameter is mandatory");
        }
    }

    /**
     * To be used by Java Service Loader only!!!
     *
     * @deprecated Use {@link #builder()} to construct a customized instance, or {@link #create()} to get an instance with
     *         defaults
     */
    @Deprecated
    public HoconConfigParser() {
        this(builder());
    }

    /**
     * Create a new instance of HOCON config parser using default configuration.
     *
     * @return a new instance of parser
     * @see #builder()
     */
    public static HoconConfigParser create() {
        return builder().build();
    }

    /**
     * Create a new fluent API builder for a HOCON config parser.
     *
     * @return a new builder instance
     */
    public static HoconConfigParserBuilder builder() {
        return new HoconConfigParserBuilder();
    }

    @Override
    public Set<String> supportedMediaTypes() {
        return SUPPORTED_MEDIA_TYPES;
    }

    @Override
    public ObjectNode parse(Content content) {
        return parse(content, it -> Optional.empty());
    }

    @Override
    public synchronized ObjectNode parse(Content content, Function<String, Optional<InputStream>> relativeResolver) {
        includer.parseOptions(parseOptions);
        includer.relativeResourceFunction(relativeResolver);
        includer.charset(content.charset());

        Config typesafeConfig;
        try (InputStreamReader readable = new InputStreamReader(content.data(), content.charset())) {
            typesafeConfig = ConfigFactory.parseReader(readable, parseOptions);
            if (resolvingEnabled) {
                typesafeConfig = typesafeConfig.resolve(resolveOptions);
            }

            return fromConfig(typesafeConfig.root());
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigParserException("Cannot read from source: " + e.getLocalizedMessage(), e);
        }
    }

    @Override
    public List<String> supportedSuffixes() {
        return SUPPORTED_SUFFIXES;
    }

    private static ObjectNode fromConfig(ConfigObject config) {
        ObjectNode.Builder builder = ObjectNode.builder();
        config.forEach((unescapedKey, value) -> {
            String key = io.helidon.config.Config.Key.escapeName(unescapedKey);
            if (value instanceof ConfigList) {
                builder.addList(key, fromList((ConfigList) value));
            } else if (value instanceof ConfigObject) {
                builder.addObject(key, fromConfig((ConfigObject) value));
            } else {
                try {
                    Object unwrapped = value.unwrapped();
                    if (unwrapped == null) {
                        builder.addValue(key, "");
                    } else {
                        builder.addValue(key, String.valueOf(unwrapped));
                    }
                } catch (com.typesafe.config.ConfigException.NotResolved e) {
                    // An unresolved ConfigReference resolved later in config module since
                    // Helidon and Hocon use the same reference syntax and resolving here
                    // would be too early for resolution across sources
                    builder.addValue(key, value.render());
                }
            }
        });
        return builder.build();
    }

    private static ListNode fromList(ConfigList list) {
        ListNode.Builder builder = ListNode.builder();
        list.forEach(value -> {
            if (value instanceof ConfigList) {
                builder.addList(fromList((ConfigList) value));
            } else if (value instanceof ConfigObject) {
                builder.addObject(fromConfig((ConfigObject) value));
            } else {
                try {
                    Object unwrapped = value.unwrapped();
                    if (unwrapped == null) {
                        builder.addValue("");
                    } else {
                        builder.addValue(String.valueOf(unwrapped));
                    }
                } catch (com.typesafe.config.ConfigException.NotResolved e) {
                    // An unresolved ConfigReference resolved later in config module since
                    // Helidon and Hocon use the same reference syntax and resolving here
                    // would be too early for resolution across sources
                    builder.addValue(value.render());
                }
            }
        });
        return builder.build();
    }

}
