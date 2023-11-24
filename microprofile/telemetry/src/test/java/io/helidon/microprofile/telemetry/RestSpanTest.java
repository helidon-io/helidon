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

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_VERSION;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.hamcrest.Matchers;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Before;
import org.junit.jupiter.api.BeforeAll;

import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static org.hamcrest.Matchers.*;

/**
 * Test Rest with Tracer Mock
 */
@ExtendWith(ArquillianExtension.class)
public class RestSpanTest {

    private static final String TEST_SERVICE_NAME = "helidon/mp/telemetry";
    private static final String TEST_SERVICE_VERSION = "0.1.0-TEST";

    @Deployment
    public static WebArchive createDeployment() {

        ConfigAsset config = new ConfigAsset()
                .add("otel.service.name", TEST_SERVICE_NAME)
                .add("otel.resource.attributes", SERVICE_VERSION.getKey() + "=" + TEST_SERVICE_VERSION)
                .add("otel.sdk.disabled", "false")
                .add("otel.traces.exporter", "in-memory");

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class, BasicHttpClient.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsResource(config, "META-INF/microprofile-config.properties");
    }

    @ArquillianResource
    URL url;

    @Inject
    InMemorySpanExporter spanExporter;

    private BasicHttpClient basicClient;

    @Before
    void setUp() {
        // Only want to run on server
        if (spanExporter != null) {
            spanExporter.reset();
            basicClient = new BasicHttpClient(url);
        }
    }

    @Test
    void spanHierarchy() {

        assertThat(basicClient.get("mixed"), is(HTTP_OK));

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(3);
        assertThat(spanItems.size(), is(3));
        assertThat(spanItems.get(0).getKind(), is(SERVER));
        assertThat(spanItems.get(0).getName(), is("mixed_inner"));
        assertThat(spanItems.get(0).getAttributes().get(AttributeKey.stringKey("attribute")), is("value"));

        assertThat(spanItems.get(1).getKind(), is(INTERNAL));
        assertThat(spanItems.get(1).getName(), is("mixed_parent"));

        assertThat(spanItems.get(2).getKind(), is(SERVER));
        assertThat(spanItems.get(2).getName(), is("mixed"));
    }


    @Path("/")
    public static class SpanResource {
        @GET
        @Path("/span")
        public Response span() {
            return Response.ok().build();
        }

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
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }
}