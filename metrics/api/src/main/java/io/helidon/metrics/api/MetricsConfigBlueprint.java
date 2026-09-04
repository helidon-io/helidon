/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;
import io.helidon.metrics.spi.MetricsPublisherProvider;

/**
 * Configuration settings for metrics.
 */
@Prototype.Configured(MetricsConfigBlueprint.METRICS_CONFIG_KEY)
@Prototype.Blueprint(decorator = MetricsConfigSupport.BuilderDecorator.class)
@Prototype.CustomMethods(MetricsConfigSupport.class)
interface MetricsConfigBlueprint {

    /**
     * The config key containing settings for all of metrics.
     */
    String METRICS_CONFIG_KEY = "metrics";

    /**
     * No-op, will be removed.
     *
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    String SCOPE_CONFIG_KEY = "scoping";

    /**
     * Config key for KPI metrics settings.
     */
    String KEY_PERFORMANCE_INDICATORS_CONFIG_KEY = "key-performance-indicators";

    /**
     * Default JSON unit.
     */
    TimeUnit DEFAULT_JSON_UNITS_DEFAULT = TimeUnit.SECONDS;

    /**
     * Whether metrics functionality is enabled.
     *
     * @return if metrics are configured to be enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Whether to allow anybody to access the metrics endpoint when this config is used by a metrics observer.
     * This setting has no effect in the top-level {@code metrics} config which controls the shared registry.
     *
     * @return whether to permit access to the observer's metrics endpoint to anybody, defaults to {@code true}
     * @see #roles()
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean permitAll();

    /**
     * Role names allowed to access the metrics endpoint when this config is used by a metrics observer and
     * {@link #permitAll()} is {@code false}.
     * This setting has no effect in the top-level {@code metrics} config which controls the shared registry.
     *
     * @return allowed role names
     */
    @Option.Configured
    @Option.Default("observe")
    List<String> roles();

    /**
     * Key performance indicator metrics settings.
     *
     * @return key performance indicator metrics settings
     */
    @Option.Configured(KEY_PERFORMANCE_INDICATORS_CONFIG_KEY)
    KeyPerformanceIndicatorMetricsConfig keyPerformanceIndicatorMetricsConfig();

    /**
     * Global tags.
     *
     * @return name/value pairs for global tags
     */
    @Option.Configured
    // for compatibility with MP metrics and earlier Helidon releases
    List<Tag> tags();

    /**
     * Value for the application tag to be added to each meter ID.
     *
     * @return application tag value
     */
    @Option.Configured
    Optional<String> appName();

    /**
     * Name for the application tag to be added to each meter ID.
     *
     * @return application tag name
     */
    @Option.Configured
    Optional<String> appTagName();

    /**
     * No-op, will be removed.
     *
     * @return ignored settings
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Option.Configured(metadata = false)
    ScopingConfig scoping();

    /**
     * Whether automatic REST request metrics should be measured.
     *
     * @return true/false
     */
    @Option.Configured("rest-request.enabled")
    @Option.DefaultBoolean(false)
    boolean restRequestEnabled();

    /**
     * Whether Helidon should expose meters related to virtual threads.
     *
     * @return true to include meters related to virtual threads
     */
    @Option.Configured("virtual-threads.enabled")
    @Option.DefaultBoolean(false)
    boolean virtualThreadsEnabled();

    /**
     * Threshold for sampling pinned virtual threads to include in the pinned threads meter.
     *
     * @return threshold used to filter virtual thread pinning events
     */
    @Option.Configured("virtual-threads.pinned.threshold")
    @Option.Default("PT0.020S")
    Duration virtualThreadsPinnedThreshold();

    /**
     * Metrics configuration node.
     *
     * @return metrics configuration
     */
    @Option.Redundant
    Config config();

    /**
     * Output format for built-in meter names.
     * <p>
     * {@link BuiltInMeterNameFormat#SNAKE} selects "snake_case" which does not conform to the MicroProfile
     * Metrics specification.
     *
     * @return the output format for built-in meter names
     */
    @Option.Configured
    @Option.Default("CAMEL")
    BuiltInMeterNameFormat builtInMeterNameFormat();

    /**
     * Default units for timer output in JSON if not specified on a given timer.
     * <p>
     * If the configuration key is absent, the Helidon JSON output uses {@link java.util.concurrent.TimeUnit#SECONDS}.
     * If the configuration key is present, Helidon formats each timer using that timer's specific units (if set) and
     * the config value otherwise.
     *
     * @return default {@link java.util.concurrent.TimeUnit} to use for JSON timer output
     */
    @Option.Configured("timers.json-units-default")
    Optional<TimeUnit> jsonUnitsDefault();

    /**
     * Whether to log warnings when multiple registries are created.
     * <p>
     * By far most applications use a single meter registry, but certain app or library programming errors can cause Helidon to
     * create more than one. By default, Helidon logs warning messages for each additional meter registry created. This
     * setting allows users with apps that <em>need</em> multiple meter registries to suppress those warnings.
     *
     * @return whether to log warnings upon creation of multiple meter registries
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean warnOnMultipleRegistries();

    /**
     * Metrics publishers which make the metrics data available to external systems. Helidon's Micrometer-based
     * metrics provider includes publishers with the config keys {@code prometheus} (inferred by default) and {@code otlp}.
     * See the config reference entries for {@code io.helidon.metrics.providers.micrometer.PrometheusPublisher} and
     * {@code io.helidon.metrics.providers.micrometer.OtlpPublisher}.
     *
     * @return metrics publishers
     */
    @Option.Configured
    @Option.Provider(value = MetricsPublisherProvider.class, discoverServices = false)
    @Option.Singular
    List<MetricsPublisher> publishers();

    /**
     * No-op, will be removed.
     *
     * @param scope ignored; must not be {@code null}
     * @return true
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default boolean isScopeEnabled(String scope) {
        Objects.requireNonNull(scope);
        return true;
    }

    /**
     * Determines whether the meter with the specified name is enabled.
     *
     * @param name meter name
     * @return whether the meter is enabled
     */
    default boolean isMeterEnabled(String name) {
        Objects.requireNonNull(name);
        return enabled();
    }

    /**
     * No-op, will be removed.
     *
     * @param name  meter name
     * @param scope ignored; must not be {@code null}
     * @return whether the meter is enabled
     * @deprecated No-op, will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default boolean isMeterEnabled(String name, String scope) {
        Objects.requireNonNull(scope);
        return isMeterEnabled(name);
    }
}
