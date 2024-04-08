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
package io.helidon.tracing.opentelemetry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

class OpenTelemetrySpan implements Span {
    private final io.opentelemetry.api.trace.Span delegate;
    private final Baggage baggage;

    OpenTelemetrySpan(io.opentelemetry.api.trace.Span span) {
        this.delegate = span;
        baggage = new MutableOpenTelemetryBaggage();
    }

    OpenTelemetrySpan(io.opentelemetry.api.trace.Span span, Baggage baggage) {
        delegate = span;
        this.baggage = baggage;
    }

    @Override
    public Span tag(String key, String value) {
        delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public Span tag(String key, Boolean value) {
        delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public Span tag(String key, Number value) {
        if (value instanceof Double || value instanceof Float) {
            delegate.setAttribute(key, value.doubleValue());
        } else {
            delegate.setAttribute(key, value.longValue());
        }
        return this;
    }

    @Override
    public void status(Status status) {
        switch (status) {
        case OK -> delegate.setStatus(StatusCode.OK);
        case ERROR -> delegate.setStatus(StatusCode.ERROR);
        default -> {
        }
        }
    }

    @Override
    public SpanContext context() {
        return new OpenTelemetrySpanContext(Context.current().with(delegate).with(baggage));
    }

    @Override
    public void addEvent(String name, Map<String, ?> attributes) {
        delegate.addEvent(name, toAttributes(attributes));
    }

    @Override
    public void end() {
        delegate.end();
    }

    @Override
    public void end(Throwable t) {
        delegate.recordException(t);
        delegate.setStatus(StatusCode.ERROR);
        delegate.end();
    }

    @Override
    public Scope activate() {
        io.opentelemetry.context.Scope baggageScope = baggage.makeCurrent();
        return new OpenTelemetryScope(delegate.makeCurrent(), baggageScope);
    }

    @Override
    public Span baggage(String key, String value) {
        Objects.requireNonNull(key, "baggage key cannot be null");
        Objects.requireNonNull(value, "baggage value cannot be null");
        if (baggage instanceof MutableOpenTelemetryBaggage mutableBaggage) {
            mutableBaggage.baggage(key, value);
        } else {
            throw new UnsupportedOperationException(
                    "Attempt to set baggage on a span with read-only baggage (perhaps from context");
        }
        return this;
    }

    @Override
    public Optional<String> baggage(String key) {
        Objects.requireNonNull(key, "Baggage Key cannot be null");
        return Optional.ofNullable(baggage.getEntryValue(key));
    }

    private Attributes toAttributes(Map<String, ?> attributes) {
        AttributesBuilder builder = Attributes.builder();
        attributes.forEach((key, value) -> {
            if (value instanceof Long l) {
                builder.put(key, l);
            } else if (value instanceof Boolean b) {
                builder.put(key, b);
            } else if (value instanceof Double d) {
                builder.put(key, d);
            } else {
                builder.put(key, String.valueOf(value));
            }
        });
        return builder.build();
    }
}
