/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.tracing.providers.zipkin;

import java.time.Instant;
import java.util.List;

import io.helidon.tracing.SpanLifeCycleListener;
import io.helidon.tracing.Tag;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * The ZipkinSpanBuilder delegates to another {@link Tracer.SpanBuilder}
 * while starting the {@link Span} with tag {@code sr} Zipkin understands as a start of the span.
 *
 * @see <a href="http://zipkin.io/pages/instrumenting.html#core-data-structures">Zipkin Attributes</a>
 * @see <a href="https://github.com/openzipkin/zipkin/issues/962">Zipkin Missing Service Name</a>
 */
class ZipkinSpanBuilder implements Tracer.SpanBuilder {
    private final Tracer tracer;
    private final Tracer.SpanBuilder spanBuilder;
    private final List<SpanLifeCycleListener> spanLifeCycleListeners;
    private Limited limited;
    private boolean isClient;

    ZipkinSpanBuilder(Tracer tracer,
                      Tracer.SpanBuilder spanBuilder,
                      List<Tag<?>> tags,
                      List<SpanLifeCycleListener> spanLifeCycleListeners) {
        this.tracer = tracer;
        this.spanBuilder = spanBuilder;
        tags.forEach(t -> this.withTag(t.key(), t)); // Updates both the native span builder and our internal tags structure.
        this.spanLifeCycleListeners = spanLifeCycleListeners;
    }

    @Override
    public Tracer.SpanBuilder asChildOf(SpanContext parent) {
        spanBuilder.asChildOf(parent);
        return this;
    }

    @Override
    public Tracer.SpanBuilder asChildOf(Span parent) {
        spanBuilder.asChildOf(parent);
        return this;
    }

    @Override
    public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        spanBuilder.addReference(referenceType, referencedContext);
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, String value) {
        if ("span.kind".equals(key)) {
            isClient = "client".equals(value);
        }
        spanBuilder.withTag(key, value);
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, boolean value) {
        spanBuilder.withTag(key, value);
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, Number value) {
        spanBuilder.withTag(key, value);
        return this;
    }

    @Override
    public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
        spanBuilder.withStartTimestamp(microseconds);
        return this;
    }

    @Override
    public Span start() {
        spanLifeCycleListeners.forEach(listener -> listener.beforeStart(limited()));
        Span span = spanBuilder.start();

        if (isClient) {
            span.log("cs");
        } else {
            span.log("sr");
        }

        var result = new ZipkinSpan(span, isClient, spanLifeCycleListeners);
        spanLifeCycleListeners.forEach(listener -> listener.afterStart(result.limited()));

        return result;
    }

    @Override
    public Tracer.SpanBuilder ignoreActiveSpan() {
        return spanBuilder.ignoreActiveSpan();
    }

    @Override
    public <T> Tracer.SpanBuilder withTag(io.opentracing.tag.Tag<T> tag, T value) {
        switch (value) {
            case Boolean b -> Tag.create(tag.getKey(), b);
            case Number n -> Tag.create(tag.getKey(), n);
            case String s -> Tag.create(tag.getKey(), s);
            default -> Tag.create(tag.getKey(), value.toString());
        }
        return spanBuilder.withTag(tag, value);
    }

    Limited limited() {
        if (limited == null) {
            if (!spanLifeCycleListeners.isEmpty()) {
                limited = new Limited(this);
            }
        }
        return limited;
    }

    private Tracer.SpanBuilder withTag(String key, Object value) {
        switch (value) {
            case Number n -> withTag(key, n);
            case Boolean b -> withTag(key, b);
            case String s -> withTag(key, s);
            default -> withTag(key, value.toString());
        }
        return this;
    }

    private record Limited(ZipkinSpanBuilder delegate) implements io.helidon.tracing.Span.Builder<Limited> {

        @Override
            public io.helidon.tracing.Span build() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Limited parent(io.helidon.tracing.SpanContext spanContext) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Limited kind(io.helidon.tracing.Span.Kind kind) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Limited tag(Tag<?> tag) {
                delegate.withTag(tag.key(), tag);
                return this;
            }

            @Override
            public Limited tag(String key, String value) {
                delegate.withTag(key, value);
                return this;
            }

            @Override
            public Limited tag(String key, Boolean value) {
                delegate.withTag(key, value);
                return this;
            }

            @Override
            public Limited tag(String key, Number value) {
                delegate.withTag(key, value);
                return this;
            }

            @Override
            public io.helidon.tracing.Span start() {
                throw new UnsupportedOperationException();
            }

            @Override
            public io.helidon.tracing.Span start(Instant instant) {
                throw new UnsupportedOperationException();
            }
        }
}
