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

import io.helidon.config.Config;
import io.helidon.integrations.oci.metrics.OciMetricsSupport;
import io.helidon.integrations.oci.metrics.OciMetricsSupportFactory;
import io.helidon.microprofile.server.RoutingBuilders;

import com.oracle.bmc.monitoring.Monitoring;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * CDI bean for preparing OCI metrics integration.
 * <p>
 * This bean relies on the OCI SDK integration to manufacture a {@link com.oracle.bmc.monitoring.Monitoring} instance.
 * It is also extensible in case an upstream library needs to fine-tune the way the OCI metrics integration is set up.
 * </p>
 * */

// This bean is added to handle injection on the ObserverMethod as it does not work on an Extension class.
@Singleton
public class OciMetricsBean extends OciMetricsSupportFactory {

    private OciMetricsSupport ociMetricsSupport;

    /**
     * For CDI use only.
     */
    @Deprecated
    public OciMetricsBean() {
    }

    @Override
    protected String configKey() {
        return "ocimetrics";
    }

    @Override
    protected void activateOciMetricsSupport(Config rootConfig, Config ociMetricsConfig, OciMetricsSupport.Builder builder) {
        this.ociMetricsSupport = builder.build();
        RoutingBuilders.create(ociMetricsConfig)
                .routingBuilder()
                .register(ociMetricsSupport);
    }

    // Make Priority higher than MetricsCdiExtension so this will only start after MetricsCdiExtension has completed.
    void registerOciMetrics(@Observes @Priority(LIBRARY_BEFORE + 20) @Initialized(ApplicationScoped.class) Object ignore,
                            Config rootConfig, Monitoring monitoringClient) {
        registerOciMetrics(rootConfig, monitoringClient);
    }

    // For testing
    OciMetricsSupport ociMetricsSupport() {
        return ociMetricsSupport;
    }
}
