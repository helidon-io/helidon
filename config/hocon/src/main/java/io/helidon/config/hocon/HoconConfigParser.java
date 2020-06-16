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

package io.helidon.config.hocon;

import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Priority;

import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
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
 * if loaded automatically as a {@link java.util.ServiceLoader service}, is {@value PRIORITY}.
 *
 * @see io.helidon.config.Config.Builder#addParser(ConfigParser)
 * @see io.helidon.config.Config.Builder#disableParserServices()
 */
@Priority(HoconConfigParser.PRIORITY)
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
    public static final int PRIORITY = ConfigParser.PRIORITY + 100;

    private static final Set<String> SUPPORTED_MEDIA_TYPES =
            Set.of(MEDIA_TYPE_APPLICATION_HOCON, MEDIA_TYPE_APPLICATION_JSON);

    private final boolean resolvingEnabled;
    private final ConfigResolveOptions resolveOptions;

    /**
     * Initializes parser.
     *
     * @param resolvingEnabled resolving substitutions support enabled
     * @param resolveOptions   resolving options
     */
    HoconConfigParser(boolean resolvingEnabled, ConfigResolveOptions resolveOptions) {
        if (resolvingEnabled) {
            Objects.requireNonNull(resolveOptions, "resolveOptions parameter is mandatory");
        }

        this.resolvingEnabled = resolvingEnabled;
        this.resolveOptions = resolveOptions;
    }

    /**
     * To be used by Java Service Loader only!!!
     *
     * @deprecated Use {@link #builder()} to construct a customized instance, or {@link #create()} to get an instance with
     * defaults
     */
    @Deprecated
    public HoconConfigParser() {
        this(true, ConfigResolveOptions.defaults());
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
        Config typesafeConfig;
        try (InputStreamReader readable = new InputStreamReader(content.data(), content.charset())) {
            typesafeConfig = ConfigFactory.parseReader(readable);
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

    private static ObjectNode fromConfig(ConfigObject config) {
        ObjectNode.Builder builder = ObjectNode.builder();
        config.forEach((unescapedKey, value) -> {
            String key = io.helidon.config.Config.Key.escapeName(unescapedKey);
            if (value instanceof ConfigList) {
                builder.addList(key, fromList((ConfigList) value));
            } else if (value instanceof ConfigObject) {
                builder.addObject(key, fromConfig((ConfigObject) value));
            } else {
                builder.addValue(key, value.unwrapped().toString());
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
                builder.addValue(value.unwrapped().toString());
            }
        });
        return builder.build();
    }

}
