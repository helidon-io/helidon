/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.tracing.opentracing;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;

import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

class OpenTracingSpanBuilder implements Span.Builder<OpenTracingSpanBuilder> {
    private final Tracer.SpanBuilder delegate;
    private final Tracer tracer;

    OpenTracingSpanBuilder(Tracer tracer, Tracer.SpanBuilder delegate) {
        this.tracer = tracer;
        this.delegate = delegate;
    }

    @Override
    public Span build() {
        return new OpenTracingSpan(tracer, delegate.start());
    }

    @Override
    public OpenTracingSpanBuilder parent(SpanContext spanContext) {
        if (spanContext instanceof OpenTracingContext otc) {
            delegate.asChildOf(otc.openTracing());
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
        return new OpenTracingSpan(tracer, delegate.withStartTimestamp(micro).start());
    }
}
