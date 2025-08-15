/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.telemetry.otelconfig;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.extension.trace.propagation.OtTracePropagator;

/**
 * Known OpenTelemetry trace context propagation formats.
 * <p>
 * OTel configuration of propagation uses lower-case names. For user-friendliness, we allow users
 * to use the Otel-friendly names (lowercase) or the enum names (UPPERCASE) in config sources.
 */
public enum ContextPropagationType {

    /*
    Enum values are chosen to be the upper-case version of the OTel setting values so Helidon's built-in enum config mapping
    works.
     */

    /**
     * W3C trace context propagation.
     */
    TRACECONTEXT(W3CTraceContextPropagator::getInstance),

    /**
     * W3C baggage propagation.
     */
    BAGGAGE(W3CBaggagePropagator::getInstance),

    /**
     * Zipkin B3 trace context propagation using a single header.
     */
    B3(B3Propagator::injectingSingleHeader),

    /**
     * Zipkin B3 trace context propagation using multiple headers.
     */
    B3MULTI(B3Propagator::injectingMultiHeaders),

    /**
     * Jaeger trace context propagation format.
     */
    JAEGER(JaegerPropagator::getInstance),

    /**
     * OT trace format propagation.
     */
    OTTRACE(OtTracePropagator::getInstance);

    /*
    The following is used in a {@value} Javadoc reference, so it needs to be a constant.
     */
    static final String DEFAULT_NAMES = "tracecontext,baggage";

    static final List<TextMapPropagator> DEFAULT_PROPAGATORS = List.of(TRACECONTEXT.propagator(),
                                                                       BAGGAGE.propagator());

    private final Supplier<TextMapPropagator> propagatorSupplier;

    ContextPropagationType(Supplier<TextMapPropagator> propagatorSupplier) {
        this.propagatorSupplier = propagatorSupplier;
    }

    /**
     * Converts the specified string to a {@code PropagationFormat} enum value, using either the string itself or its upper-case
     * equivalent to match against enum value names. This allows users to use the OTel-friendly lower-case names in Helidon
     * config.
     *
     * @param value string to convert
     * @return {@code PropagationFormat} value corresponding to the provided string
     */
    static ContextPropagationType from(String value) {
        for (ContextPropagationType contextPropagation : ContextPropagationType.values()) {
            if (contextPropagation.name().equals(value) || contextPropagation.name().equals(value.toUpperCase(Locale.ROOT))) {
                return contextPropagation;
            }
        }
        throw new IllegalArgumentException("Unknown propagation format: "
                                                   + value
                                                   + "; expected one or more of "
                                                   + Arrays.toString(ContextPropagationType.values()));
    }

    TextMapPropagator propagator() {
        return propagatorSupplier.get();
    }
}
