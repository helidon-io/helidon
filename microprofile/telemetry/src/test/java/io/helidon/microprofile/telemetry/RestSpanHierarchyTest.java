/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.List;

import io.helidon.http.Status;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test Span Hierarchy with Tracer Mock
 */
@HelidonTest
@AddBean(InMemorySpanExporter.class)
@AddBean(InMemorySpanExporterProvider.class)
@AddConfig(key = "otel.service.name", value = "helidon-mp-telemetry")
@AddConfig(key = "otel.sdk.disabled", value = "false")
@AddConfig(key = "telemetry.span.full.url", value = "false")
@AddConfig(key = "otel.traces.exporter", value = "in-memory")
public class RestSpanHierarchyTest {

    @Inject
    WebTarget webTarget;

    @Inject
    InMemorySpanExporter spanExporter;

    @BeforeEach
    void setup() {
        if (spanExporter != null) {
            spanExporter.reset();
        }
    }

    @AfterEach
    void reset(){
        spanExporter.reset();
    }

    @Test
    void spanHierarchy() {

        assertThat(webTarget.request("mixed").get(), is(Status.OK_200));

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(3);
        assertThat(spanItems.size(), is(3));
        assertThat(spanItems.get(0).getKind(), is(SERVER));
        assertThat(spanItems.get(0).getName(), is("mixed_inner"));
        assertThat(spanItems.get(0).getAttributes().get(AttributeKey.stringKey("attribute")), is("value"));
        assertThat(spanItems.get(0).getParentSpanId(), is(spanItems.get(1).getSpanId()));


        assertThat(spanItems.get(1).getKind(), is(INTERNAL));
        assertThat(spanItems.get(1).getName(), is("mixed_parent"));
        assertThat(spanItems.get(1).getParentSpanId(), is(spanItems.get(2).getSpanId()));


        assertThat(spanItems.get(2).getKind(), is(SERVER));
        assertThat(spanItems.get(2).getName(), is("mixed"));
    }

    @Test
    void spanHierarchyInjected() {

        assertThat(webTarget.request("mixed_injected").get(), is(Status.OK_200));

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(3);
        assertThat(spanItems.size(), is(3));
        assertThat(spanItems.get(0).getKind(), is(SERVER));
        assertThat(spanItems.get(0).getName(), is("mixed_inner_injected"));
        assertThat(spanItems.get(0).getAttributes().get(AttributeKey.stringKey("attribute")), is("value"));
        assertThat(spanItems.get(0).getParentSpanId(), is(spanItems.get(1).getSpanId()));


        assertThat(spanItems.get(1).getKind(), is(INTERNAL));
        assertThat(spanItems.get(1).getName(), is("mixed_parent_injected"));
        assertThat(spanItems.get(1).getParentSpanId(), is(spanItems.get(2).getSpanId()));


        assertThat(spanItems.get(2).getKind(), is(SERVER));
        assertThat(spanItems.get(2).getName(), is("mixed_injected"));
    }


    @Path("/")
    public static class SpanResource {

        @Inject
        private io.helidon.tracing.Tracer helidonTracerInjected;


        @GET
        @Path("mixed")
        @WithSpan("mixed_parent")
        public Response mixedSpan() {

            io.helidon.tracing.Tracer helidonTracer = io.helidon.tracing.Tracer.global();
            io.helidon.tracing.Span mixedSpan = helidonTracer.spanBuilder("mixed_inner")
                    .kind(io.helidon.tracing.Span.Kind.SERVER)
                    .tag("attribute", "value")
                    .start();
            mixedSpan.end();

            return Response.ok().build();
        }

        @GET
        @Path("mixed_injected")
        @WithSpan("mixed_parent_injected")
        public Response mixedSpanInjected() {

            io.helidon.tracing.Span mixedSpan = helidonTracerInjected.spanBuilder("mixed_inner_injected")
                    .kind(io.helidon.tracing.Span.Kind.SERVER)
                    .tag("attribute", "value")
                    .start();
            mixedSpan.end();

            return Response.ok().build();
        }
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }
}