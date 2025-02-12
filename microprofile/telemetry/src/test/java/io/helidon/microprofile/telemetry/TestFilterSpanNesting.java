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
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(TestSpanExporter.class)
@AddBean(TestFilterSpanNesting.TestBean.class)
@AddBean(TestFilterSpanNesting.IngressSpanSetter.class)
@AddConfig(key = "otel.sdk.disabled", value = "false")
@AddConfig(key = "otel.traces.exporter", value = "in-memory")
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

        var requestBuilder = webTarget.path("/parentSpanCheck")
                .request(MediaType.TEXT_PLAIN);

        // Our client filter will automatically establish a span for the outgoing Jakarta REST client request.
        Response response = requestBuilder.get();

        assertThat("Response status", response.getStatus(), is(200));

        // Check structure of nested spans.
        List<SpanData> spanData = testSpanExporter.spanData(3);
        Optional<SpanData> ingressSpanData = spanData.stream()
                .filter(sd -> sd.getName().equals("ingressSpan"))
                .findFirst();
        assertThat("ingress span data", ingressSpanData, OptionalMatcher.optionalPresent());

        Optional<SpanData> spanFromJakartaFilter = spanData.stream()
                .filter(sd -> sd.getName().equals("/parentSpanCheck"))
                .findFirst();
        assertThat("/parentSpanCheck span data", spanFromJakartaFilter, OptionalMatcher.optionalPresent());

        // Make sure the parent for the span created by the container filter is the current span we set in our test filter,
        // not the span inspired by the incoming headers.
        assertThat("/parentSpanCheck parent span ID",
                   spanFromJakartaFilter.get().getParentSpanContext().getSpanId(),
                   equalTo(ingressSpanData.get().getSpanContext().getSpanId()));

    }

    @ApplicationScoped
    @Path("/parentSpanCheck")
    public static class TestBean {

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String parentSpanCheck(Request request) {
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

        private Span pseudoIngressSpan;
        private Scope pseudoIngressScope;

        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
            // Create a span that's a child of the span represented in the headers and make it current.
            // Then the HelidonTelemetryContainerFilter will find this one as current and the span *it* adds should be a child
            // of this new pseudo-ingress span which we'll check in the test code.

            Optional<SpanContext> helidonSpanContext =
                    staticTracer.extract(new RequestContextHeaderProvider(requestContext.getHeaders()));

            pseudoIngressSpan = staticTracer.spanBuilder("ingressSpan")
                    .update(spanBuilder -> helidonSpanContext.ifPresent(spanBuilder::parent))
                    .build();
            pseudoIngressScope = pseudoIngressSpan.activate();
        }

        @Override
        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
            pseudoIngressScope.close();
            pseudoIngressSpan.end();
        }
    }
}
