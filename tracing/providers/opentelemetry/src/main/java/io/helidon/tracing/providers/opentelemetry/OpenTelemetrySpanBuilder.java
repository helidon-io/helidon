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

import java.time.Instant;

import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

class OpenTelemetrySpanBuilder implements Span.Builder<OpenTelemetrySpanBuilder> {
    private final SpanBuilder spanBuilder;
    private boolean parentSet;
    private Baggage parentBaggage;

    OpenTelemetrySpanBuilder(SpanBuilder spanBuilder) {
        this.spanBuilder = spanBuilder;
    }

    @Override
    public Span build() {
        return start();
    }

    @Override
    public OpenTelemetrySpanBuilder parent(SpanContext spanContext) {
        this.parentSet = true;
        spanContext.asParent(this);
        parentBaggage = Baggage.fromContext(((OpenTelemetrySpanContext) spanContext).openTelemetry());
        return this;
    }

    @Override
    public OpenTelemetrySpanBuilder kind(Span.Kind kind) {
        switch (kind) {
        case SERVER -> spanBuilder.setSpanKind(SpanKind.SERVER);
        case CLIENT -> spanBuilder.setSpanKind(SpanKind.CLIENT);
        case PRODUCER -> spanBuilder.setSpanKind(SpanKind.PRODUCER);
        case CONSUMER -> spanBuilder.setSpanKind(SpanKind.CONSUMER);
        default -> spanBuilder.setSpanKind(SpanKind.INTERNAL);
        }

        return this;
    }

    @Override
    public OpenTelemetrySpanBuilder tag(String key, String value) {
        spanBuilder.setAttribute(key, value);
        return this;
    }

    @Override
    public OpenTelemetrySpanBuilder tag(String key, Boolean value) {
        spanBuilder.setAttribute(key, value);
        return this;
    }

    @Override
    public OpenTelemetrySpanBuilder tag(String key, Number value) {
        if (value instanceof Double || value instanceof Float) {
            spanBuilder.setAttribute(key, value.doubleValue());
        } else {
            spanBuilder.setAttribute(key, value.longValue());
        }

        return this;
    }

    @Override
    public Span start(Instant instant) {
        if (!parentSet) {
            spanBuilder.setNoParent();
        }
        spanBuilder.setStartTimestamp(instant);
        io.opentelemetry.api.trace.Span span = spanBuilder.startSpan();
        Span result = new OpenTelemetrySpan(span);
        if (parentBaggage != null) {
            parentBaggage.forEach((key, baggageEntry) -> result.baggage(key, baggageEntry.getValue()));
        }
        return result;
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        if (type.isInstance(spanBuilder)) {
            return type.cast(spanBuilder);
        }
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new IllegalArgumentException("Cannot provide an instance of " + type.getName()
                                                   + ", span builder is: " + spanBuilder.getClass().getName());
    }

    // used to set open telemetry context as parent, to be equivalent in function to
    // #parent(SpanContext)
    void parent(Context context) {
        this.parentSet = true;
        this.spanBuilder.setParent(context);
    }
}
