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

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import io.helidon.tracing.Baggage;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.WritableBaggage;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_STATUS_CODE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class ServerSpanLifecycleTest {

    @Test
    void successfulFinishedEventEndsSpanWithoutWebServerCallback() {
        RecordingSpan span = new RecordingSpan();
        RecordingScope requestScope = new RecordingScope();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, requestScope);

        lifecycle.requestFinished(true);

        assertThat("Span ended", span.endCount.get(), is(1));
        assertThat("Span status", span.status, is(Span.Status.UNSET));
    }

    @Test
    void unsuccessfulFinishedEventEndsSpanWithError() {
        RecordingSpan span = new RecordingSpan();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, new RecordingScope());

        lifecycle.requestFinished(false);

        assertThat("Span ended", span.endCount.get(), is(1));
        assertThat("Span status", span.status, is(Span.Status.ERROR));
        assertThat("Span failure", span.failure, nullValue());
    }

    @Test
    void repeatedFinishedEventsEndSpanOnce() {
        RecordingSpan span = new RecordingSpan();
        RecordingScope requestScope = new RecordingScope();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, requestScope);

        IntStream.range(0, 100).parallel().forEach(index -> lifecycle.requestFinished(index % 2 == 0));

        assertThat("Span ended once", span.endCount.get(), is(1));
    }

    @Test
    void requestScopeRemainsOpenUnderNestedContext() {
        RecordingScope requestScope = new RecordingScope();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(new RecordingSpan(), requestScope);
        ContextKey<String> nestedValue = ContextKey.named("nested-value");

        try (io.opentelemetry.context.Scope ignored = Context.current().with(nestedValue, "nested").makeCurrent()) {
            lifecycle.closeRequestScopeIfCurrent();
            assertThat("Request scope remains open under a nested context", requestScope.closeCount.get(), is(0));
        }

        lifecycle.closeRequestScopeIfCurrent();
        assertThat("Request scope closes after restoring its context", requestScope.closeCount.get(), is(1));
    }

    @Test
    void finalResponseStatusReplacesEarlierStatus() {
        RecordingSpan span = new RecordingSpan();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, new RecordingScope());

        lifecycle.responseStatus(200);
        lifecycle.responseStatus(500);
        lifecycle.requestFinished(true);

        assertThat("Final HTTP status", span.httpStatus, is(500));
        assertThat("Span status", span.status, is(Span.Status.ERROR));
    }

    @Test
    void finishedEventFinalizesStatusAndReleasesLifecycleReference() throws ReflectiveOperationException {
        RecordingSpan span = new RecordingSpan();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, new RecordingScope());
        lifecycle.responseStatus(200);
        ContainerRequest request = containerRequest();
        request.setProperty(ServerSpanLifecycle.PROPERTY, lifecycle);
        ContainerResponse response = new ContainerResponse(request, Response.status(503).build());
        RequestEvent finishedEvent = requestEvent(request, response, RequestEvent.Type.FINISHED, false);
        RequestEventListener requestListener = new HelidonTelemetryRequestEventListener().onRequest(finishedEvent);

        requestListener.onEvent(finishedEvent);

        Field lifecycleField = requestListener.getClass().getDeclaredField("lifecycle");
        lifecycleField.setAccessible(true);
        assertThat("Listener releases lifecycle", lifecycleField.get(requestListener), nullValue());
        assertThat("Monitoring listener does not mutate request properties",
                   request.getProperty(ServerSpanLifecycle.PROPERTY), is(lifecycle));
        assertThat("FINISHED response status", span.httpStatus, is(503));
        assertThat("Unwritten response status", span.status, is(Span.Status.ERROR));
        assertThat("Span ended", span.endCount.get(), is(1));
    }

    @Test
    void listenerReleasesLifecycleAfterAsynchronousResourceFinishes() throws ReflectiveOperationException {
        RecordingSpan span = new RecordingSpan();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, new RecordingScope());
        ContainerRequest request = containerRequest();
        request.setProperty(ServerSpanLifecycle.PROPERTY, lifecycle);
        ContainerResponse response = new ContainerResponse(request, Response.ok().build());
        RequestEventListener requestListener = new HelidonTelemetryRequestEventListener()
                .onRequest(requestEvent(request, response, RequestEvent.Type.RESOURCE_METHOD_START, false));
        Field lifecycleField = requestListener.getClass().getDeclaredField("lifecycle");
        lifecycleField.setAccessible(true);

        requestListener.onEvent(requestEvent(request, response, RequestEvent.Type.RESOURCE_METHOD_START, false));
        requestListener.onEvent(requestEvent(request, response, RequestEvent.Type.FINISHED, true));

        assertThat("Span remains open until resource completion", span.endCount.get(), is(0));
        assertThat("Listener retains lifecycle until resource completion", lifecycleField.get(requestListener), is(lifecycle));

        requestListener.onEvent(requestEvent(request, response, RequestEvent.Type.RESOURCE_METHOD_FINISHED, false));

        assertThat("Span ends at resource completion", span.endCount.get(), is(1));
        assertThat("Listener releases lifecycle after resource completion", lifecycleField.get(requestListener), nullValue());
    }

    @Test
    void resourceMethodCanFinishBeforeRequest() {
        RecordingSpan span = new RecordingSpan();
        RecordingScope requestScope = new RecordingScope();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, requestScope);

        lifecycle.resourceMethodStarted();
        lifecycle.resourceMethodFinished();

        assertThat("Span remains open", span.endCount.get(), is(0));

        lifecycle.requestFinished(true);

        assertThat("Span ended", span.endCount.get(), is(1));
    }

    @Test
    void requestCanFinishBeforeResourceMethod() {
        RecordingSpan span = new RecordingSpan();
        RecordingScope requestScope = new RecordingScope();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, requestScope);

        lifecycle.resourceMethodStarted();
        lifecycle.requestFinished(true);

        assertThat("Span remains open", span.endCount.get(), is(0));

        lifecycle.resourceMethodFinished();

        assertThat("Span ended", span.endCount.get(), is(1));
    }

    @Test
    void responseFailureIsCapturedOnlyAfterResponseProcessingStarts() {
        RecordingSpan span = new RecordingSpan();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, new RecordingScope());
        RuntimeException mappedResourceFailure = new RuntimeException("mapped");
        RuntimeException responseFailure = new RuntimeException("response");

        lifecycle.responseFailure(mappedResourceFailure);
        lifecycle.responseProcessingStarted();
        lifecycle.responseFailure(responseFailure);
        lifecycle.requestFinished(true);

        assertThat("Response failure recorded", span.failure, is(responseFailure));
        assertThat("Span status", span.status, is(Span.Status.ERROR));
    }

    @Test
    void writerFailureIsCaptured() {
        RecordingSpan span = new RecordingSpan();
        ServerSpanLifecycle lifecycle = new ServerSpanLifecycle(span, new RecordingScope());
        RuntimeException writerFailure = new RuntimeException("writer");

        lifecycle.writerFailure(writerFailure);
        lifecycle.requestFinished(false);

        assertThat("Writer failure recorded", span.failure, is(writerFailure));
        assertThat("Span status", span.status, is(Span.Status.ERROR));
    }

    private static ContainerRequest containerRequest() {
        URI uri = URI.create("http://localhost/test");
        return new ContainerRequest(uri, uri, "GET", null, new MapPropertiesDelegate());
    }

    private static RequestEvent requestEvent(ContainerRequest request,
                                             ContainerResponse response,
                                             RequestEvent.Type type,
                                             boolean responseWritten) {
        return (RequestEvent) Proxy.newProxyInstance(RequestEvent.class.getClassLoader(),
                                                     new Class<?>[] {RequestEvent.class},
                                                     (proxy, method, args) -> switch (method.getName()) {
                                                     case "getType" -> type;
                                                     case "getContainerRequest" -> request;
                                                     case "getContainerResponse" -> response;
                                                     case "isResponseWritten" -> responseWritten;
                                                     default -> method.getReturnType() == boolean.class ? false : null;
                                                     });
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

        private final AtomicInteger endCount = new AtomicInteger();
        private volatile Status status = Status.UNSET;
        private volatile Throwable failure;
        private volatile int httpStatus;

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
            if (HTTP_STATUS_CODE.equals(key)) {
                httpStatus = value.intValue();
            }
            return this;
        }

        @Override
        public void status(Status status) {
            this.status = status;
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
            endCount.incrementAndGet();
        }

        @Override
        public void end(Throwable t) {
            failure = t;
            endCount.incrementAndGet();
        }

        @Override
        public Scope activate() {
            return new RecordingScope();
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
        private final Thread owner = Thread.currentThread();
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public void close() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Scope closed from a thread other than its owner");
            }
            closeCount.incrementAndGet();
        }

        @Override
        public boolean isClosed() {
            return closeCount.get() > 0;
        }
    }
}
