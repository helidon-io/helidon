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

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigSource;

/**
 * Configuration of an override filter provider.
 */
@Prototype.Blueprint
@Prototype.Configured("overrides")
interface OverrideConfigBlueprint extends Prototype.Factory<OverrideConfigFilterProvider> {
    /**
     * Ordered regular expression rules. The first matching rule wins, and patterns take precedence over wildcard
     * expressions. Empty by default.
     *
     * @return regular expression rules
     */
    @Option.Singular
    @Option.Configured("patterns")
    List<OverridePatternConfig> overridePatterns();

    /**
     * Explicit config override settings, using expressions with {@code *} to match one or more word characters.
     * Dots are escaped and other regular expression metacharacters retain their meaning, matching the legacy conversion.
     * For example, a wildcard does not match a hyphenated segment.
     * Empty by default.
     *
     * @return a map of an expression to a value, i.e. {@code environments.*.batch-size=200}
     */
    @Option.Singular
    @Option.Configured("expressions")
    Map<String, String> overrideExpressions();

    /**
     * Suppliers of definition sources whose content maps wildcard expressions to replacement values. A provider calls
     * each supplier once for every independently built configuration runtime. Suppliers must return fresh source instances,
     * including their polling and watching resources, when a provider is reused across runtimes. Empty by default.
     * Configuration-based sources are supplied through {@link #sourceDescriptors()}.
     *
     * @return config sources to use to obtain the configuration of overrides
     */
    @Option.Singular
    List<Supplier<? extends ConfigSource>> configSources();

    /**
     * Standard Config source descriptors for files containing override expressions. Each descriptor contains
     * {@code type} and {@code properties}, as accepted by {@link io.helidon.config.MetaConfig#configSource(Config)}.
     * Descriptors are captured when the provider is built and resolved separately for each configuration runtime,
     * after programmatic source suppliers. Their locations and monitoring settings remain fixed for that runtime;
     * the loaded definition contents may change. Empty by default.
     *
     * @return definition source descriptors
     */
    @Option.Singular
    @Option.Configured("sources")
    List<Config> sourceDescriptors();
}
