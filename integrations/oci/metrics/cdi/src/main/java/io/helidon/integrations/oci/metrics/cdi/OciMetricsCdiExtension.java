/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;

import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.integrations.oci.metrics.OciMetricsSupport;
import io.helidon.microprofile.server.RoutingBuilders;

import com.oracle.bmc.monitoring.Monitoring;
import org.eclipse.microprofile.config.ConfigProvider;

import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * OCI metrics integration CDI extension.
 */
public class OciMetricsCdiExtension implements Extension {

    void registerOciMetrics(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object adv) {
        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
        Config helidonConfig = MpConfig.toHelidonConfig(config).get("ocimetrics");

        Monitoring monitoringClient = CDI.current()
                .select(Monitoring.class)
                .get();

        OciMetricsSupport.Builder builder = OciMetricsSupport.builder()
                .config(helidonConfig)
                .monitoringClient(monitoringClient);

        RoutingBuilders.create(helidonConfig)
                .routingBuilder()
                .register(builder.build());
    }
}
