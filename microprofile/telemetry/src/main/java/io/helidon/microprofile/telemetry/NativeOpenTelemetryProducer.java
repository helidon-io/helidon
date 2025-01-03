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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

/**
 * Producer implementation which injects the native OTel objects (which do not participate in the Helidon-specific
 * span listener feature).
 */
class NativeOpenTelemetryProducer implements OpenTelemetryProducer.Producer {

    static NativeOpenTelemetryProducer create(Tracer nativeTracer) {
        return new NativeOpenTelemetryProducer(nativeTracer);
    }

    private final Tracer nativeTracer;

    private NativeOpenTelemetryProducer(Tracer nativeTracer) {
        this.nativeTracer = nativeTracer;
    }

    @Override
    public Tracer tracer() {
        return nativeTracer;
    }

    @Override
    public Span span() {
        return new Span() {
            @Override
            public <T> Span setAttribute(AttributeKey<T> key, T value) {
                return Span.current().setAttribute(key, value);
            }

            @Override
            public Span addEvent(String name, Attributes attributes) {
                return Span.current().addEvent(name, attributes);
            }

            @Override
            public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
                return Span.current().addEvent(name, attributes, timestamp, unit);
            }

            @Override
            public Span setStatus(StatusCode statusCode, String description) {
                return Span.current().setStatus(statusCode, description);
            }

            @Override
            public Span recordException(Throwable exception, Attributes additionalAttributes) {
                return Span.current().recordException(exception, additionalAttributes);
            }

            @Override
            public Span updateName(String name) {
                return Span.current().updateName(name);
            }

            @Override
            public void end() {
                Span.current().end();
            }

            @Override
            public void end(long timestamp, TimeUnit unit) {
                Span.current().end(timestamp, unit);
            }

            @Override
            public SpanContext getSpanContext() {
                return Span.current().getSpanContext();
            }

            @Override
            public boolean isRecording() {
                return Span.current().isRecording();
            }
        };
    }
}
