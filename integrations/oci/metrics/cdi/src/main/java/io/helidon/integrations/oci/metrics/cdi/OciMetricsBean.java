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
 * OCI metrics integration CDI extension.
 */

// This bean is added to handle injection on the ObserverMethod as it does not work on an Extension class.
@Singleton
public class OciMetricsBean {

    // Make Priority higher than MetricsCdiExtension so this will only start after MetricsCdiExtension has completed.
    void registerOciMetrics(@Observes @Priority(LIBRARY_BEFORE + 20) @Initialized(ApplicationScoped.class) Object ignore,
                            @OciMetrics Config config, Monitoring monitoringClient) {
        Config ocimetrics = config.get("ocimetrics");
        OciMetricsSupport.Builder builder = OciMetricsSupport.builder()
                .config(ocimetrics)
                .monitoringClient(monitoringClient);
        if (builder.enabled()) {
            activateOciMetricsSupport(ocimetrics, builder);
        }
    }

    void activateOciMetricsSupport(Config ocimetrics, OciMetricsSupport.Builder builder) {
        RoutingBuilders.create(ocimetrics)
                .routingBuilder()
                .register(builder.build());
    }
}
