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
package io.helidon.metrics.api;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;

/**
 * Configuration settings for metrics.
 *
 * <h2>Scope handling configuration</h2>
 * Helidon allows developers to associate a scope with each meter. The {@value SCOPE_CONFIG_KEY} subsection of the
 * {@value METRICS_CONFIG_KEY} configuration controls
 * <ul>
 *     <li>the default scope value to use if a meter is registered without an explicit scope setting, and</li>
 *     <li>whether and how Helidon records each meter's scope as a tag in the underlying implementation meter registry.
 *     <p>
 *         Specifically, users can specify whether scope tags are used at all and, if so, what tag name to use.
 *     </li>
 * </ul>
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
     * Config key for scope-related settings.
     */
    String SCOPE_CONFIG_KEY = "scoping";

    /**
     * Config key for KPI metrics settings.
     */
    String KEY_PERFORMANCE_INDICATORS_CONFIG_KEY = "key-performance-indicators";

    TimeUnit DEFAULT_JSON_UNITS_DEFAULT = TimeUnit.SECONDS;

    /**
     * This method is internal and will be removed without replacement.
     *
     * @param globalTagExpression config node
     * @return list of tags
     * @deprecated this is an internal method used from the builder, will be removed without replacement
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    static List<Tag> createTags(Config globalTagExpression) {
        return createTags(globalTagExpression.asString().get());
    }

    /**
     * This method is internal and will be removed without replacement.
     *
     * @param pairs tag pairs
     * @return list of tags
     * @deprecated this is an internal method used from the builder, will be removed without replacement
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    static List<Tag> createTags(String pairs) {
        return MetricsConfigSupport.createTags(pairs);
    }

    /**
     * Whether metrics functionality is enabled.
     *
     * @return if metrics are configured to be enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Whether to allow anybody to access the endpoint.
     *
     * @return whether to permit access to metrics endpoint to anybody, defaults to {@code true}
     * @see #roles()
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean permitAll();

    /**
     * Hints for role names the user is expected to be in.
     *
     * @return list of hints
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
     * Settings related to scoping management.
     *
     * @return scoping settings
     */
    @Option.Configured
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
     * Whether automatic REST request metrics should be measured (as indicated by the deprecated config
     * key {@code rest-request-enabled}, the config key using a hyphen instead of a dot separator).
     *
     * @return true/false
     * @deprecated Use {@code rest-request.enabled} instead.
     */
    @Deprecated(since = "4.2.3", forRemoval = true)
    @Option.Configured("rest-request-enabled")
    @Option.Access("")
    @Option.Decorator(MetricsConfigSupport.RestRequestEnabledDecorator.class)
    Optional<Boolean> restRequestEnabledShadow();

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
     * Whether the {@code gc.time} meter should be registered as a gauge (vs. a counter).
     * The {@code gc.time} meter is inspired by the MicroProfile Metrics spec, in which the meter was originally checked to
     * be a counter but starting in 5.1 was checked be a gauge. For the duration of Helidon 4.x users can choose which
     * type of meter Helidon registers for {@code gc.time}.
     *
     * @return the type of meter to use for registering {@code gc.time}
     * @deprecated Provided for backward compatibility only; no replacement
     */
    @Deprecated(since = "4.1", forRemoval = true)
    @Option.Configured
    @Option.Default("COUNTER")
    GcTimeType gcTimeType();

    /**
     * Output format for built-in meter names.
     * <p>
     * {@link BuiltInMeterNameFormat#SNAKE} selects "snake_case" which does not conform to the MicroProfile
     * Metrics specification.
     *
     * @return the output format for built-in meter names
     */
    @Option.Configured
    @Option.Default(BuiltInMeterNameFormat.DEFAULT)
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
     * Reports whether the specified scope is enabled, according to any scope configuration that
     * is part of this metrics configuration.
     *
     * @param scope scope name
     * @return true if the scope as a whole is enabled; false otherwise
     */
    default boolean isScopeEnabled(String scope) {
        var scopeConfig = scoping().scopes().get(scope);
        return scopeConfig == null || scopeConfig.enabled();
    }

    /**
     * Determines whether the meter with the specified name and within the indicated scope is enabled.
     *
     * @param name  meter name
     * @param scope scope name
     * @return whether the meter is enabled
     */
    default boolean isMeterEnabled(String name, String scope) {
        return enabled()
                && isScopeEnabled(scope)
                && (
                scoping().scopes().get(scope) == null
                        || scoping().scopes().get(scope).isMeterEnabled(name));
    }
}
