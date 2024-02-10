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
package io.helidon.microprofile.telemetry;

import org.glassfish.jersey.internal.spi.AutoDiscoverable;

/**
 * Register filter and mappers to Jersey.
 */
public class TelemetryAutoDiscoverable implements AutoDiscoverable {

    /**
     * Used to register {@code HelidonTelemetryContainerFilter} and {@code HelidonTelemetryClientFilter}
     * filters.
     *
     * @param ctx FeatureContext which is used to register the filters.
     */
    @Override
    public void configure(jakarta.ws.rs.core.FeatureContext ctx) {
        if (!Boolean.getBoolean(TelemetryCdiExtension.OTEL_AGENT_PRESENT)) {
            ctx.register(HelidonTelemetryContainerFilter.class)
                    .register(HelidonTelemetryClientFilter.class);
        }
    }
}
