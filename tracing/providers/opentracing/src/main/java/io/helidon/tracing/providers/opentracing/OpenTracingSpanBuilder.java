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
package io.helidon.tracing.providers.opentracing;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.SpanListener;

import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

class OpenTracingSpanBuilder implements Span.Builder<OpenTracingSpanBuilder> {

    private static final System.Logger LOGGER = System.getLogger(OpenTracingSpanBuilder.class.getName());

    private final Tracer.SpanBuilder delegate;
    private final Tracer tracer;
    private final List<SpanListener> spanListeners;
    private Map<String, String> baggage;
    private Limited limited;

    OpenTracingSpanBuilder(Tracer tracer, Tracer.SpanBuilder delegate, List<SpanListener> spanListeners) {
        this.tracer = tracer;
        this.delegate = delegate;
        this.spanListeners = spanListeners;
    }

    @Override
    public Span build() {
        return start();
    }

    @Override
    public OpenTracingSpanBuilder parent(SpanContext spanContext) {
        if (spanContext instanceof OpenTracingContext otc) {
            delegate.asChildOf(otc.openTracing());
            if (baggage == null) {
                baggage = new HashMap<>();
            } else {
                baggage.clear();
            }
            ((OpenTracingContext) spanContext).openTracing().baggageItems().forEach(entry -> baggage.put(entry.getKey(),
                                                                                                         entry.getValue()));
        }
        return this;
    }

    @Override
    public OpenTracingSpanBuilder kind(Span.Kind kind) {
        String spanKind = switch (kind) {
            case SERVER -> Tags.SPAN_KIND_SERVER;
            case CLIENT -> Tags.SPAN_KIND_CLIENT;
            case PRODUCER -> Tags.SPAN_KIND_PRODUCER;
            case CONSUMER -> Tags.SPAN_KIND_CONSUMER;
            default -> null;
        };

        if (spanKind != null) {
            delegate.withTag(Tags.SPAN_KIND.getKey(), spanKind);
        }
        return this;
    }

    @Override
    public OpenTracingSpanBuilder tag(String key, String value) {
        delegate.withTag(key, value);
        return this;
    }

    @Override
    public OpenTracingSpanBuilder tag(String key, Boolean value) {
        delegate.withTag(key, value);
        return this;
    }

    @Override
    public OpenTracingSpanBuilder tag(String key, Number value) {
        delegate.withTag(key, value);
        return this;
    }

    @Override
    public Span start(Instant instant) {
        long micro = TimeUnit.MILLISECONDS.toMicros(instant.toEpochMilli());
        OpenTracing.invokeListeners(spanListeners, LOGGER, listener -> listener.starting(limited()));
        OpenTracingSpan result = new OpenTracingSpan(tracer, delegate.withStartTimestamp(micro).start(), spanListeners);
        if (baggage != null) {
            baggage.forEach((k, v) -> result.baggage().set(k, v));
        }
        OpenTracing.invokeListeners(spanListeners, LOGGER, listener -> listener.started(result.limited()));
        return result;
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        if (type.isInstance(delegate)) {
            return type.cast(delegate);
        }
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new IllegalArgumentException("Cannot provide an instance of " + type.getName()
                                                   + ", span builder is: " + delegate.getClass().getName());
    }

    Limited limited() {
        if (limited == null) {
            if (!spanListeners.isEmpty()) {
                limited = new Limited(this);
            }
        }
        return limited;
    }

    private record Limited(OpenTracingSpanBuilder delegate) implements Span.Builder<Limited> {

        @Override
        public Span build() {
            throw new SpanListener.ForbiddenOperationException();
        }

        @Override
        public Limited parent(SpanContext spanContext) {
            throw new SpanListener.ForbiddenOperationException();
        }

        @Override
        public Limited kind(Span.Kind kind) {
            throw new SpanListener.ForbiddenOperationException();
        }

        @Override
        public Limited tag(String key, String value) {
            delegate.tag(key, value);
            return this;
        }

        @Override
        public Limited tag(String key, Boolean value) {
            delegate.tag(key, value);
            return this;
        }

        @Override
        public Limited tag(String key, Number value) {
            delegate.tag(key, value);
            return this;
        }

        @Override
        public Span start(Instant instant) {
            throw new SpanListener.ForbiddenOperationException();
        }
    }
}
