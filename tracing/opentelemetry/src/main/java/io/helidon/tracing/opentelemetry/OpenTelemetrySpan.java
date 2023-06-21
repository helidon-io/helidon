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
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.context.Contexts;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

class OpenTelemetrySpan implements Span {
    private final io.opentelemetry.api.trace.Span delegate;

    OpenTelemetrySpan(io.opentelemetry.api.trace.Span span) {
        this.delegate = span;
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
        return new OpenTelemetrySpanContext(Context.current().with(delegate));
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
        return new OpenTelemetryScope(delegate.makeCurrent());
    }

    @Override
    public Span baggage(String key, String value) {
        Objects.requireNonNull(key, "Baggage Key cannot be null");
        Objects.requireNonNull(value, "Baggage Value cannot be null");

        BaggageBuilder baggageBuilder = Baggage.builder();

        //Check for previously added baggage items
        Map<String, BaggageEntry> baggageEntryMap = Baggage.fromContext(getContext()).asMap();
        baggageEntryMap.forEach((k, v) -> baggageBuilder.put(k, v.getValue()));

        baggageBuilder
                .put(key, value)
                .build()
                .storeInContext(getContext()
                        .with(delegate))
                .makeCurrent();
        return this;
    }

    @Override
    public Optional<String> baggage(String key) {
        Objects.requireNonNull(key, "Baggage Key cannot be null");
        return Optional.ofNullable(Baggage.fromContext(getContext()).getEntryValue(key));
    }

    // Check if OTEL Context is already available in Global Helidon Context.
    // If not – use Current context.
    private static Context getContext() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(Context.class))
                .orElseGet(Context::current);
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
