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

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NameBinding;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.junit.jupiter.api.BeforeEach;

import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_STATUS_CODE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@AddBean(TestSpanExporter.class)
@AddBean(AutoSpanIncludesWriteTestBase.TestResource.class)
@AddBean(AutoSpanIncludesWriteTestBase.WriteProbe.class)
@AddBean(AutoSpanIncludesWriteTestBase.TestSpanListener.class)
@AddConfig(key = "otel.sdk.disabled", value = "false")
@AddConfig(key = "otel.traces.exporter", value = "in-memory")
class AutoSpanIncludesWriteTestBase {

    private static final String BASE_PATH = "/auto-span-includes-write";
    static final String WRITER_CHILD_SPAN_NAME = "writerChild";
    static final String STREAMING_CHILD_SPAN_NAME = "streamingChild";

    @Inject
    private WebTarget webTarget;

    @Inject
    private WriteProbe writeProbe;

    @Inject
    private TestSpanListener spanListener;

    @Inject
    private TestSpanExporter spanExporter;

    @Inject
    private Tracer tracer;

    private boolean listenerRegistered;

    @BeforeEach
    void reset() {
        if (!listenerRegistered) {
            tracer.register(spanListener);
            listenerRegistered = true;
        }
        writeProbe.reset();
        spanListener.reset();
        spanExporter.clear();
    }

    void checkSpanAtWrite(boolean expectedClosedAtWrite,
                          boolean expectedEndedAtWrite,
                          boolean expectedCurrentAtWrite) {
        try (Response response = webTarget.path(BASE_PATH + "/ok")
                .request(MediaType.TEXT_PLAIN)
                .get()) {
            assertThat("Response status", response.getStatus(), is(200));
            assertThat("Response entity", response.readEntity(String.class), is("ok"));
        }

        Span observedSpan = writeProbe.observedSpan();
        assertThat("Writer observed a span", observedSpan, notNullValue());
        assertThat("Span scope state at entity serialization", writeProbe.spanClosed(), is(expectedClosedAtWrite));
        assertThat("Span end state at entity serialization", writeProbe.spanEnded(), is(expectedEndedAtWrite));
        assertThat("Automatic span current state at entity serialization",
                   writeProbe.spanWasCurrent(), is(expectedCurrentAtWrite));
        assertThat("Span eventually ended", spanListener.awaitEnded(observedSpan), is(true));
        spanExporter.spanData(2);
    }

    void checkErrorStatus() {
        try (Response response = webTarget.path(BASE_PATH + "/error")
                .request(MediaType.TEXT_PLAIN)
                .get()) {
            assertThat("Response status", response.getStatus(), is(500));
            assertThat("Response entity", response.readEntity(String.class), is("error"));
        }

        Span observedSpan = writeProbe.observedSpan();
        assertThat("Writer observed a span", observedSpan, notNullValue());
        assertThat("Span eventually ended", spanListener.awaitEnded(observedSpan), is(true));

        List<SpanData> spans = spanExporter.spanData(2);
        SpanData serverSpan = spans.stream()
                .filter(span -> span.getKind() == SpanKind.SERVER)
                .findFirst()
                .orElseThrow();
        assertThat("Server span status", serverSpan.getStatus().getStatusCode(), is(StatusCode.ERROR));
        assertThat("HTTP status attribute",
                   serverSpan.getAttributes().get(AttributeKey.longKey(HTTP_STATUS_CODE)),
                   is(500L));
    }

