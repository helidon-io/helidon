/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.yaml;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParserException;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * YAML {@link ConfigParser} implementation that supports {@link io.helidon.common.media.type.MediaTypes#APPLICATION_YAML}.
 * <p>
 * The parser implementation supports {@link java.util.ServiceLoader}, i.e. {@link io.helidon.config.Config.Builder}
 * can automatically load and register {@code YamlConfigParser} instance,
 * if not {@link io.helidon.config.Config.Builder#disableParserServices() disabled}.
 * And of course it can be {@link io.helidon.config.Config.Builder#addParser(ConfigParser) registered programmatically}.
 * <p>
 * By default, YAML mapping keys use Helidon path syntax: dots separate path segments, and {@code ~1} and {@code ~0}
 * represent literal dots and tildes. Quoting a YAML key does not change this behavior.
 * To preserve mapping keys as literal names, use {@code builder().literalKeys(true).build()}.
 * The builder also accepts a configuration node containing {@code literal-keys} through its {@code config(Config)} method.
 * Register the configured parser on a source or with {@link io.helidon.config.Config.Builder#addParser(ConfigParser)}.
 * Parser options must be supplied before parsing; they are not read from the document being parsed.
 * <p>
 * Weight of the {@code YamlConfigParser} to be used by {@link io.helidon.config.Config.Builder},
 * if loaded automatically as a {@link java.util.ServiceLoader service}, is {@value WEIGHT}.
 *
 * @see io.helidon.config.Config.Builder#addParser(ConfigParser)
 * @see io.helidon.config.Config.Builder#disableParserServices()
 */
@Api.Stable
@Weight(YamlConfigParser.WEIGHT)
public class YamlConfigParser implements ConfigParser, RuntimeType.Api<YamlConfigParserConfig> {

    /**
     * Priority of the parser used if registered by {@link io.helidon.config.Config.Builder} automatically.
     */
    public static final double WEIGHT = Weighted.DEFAULT_WEIGHT - 10;

    private static final Set<MediaType> SUPPORTED_MEDIA_TYPES = Set.of(MediaTypes.APPLICATION_YAML,
                                                                       MediaTypes.APPLICATION_X_YAML);
    private static final List<String> SUPPORTED_SUFFIXES = List.of("yml", "yaml");

    private final YamlConfigParserConfig config;

    private YamlConfigParser(YamlConfigParserConfig config) {
        this.config = config;
    }

    /**
     * Default constructor needed by Java Service loader.
     */
    @Api.Internal
    public YamlConfigParser() {
        this(YamlConfigParserConfig.create());
    }

    /**
     * Create a new YAML Config Parser.
     *
     * @return a new instance of parser for YAML
     */
    public static YamlConfigParser create() {
        return builder().build();
    }

    /**
     * Create a YAML parser from a component configuration node containing {@code literal-keys}.
     *
     * @param config parser configuration
     * @return configured YAML parser
     */
    public static YamlConfigParser create(Config config) {
        return builder().config(Objects.requireNonNull(config)).build();
    }

    /**
     * Create a YAML parser from its immutable configuration.
     *
     * @param config parser configuration
     * @return configured YAML parser
     */
    public static YamlConfigParser create(YamlConfigParserConfig config) {
        return new YamlConfigParser(Objects.requireNonNull(config));
    }

    /**
     * Create a YAML parser by customizing its builder.
     *
     * @param consumer builder customization
     * @return configured YAML parser
     */
    public static YamlConfigParser create(Consumer<YamlConfigParserConfig.Builder> consumer) {
        return builder().update(Objects.requireNonNull(consumer)).build();
    }

    /**
     * Create a builder for a YAML parser. Literal keys are disabled by default.
     *
     * @return parser builder
     */
    public static YamlConfigParserConfig.Builder builder() {
        return YamlConfigParserConfig.builder();
    }

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return SUPPORTED_MEDIA_TYPES;
    }

    @Override
    public List<String> supportedSuffixes() {
        return SUPPORTED_SUFFIXES;
    }

    @Override
    public YamlConfigParserConfig prototype() {
        return config;
    }

    @Override
    public ObjectNode parse(Content content) throws ConfigParserException {
        try (InputStreamReader reader = new InputStreamReader(content.data(), content.charset())) {
            Map yamlMap = toMap(reader);
            if (yamlMap == null) { // empty source
                return ObjectNode.empty();
            }

            return fromMap(yamlMap);
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigParserException("Cannot read from source: " + e.getLocalizedMessage(), e);
        }
    }

    @Override
    public String toString() {
        return "YAML(" + MediaTypes.APPLICATION_YAML.text() + ")";
    }

    static Map toMap(Reader reader) {
        // the default of Snake YAML is a Map, safe constructor makes sure we never deserialize into anything
        // harmful
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        return (Map) yaml.loadAs(reader, Object.class);
    }

    private ObjectNode fromMap(Map<?, ?> map) {
        ObjectNode.Builder builder = ObjectNode.builder();
        if (map != null) {
            map.forEach((k, v) -> {
                String strKey = k.toString();
                if (config.literalKeys()) {
                    strKey = Config.Key.escapeName(strKey);
                }
                if (v instanceof List listValue) {
                    builder.addList(strKey, fromList(listValue));
                } else if (v instanceof Map mapValue) {
                    builder.addObject(strKey, fromMap(mapValue));
                } else {
                    String strValue = v == null ? "" : v.toString();
                    builder.addValue(strKey, strValue);
                }
            });
        }
        return builder.build();
    }

    private ListNode fromList(List<?> list) {
        ListNode.Builder builder = ListNode.builder();
        list.forEach(value -> {
            if (value instanceof List listValue) {
                builder.addList(fromList(listValue));
            } else if (value instanceof Map mapValue) {
                builder.addObject(fromMap(mapValue));
            } else {
                String strValue = value == null ? "" : value.toString();
                builder.addValue(strValue);
            }
        });
        return builder.build();
    }
}

