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
package io.helidon.microprofile.telemetry;

import java.util.concurrent.TimeUnit;

import io.helidon.tracing.Wrapper;
import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;
import io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerProvider;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Producer implementation that returns wrappers which implement the OTel interfaces but wrap Helidon OTel implementations,
 * thus notifying any registered span listeners of state changes.
 * <p>
 *     Each wrapped instance records its Helidon counterpart which in turn wraps the OTel native object. In each wrapped type,
 *     the OTel methods which do not alter states delegate to the OTel native object, whereas for the most part those which do
 *     change state delegate to the Helidon object thereby notifying any registered span listeners.
 */
class WrappedProducer implements OpenTelemetryProducer.Producer {

    private static final System.Logger LOGGER = System.getLogger(WrappedProducer.class.getName());

    private final io.helidon.tracing.Tracer helidonTracer;

    private WrappedProducer(io.helidon.tracing.Tracer helidonTracer) {
        this.helidonTracer = helidonTracer;
    }

    static WrappedProducer create(io.helidon.tracing.Tracer helidonTracer) {
        return new WrappedProducer(helidonTracer);
    }

    @Override
    public Tracer tracer() {
        return new WrappedTracer(helidonTracer);
    }

    @Override
    public Span span() {
        return new WrappedSpan(helidonTracer);
    }

    static class WrappedTracer implements Tracer {

        private final io.helidon.tracing.Tracer helidonTracer;

        WrappedTracer(io.helidon.tracing.Tracer helidonTracer) {
            this.helidonTracer = helidonTracer;
        }

        @Override
        public SpanBuilder spanBuilder(String spanName) {
            return new WrappedSpanBuilder(helidonTracer, spanName);
        }
    }

    static class WrappedSpanBuilder implements io.opentelemetry.api.trace.SpanBuilder, Wrapper {

        private final io.helidon.tracing.Span.Builder<?> helidonSpanBuilder;
        private final SpanBuilder nativeSpanBuilder;

        private WrappedSpanBuilder(io.helidon.tracing.Tracer helidonTracer, String spanName) {
            nativeSpanBuilder = helidonTracer.unwrap(Tracer.class).spanBuilder(spanName);
            helidonSpanBuilder = HelidonOpenTelemetry.create(nativeSpanBuilder, helidonTracer);
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
            return new WrappedSpan(helidonSpanBuilder.start());
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

    static class WrappedSpan implements io.opentelemetry.api.trace.Span, Wrapper {

        private final io.helidon.tracing.Span helidonSpan;
        private final Span nativeSpan;

        private WrappedSpan(io.helidon.tracing.Span helidonSpan) {
            this.helidonSpan = helidonSpan;
            nativeSpan = helidonSpan.unwrap(io.opentelemetry.api.trace.Span.class);
        }

        /**
         * Constructor for use by the injection producer method which does not have a Helidon span already.
         *
         * @param helidonTracer Helidon tracer
         */
        private WrappedSpan(io.helidon.tracing.Tracer helidonTracer) {
            Span nativeCurrentSpan = Span.fromContextOrNull(Context.current());

            nativeSpan = Span.current();
            helidonSpan = OpenTelemetryTracerProvider.span(helidonTracer, nativeSpan, nativeCurrentSpan == null);
        }

        @Override
        public <T> Span setAttribute(AttributeKey<T> key, T value) {
            nativeSpan.setAttribute(key, value);
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes) {
            nativeSpan.addEvent(name, attributes);
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
            nativeSpan.addEvent(name, attributes, timestamp, unit);
            return this;
        }

        @Override
        public Span setStatus(StatusCode statusCode, String description) {
            nativeSpan.setStatus(statusCode, description);
            return this;
        }

        @Override
        public Span recordException(Throwable exception, Attributes additionalAttributes) {
            nativeSpan.recordException(exception, additionalAttributes);
            return this;
        }

        @Override
        public Span updateName(String name) {
            nativeSpan.updateName(name);
            return this;
        }

        @Override
        public void end() {
            // Invoking the Helidon end method will notify listeners as well as end its native delegate span.
            helidonSpan.end();
        }

        @Override
        public void end(long timestamp, TimeUnit unit) {
            // The Helidon API does not have an end(long, TimeUnit) method. So we have to invoke the native span directly
            // with those arguments and then invoke the listeners explicitly. We don't want to also invoke the Helidon "end()"
            // method to accomplish the notifications because that would end the native span again.
            nativeSpan.end(timestamp, unit);
            HelidonOpenTelemetry.invokeListeners(helidonSpan, LOGGER, listener -> listener.ended(helidonSpan));
        }

        @Override
        public SpanContext getSpanContext() {
            return nativeSpan.getSpanContext();
        }

        @Override
        public boolean isRecording() {
            return nativeSpan.isRecording();
        }

        @Override
        public Scope makeCurrent() {
            return new WrappedScope(helidonSpan.activate());
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            if (c.isInstance(nativeSpan)) {
                return c.cast(nativeSpan);
            }
            if (c.isInstance(helidonSpan)) {
                return c.cast(helidonSpan);
            }
            throw new IllegalArgumentException("Cannot provide an instance of " + c.getName()
                                                                   + "; the wrapped telemetry span has type "
                                                                   + nativeSpan.getClass().getName()
                                                                   + " and the wrapped Helidon span has type "
                                                                   + helidonSpan.getClass().getName());
        }
    }

    static class WrappedScope implements io.opentelemetry.context.Scope, Wrapper {

        private final io.helidon.tracing.Scope helidonScope;

        private WrappedScope(io.helidon.tracing.Scope helidonScope) {
            this.helidonScope = helidonScope;
        }

        @Override
        public void close() {
            helidonScope.close();
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            Scope nativeScope = helidonScope.unwrap(Scope.class);
            if (c.isInstance(nativeScope)) {
                return c.cast(nativeScope);
            }
            if (c.isInstance(helidonScope)) {
                return c.cast(helidonScope);
            }
            throw new IllegalArgumentException("Cannot provide an instance of " + c.getName()
                                                       + "; the wrapped telemetry scope has type "
                                                       + nativeScope.getClass().getName()
                                                       + " and the wrapped Helidon scope has type "
                                                       + helidonScope.getClass().getName());
        }
    }
}
