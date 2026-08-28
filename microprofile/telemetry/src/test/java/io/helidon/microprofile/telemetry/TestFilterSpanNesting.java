/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Optional;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@AddBean(TestSpanExporter.class)
@AddBean(TestFilterSpanNesting.TestBean.class)
@AddBean(TestFilterSpanNesting.IngressSpanSetter.class)
@AddBean(TestFilterSpanNesting.LateChildSpanSetter.class)
@AddBean(TestFilterSpanNesting.LateBaggageSetter.class)
@AddConfig(key = "otel.sdk.disabled", value = "false")
@AddConfig(key = "otel.traces.exporter", value = "in-memory")
@AddConfig(key = HelidonTelemetryContainerFilter.AUTO_SPAN_INCLUDES_RESPONSE_WRITE, value = "true")
class TestFilterSpanNesting {

    private static Tracer staticTracer;

    @Inject
    private WebTarget webTarget;

    @Inject
    private TestSpanExporter testSpanExporter;

    @Inject
    public TestFilterSpanNesting(Tracer tracer) {
        staticTracer = tracer;
    }

    @BeforeEach
    void setUp() {
        testSpanExporter.clear();
    }

    @Test
    void testExternalParentSpan() {
        for (int i = 0; i < 2; i++) {
            // Our client filter will automatically establish a span for the outgoing Jakarta REST client request.
            try (Response response = webTarget.path("/parentSpanCheck")
                    .request(MediaType.TEXT_PLAIN)
                    .get()) {
                assertThat("Response status", response.getStatus(), is(200));
            }
        }

        // Check structure of nested spans.
        List<SpanData> spanData = testSpanExporter.spanData(8);
        List<SpanData> ingressSpans = spanData.stream()
                .filter(sd -> sd.getName().equals("ingressSpan"))
                .toList();
        List<SpanData> serverSpans = spanData.stream()
                .filter(sd -> sd.getName().equals("/parentSpanCheck"))
                .toList();
        List<SpanData> clientSpans = spanData.stream()
                .filter(sd -> sd.getKind() == SpanKind.CLIENT)
                .toList();
        List<SpanData> lateFilterSpans = spanData.stream()
                .filter(sd -> sd.getName().equals("lateFilterSpan"))
                .toList();
        assertThat("ingress span count", ingressSpans.size(), is(2));
        assertThat("server span count", serverSpans.size(), is(2));
        assertThat("client span count", clientSpans.size(), is(2));
        assertThat("late filter span count", lateFilterSpans.size(), is(2));

        for (SpanData serverSpan : serverSpans) {
            Optional<SpanData> ingressSpan = ingressSpans.stream()
                    .filter(candidate -> candidate.getSpanContext().getSpanId()
                            .equals(serverSpan.getParentSpanContext().getSpanId()))
                    .findFirst();
            assertThat("Automatic server span parent", ingressSpan, OptionalMatcher.optionalPresent());
            assertThat("Parent and child trace IDs",
                       serverSpan.getSpanContext().getTraceId(),
                       equalTo(ingressSpan.orElseThrow().getSpanContext().getTraceId()));
        }

        for (SpanData ingressSpan : ingressSpans) {
            assertThat("Ingress span parent is the corresponding client span",
                       clientSpans.stream().anyMatch(clientSpan -> clientSpan.getSpanContext().getSpanId()
                               .equals(ingressSpan.getParentSpanContext().getSpanId())),
                       is(true));
        }

        for (SpanData lateFilterSpan : lateFilterSpans) {
            assertThat("Later request-filter span is a child of the automatic server span",
                       serverSpans.stream().anyMatch(serverSpan -> serverSpan.getSpanContext().getSpanId()
                               .equals(lateFilterSpan.getParentSpanContext().getSpanId())),
                       is(true));
        }

    }

    @ApplicationScoped
    @Path("/parentSpanCheck")
    public static class TestBean {

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String parentSpanCheck() {
            // The HelidonTelemetryContainerFilter should have been run to establish a new current span. Create a new child.
            return "Hello World!";
        }
    }

