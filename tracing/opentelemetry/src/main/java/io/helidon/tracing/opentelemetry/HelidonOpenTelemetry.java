/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.tracing.opentelemetry;


import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

/**
 * Open Telemetry factory methods to create wrappers for Open Telemetry types.
 */
public final class HelidonOpenTelemetry {

    private static final System.Logger LOGGER = System.getLogger(HelidonOpenTelemetry.class.getName());
    static final String OTEL_AGENT_PRESENT_PROPERTY = "otel.agent.present";
    static final String IO_OPENTELEMETRY_JAVAAGENT = "io.opentelemetry.javaagent";
    private HelidonOpenTelemetry() {
    }
    /**
     * Wrap an open telemetry tracer.
     *
     * @param telemetry open telemetry instance
     * @param tracer    tracer
     * @param tags      tracer tags
     * @return Helidon {@link io.helidon.tracing.Tracer}
     */
    public static OpenTelemetryTracer create(OpenTelemetry telemetry, Tracer tracer, Map<String, String> tags) {
        return new OpenTelemetryTracer(telemetry, tracer, tags);
    }

    /**
     * Wrap an open telemetry span.
     *
     * @param span open telemetry span
     * @return Helidon {@link io.helidon.tracing.Span}
     */
    public static io.helidon.tracing.Span create(Span span) {
        return new OpenTelemetrySpan(span);
    }


    /**
     * Check if OpenTelemetry is present by indirect properties.
     * This class does best explicit check if OTEL_AGENT_PRESENT_PROPERTY config property is set and uses its
     * value to set the behaviour of OpenTelemetry producer.
     *
     * If the value is not explicitly set, the detector does best effort to estimate indirect means if the agent is present.
     * This detector may stop working if OTEL changes the indirect indicators.
     */
    public static final class AgentDetector {

        //Private constructor for a utility class.
        private AgentDetector() {
        }

        /**
         * Check if the OTEL Agent is present.
         *
         * @param config Configuration
         * @return boolean
         */
        public static boolean isAgentPresent(Config config) {

            //Explicitly check if agent property is set
            if (config != null) {
                Optional<Boolean> agentPresent = config.get(OTEL_AGENT_PRESENT_PROPERTY).asBoolean().asOptional();
                if (agentPresent.isPresent()) {
                    return agentPresent.get();
                }
            }

            if (checkContext() || checkSystemProperties()) {
                if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
                    LOGGER.log(System.Logger.Level.INFO, "OpenTelemetry Agent detected");
                }
                return true;
            }
            return false;
        }

        private static boolean checkSystemProperties() {
            return System.getProperties().stringPropertyNames()
                    .stream()
                    .anyMatch(property -> property.contains(IO_OPENTELEMETRY_JAVAAGENT));
        }

        private static boolean checkContext() {
            return io.opentelemetry.context.Context.current().getClass().getName().contains("agent");
        }
    }
}
