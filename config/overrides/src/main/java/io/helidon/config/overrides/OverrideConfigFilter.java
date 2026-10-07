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
import java.util.regex.Pattern;

import io.helidon.builder.api.RuntimeType;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigFilterProvider;

/**
 * An immutable configuration filter that replaces existing values whose keys match configured wildcard expressions
 * or regular expressions. The first matching rule wins. Filters do not add missing configuration nodes.
 * <p>
 * The optional module discovers an {@link OverrideConfigFilterProvider} automatically. For manual setup, register
 * {@code builder().buildProvider()} using {@link Config.Builder#addFilterProvider(io.helidon.config.spi.ConfigFilterProvider)}.
 * The provider owns separate definition sources for each configuration runtime and creates an immutable filter for
 * every generation. {@link #builder()} also supports constructing a fixed filter directly.
 */
public final class OverrideConfigFilter implements ConfigFilter, RuntimeType.Api<OverrideConfig> {
    /**
     * Configuration key containing inline wildcard expressions for the automatically discovered provider.
     * Explicit definition configurations supplied to {@link #create(Config)} contain the expressions directly,
     * without this prefix.
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
     * For independently changing definitions, register {@code builder().buildProvider()} instead.
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
        for (var source : config.configSources()) {
            definitionsBuilder.addSource(Objects.requireNonNull(source.get()));
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
    public static OverrideConfigFilter create(Consumer<Builder> consumer) {
        Objects.requireNonNull(consumer);
        return builder().update(consumer).build();
    }

    /**
     * Create a builder for fixed rules.
     *
     * @return filter configuration builder
     */
    public static Builder builder() {
        return new Builder();
    }

    static OverrideConfigFilter snapshot(OverrideConfig config, Config definitions) {
        List<OverrideEntry> entries = new ArrayList<>();
        config.overridePatterns().forEach((pattern, value) -> entries.add(new OverrideEntry(pattern, value)));
        config.overrideExpressions().forEach((expression, value) -> entries.add(new OverrideEntry(
                OverrideConfigSupport.expressionToPattern(expression), value)));
        entries.addAll(entriesFromConfig(definitions));
        return new OverrideConfigFilter(config, entries);
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
     * Builder for immutable filters or providers with independent definition machinery for each configuration runtime.
     * All settings are supplied by the generated {@link OverrideConfig} builder base.
     */
    public static final class Builder extends OverrideConfig.BuilderBase<Builder, OverrideConfig>
            implements io.helidon.common.Builder<Builder, OverrideConfigFilter> {
        private Builder() {
        }

        @Override
        public OverrideConfig buildPrototype() {
            return OverrideConfig.builder().from(this).build();
        }

        @Override
        public OverrideConfigFilter build() {
            return OverrideConfigFilter.create(buildPrototype());
        }

        /**
         * Build a provider which creates separate definition machinery for each configuration runtime. Inline rules
         * are captured now; source suppliers are called once for each runtime and must return independent sources and
         * monitoring resources. Register the provider with
         * {@link io.helidon.config.Config.Builder#addFilterProvider(io.helidon.config.spi.ConfigFilterProvider)}.
         *
         * @return provider of per-runtime factories and immutable generation filters
         */
        public ConfigFilterProvider buildProvider() {
            OverrideConfig config = buildPrototype();
            return _ -> OverrideFilterFactory.create(config);
        }
    }

    private record OverrideEntry(Pattern pattern, String value) {
    }
}
