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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tracing.HeaderConsumer;
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
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(TestSpanExporter.class)
@AddBean(TestFilterSpanNesting.TestBean.class)
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

        // Create a span so we can use its span context as a parent.
        Span spanForHeaders = staticTracer.spanBuilder("spanForHeaders").build();

        Response response;

        // Now, create another span to be the "real" current one when our filter is executed. This represents, for example,
        // an ingress span different from the span conveyed by incoming headers.
        Span pseudoIngressSpan = staticTracer.spanBuilder("ingressSpan")
                .parent(spanForHeaders.context())
                .build();

        try (Scope ignored = pseudoIngressSpan.activate()) {
            response = requestBuilder.get();
        }

        assertThat("Response status", response.getStatus(), is(200));

        // Check structure of nested spans.
        List<SpanData> spanData = testSpanExporter.spanData(2);
        Optional<SpanData> httpGetSpanData = spanData.stream()
                // Use startsWith because starting in 5.x the name will also include the path.
                .filter(sd -> sd.getName().startsWith("HTTP GET"))
                .findFirst();
        assertThat("HTTP GET span data", httpGetSpanData, OptionalMatcher.optionalPresent());

        Optional<SpanData> spanFromJakartaFilter = spanData.stream()
                .filter(sd -> sd.getName().equals("/parentSpanCheck"))
                .findFirst();
        assertThat("/parentSpanCheck span data", spanFromJakartaFilter, OptionalMatcher.optionalPresent());

        // Make sure the parent for the span created by the filter is the current span we set in our test filter,
        // not the span inspired by the incoming headers.
        assertThat("/parentSpanCheck parent span ID",
                   spanFromJakartaFilter.get().getParentSpanContext().getSpanId(),
                   equalTo(httpGetSpanData.get().getSpanContext().getSpanId()));

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
}
