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

import java.util.List;

import io.helidon.tracing.SpanInfo;
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
class ZipkinSpanBuilder implements Tracer.SpanBuilder, SpanInfo.BuilderInfo<Tracer.SpanBuilder> {
    private final Tracer tracer;
    private final Tracer.SpanBuilder spanBuilder;
    private final List<Tag<?>> tags;
    private boolean isClient;

    ZipkinSpanBuilder(Tracer tracer, Tracer.SpanBuilder spanBuilder, List<Tag<?>> tags) {
        this.tracer = tracer;
        this.spanBuilder = spanBuilder;
        this.tags = tags;
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
        ZipkinTracerProvider.lifeCycleListeners().forEach(listener -> listener.beforeStart(this));
        Span span = spanBuilder.start();

        if (isClient) {
            span.log("cs");
        } else {
            span.log("sr");
        }

        var result = new ZipkinSpan(span, isClient);
        ZipkinTracerProvider.lifeCycleListeners().forEach(listener -> listener.afterStart(result));

        return result;
    }

    @Override
    public Tracer.SpanBuilder ignoreActiveSpan() {
        return spanBuilder.ignoreActiveSpan();
    }

    @Override
    public <T> Tracer.SpanBuilder withTag(io.opentracing.tag.Tag<T> tag, T value) {
        return spanBuilder.withTag(tag, value);
    }

    @Override
    public Tracer.SpanBuilder tag(Tag<?> tag) {
        return switch (tag.value()) {
            case Boolean b -> withTag(tag.key(), b);
            case Number n -> withTag(tag.key(), n);
            case String s -> withTag(tag.key(), s);
            default -> withTag(tag.key(), tag.value().toString());
        };
    }

    @Override
    public Tracer.SpanBuilder tag(String key, String value) {
        return withTag(key, value);
    }

    @Override
    public Tracer.SpanBuilder tag(String key, Boolean value) {
        return withTag(key, value);
    }

    @Override
    public Tracer.SpanBuilder tag(String key, Number value) {
        return withTag(key, value);
    }
}
