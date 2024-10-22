/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.metrics;

import io.helidon.config.Config;

import com.oracle.bmc.monitoring.Monitoring;

/**
 * Holds information needed to create an instance of {@link io.helidon.integrations.oci.metrics.OciMetricsSupport}.
 * <p>
 * This class is intended as an abstract superclass for CDI beans responsible for initializing an
 * {@code OciMetricsSupport} instance using config and the {@link com.oracle.bmc.monitoring.Monitoring} instance.
 * Concrete implementations implement must, of course, implement the abstract methods and might override other methods
 * as needed.
 * <p>
 * Callers typically invoke {@link #registerOciMetrics(io.helidon.config.Config, com.oracle.bmc.monitoring.Monitoring)}
 * directly.
 * </p>
 */
public abstract class OciMetricsSupportFactory {

    private Config ociMetricsConfig;

    /**
     * Creates a new instance of the factory.
     */
    protected OciMetricsSupportFactory() {
    }

    /**
     * Registers OCI metrics using the configuration and the provided monitoring client by preparing
     * an {@link io.helidon.integrations.oci.metrics.OciMetricsSupport} instance and then calling back to the
     * subclass to activate that instance with, for example, routing.
     *
     * @param rootConfig       root config node
     * @param monitoringClient {@link Monitoring} instance to use in preparing the {@code OciMetricsSupport} instance
     */
    protected void registerOciMetrics(Config rootConfig, Monitoring monitoringClient) {
        ociMetricsConfig = rootConfig.get(configKey());
        OciMetricsSupport.Builder builder = ociMetricsSupportBuilder(rootConfig, ociMetricsConfig, monitoringClient);
        if (builder.enabled()) {
            activateOciMetricsSupport(rootConfig, ociMetricsConfig, builder);
        }
    }

    /**
     * Returns the builder for constructing a new {@link io.helidon.integrations.oci.metrics.OciMetricsSupport} instance,
     * initialized using the config retrieved using the {@link #configKey()} return value and the provided
     * {@link com.oracle.bmc.monitoring.Monitoring} instance.
     *
     * @param rootConfig       root {@link io.helidon.config.Config} node
     * @param ociMetricsConfig config node for the OCI metrics settings
     * @param monitoring       monitoring implementation to be used in preparing the {@code OciMetricsSupport.Builder}.
     * @return resulting builder
     */
    protected OciMetricsSupport.Builder ociMetricsSupportBuilder(Config rootConfig,
                                                                 Config ociMetricsConfig,
                                                                 Monitoring monitoring) {
        this.ociMetricsConfig = ociMetricsConfig;
        return OciMetricsSupport.builder()
                .config(ociMetricsConfig)
                .monitoringClient(monitoring);
    }

    /**
     * Returns the OCI metrics config node used to set up the {@code OciMetricsSupport} instance.
     *
     * @return config node controlling the OCI metrics support behavior
     */
    protected Config ociMetricsConfig() {
        return ociMetricsConfig;
    }

    /**
     * Returns the config key to use for retrieving OCI metrics settings from the root config.
     *
     * @return config key for OCI metrics settings
     */
    protected abstract String configKey();

    /**
     * Activates OCI metrics support.
     *
     * @param rootConfig       root config node
     * @param ociMetricsConfig OCI metrics configuration
     * @param builder          {@link io.helidon.integrations.oci.metrics.OciMetricsSupport.Builder} instance
     */
    protected abstract void activateOciMetricsSupport(Config rootConfig,
                                                      Config ociMetricsConfig,
                                                      OciMetricsSupport.Builder builder);
}
