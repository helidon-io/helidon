/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.metrics.cdi;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Singleton;

import io.helidon.config.Config;
import io.helidon.integrations.oci.metrics.OciMetricsSupport;
import io.helidon.microprofile.server.RoutingBuilders;

import com.oracle.bmc.monitoring.Monitoring;

import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * CDI bean for preparing OCI metrics integration.
 * <p>
 * This bean relies on the OCI SDK integration to manufacture a {@link com.oracle.bmc.monitoring.Monitoring} instance.
 * It is also extensible in case an upstream library needs to fine-tune the way the OCI metrics integration is set up.
 * </p>
 */

// This bean is added to handle injection on the ObserverMethod as it does not work on an Extension class.
@Singleton
public class OciMetricsBean {

    private Config ociMetricsConfig;
    private OciMetricsSupport ociMetricsSupport;

    /**
     * For CDI use only.
     */
    @Deprecated
    public OciMetricsBean() {
    }

    /**
     * Returns the config key to use for retrieving OCI metrics settings from the root config.
     *
     * @return config key for OCI metrics settings
     */
    protected String configKey() {
        return "ocimetrics";
    }

    /**
     * Returns the builder for constructing a new {@link io.helidon.integrations.oci.metrics.OciMetricsSupport} instance,
     * initialized using the config retrieved using the {@link #configKey()} return value and the provided
     * {@link com.oracle.bmc.monitoring.Monitoring} instance.
     *
     * @param rootConfig root {@link io.helidon.config.Config} node
     * @param ociMetricsConfig config node for the OCI metrics settings
     * @param monitoring monitoring implementation to be used in preparing the {@code OciMetricsSupport.Builder}.
     * @return resulting builder
     */
    protected OciMetricsSupport.Builder ociMetricsSupportBuilder(Config rootConfig,
                                                                 Config ociMetricsConfig,
                                                                 Monitoring monitoring) {
        return OciMetricsSupport.builder()
                .config(ociMetricsConfig)
                .monitoringClient(monitoring);
    }

    /**
     * Returns the OCI metrics config settings previously retrieved from the config root.
     *
     * @return  OCI metrics config node
     */
    protected Config ociMetricsConfig() {
        return ociMetricsConfig;
    }

    /**
     * Activates OCI metrics support.
     *
     * @param rootConfig root config node
     * @param ociMetricsConfig OCI metrics configuration
     * @param builder builder for {@link io.helidon.integrations.oci.metrics.OciMetricsSupport}
     */
    protected void activateOciMetricsSupport(Config rootConfig, Config ociMetricsConfig, OciMetricsSupport.Builder builder) {
        ociMetricsSupport = builder.build();
        RoutingBuilders.create(ociMetricsConfig)
                .routingBuilder()
                .register(ociMetricsSupport);
    }

    // Make Priority higher than MetricsCdiExtension so this will only start after MetricsCdiExtension has completed.
    void registerOciMetrics(@Observes @Priority(LIBRARY_BEFORE + 20) @Initialized(ApplicationScoped.class) Object ignore,
                            Config rootConfig, Monitoring monitoringClient) {
        ociMetricsConfig = rootConfig.get(configKey());
        OciMetricsSupport.Builder builder = ociMetricsSupportBuilder(rootConfig, ociMetricsConfig, monitoringClient);
        if (builder.enabled()) {
            activateOciMetricsSupport(rootConfig, ociMetricsConfig, builder);
        }
    }

    // For testing
    OciMetricsSupport ociMetricsSupport() {
        return ociMetricsSupport;
    }
}
