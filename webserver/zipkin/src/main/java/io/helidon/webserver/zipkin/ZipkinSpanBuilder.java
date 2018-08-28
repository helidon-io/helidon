/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.zipkin;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * The ZipkinSpanBuilder delegates to another {@link io.opentracing.Tracer.SpanBuilder}
 * while starting the {@link Span} with tag {@code sr} Zipkin understands as a start of the span.
 *
 * @see <a href="http://zipkin.io/pages/instrumenting.html#core-data-structures">Zipkin Attributes</a>
 * @see <a href="https://github.com/openzipkin/zipkin/issues/962">Zipkin Missing Service Name</a>
 */
class ZipkinSpanBuilder implements Tracer.SpanBuilder {
    private final Tracer.SpanBuilder spanBuilder;

    ZipkinSpanBuilder(Tracer.SpanBuilder spanBuilder) {
        this.spanBuilder = spanBuilder;
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
        Span span = spanBuilder.start();
        span.log("sr");
        return new ZipkinSpan(span);
    }

    @Override
    public Tracer.SpanBuilder ignoreActiveSpan() {
        return spanBuilder.ignoreActiveSpan();
    }

    @Override
    public Scope startActive(boolean finishSpanOnClose) {
        return spanBuilder.startActive(finishSpanOnClose);
    }

    @Override
    public Span startManual() {
        return null;
    }
}
