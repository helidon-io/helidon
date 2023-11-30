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

import java.net.URI;
import java.net.URL;
import java.util.List;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test Span Hierarchy with Tracer Mock
 */
@ExtendWith(ArquillianExtension.class)
public class TestFullUrlName {

    private Http1Client client;

    @Deployment
    public static WebArchive createDeployment() {

        ConfigAsset config = new ConfigAsset()
                .add("otel.service.name", "helidon-mp-telemetry")
                .add("otel.sdk.disabled", "false")
                .add("telemetry.span.full.url", "true")
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

    @After
    void cleanup() {
        spanExporter.reset();
    }

    @Test
    void spanNaming() {

        assertThat(client.get("/named").request().status(), is(Status.OK_200));

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        assertThat(spanItems.size(), is(1));
        assertThat(spanItems.get(0).getName(), is("http://localhost:" + url.getPort() + "/named"));
    }


    @Path("/")
    public static class SpanResource {

        @GET
        @Path("named")
        public Response mixedSpan() {
            return Response.ok().build();
        }

    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }
}