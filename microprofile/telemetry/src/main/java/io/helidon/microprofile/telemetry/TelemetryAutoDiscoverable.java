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

import io.helidon.config.mp.MpConfig;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.core.FeatureContext;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.glassfish.jersey.internal.spi.AutoDiscoverable;

/**
 * Registers telemetry filters with Jersey.
 * <p>
 * The container filter is always registered. The client filter is registered only when an active CDI container can
 * provide a Helidon {@link Tracer}; otherwise, a standalone Jersey client continues without MP telemetry.
 */
public class TelemetryAutoDiscoverable implements AutoDiscoverable {
    private static final System.Logger LOGGER = System.getLogger(TelemetryAutoDiscoverable.class.getName());

    /**
     * For service loading.
     */
    public TelemetryAutoDiscoverable() {
    }

    /**
     * Used to register the telemetry filters.
     *
     * @param ctx feature context used to register the filters
     */
    @Override
    public void configure(FeatureContext ctx) {
        ctx.register(HelidonTelemetryContainerFilter.class);
        if (ctx.getConfiguration().getRuntimeType() == RuntimeType.SERVER && shouldRegisterResponseWriteProviders()) {
            ctx.register(HelidonTelemetryWriterInterceptor.class);
            ctx.register(new HelidonTelemetryRequestEventListener());
        }

        try {
            Instance<Tracer> tracers = CDI.current().select(Tracer.class);
            if (!tracers.isResolvable()) {
                LOGGER.log(System.Logger.Level.TRACE,
                           "Skipping Jersey client telemetry because CDI cannot resolve a Helidon tracer");
                return;
            }

            ctx.register(HelidonTelemetryClientFilter.class);
        } catch (IllegalStateException e) {
            LOGGER.log(System.Logger.Level.TRACE,
                       "Skipping Jersey client telemetry because no active CDI container is available",
                       e);
        }
    }

    @SuppressWarnings("removal")
    private static boolean shouldRegisterResponseWriteProviders() {
        try {
            Config mpConfig = ConfigProvider.getConfig();
            return mpConfig.getOptionalValue(HelidonTelemetryContainerFilter.AUTO_SPAN_INCLUDES_RESPONSE_WRITE,
                                             Boolean.class)
                    .orElse(false)
                    && !HelidonOpenTelemetry.AgentDetector.isAgentPresent(MpConfig.toHelidonConfig(mpConfig));
        } catch (IllegalStateException e) {
            LOGGER.log(System.Logger.Level.TRACE,
                       "Skipping response-write-inclusive telemetry because MP Config is unavailable",
                       e);
            return false;
        }
    }
}