    void checkResponseWriteSpanParent(String path, String childSpanName, String expectedEntity) {
        try (Response response = webTarget.path(BASE_PATH + path)
                .request(MediaType.TEXT_PLAIN)
                .get()) {
            assertThat("Response status", response.getStatus(), is(200));
            assertThat("Response entity", response.readEntity(String.class), is(expectedEntity));
        }

        Span serverSpanAtWrite = writeProbe.observedSpan();
        assertThat("Writer observed the automatic span", serverSpanAtWrite, notNullValue());
        assertThat("Automatic span scope remained open during entity serialization",
                   writeProbe.spanClosed(), is(false));
        assertThat("Automatic span remained open during entity serialization",
                   writeProbe.spanEnded(), is(false));
        assertThat("Automatic span was current during entity serialization",
                   writeProbe.spanWasCurrent(), is(true));
        assertThat("Automatic span eventually ended", spanListener.awaitEnded(serverSpanAtWrite), is(true));

        List<SpanData> spans = spanExporter.spanData(3);
        SpanData serverSpan = spans.stream()
                .filter(span -> span.getKind() == SpanKind.SERVER)
                .findFirst()
                .orElseThrow();
        SpanData childSpan = spans.stream()
                .filter(span -> span.getName().equals(childSpanName))
                .findFirst()
                .orElseThrow();

        assertThat("Response-write child span parent",
                   childSpan.getParentSpanContext().getSpanId(),
                   equalTo(serverSpan.getSpanContext().getSpanId()));
    }

    void checkFailedWriteEndsSpan() {
        try (Response ignored = webTarget.path(BASE_PATH + "/write-error")
                .request(MediaType.TEXT_PLAIN)
                .get()) {
            // Depending on how far response writing progressed, the client can receive an error response or an exception.
        } catch (ProcessingException ignored) {
            // The server-side lifecycle assertions below are authoritative for this test.
        }

        Span observedSpan = writeProbe.observedSpan();
        assertThat("Failing writer observed the automatic span", observedSpan, notNullValue());
        assertThat("Automatic span was current in the failing writer", writeProbe.spanWasCurrent(), is(true));
        assertThat("Failed response span eventually ended", spanListener.awaitEnded(observedSpan), is(true));
        assertThat("Failed response span ended once", spanListener.endCount(observedSpan), is(1));
        assertThat("Failed response span recorded the write failure",
                   spanListener.failure(observedSpan), notNullValue());

        List<SpanData> spans = spanExporter.spanData(2);
        SpanData serverSpan = spans.stream()
                .filter(span -> span.getKind() == SpanKind.SERVER)
                .findFirst()
                .orElseThrow();
        assertThat("Failed response span status", serverSpan.getStatus().getStatusCode(), is(StatusCode.ERROR));
    }

    void checkNoEntitySpanEnds() {
        try (Response response = webTarget.path(BASE_PATH + "/no-content")
                .request()
                .get()) {
            assertThat("Response status", response.getStatus(), is(204));
        }

        List<SpanData> spans = spanExporter.spanData(2);
        assertThat("Entity-less response exported its server span",
                   spans.stream().anyMatch(span -> span.getKind() == SpanKind.SERVER), is(true));
    }

    @NameBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface ObserveWrite {
    }

    @ApplicationScoped
    public static class TestSpanListener implements SpanListener {

        private final AtomicReference<Span> latestStarted = new AtomicReference<>();
        private final Set<Span> closed = ConcurrentHashMap.newKeySet();
        private final ConcurrentHashMap<Span, CompletableFuture<Void>> ended = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Span, AtomicInteger> endCounts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Span, Throwable> failures = new ConcurrentHashMap<>();

        @Override
        public void started(Span span) {
            ended.put(span, new CompletableFuture<>());
            latestStarted.set(span);
        }

        @Override
        public void closed(Span span, Scope scope) {
            closed.add(span);
        }

        @Override
        public void ended(Span span) {
            endCounts.computeIfAbsent(span, ignored -> new AtomicInteger()).incrementAndGet();
            ended.computeIfAbsent(span, ignored -> new CompletableFuture<>()).complete(null);
        }

        @Override
        public void ended(Span span, Throwable t) {
            failures.put(span, t);
            ended(span);
        }

        Span latestStarted() {
            return latestStarted.get();
        }

        boolean isClosed(Span span) {
            return closed.contains(span);
        }

        boolean isEnded(Span span) {
            CompletableFuture<Void> future = ended.get(span);
            return future != null && future.isDone();
        }

