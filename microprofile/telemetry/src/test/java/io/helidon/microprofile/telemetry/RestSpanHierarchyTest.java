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

import static org.hamcrest.MatcherAssert.assertThat;

import java.net.URI;
import java.net.URL;
import java.util.List;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Before;

import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static org.hamcrest.Matchers.*;

/**
 * Test Span Hierarchy with Tracer Mock
 */
@ExtendWith(ArquillianExtension.class)
public class RestSpanHierarchyTest {

    private Http1Client client;

    @Deployment
    public static WebArchive createDeployment() {

        ConfigAsset config = new ConfigAsset()
                .add("otel.service.name", "helidon-mp-telemetry")
                .add("otel.sdk.disabled", "false")
                .add("telemetry.span.full.url", "false")
                .add("otel.traces.exporter", "in-memory");

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsResource(config, "META-INF/microprofile-config.properties");
    }

    @ArquillianResource
    URL url;

    @Inject
    InMemorySpanExporter spanExporter;

    @Before
    void setup() {
        if (spanExporter != null) {
            spanExporter.reset();
        }

        if (client == null){
            client = Http1Client
                    .builder()
                    .baseUri(URI.create(url.toString()))
                    .build();
        }
    }

    @AfterEach
    void reset(){
        spanExporter.reset();
    }

    @Test
    void spanHierarchy() {

        assertThat(client.get("mixed").request().status(), is(Status.OK_200));

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

        assertThat(client.get("mixed_injected").request().status(), is(Status.OK_200));

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