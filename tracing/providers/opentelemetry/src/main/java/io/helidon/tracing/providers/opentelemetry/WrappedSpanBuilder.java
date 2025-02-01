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
package io.helidon.tracing.providers.opentelemetry;

import java.util.concurrent.TimeUnit;

import io.helidon.tracing.Wrapper;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

class WrappedSpanBuilder implements SpanBuilder, Wrapper {

    private final io.helidon.tracing.Span.Builder<?> helidonSpanBuilder;
    private final SpanBuilder nativeSpanBuilder;

    static WrappedSpanBuilder create(io.helidon.tracing.Tracer helidonTracer, String spanName) {
        return create(helidonTracer.spanBuilder(spanName));
    }

    static WrappedSpanBuilder create(io.helidon.tracing.Span.Builder<?> helidonSpanBuilder) {
        return new WrappedSpanBuilder(helidonSpanBuilder.unwrap(SpanBuilder.class), helidonSpanBuilder);
    }

    private WrappedSpanBuilder(SpanBuilder otelSpanBuilder, io.helidon.tracing.Span.Builder<?> helidonSpanBuilder) {
        nativeSpanBuilder = otelSpanBuilder;
        this.helidonSpanBuilder = helidonSpanBuilder;
        // To maintain the Helidon span lineage set the parent if one is available.
        io.helidon.tracing.Span.current().map(io.helidon.tracing.Span::context).ifPresent(helidonSpanBuilder::parent);
    }

    @Override
    public SpanBuilder setParent(Context context) {
        nativeSpanBuilder.setParent(context);
        // Generally we don't also invoke the Helidon implementation's method because most of the methods simply delegate
        // to the native OTel counterpart method. But we need to set the parent of the Helidon span builder
        // explicitly because the parentage is not maintained via simple delegation to the native OTel span builder.
        helidonSpanBuilder.parent(HelidonOpenTelemetry.create(context));
        return this;
    }

    @Override
    public SpanBuilder setNoParent() {
        // There is no Helidon counterpart for clearing the parent once set.
        nativeSpanBuilder.setNoParent();
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext) {
        nativeSpanBuilder.addLink(spanContext);
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext, Attributes attributes) {
        nativeSpanBuilder.addLink(spanContext, attributes);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, String value) {
        nativeSpanBuilder.setAttribute(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, long value) {
        nativeSpanBuilder.setAttribute(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, double value) {
        nativeSpanBuilder.setAttribute(key, value);
        helidonSpanBuilder.tag(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, boolean value) {
        nativeSpanBuilder.setAttribute(key, value);
        return this;
    }

    @Override
    public <T> SpanBuilder setAttribute(AttributeKey<T> key, T value) {
        nativeSpanBuilder.setAttribute(key, value);
        return this;
    }

    @Override
    public SpanBuilder setSpanKind(SpanKind spanKind) {
        nativeSpanBuilder.setSpanKind(spanKind);
        return this;
    }

    @Override
    public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
        nativeSpanBuilder.setStartTimestamp(startTimestamp, unit);
        return this;
    }

    @Override
    public Span startSpan() {
        return WrappedSpan.create(helidonSpanBuilder.start());
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        if (c.isInstance(helidonSpanBuilder)) {
            return c.cast(helidonSpanBuilder);
        }
        if (c.isInstance(nativeSpanBuilder)) {
            return c.cast(nativeSpanBuilder);
        }
        throw new IllegalArgumentException("Cannot provide an instance of " + c.getName()
                                                   + "; the wrapped telemetry span builder has type "
                                                   + nativeSpanBuilder.getClass().getName()
                                                   + " and the wrapped Helidon span builder has type "
                                                   + helidonSpanBuilder.getClass().getName());
    }
}
