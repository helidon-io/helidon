/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.micrometer;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configured meter registry settings specific to Prometheus.
 */
@Prototype.Configured
@Prototype.Blueprint
@Prototype.Implement("io.micrometer.prometheus.PrometheusConfig")
@Prototype.CustomMethods(ConfigSupport.PrometheusMeterRegistrySupport.CustomMethods.class)
interface PrometheusMeterRegistryConfigBlueprint {

    /**
     * Property name prefix for Prometheus settings.
     *
     * @return prefix
     */
    @Option.Configured
    @Option.Default("prometheus")
    String prefix();

    /**
     * Whether metric descriptions should be included in Prometheus metrics output.
     *
     * @return whether to include descriptions
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean descriptions();

    /**
     * Duration used to compute windowed statistics such as max. The Prometheus default is 1 minute. Ideally, align
     * this setting to be close to how often a backend system scrapes metrics from the endpoint.
     *
     * @return windowed statistics step size
     */
    @Option.Configured
    @Option.DefaultCode("java.time.Duration.ofMinutes(1)")
    Duration step();

    /**
     * Properties related specifically to the Prometheus meter registry.
     *
     * @return Prometheus-related properties
     */
    @Option.Configured
    @Option.DefaultCode(
            "io.helidon.metrics.providers.micrometer.ConfigSupport.PrometheusMeterRegistrySupport.defaultProperties()")
    Properties prometheusProperties();

    /**
     * If the configuration should be used to create a Prometheus meter registry.
     *
     * @return true if the config is enabled, false otherwise
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Property values to be returned by the Prometheus meter registry configuration (required by the Micrometer
     * {@link io.micrometer.core.instrument.config.MeterRegistryConfig} type.
     *
     * @return properties
     */
    @Option.Configured
    Map<String, String> properties();

}