    /**
     * Filter to kind-of play the role of upstream ingress code which sets a current span before our normal filter
     * HelidonTelemetryContainerFilter runs.
     */
    @Provider
    @Priority(Priorities.HEADER_DECORATOR)
    static class IngressSpanSetter implements ContainerRequestFilter, ContainerResponseFilter {
        private static final String SCOPE_PROPERTY = IngressSpanSetter.class.getName() + ".scope";
        private static final String SPAN_PROPERTY = IngressSpanSetter.class.getName() + ".span";

        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
            // Create a span that's a child of the span represented in the headers and make it current.
            // Then the HelidonTelemetryContainerFilter will find this one as current and the span *it* adds should be a child
            // of this new pseudo-ingress span which we'll check in the test code.

            Optional<SpanContext> helidonSpanContext =
                    staticTracer.extract(new RequestContextHeaderProvider(requestContext.getHeaders()));

            Span pseudoIngressSpan = staticTracer.spanBuilder("ingressSpan")
                    .update(spanBuilder -> helidonSpanContext.ifPresent(spanBuilder::parent))
                    .build();
            requestContext.setProperty(SPAN_PROPERTY, pseudoIngressSpan);
            requestContext.setProperty(SCOPE_PROPERTY, pseudoIngressSpan.activate());
        }

        @Override
        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
            assertThat("Request context", requestContext, notNullValue());
            assertThat("Response context", responseContext, notNullValue());
            ((Scope) requestContext.getProperty(SCOPE_PROPERTY)).close();
            ((Span) requestContext.getProperty(SPAN_PROPERTY)).end();
            requestContext.removeProperty(SCOPE_PROPERTY);
            requestContext.removeProperty(SPAN_PROPERTY);
        }
    }

    /**
     * Opens a child scope after the telemetry request filter and closes it from the paired response filter.
     */
    @Provider
    @Priority(Priorities.USER + 1000)
    static class LateChildSpanSetter implements ContainerRequestFilter, ContainerResponseFilter {
        private static final String SCOPE_PROPERTY = LateChildSpanSetter.class.getName() + ".scope";
        private static final String SPAN_PROPERTY = LateChildSpanSetter.class.getName() + ".span";

        @Override
        public void filter(ContainerRequestContext requestContext) {
            assertThat("Request context", requestContext, notNullValue());
            Span span = staticTracer.spanBuilder("lateFilterSpan").build();
            requestContext.setProperty(SPAN_PROPERTY, span);
            requestContext.setProperty(SCOPE_PROPERTY, span.activate());
        }

        @Override
        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
            assertThat("Request context", requestContext, notNullValue());
            assertThat("Response context", responseContext, notNullValue());
            ((Scope) requestContext.getProperty(SCOPE_PROPERTY)).close();
            ((Span) requestContext.getProperty(SPAN_PROPERTY)).end();
            requestContext.removeProperty(SCOPE_PROPERTY);
            requestContext.removeProperty(SPAN_PROPERTY);
        }
    }

    /**
     * Opens a nested context which retains the automatic span and changes only baggage. Comparing current span IDs cannot
     * distinguish this scope from the automatic request scope.
     */
    @Provider
    @Priority(Priorities.USER + 1100)
    static class LateBaggageSetter implements ContainerRequestFilter, ContainerResponseFilter {
        private static final String SCOPE_PROPERTY = LateBaggageSetter.class.getName() + ".scope";

        @Override
        public void filter(ContainerRequestContext requestContext) {
            io.opentelemetry.context.Scope scope = io.opentelemetry.api.baggage.Baggage.current()
                    .toBuilder()
                    .put("late-filter", "active")
                    .build()
                    .makeCurrent();
            requestContext.setProperty(SCOPE_PROPERTY, scope);
        }

        @Override
        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
            assertThat("Request context", requestContext, notNullValue());
            assertThat("Response context", responseContext, notNullValue());
            ((io.opentelemetry.context.Scope) requestContext.getProperty(SCOPE_PROPERTY)).close();
            requestContext.removeProperty(SCOPE_PROPERTY);
        }
    }
}
