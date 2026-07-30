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
package io.helidon.microprofile.telemetry;

import java.util.function.Supplier;

import io.helidon.microprofile.telemetry.spi.HelidonTelemetryClientFilterHelper;
import io.helidon.tracing.Tracer;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.core.FeatureContext;
import org.glassfish.jersey.internal.spi.AutoDiscoverable;

/**
 * Registers telemetry filters with Jersey.
 * <p>
 * The container filter is always registered. The client filter is registered only when an active CDI container can
 * provide a Helidon {@link Tracer}; otherwise, a standalone Jersey client continues without MP telemetry.
 */
public class TelemetryAutoDiscoverable implements AutoDiscoverable {
    private static final System.Logger LOGGER = System.getLogger(TelemetryAutoDiscoverable.class.getName());

    private final Supplier<CDI<Object>> cdiSupplier;

    /**
     * For service loading.
     */
    public TelemetryAutoDiscoverable() {
        this(CDI::current);
    }

    TelemetryAutoDiscoverable(Supplier<CDI<Object>> cdiSupplier) {
        this.cdiSupplier = cdiSupplier;
    }

    /**
     * Used to register the telemetry filters.
     *
     * @param ctx feature context used to register the filters
     */
    @Override
    public void configure(FeatureContext ctx) {
        ctx.register(HelidonTelemetryContainerFilter.class);

        try {
            CDI<Object> cdi = cdiSupplier.get();
            Instance<Tracer> tracers = cdi.select(Tracer.class);
            if (!tracers.isResolvable()) {
                LOGGER.log(System.Logger.Level.TRACE,
                           "Skipping Jersey client telemetry because CDI cannot resolve a Helidon tracer");
                return;
            }

            Instance<HelidonTelemetryClientFilterHelper> helpers = cdi.select(HelidonTelemetryClientFilterHelper.class);
            ctx.register(new HelidonTelemetryClientFilter(tracers.get(), helpers));
        } catch (IllegalStateException e) {
            LOGGER.log(System.Logger.Level.TRACE,
                       "Skipping Jersey client telemetry because no active CDI container is available");
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.TRACE,
                       "Skipping Jersey client telemetry because CDI dependencies could not be obtained: "
                               + e.getMessage());
        }
    }
}
