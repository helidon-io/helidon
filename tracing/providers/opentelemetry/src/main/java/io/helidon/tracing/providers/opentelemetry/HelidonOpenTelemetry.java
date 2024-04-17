/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.opentelemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.tracing.SpanListener;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import static io.opentelemetry.context.Context.current;

/**
 * Open Telemetry factory methods to create wrappers for Open Telemetry types.
 */
public final class HelidonOpenTelemetry {

    /**
     * OpenTelemetry property for indicating if the Java agent is present.
     */
    public static final String OTEL_AGENT_PRESENT_PROPERTY = "otel.agent.present";

    /**
     * OpenTelemetry property for the Java agent.
     */
    public static final String IO_OPENTELEMETRY_JAVAAGENT = "io.opentelemetry.javaagent";

    static final String UNSUPPORTED_OPERATION_MESSAGE = "Span listener attempted to invoke an illegal operation";

    private static final System.Logger LOGGER = System.getLogger(HelidonOpenTelemetry.class.getName());
    private static final LazyValue<List<SpanListener>> SPAN_LISTENERS =
            LazyValue.create(() -> HelidonServiceLoader.create(ServiceLoader.load(SpanListener.class)).asList());


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
        return new OpenTelemetrySpan(span, SPAN_LISTENERS.get());
    }

    /**
     * Wrap an open telemetry span.
     *
     * @param span open telemetry span
     * @param baggage open telemetry baggage
     * @return Helidon (@link io.helidon.tracing.Span}
     */
    public static io.helidon.tracing.Span create(Span span, Baggage baggage) {
        return new OpenTelemetrySpan(span, baggage, SPAN_LISTENERS.get());
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

            if (checkContext() || checkSystemProperties()){
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
            return current().getClass().getName().contains("agent");
        }

    }

    static void invokeListeners(List<SpanListener> spanListeners, System.Logger logger, Consumer<SpanListener> operation) {
        List<Throwable> throwables = new ArrayList<>();
        for (SpanListener listener : spanListeners) {
            try {
                operation.accept(listener);
            } catch (Throwable t) {
                throwables.add(t);
            }
        }

        Throwable throwableToLog = null;
        if (throwables.size() == 1) {
            // If only one exception is present, propagate that one in the log record.
            throwableToLog = throwables.getFirst();
        } else if (!throwables.isEmpty()) {
            // Propagate a RuntimeException with multiple suppressed exceptions in the log record.
            throwableToLog = new RuntimeException();
            throwables.forEach(throwableToLog::addSuppressed);
        }
        if (throwableToLog != null) {
            logger.log(System.Logger.Level.WARNING, "Error(s) from listener(s)", throwableToLog);
        }
    }
}
