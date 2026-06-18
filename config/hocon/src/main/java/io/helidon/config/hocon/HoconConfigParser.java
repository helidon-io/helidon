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

package io.helidon.config.hocon;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.Api;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
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
import com.typesafe.config.ConfigResolver;
import com.typesafe.config.ConfigUtil;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

/**
 * Typesafe (Lightbend) Config (HOCON) {@link ConfigParser} implementation that supports following media types:
 * {@link io.helidon.common.media.type.MediaTypes#APPLICATION_HOCON} and
 * {@link io.helidon.common.media.type.MediaTypes#APPLICATION_JSON}.
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
     * Priority of the parser used if registered by {@link io.helidon.config.Config.Builder} automatically.
     */
    public static final double WEIGHT = Weighted.DEFAULT_WEIGHT - 10;

    private static final List<String> SUPPORTED_SUFFIXES = List.of("json", "conf");
    private static final Set<MediaType> SUPPORTED_MEDIA_TYPES =
            Set.of(MediaTypes.APPLICATION_HOCON, MediaTypes.APPLICATION_JSON);
    private static final Pattern HOCON_REFERENCE = Pattern.compile("(?<!\\\\)\\$\\{(\\?)?\\s*([^}]+?)\\s*}");
    private static final String LOCAL_RESOLVE_PATH = "helidon-local-resolution";

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
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
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
    public Set<MediaType> supportedMediaTypes() {
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
                return fromConfig(typesafeConfig.root());
            }

            return fromConfig(typesafeConfig.root(), typesafeConfig, List.of());
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

    @Override
    public String toString() {
        return "HOCON(" + MediaTypes.APPLICATION_HOCON.text() + ")";
    }

    private static ObjectNode fromConfig(ConfigObject configObject) {
        return fromConfig(configObject, configObject.toConfig(), List.of(), false);
    }

    private static ObjectNode fromConfig(ConfigObject configObject, Config config, List<String> path) {
        return fromConfig(configObject, config, path, true);
    }

    private static ObjectNode fromConfig(ConfigObject configObject, Config config, List<String> path, boolean locallyResolve) {
        ObjectNode.Builder builder = ObjectNode.builder();
        configObject.forEach((unescapedKey, value) -> {
            List<String> childPath = childPath(path, unescapedKey);
            ConfigValue valueToConvert = valueToConvert(value, config, childPath, locallyResolve);

            String key = io.helidon.config.Config.Key.escapeName(unescapedKey);
            if (valueToConvert instanceof ConfigList configList) {
                builder.addList(key, fromList(configList, config, childPath, locallyResolve));
            } else if (valueToConvert instanceof ConfigObject hoconObject) {
                builder.addObject(key, fromConfig(hoconObject, config, childPath, locallyResolve));
            } else {
                try {
                    Object unwrapped = valueToConvert.unwrapped();
                    if (unwrapped == null) {
                        builder.addValue(key, "");
                    } else {
                        builder.addValue(key, String.valueOf(unwrapped));
                    }
                } catch (com.typesafe.config.ConfigException.NotResolved e) {
                    // An unresolved ConfigReference resolved later in config module since
                    // Helidon and Hocon use the same reference syntax and resolving here
                    // would be too early for resolution across sources
                    builder.addValue(key, valueToConvert.render());
                }
            }
        });
        return builder.build();
    }

    private static ListNode fromList(ConfigList list, Config config, List<String> path, boolean locallyResolve) {
        ListNode.Builder builder = ListNode.builder();
        for (int i = 0; i < list.size(); i++) {
            ConfigValue value = list.get(i);
            List<String> childPath = childPath(path, String.valueOf(i));
            ConfigValue valueToConvert = valueToConvert(value, config, childPath, locallyResolve);
            if (valueToConvert instanceof ConfigList configList) {
                builder.addList(fromList(configList, config, childPath, locallyResolve));
            } else if (valueToConvert instanceof ConfigObject configObject) {
                builder.addObject(fromConfig(configObject, config, childPath, locallyResolve));
            } else {
                try {
                    Object unwrapped = valueToConvert.unwrapped();
                    if (unwrapped == null) {
                        builder.addValue("");
                    } else {
                        builder.addValue(String.valueOf(unwrapped));
                    }
                } catch (com.typesafe.config.ConfigException.NotResolved e) {
                    // An unresolved ConfigReference resolved later in config module since
                    // Helidon and Hocon use the same reference syntax and resolving here
                    // would be too early for resolution across sources
                    builder.addValue(valueToConvert.render());
                }
            }
        }
        return builder.build();
    }

    private static ConfigValue valueToConvert(ConfigValue value, Config config, List<String> path, boolean locallyResolve) {
        if (!locallyResolve || !needsLocalResolution(value)) {
            return value;
        }

        String hoconPath = ConfigUtil.joinPath(path);
        try {
            // Materialize delayed HOCON merges while preserving required references for Helidon source-merge resolution.
            return config.withOnlyPath(hoconPath)
                    .resolve(localResolveOptions(value))
                    .getValue(hoconPath);
        } catch (com.typesafe.config.ConfigException e) {
            return resolveLocalValue(value);
        }
    }

    private static ConfigValue resolveLocalValue(ConfigValue value) {
        try {
            return value.atPath(LOCAL_RESOLVE_PATH)
                    .resolve(localResolveOptions(value))
                    .getValue(LOCAL_RESOLVE_PATH);
        } catch (com.typesafe.config.ConfigException e) {
            return value;
        }
    }

    private static boolean needsLocalResolution(ConfigValue value) {
        try {
            value.valueType();
            return false;
        } catch (com.typesafe.config.ConfigException.NotResolved e) {
            return true;
        }
    }

    private static ConfigResolveOptions localResolveOptions(ConfigValue value) {
        return ConfigResolveOptions.defaults()
                .setAllowUnresolved(true)
                .setUseSystemEnvironment(false)
                .appendResolver(new DeferredReferenceResolver(referencePaths(value, false),
                                                             referencePaths(value, true)));
    }

    static Set<List<String>> referencePaths(ConfigValue value, boolean optional) {
        Set<List<String>> result = new HashSet<>();
        Matcher matcher = HOCON_REFERENCE.matcher(value.render());
        while (matcher.find()) {
            boolean referenceOptional = matcher.group(1) != null;
            if (optional == referenceOptional) {
                try {
                    result.add(ConfigUtil.splitPath(matcher.group(2).trim()));
                } catch (com.typesafe.config.ConfigException e) {
                    // Ignore values that are not parseable as HOCON paths.
                }
            }
        }
        return result;
    }

    private static List<String> childPath(List<String> path, String child) {
        List<String> childPath = new ArrayList<>(path.size() + 1);
        childPath.addAll(path);
        childPath.add(child);
        return childPath;
    }

    private static class DeferredReferenceResolver implements ConfigResolver {
        private final Set<List<String>> requiredReferences;
        private final Set<List<String>> optionalReferences;

        DeferredReferenceResolver(Set<List<String>> requiredReferences, Set<List<String>> optionalReferences) {
            this.requiredReferences = requiredReferences;
            this.optionalReferences = optionalReferences;
        }

        @Override
        public ConfigValue lookup(String path) {
            List<String> referencePath;
            try {
                referencePath = ConfigUtil.splitPath(path);
            } catch (com.typesafe.config.ConfigException e) {
                return ConfigValueFactory.fromAnyRef("${" + path + "}");
            }

            if (!requiredReferences.contains(referencePath) && optionalReferences.contains(referencePath)) {
                return null;
            }

            return ConfigValueFactory.fromAnyRef("${" + path + "}");
        }

        @Override
        public ConfigResolver withFallback(ConfigResolver fallback) {
            return this;
        }
    }

}
