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

import java.util.ArrayList;
import java.util.List;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.Wrapper;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(TestListenersWithInjection.TestResource.class)
@AddConfig(key = "otel.sdk.disabled", value = "false")
@AddConfig(key = "telemetry.injection-type", value = "neutral")
class TestListenersWithInjection {

    private static final TestSpanListener testSpanListener = new TestSpanListener();

    @Inject
    private WebTarget webTarget;

    @Inject
    private TestResource testResource;

    @BeforeAll
    static void prepareTracer() {
        Tracer.global().register(testSpanListener);
    }

    @BeforeEach
    void clear() {
        testSpanListener.clear();
    }

    @Test
    void checkNotifications() throws InterruptedException {
        int startingBefore = testSpanListener.starting();
        int startedBefore = testSpanListener.started();
        int closedBefore = testSpanListener.closed();
        int endedBefore = testSpanListener.ended();

        testResource.go();

        assertThat("Starting", testSpanListener.starting() - startingBefore, is(1));
        assertThat("Started", testSpanListener.started() - startedBefore, is(1));
        assertThat("Closed", testSpanListener.closed() - closedBefore, is(1));
        assertThat("Ended", testSpanListener.ended() - endedBefore, is(1));

    }

    @Test
    void checkTypesOfListenerParameters() throws InterruptedException {

        int startingBefore = testSpanListener.starting();
        int startedBefore = testSpanListener.started();
        int closedBefore = testSpanListener.closed();
        int endedBefore = testSpanListener.ended();

        // Access the resource using HTTP so the filter will set a current span that the resource will inject.
        Response response = webTarget.path("/test/work").request(MediaType.TEXT_PLAIN).get();

        assertThat("Starting", testSpanListener.starting() - startingBefore, greaterThan(0));
        assertThat("Started", testSpanListener.started() - startedBefore, greaterThan(0));
        assertThat("Closed", testSpanListener.closed() - closedBefore, greaterThan(0));
        assertThat("Ended", testSpanListener.ended() - endedBefore, greaterThan(0));

        // The listener should have recorded span lifecycles for these spans:
        // 1. outgoing client request with name HTTP GET
        // 2. incoming server request added by the Helidon MP filter with name /test/work
        // 3. explicitly-created span in the service REST method with name explicitSpan
        //
        // Further, the injected span in the REST resource should match span 2 above (the span added by the Helidon MP filter)

        assertThat("Response from test resource", response.getStatus(), is(equalTo(200)));

        assertThat("Number of spans recorded by listener", testSpanListener.spansStarted, hasSize(3));

        Span outgoingClientSpan = testSpanListener.spansStarted.getFirst();
        Span incomingServerSpanFromFilter = testSpanListener.spansStarted.get(1);
        Span explicitlyCreatedSpan = testSpanListener.spansStarted.getLast();

        io.opentelemetry.api.trace.SpanContext incomingServerRequestNativeSpanContextViaInjection =
                testResource.copyOfInjectedOtelSpanContext();

        io.opentelemetry.api.trace.Span incomingServerRequestNativeSpanFromFilterViaListener =
                incomingServerSpanFromFilter.unwrap(io.opentelemetry.api.trace.Span.class);
        assertThat("Base native span context via injection vs. via listener",
                   incomingServerRequestNativeSpanContextViaInjection,
                   is(equalTo(incomingServerRequestNativeSpanFromFilterViaListener.getSpanContext())));

        assertThat("Parent of explicitly-created span", explicitlyCreatedSpan.unwrap(ReadableSpan.class).getParentSpanContext(),
                   equalTo(incomingServerRequestNativeSpanContextViaInjection));

    }

    @Test
    void checkTypesOfInjectedFields() {

        assertThat("Injected tracer",
                   testResource.injectedOtelTracer(),
                   allOf(instanceOf(io.opentelemetry.api.trace.Tracer.class),
                         instanceOf(WrappedProducer.WrappedTracer.class)));

        assertThat("Injected span", testResource.injectedOtelSpan(), allOf(instanceOf(io.opentelemetry.api.trace.Span.class),
                                                                           instanceOf(Wrapper.class)));
    }

    @Path("/test")
    public static class TestResource {

        SpanContext copyOfInjectedOtelSpanContext;

        @Inject
        private io.opentelemetry.api.trace.Tracer injectedOtelTracer;

        @Inject
        private io.opentelemetry.api.trace.Span injectedOtelSpan;

        @WithSpan
        void go() throws InterruptedException {
            Thread.sleep(500);
        }

        io.opentelemetry.api.trace.Tracer injectedOtelTracer() {
            return injectedOtelTracer;
        }

        io.opentelemetry.api.trace.Span injectedOtelSpan() {
            return injectedOtelSpan;
        }

        io.opentelemetry.api.trace.SpanContext copyOfInjectedOtelSpanContext() {
            return copyOfInjectedOtelSpanContext;
        }

        @Path("/work")
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String workWithInjectedTracerAndSpan() throws InterruptedException {
            copyOfInjectedOtelSpanContext = injectedOtelSpan.getSpanContext();
            io.opentelemetry.api.trace.SpanBuilder otelSpanBuilder = injectedOtelTracer.spanBuilder("explicitSpan");
            io.opentelemetry.api.trace.Span otelSpan = otelSpanBuilder.startSpan();

            try (io.opentelemetry.context.Scope ignored = otelSpan.makeCurrent()) {
                Thread.sleep(200);
            }
            otelSpan.end();

            injectedOtelSpan.setAttribute("marker", true);
            return "worked";
        }
    }

    private static class TestSpanListener implements SpanListener {

        private final List<Span.Builder<?>> spanBuildersStarting = new ArrayList<>();

        private final List<Span> spansStarted = new ArrayList<>();

        private final List<Span> spansActivated = new ArrayList<>();

        private final List<Scope> scopesActivated = new ArrayList<>();

        private final List<Span> spansClosed = new ArrayList<>();
        private final List<Scope> scopesClosed = new ArrayList<>();

        private final List<Span> spansEnded = new ArrayList<>();

        void clear() {
            spanBuildersStarting.clear();

            spansStarted.clear();

            spansActivated.clear();
            scopesActivated.clear();

            spansClosed.clear();
            scopesClosed.clear();

            spansEnded.clear();
        }

        @Override
        public void starting(Span.Builder<?> spanBuilder) {
            spanBuildersStarting.add(spanBuilder);
        }

        @Override
        public void started(Span span) {
            spansStarted.add(span);
        }

        @Override
        public void activated(Span span, Scope scope) {
            spansActivated.add(span);
            scopesActivated.add(scope);
        }

        @Override
        public void closed(Span span, Scope scope) {
            spansClosed.add(span);
            scopesClosed.add(scope);
        }

        @Override
        public void ended(Span span) {
            spansEnded.add(span);
        }

        int starting() {
            return spanBuildersStarting.size();
        }

        List<Span.Builder<?>> spanBuilderStarting() {
            return spanBuildersStarting;
        }

        int started() {
            return spansStarted.size();
        }

        List<Span> spanStarted() {
            return spansStarted;
        }

        int activated() {
            return spansActivated.size();
        }

        List<Span> spanActivated() {
            return spansActivated;
        }

        List<Scope> scopeActivated() {
            return scopesActivated;
        }

        int closed() {
            return spansClosed.size();
        }

        List<Span> spanClosed() {
            return spansClosed;
        }

        List<Scope> scopeClosed() {
            return scopesClosed;
        }

        int ended() {
            return spansEnded.size();
        }

        List<Span> spanEnded() {
            return spansEnded;
        }
    }
}
