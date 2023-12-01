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
import io.helidon.microprofile.telemetry.InMemorySpanExporter;
import io.helidon.microprofile.telemetry.InMemorySpanExporterProvider;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test Span Hierarchy with Tracer Mock
 */
@HelidonTest
@AddBean(TestFullUrlName.SpanResource.class)
@AddBean(InMemorySpanExporter.class)
@AddBean(InMemorySpanExporterProvider.class)
@AddExtension(TelemetryCdiExtension.class)
@AddConfig(key = "otel.service.name", value = "helidon-mp-telemetry")
@AddConfig(key = "otel.sdk.disabled", value = "false")
@AddConfig(key = "telemetry.span.full.url", value = "true")
@AddConfig(key = "otel.traces.exporter", value = "in-memory")
public class TestFullUrlName {


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

    @Test
    void spanNaming() {

        assertThat(webTarget.path("named").request().get().getStatus(), is(Response.Status.OK.getStatusCode()));

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(2);
        assertThat(spanItems.size(), is(2));
        assertThat(spanItems.get(0).getName(), is("http://localhost:" + webTarget.getUri().getPort() + "/named"));
    }


    @Path("/")
    @ApplicationScoped
    public static class SpanResource {

        @GET
        @Path("named")
        public Response mixedSpan() {
            return Response.ok().build();
        }

    }
}