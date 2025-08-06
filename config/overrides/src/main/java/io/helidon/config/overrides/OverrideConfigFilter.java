/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.helidon.builder.api.RuntimeType;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigSource;
import io.helidon.service.registry.Service;

/**
 * A config filter that replaces values with a new ones of keys that matching with {@link java.util.regex.Pattern}.
 */
@RuntimeType.PrototypedBy(OverrideConfig.class)
@Service.Singleton
public class OverrideConfigFilter implements ConfigFilter, RuntimeType.Api<OverrideConfig> {
    /**
     * Configuration key used by this filter when reading configuration for the config instance it filters.
     * This key is NOT used when using
     * {@link io.helidon.config.overrides.OverrideConfig.Builder#addConfigSource(io.helidon.config.spi.ConfigSource)}
     * and/or {@link #create(io.helidon.config.Config)}.
     *
     * @see #init(io.helidon.config.Config)
     */
    public static final String CONFIG_KEY = "overrides.expressions";

    private final OverrideConfig config;
    private final Set<OverrideEntry> explicitOverrides;

    private volatile Config lastOverrideConfigInstance;
    private volatile Config lastFilterConfigInstance;
    private volatile BiFunction<Config.Key, String, String> filterFunction;

    /**
     * Constructor request by Java {@link java.util.ServiceLoader}.
     */
    public OverrideConfigFilter() {
        this(null, OverrideConfig.builder().buildPrototype());
    }

    @Service.Inject
    OverrideConfigFilter(Optional<OverrideConfig> config) {
        this(null, config.orElseGet(() -> OverrideConfig.builder().buildPrototype()));
    }

    private OverrideConfigFilter(Config configNode, OverrideConfig config) {
        this.config = config;

        Set<OverrideEntry> entries = new LinkedHashSet<>();
        for (var entry : config.overridePatterns().entrySet()) {
            entries.add(OverrideEntry.create(entry.getKey(), entry.getValue()));
        }
        this.explicitOverrides = new LinkedHashSet<>(entries);

        if (configNode == null) {
            // there is no configuration
            this.filterFunction = filterFromEntries(entries);
            this.lastOverrideConfigInstance = null;
        } else {
            // we have a full configuration to configure this filter
            entries = entriesFromConfig(configNode, entries);
            this.filterFunction = filterFromEntries(entries);
            this.lastOverrideConfigInstance = configNode;

            addConfigOnChange(configNode, this::lastFilterConfigInstance, this::lastOverrideConfigInstance);
        }
    }

    /**
     * Create a new override config filter from its configuration.
     * This allows a custom set of patterns, as well as custom config sources to use to construct the override filter
     * (i.e. if there is a desire for watching for changes).
     * <p>
     * In case no configuration sources are provided, the
     *
     * @param config the override filter configuration
     * @return a new override filter
     */
    public static OverrideConfigFilter create(OverrideConfig config) {
        Config configNode;
        if (config.configSources().isEmpty()) {
            configNode = null;
        } else {
            configNode = Config.just(config.configSources().toArray(new ConfigSource[0]));
        }
        return new OverrideConfigFilter(configNode, config);
    }

    /**
     * Create an override config filter using an explicit override config instance.
     *
     * @param overrideConfig override config instance
     * @return filter using the provided config instance
     */
    public static OverrideConfigFilter create(Config overrideConfig) {
        return new OverrideConfigFilter(overrideConfig, OverrideConfig.create());
    }

    /**
     * Create a new override config filter updating its configuration.
     *
     * @param consumer builder consumer
     * @return a new override filter
     */
    public static OverrideConfigFilter create(Consumer<OverrideConfig.Builder> consumer) {
        var builder = OverrideConfig.builder();
        consumer.accept(builder);
        return builder.build();
    }

    /**
     * Create a new builder to customize configuration of the override filter.
     *
     * @return a new fluent API builder
     */
    public static OverrideConfig.Builder builder() {
        return OverrideConfig.builder();
    }

    @Override
    public void init(Config config) {
        if (!this.config.useTargetConfig()) {
            // ignore this config instance, just use whatever was configured when creating the filter
            return;
        }

        /*
        combine original configuration with configuration from the config node
        if there were config sources defined, also combine with this one
         */
        var filterConfig = config.get(CONFIG_KEY);

        Set<OverrideEntry> entries = new LinkedHashSet<>(this.explicitOverrides);
        if (lastOverrideConfigInstance != null) {
            entries = entriesFromConfig(lastOverrideConfigInstance, entries);
        }
        entries = entriesFromConfig(filterConfig.detach(), entries);

        this.filterFunction = filterFromEntries(entries);

        addConfigOnChange(filterConfig, this::lastOverrideConfigInstance, this::lastFilterConfigInstance);
    }

    @Override
    public String apply(Config.Key key, String stringValue) {
        return filterFunction.apply(key, stringValue);
    }

    @Override
    public OverrideConfig prototype() {
        return config;
    }

    private Optional<Config> lastOverrideConfigInstance() {
        return Optional.ofNullable(this.lastOverrideConfigInstance);
    }

    private Optional<Config> lastFilterConfigInstance() {
        return Optional.ofNullable(this.lastFilterConfigInstance);
    }

    private void lastOverrideConfigInstance(Config config) {
        this.lastOverrideConfigInstance = config;
    }

    private void lastFilterConfigInstance(Config config) {
        this.lastFilterConfigInstance = config;
    }

    private void addConfigOnChange(Config configNode,
                                   Supplier<Optional<Config>> otherConfigSupplier,
                                   Consumer<Config> updatedConfigConsumer) {
        configNode.onChange(updatedConfig -> {
            Set<OverrideEntry> entries = new LinkedHashSet<>(this.explicitOverrides);

            // update with changed data
            var otherConfig = otherConfigSupplier.get();
            if (otherConfig.isPresent()) {
                entries = entriesFromConfig(otherConfig.get().detach(), entries);
            }
            entries = entriesFromConfig(updatedConfig.detach(), entries);
            this.filterFunction = filterFromEntries(entries);
            updatedConfigConsumer.accept(updatedConfig);
        });
    }

    private Set<OverrideEntry> entriesFromConfig(Config configNode, Set<OverrideEntry> existingEntries) {
        Set<OverrideEntry> entries = new LinkedHashSet<>(existingEntries);
        // we do have a custom config instance
        Map<String, String> configuredPatterns = configNode.asMap().orElseGet(Map::of);
        // replace existing with ones from config, as config has higher priority than source code
        configuredPatterns.forEach((expression, value) -> entries.add(OverrideEntry.create(expression, value)));
        return entries;
    }

    private BiFunction<Config.Key, String, String> filterFromEntries(Set<OverrideEntry> entries) {
        return (key, value) -> {
            for (OverrideEntry entry : entries) {
                if (entry.matches(key)) {
                    // matched the pattern, return replacement
                    return entry.value();
                }
            }
            // fallback to the original value
            return value;
        };
    }

    private static class OverrideEntry {
        private final Pattern pattern;
        private final String value;

        private OverrideEntry(Pattern pattern, String value) {
            this.pattern = pattern;
            this.value = value;
        }

        static OverrideEntry create(String expression, String value) {
            var pattern = OverrideConfigSupport.expressionToPattern(expression);
            return new OverrideEntry(pattern, value);
        }

        static OverrideEntry create(Pattern pattern, String value) {
            return new OverrideEntry(pattern, value);
        }

        boolean matches(Config.Key key) {
            return pattern.matcher(key.toString()).matches();
        }

        String value() {
            return this.value;
        }
    }
}