        boolean awaitEnded(Span span) {
            try {
                ended.computeIfAbsent(span, ignored -> new CompletableFuture<>()).get(5, TimeUnit.SECONDS);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        int endCount(Span span) {
            AtomicInteger count = endCounts.get(span);
            return count == null ? 0 : count.get();
        }

        Throwable failure(Span span) {
            return failures.get(span);
        }

        void reset() {
            latestStarted.set(null);
            closed.clear();
            ended.clear();
            endCounts.clear();
            failures.clear();
        }
    }

    @Provider
    @ObserveWrite
    @Priority(Priorities.USER)
    @ApplicationScoped
    public static class WriteProbe implements WriterInterceptor {

        private final TestSpanListener spanListener;
        private final AtomicReference<Span> observedSpan = new AtomicReference<>();
        private volatile boolean spanClosed;
        private volatile boolean spanEnded;
        private volatile boolean spanWasCurrent;

        @Inject
        WriteProbe(TestSpanListener spanListener) {
            this.spanListener = spanListener;
        }

        @Override
        public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
            Span span = spanListener.latestStarted();
            observedSpan.set(span);
            spanClosed = spanListener.isClosed(span);
            spanEnded = spanListener.isEnded(span);
            spanWasCurrent = Span.current()
                    .map(current -> current.context().spanId().equals(span.context().spanId()))
                    .orElse(false);
            context.proceed();
        }

        Span observedSpan() {
            return observedSpan.get();
        }

        boolean spanClosed() {
            return spanClosed;
        }

        boolean spanEnded() {
            return spanEnded;
        }

        boolean spanWasCurrent() {
            return spanWasCurrent;
        }

        void reset() {
            observedSpan.set(null);
            spanClosed = false;
            spanEnded = false;
            spanWasCurrent = false;
        }
    }

    @Provider
    @WriterChild
    @Priority(Priorities.USER + 100)
    @ApplicationScoped
    public static class ChildSpanWriterInterceptor implements WriterInterceptor {

        private final Tracer tracer;

        @Inject
        ChildSpanWriterInterceptor(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
            Span childSpan = tracer.spanBuilder(WRITER_CHILD_SPAN_NAME).build();
            try (Scope ignored = childSpan.activate()) {
                context.proceed();
            } finally {
                childSpan.end();
            }
        }
    }

    @Provider
    @FailWrite
    @Priority(Priorities.USER + 100)
    @ApplicationScoped
    public static class FailingWriterInterceptor implements WriterInterceptor {
        @Override
        public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
            throw new IOException("Deliberate response write failure");
        }
    }

    @NameBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface WriterChild {
    }

    @NameBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface FailWrite {
    }

    @Path(BASE_PATH)
    @ObserveWrite
    @ApplicationScoped
    public static class TestResource {

        private final Tracer tracer;

        @Inject
        TestResource(Tracer tracer) {
            this.tracer = tracer;
        }

        @GET
        @Path("/ok")
        @Produces(MediaType.TEXT_PLAIN)
        public String ok() {
            return "ok";
        }

        @GET
        @Path("/error")
        @Produces(MediaType.TEXT_PLAIN)
        public Response error() {
            return Response.serverError().entity("error").build();
        }

        @GET
        @Path("/writer-child")
        @Produces(MediaType.TEXT_PLAIN)
        @WriterChild
        public String writerChild() {
            return "writer-child";
        }

        @GET
        @Path("/streaming-child")
        @Produces(MediaType.TEXT_PLAIN)
        public StreamingOutput streamingChild() {
            return output -> {
                Span childSpan = tracer.spanBuilder(STREAMING_CHILD_SPAN_NAME).build();
                try (Scope ignored = childSpan.activate()) {
                    output.write("streaming-child".getBytes(StandardCharsets.UTF_8));
                } finally {
                    childSpan.end();
                }
            };
        }

        @GET
        @Path("/write-error")
        @Produces(MediaType.TEXT_PLAIN)
        @FailWrite
        public String writeError() {
            return "never-written";
        }

        @GET
        @Path("/no-content")
        public Response noContent() {
            return Response.noContent().build();
        }
    }
}
