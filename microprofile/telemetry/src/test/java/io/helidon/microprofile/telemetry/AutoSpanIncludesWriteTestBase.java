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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;
import io.helidon.webserver.http.ServerResponse;

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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
    static final String ENTIRE_REQUEST_SPAN_NAME = "entireRequest";

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

    void checkSpanAtWrite(boolean expectedEndedAtWrite) {
        try (Response response = webTarget.path(BASE_PATH + "/ok")
                .request(MediaType.TEXT_PLAIN)
                .get()) {
            assertThat("Response status", response.getStatus(), is(200));
            assertThat("Response entity", response.readEntity(String.class), is("ok"));
        }

        Span observedSpan = writeProbe.observedSpan();
        assertThat("Writer observed a span", observedSpan, notNullValue());
        assertThat("Span scope was closed before entity serialization", writeProbe.spanClosed(), is(true));
        assertThat("Span end state at entity serialization", writeProbe.spanEnded(), is(expectedEndedAtWrite));
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

    void checkResponseWriteSpanParent() {
        try (Response response = webTarget.path(BASE_PATH + "/ok")
                .request(MediaType.TEXT_PLAIN)
                .get()) {
            assertThat("Response status", response.getStatus(), is(200));
            assertThat("Response entity", response.readEntity(String.class), is("ok"));
        }

        Span responseWriteSpan = writeProbe.observedSpan();
        assertThat("Writer observed a span", responseWriteSpan, notNullValue());
        assertThat("Response write span scope was closed before entity serialization",
                   writeProbe.spanClosed(), is(true));
        assertThat("Response write span remained open during entity serialization",
                   writeProbe.spanEnded(), is(false));
        assertThat("Response write span eventually ended", spanListener.awaitEnded(responseWriteSpan), is(true));

        List<SpanData> spans = spanExporter.spanData(3);
        SpanData entireRequestSpan = spans.stream()
                .filter(span -> span.getName().equals(ENTIRE_REQUEST_SPAN_NAME))
                .findFirst()
                .orElseThrow();
        SpanData exportedResponseWriteSpan = spans.stream()
                .filter(span -> span.getSpanContext().getSpanId().equals(responseWriteSpan.context().spanId()))
                .findFirst()
                .orElseThrow();

        assertThat("Response write span parent",
                   exportedResponseWriteSpan.getParentSpanContext().getSpanId(),
                   equalTo(entireRequestSpan.getSpanContext().getSpanId()));
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
            ended.computeIfAbsent(span, ignored -> new CompletableFuture<>()).complete(null);
        }

        @Override
        public void ended(Span span, Throwable t) {
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

        void reset() {
            latestStarted.set(null);
            closed.clear();
            ended.clear();
        }
    }

    @Provider
    @ObserveWrite
    @ApplicationScoped
    public static class WriteProbe implements WriterInterceptor {

        private final TestSpanListener spanListener;
        private final AtomicReference<Span> observedSpan = new AtomicReference<>();
        private volatile boolean spanClosed;
        private volatile boolean spanEnded;

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

        void reset() {
            observedSpan.set(null);
            spanClosed = false;
            spanEnded = false;
        }
    }

    @Provider
    @Priority(Priorities.HEADER_DECORATOR)
    @ApplicationScoped
    public static class EntireRequestSpanFilter implements ContainerRequestFilter, ContainerResponseFilter {

        private final Tracer tracer;

        @jakarta.ws.rs.core.Context
        private ServerResponse serverResponse;

        private Span entireRequestSpan;
        private Scope entireRequestScope;

        @Inject
        EntireRequestSpanFilter(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public void filter(ContainerRequestContext requestContext) {
            Optional<SpanContext> propagatedSpanContext =
                    tracer.extract(new RequestContextHeaderProvider(requestContext.getHeaders()));
            entireRequestSpan = tracer.spanBuilder(ENTIRE_REQUEST_SPAN_NAME)
                    .update(builder -> propagatedSpanContext.ifPresent(builder::parent))
                    .build();
            entireRequestScope = entireRequestSpan.activate();
        }

        @Override
        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
            entireRequestScope.close();
            serverResponse.whenSent(entireRequestSpan::end);
        }
    }

    @Path(BASE_PATH)
    @ObserveWrite
    @ApplicationScoped
    public static class TestResource {

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
    }
}
