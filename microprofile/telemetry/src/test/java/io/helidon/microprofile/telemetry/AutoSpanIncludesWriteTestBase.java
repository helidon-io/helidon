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
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.ext.ExceptionMapper;
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

    void checkSpanAtWrite(boolean expectedEndedAtWrite,
                          boolean expectedCurrentAtWrite) {
        try (Response response = webTarget.path(BASE_PATH + "/ok")
                .request(MediaType.TEXT_PLAIN)
                .get()) {
            assertThat("Response status", response.getStatus(), is(200));
            assertThat("Response entity", response.readEntity(String.class), is("ok"));
        }

        Span observedSpan = writeProbe.observedSpan();
        assertThat("Writer observed a span", observedSpan, notNullValue());
        assertThat("Span end state at entity serialization", writeProbe.spanEnded(), is(expectedEndedAtWrite));
        assertThat("Automatic span current state at entity serialization",
                   writeProbe.spanWasCurrent(), is(expectedCurrentAtWrite));
        assertThat("Span eventually ended", spanListener.awaitEnded(observedSpan), is(true));
        assertThat("Span ended once", spanListener.endCount(observedSpan), is(1));
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

        SpanData serverSpan = spanExporter.spanData(observedSpan);
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
        assertThat("Automatic span remained open during entity serialization",
                   writeProbe.spanEnded(), is(false));
        assertThat("Automatic span was current during entity serialization",
                   writeProbe.spanWasCurrent(), is(true));
        assertThat("Automatic span eventually ended", spanListener.awaitEnded(serverSpanAtWrite), is(true));
        assertThat("Automatic span ended once", spanListener.endCount(serverSpanAtWrite), is(1));

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

    void checkAsyncResponseWriteSpanParent() {
        checkResponseWriteSpanParent("/async-streaming-child", STREAMING_CHILD_SPAN_NAME, "async-streaming-child");
        assertThat("Asynchronous entity writing used another thread",
                   writeProbe.writeThread(), org.hamcrest.Matchers.not(equalTo(writeProbe.resourceThread())));
    }

    void checkMappedApplicationException() {
        try (Response response = webTarget.path(BASE_PATH + "/mapped-not-found")
                .request(MediaType.TEXT_PLAIN)
                .get()) {
            assertThat("Response status", response.getStatus(), is(404));
            assertThat("Response entity", response.readEntity(String.class), is("mapped-not-found"));
        }

        Span observedSpan = writeProbe.observedSpan();
        assertThat("Mapped response writer observed the automatic span", observedSpan, notNullValue());
        assertThat("Mapped response span eventually ended", spanListener.awaitEnded(observedSpan), is(true));
        assertThat("Mapped response span ended once", spanListener.endCount(observedSpan), is(1));
        assertThat("Mapped application exception was not treated as a write failure",
                   spanListener.failure(observedSpan), org.hamcrest.Matchers.nullValue());

        SpanData serverSpan = spanExporter.spanData(observedSpan);
        assertThat("Mapped 404 server span status", serverSpan.getStatus().getStatusCode(), is(StatusCode.UNSET));
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

        SpanData serverSpan = spanExporter.spanData(observedSpan);
        assertThat("Failed response span status", serverSpan.getStatus().getStatusCode(), is(StatusCode.ERROR));
    }

    void checkFailedResponseFilterEndsSpan() {
        try (Response ignored = webTarget.path(BASE_PATH + "/response-filter-error")
                .request(MediaType.TEXT_PLAIN)
                .get()) {
            // Depending on how far response processing progressed, the client can receive an error response or an exception.
        } catch (ProcessingException ignored) {
            // The server-side lifecycle assertions below are authoritative for this test.
        }

        Span resourceSpan = writeProbe.resourceSpan();
        assertThat("Resource observed the automatic span", resourceSpan, notNullValue());
        assertThat("Failed response-filter span eventually ended", spanListener.awaitEnded(resourceSpan), is(true));
        assertThat("Failed response-filter span ended once", spanListener.endCount(resourceSpan), is(1));
        assertThat("Failed response-filter span recorded the failure",
                   spanListener.failure(resourceSpan), notNullValue());
        assertThat("Failed response-filter span status",
                   spanExporter.spanData(resourceSpan).getStatus().getStatusCode(), is(StatusCode.ERROR));
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
        private final ConcurrentHashMap<Span, CompletableFuture<Void>> ended = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Span, AtomicInteger> endCounts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Span, Throwable> failures = new ConcurrentHashMap<>();

        @Override
        public void started(Span span) {
            ended.put(span, new CompletableFuture<>());
            latestStarted.set(span);
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
        private volatile boolean spanEnded;
        private volatile boolean spanWasCurrent;
        private volatile String resourceThread;
        private volatile String writeThread;
        private final AtomicReference<Span> resourceSpan = new AtomicReference<>();

        @Inject
        WriteProbe(TestSpanListener spanListener) {
            this.spanListener = spanListener;
        }

        @Override
        public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
            Span span = spanListener.latestStarted();
            observedSpan.set(span);
            spanEnded = spanListener.isEnded(span);
            spanWasCurrent = Span.current()
                    .map(current -> current.context().spanId().equals(span.context().spanId()))
                    .orElse(false);
            writeThread = Thread.currentThread().getName();
            context.proceed();
        }

        Span observedSpan() {
            return observedSpan.get();
        }

        boolean spanEnded() {
            return spanEnded;
        }

        boolean spanWasCurrent() {
            return spanWasCurrent;
        }

        void resourceThread(String threadName) {
            resourceThread = threadName;
        }

        String resourceThread() {
            return resourceThread;
        }

        String writeThread() {
            return writeThread;
        }

        void observeResourceSpan() {
            resourceSpan.set(spanListener.latestStarted());
        }

        Span resourceSpan() {
            return resourceSpan.get();
        }

        void reset() {
            observedSpan.set(null);
            spanEnded = false;
            spanWasCurrent = false;
            resourceThread = null;
            writeThread = null;
            resourceSpan.set(null);
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

    @Provider
    @FailResponseFilter
    @ApplicationScoped
    public static class FailingResponseFilter implements ContainerResponseFilter {
        @Override
        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
                throws IOException {
            throw new IOException("Deliberate response filter failure");
        }
    }

    @Provider
    @ApplicationScoped
    public static class MappedNotFoundExceptionMapper implements ExceptionMapper<MappedNotFoundException> {
        @Override
        public Response toResponse(MappedNotFoundException exception) {
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("mapped-not-found")
                    .build();
        }
    }

    public static class MappedNotFoundException extends WebApplicationException {
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

    @NameBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface FailResponseFilter {
    }

    @Path(BASE_PATH)
    @ObserveWrite
    @ApplicationScoped
    public static class TestResource {

        private final Tracer tracer;
        private final WriteProbe writeProbe;

        @Inject
        TestResource(Tracer tracer, WriteProbe writeProbe) {
            this.tracer = tracer;
            this.writeProbe = writeProbe;
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
            return streamingOutput("streaming-child");
        }

        @GET
        @Path("/async-streaming-child")
        @Produces(MediaType.TEXT_PLAIN)
        public void asyncStreamingChild(@Suspended AsyncResponse response) {
            writeProbe.resourceThread(Thread.currentThread().getName());
            Thread.ofPlatform()
                    .name("auto-span-response-writer")
                    .start(() -> response.resume(streamingOutput("async-streaming-child")));
        }

        @GET
        @Path("/mapped-not-found")
        @Produces(MediaType.TEXT_PLAIN)
        public String mappedNotFound() {
            throw new MappedNotFoundException();
        }

        @GET
        @Path("/write-error")
        @Produces(MediaType.TEXT_PLAIN)
        @FailWrite
        public String writeError() {
            return "never-written";
        }

        @GET
        @Path("/response-filter-error")
        @Produces(MediaType.TEXT_PLAIN)
        @FailResponseFilter
        public String responseFilterError() {
            writeProbe.observeResourceSpan();
            return "never-written";
        }

        @GET
        @Path("/no-content")
        public Response noContent() {
            return Response.noContent().build();
        }

        private StreamingOutput streamingOutput(String value) {
            return output -> {
                Span childSpan = tracer.spanBuilder(STREAMING_CHILD_SPAN_NAME).build();
                try (Scope ignored = childSpan.activate()) {
                    output.write(value.getBytes(StandardCharsets.UTF_8));
                } finally {
                    childSpan.end();
                }
            };
        }
    }
}
