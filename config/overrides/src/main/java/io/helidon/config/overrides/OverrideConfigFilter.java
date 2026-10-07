/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.overrides;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import io.helidon.builder.api.RuntimeType;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigSource;

/**
 * An immutable configuration filter that replaces existing values whose keys match configured wildcard expressions
 * or regular expressions. The first matching rule wins. Filters do not add missing configuration nodes.
 * <p>
 * Register a fixed filter using {@link Config.Builder#addFilter(ConfigFilter)}. To obtain new rules for each target
 * configuration generation, register a factory from {@link #fromConfig()} or {@link #fromConfig(Config)} instead.
 */
public final class OverrideConfigFilter implements ConfigFilter, RuntimeType.Api<OverrideConfig> {
    /**
     * Configuration key read from the target configuration by {@link #fromConfig()}.
     * Explicit definition configurations supplied to {@link #create(Config)} or {@link #fromConfig(Config)} contain
     * the expressions directly, without this prefix.
     */
    public static final String CONFIG_KEY = "overrides.expressions";

    private final OverrideConfig config;
    private final List<OverrideEntry> entries;

    private OverrideConfigFilter(OverrideConfig config, List<OverrideEntry> entries) {
        this.config = config;
        this.entries = List.copyOf(entries);
    }

    /**
     * Create a fixed filter from programmatic rules and optional configuration sources.
     * Programmatic regular expression patterns precede programmatic wildcard expressions, followed by expressions loaded
     * from sources. Sources are loaded once; background change support started for this private configuration is stopped
     * after its values have been captured.
     * For independently changing definitions, use {@link #fromConfig(Config)} and {@link #connect(Config, Config)}.
     *
     * @param config filter configuration
     * @return immutable filter
     */
    public static OverrideConfigFilter create(OverrideConfig config) {
        Objects.requireNonNull(config);
        List<OverrideEntry> entries = new ArrayList<>();
        config.overridePatterns().forEach((pattern, value) -> entries.add(new OverrideEntry(pattern, value)));
        config.overrideExpressions().forEach((expression, value) -> entries.add(new OverrideEntry(
                OverrideConfigSupport.expressionToPattern(expression), value)));
        if (config.configSources().isEmpty()) {
            return new OverrideConfigFilter(config, entries);
        }

        var definitionsBuilder = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableValueResolving()
                .disableFilterServices();
        for (ConfigSource source : config.configSources()) {
            definitionsBuilder.addSource(source);
        }
        Config definitions = definitionsBuilder.build();
        try {
            entries.addAll(entriesFromConfig(definitions));
            return new OverrideConfigFilter(config, entries);
        } finally {
            definitions.context().stopChangeSupport();
        }
    }

    /**
     * Capture a fixed filter from the supplied definition snapshot.
     * This factory does not subscribe to changes or stop change support on the caller-owned configuration.
     *
     * @param definitions configuration containing wildcard expressions mapped to replacement values
     * @return immutable filter using the supplied snapshot
     */
    public static OverrideConfigFilter create(Config definitions) {
        Objects.requireNonNull(definitions);
        return builder().config(definitions).build();
    }

    /**
     * Create a fixed filter by customizing its configuration.
     *
     * @param consumer builder customization
     * @return immutable filter
     */
    public static OverrideConfigFilter create(Consumer<OverrideConfig.Builder> consumer) {
        Objects.requireNonNull(consumer);
        return builder().update(consumer).build();
    }

    /**
     * Create a builder for fixed rules.
     *
     * @return filter configuration builder
     */
    public static OverrideConfig.Builder builder() {
        return OverrideConfig.builder();
    }

    /**
     * Create a filter factory that reads {@value #CONFIG_KEY} from each target configuration generation.
     * Register the returned factory with {@link Config.Builder#addFilter(Function)} so target reloads capture new rules.
     *
     * @return factory producing an immutable filter for each target generation
     */
    public static Function<Config, ConfigFilter> fromConfig() {
        return target -> create(target.get(CONFIG_KEY));
    }

    /**
     * Create a filter factory that captures the latest independently managed definition configuration whenever
     * the target configuration is rebuilt. Registration alone does not cause target reloads when definitions change;
     * use {@link #connect(Config, Config)} to connect those notifications.
     * The caller retains ownership of change support on the definition configuration.
     *
     * @param definitions configuration containing wildcard expressions mapped to replacement values
     * @return factory producing an immutable filter from the latest definition snapshot
     */
    public static Function<Config, ConfigFilter> fromConfig(Config definitions) {
        Objects.requireNonNull(definitions);
        return _ -> create(definitions.context().last());
    }

    /**
     * Connect definition changes to target configuration reloads. The target must use a filter factory returned by
     * {@link #fromConfig(Config)} for the same definition configuration. Connection starts an asynchronous reconciliation
     * to capture changes that occurred while the target was being built.
     * Close the returned handle when this connection is no longer needed; neither configuration's caller-owned change
     * support is stopped by closing the connection. A reload already admitted when closing may finish afterward.
     * Definition and target configurations must be independent trees; direct or indirect dependency cycles are unsupported.
     *
     * @param definitions independently changing definition configuration
     * @param target target configuration using these definitions
     * @return closeable notification connection
     */
    public static ChangeSupport connect(Config definitions, Config target) {
        Objects.requireNonNull(definitions);
        Objects.requireNonNull(target);
        return OverrideChangeSupport.create(definitions, target);
    }

    @Override
    public String apply(Config.Key key, String stringValue) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(stringValue);
        for (OverrideEntry entry : entries) {
            if (entry.pattern().matcher(key.toString()).matches()) {
                return entry.value();
            }
        }
        return stringValue;
    }

    @Override
    public OverrideConfig prototype() {
        return config;
    }

    private static List<OverrideEntry> entriesFromConfig(Config definitions) {
        List<OverrideEntry> entries = new ArrayList<>();
        definitions.asMap().orElseGet(Map::of)
                .forEach((expression, value) -> entries.add(new OverrideEntry(
                        OverrideConfigSupport.expressionToPattern(expression), value)));
        return entries;
    }

    /**
     * A connection between definition changes and target configuration reloads.
     */
    public interface ChangeSupport extends AutoCloseable {
        /**
         * Stop forwarding changes and release this connection's resources.
         * A reload already admitted may finish after this method returns.
         */
        @Override
        void close();
    }

    private record OverrideEntry(Pattern pattern, String value) {
    }
}
