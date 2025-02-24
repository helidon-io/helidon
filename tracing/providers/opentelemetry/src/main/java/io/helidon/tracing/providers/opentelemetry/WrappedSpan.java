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

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.helidon.tracing.Wrapper;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

class WrappedSpan implements io.opentelemetry.api.trace.Span, Wrapper {

    private static final System.Logger LOGGER = System.getLogger(WrappedSpan.class.getName());

    private final io.helidon.tracing.Span helidonSpan;
    private final Span nativeSpan;

    static WrappedSpan create(io.helidon.tracing.Span helidonSpan) {
        return new WrappedSpan(helidonSpan);
    }

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
        helidonSpan = new OpenTelemetrySpan(helidonTracer, nativeSpan, nativeCurrentSpan == null);
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
        return WrappedScope.create(helidonSpan.activate());
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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WrappedSpan that)) {
            return false;
        }
        return Objects.equals(helidonSpan, that.helidonSpan) && Objects.equals(nativeSpan, that.nativeSpan);
    }

    @Override
    public int hashCode() {
        return Objects.hash(helidonSpan, nativeSpan);
    }
}
