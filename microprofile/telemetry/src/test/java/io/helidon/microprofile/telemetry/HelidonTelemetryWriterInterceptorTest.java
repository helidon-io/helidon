/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.tracing.Baggage;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.WritableBaggage;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HelidonTelemetryWriterInterceptorTest {

    @Test
    void removesLifecyclePropertyAfterSuccessfulWrite() throws IOException {
        RecordingSpan span = new RecordingSpan();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, new RecordingScope());
        TestWriterInterceptorContext context = new TestWriterInterceptorContext(lifecycle, null);

        new HelidonTelemetryWriterInterceptor().aroundWriteTo(context);

        assertThat("Writer chain invoked", context.proceedCount.get(), is(1));
        assertThat("Writer scope closed", span.scope.closeCount.get(), is(1));
        assertThat("Lifecycle property removed",
                   context.getProperty(ServerSpanLifecycle.PROPERTY), nullValue());
    }

    @Test
    void removesLifecyclePropertyAndCapturesFailedWrite() {
        RecordingSpan span = new RecordingSpan();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, new RecordingScope());
        IOException failure = new IOException("deliberate");
        TestWriterInterceptorContext context = new TestWriterInterceptorContext(lifecycle, failure);

        assertThrows(IOException.class, () -> new HelidonTelemetryWriterInterceptor().aroundWriteTo(context));
        lifecycle.requestFinished(false);

        assertThat("Writer chain invoked", context.proceedCount.get(), is(1));
        assertThat("Writer scope closed", span.scope.closeCount.get(), is(1));
        assertThat("Lifecycle property removed",
                   context.getProperty(ServerSpanLifecycle.PROPERTY), nullValue());
        assertThat("Write failure recorded", span.failure, instanceOf(IOException.class));
    }

    private static final class RecordingSpan implements Span {
        private static final SpanContext CONTEXT = new SpanContext() {
            @Override
            public String traceId() {
                return "trace";
            }

            @Override
            public String spanId() {
                return "span";
            }

            @Override
            public void asParent(Builder<?> spanBuilder) {
                Objects.requireNonNull(spanBuilder);
            }

            @Override
            public Baggage baggage() {
                return null;
            }
        };

        private final RecordingScope scope = new RecordingScope();
        private volatile Throwable failure;

        @Override
        public Span tag(String key, String value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            return this;
        }

        @Override
        public Span tag(String key, Boolean value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            return this;
        }

        @Override
        public Span tag(String key, Number value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            return this;
        }

        @Override
        public void status(Status status) {
            Objects.requireNonNull(status);
        }

        @Override
        public SpanContext context() {
            return CONTEXT;
        }

        @Override
        public void addEvent(String name, Map<String, ?> attributes) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(attributes);
        }

        @Override
        public void end() {
        }

        @Override
        public void end(Throwable t) {
            failure = Objects.requireNonNull(t);
        }

        @Override
        public Scope activate() {
            return scope;
        }

        @Override
        @SuppressWarnings("removal")
        public Span baggage(String key, String value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            return this;
        }

        @Override
        @SuppressWarnings("removal")
        public Optional<String> baggage(String key) {
            Objects.requireNonNull(key);
            return Optional.empty();
        }

        @Override
        public WritableBaggage baggage() {
            return null;
        }
    }

    private static final class RecordingScope implements Scope {
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }

        @Override
        public boolean isClosed() {
            return closeCount.get() > 0;
        }
    }

    private static final class TestWriterInterceptorContext implements WriterInterceptorContext {
        private final Map<String, Object> properties = new HashMap<>();
        private final AtomicInteger proceedCount = new AtomicInteger();
        private final IOException failure;
        private final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        private Annotation[] annotations = new Annotation[0];
        private Object entity = "entity";
        private OutputStream outputStream = new ByteArrayOutputStream();
        private Class<?> type = String.class;
        private Type genericType = String.class;
        private MediaType mediaType = MediaType.TEXT_PLAIN_TYPE;

        private TestWriterInterceptorContext(ServerSpanLifecycle lifecycle, IOException failure) {
            properties.put(ServerSpanLifecycle.PROPERTY, lifecycle);
            this.failure = failure;
        }

        @Override
        public void proceed() throws IOException {
            proceedCount.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public Object getEntity() {
            return entity;
        }

        @Override
        public void setEntity(Object entity) {
            this.entity = entity;
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public void setOutputStream(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public MultivaluedMap<String, Object> getHeaders() {
            return headers;
        }

        @Override
        public Object getProperty(String name) {
            return properties.get(name);
        }

        @Override
        public Collection<String> getPropertyNames() {
            return properties.keySet();
        }

        @Override
        public void setProperty(String name, Object object) {
            properties.put(name, object);
        }

        @Override
        public void removeProperty(String name) {
            properties.remove(name);
        }

        @Override
        public Annotation[] getAnnotations() {
            return annotations;
        }

        @Override
        public void setAnnotations(Annotation[] annotations) {
            this.annotations = annotations;
        }

        @Override
        public Class<?> getType() {
            return type;
        }

        @Override
        public void setType(Class<?> type) {
            this.type = type;
        }

        @Override
        public Type getGenericType() {
            return genericType;
        }

        @Override
        public void setGenericType(Type genericType) {
            this.genericType = genericType;
        }

        @Override
        public MediaType getMediaType() {
            return mediaType;
        }

        @Override
        public void setMediaType(MediaType mediaType) {
            this.mediaType = mediaType;
        }
    }
}
