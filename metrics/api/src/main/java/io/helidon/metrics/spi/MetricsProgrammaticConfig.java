/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
package io.helidon.metrics.spi;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.Weighted;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.ScopingConfig;
import io.helidon.metrics.api.SeMetricsProgrammaticConfig;

/**
 * Programmatic (rather than user-configurable) settings that govern certain metrics behavior.
 * <p>
 * Implementations of this interface are typically provided by Helidon itself rather than
 * developers building applications and are not intended for per-deployment (or even per-application)
 * customization.
 * </p>
 */
public interface MetricsProgrammaticConfig {

    /**
     * Returns the singleton instance of the metrics programmatic settings.
     *
     * @return the singleton
     */
    static MetricsProgrammaticConfig instance() {
        return Instance.INSTANCE.get();
    }

    /**
     * Default tag value to use for the scope tag if none is specified when the meter ID is created.
     *
     * @return default scope tag value
     */
    default Optional<String> scopeDefaultValue() {
        return Optional.empty();
    }

    /**
     * Name to use for a tag, added to each meter's identity, conveying its scope in output.
     *
     * @return the scope tag name
     */
    default Optional<String> scopeTagName() {
        return Optional.empty();
    }

    /**
     * Name to use for a tag, added to each meter's identity, conveying the application it belongs to.
     *
     * @return the app tag name
     */
    default Optional<String> appTagName() {
        return Optional.empty();
    }

    /**
     * Returns the reserved tag names (for scope and app).
     *
     * @return reserved tag names
     */
    default Set<String> reservedTagNames() {
        return Stream.of(scopeTagName(), appTagName())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Augments an existing {@link io.helidon.metrics.api.MetricsConfig.Builder}, presumably from actual config,
     * with overrides provided by this programmatic config instance.
     *
     * @param builder original metrics configuration builder
     * @return metrics config with any overrides applied
     */
    default MetricsConfig.Builder apply(MetricsConfig.Builder builder) {

        ScopingConfig.Builder scopingBuilder = builder
                .scoping()
                .map(ScopingConfig::builder)
                .orElseGet(ScopingConfig::builder);

        scopeDefaultValue().ifPresent(scopingBuilder::defaultValue);
        scopeTagName().ifPresent(scopingBuilder::tagName);

        appTagName().ifPresent(builder::appTagName);

        return builder
                .scoping(scopingBuilder);
    }

    /**
     * Creates a new {@link io.helidon.metrics.api.MetricsConfig} instance by applying overrides from this programmatic
     * config instance.
     *
     * @param metricsConfig original metrics configuration
     * @return new metrics configuration with overrides applied
     */
    default MetricsConfig apply(MetricsConfig metricsConfig) {
        return apply(MetricsConfig.builder(metricsConfig)).build();
    }

    /**
     * Internal use class to hold a reference to the singleton.
     */
    class Instance {

        private static final LazyValue<MetricsProgrammaticConfig> INSTANCE =
                LazyValue.create(() ->
                                         HelidonServiceLoader.builder(
                                                         ServiceLoader.load(
                                                                 MetricsProgrammaticConfig.class))
                                                 .addService(new SeMetricsProgrammaticConfig(),
                                                             Weighted.DEFAULT_WEIGHT - 50)
                                                 .build()
                                                 .asList()
                                                 .get(0));

        private Instance() {
        }
    }
}
