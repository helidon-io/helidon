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
package io.helidon.microprofile.lra;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.telemetry.InMemorySpanExporter;
import io.helidon.microprofile.telemetry.InMemorySpanExporterProvider;
import io.helidon.microprofile.telemetry.TelemetryCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfigBlock;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tracing.Tracer;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.tracing.TracingObserver;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;

@HelidonTest
@DisableDiscovery
@AddExtension(ConfigCdiExtension.class)
@AddExtension(TelemetryCdiExtension.class)
@AddBean(InMemorySpanExporter.class)
@AddBean(InMemorySpanExporterProvider.class)
@AddConfigBlock("""
        otel.service.name=helidon-mp-lra-test
        otel.sdk.disabled=false
        otel.traces.exporter=in-memory
        otel.bsp.schedule.delay=100
        """)
class NonJaxRsCallbackTracingTest {

    private static final String CONTEXT_PATH = "lra-tracing-test";
    private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final URI LRA_ID = URI.create("http://localhost/lra-coordinator/test-lra");
    private static final String CALLBACK_TYPE = "unknown";
    private static final String CLASS_NAME = "test.Participant";
    private static final String METHOD_NAME = "compensate";
    private static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");
    private static final AttributeKey<String> URL_QUERY = AttributeKey.stringKey("url.query");

    @Inject
    Tracer tracer;

    @Inject
    InMemorySpanExporter spanExporter;

    @Test
    void capabilityIsRedactedFromExportedServerSpan() throws Exception {
        spanExporter.reset();
        NonJaxRsCallbackAuthenticator callbackAuthenticator =
                new NonJaxRsCallbackAuthenticator(Optional.of(TEST_SECRET), false);
        ParticipantService participantService =
                new ParticipantService(null, null, CONTEXT_PATH, Optional.empty(), Set.of(), callbackAuthenticator);
        String capability = callbackAuthenticator.capability(LRA_ID, CALLBACK_TYPE, CLASS_NAME, METHOD_NAME);
        String callbackPath = "/" + CONTEXT_PATH + "/" + CALLBACK_TYPE + "/" + CLASS_NAME + "/" + METHOD_NAME;
        NonJaxRsResource resource =
                new NonJaxRsResource(participantService, callbackAuthenticator, CONTEXT_PATH, Config.empty());

        ObserveFeature observe = ObserveFeature.builder()
                .addObserver(TracingObserver.create(tracer))
                .build();
        WebServer server = WebServer.builder()
                .addFeature(observe)
                .addRouting(HttpRouting.builder()
                                    .register("/" + CONTEXT_PATH, resource.createNonJaxRsParticipantResource()))
                .port(0)
                .build()
                .start();
        try {
            URI callbackUri = URI.create("http://localhost:" + server.port() + callbackPath + "?"
                                                 + NonJaxRsCallbackAuthenticator.CAPABILITY_QUERY_PARAMETER + "="
                                                 + capability);
            HttpRequest request = HttpRequest.newBuilder(callbackUri)
                    .header(LRA_HTTP_CONTEXT_HEADER, LRA_ID.toASCIIString())
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode(), is(404));

            HttpRequest malformedHeaderRequest = HttpRequest.newBuilder(callbackUri)
                    .header(LRA_HTTP_CONTEXT_HEADER, LRA_ID.toASCIIString())
                    .header(LRA_HTTP_PARENT_CONTEXT_HEADER, "%")
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            client.send(malformedHeaderRequest, HttpResponse.BodyHandlers.discarding());

            List<SpanData> serverSpans = spanExporter.getFinishedSpanItems(2, span -> span.getKind() == SpanKind.SERVER
                    && callbackPath.equals(span.getAttributes().get(URL_PATH)));
            assertThat(serverSpans.stream().map(span -> span.getAttributes().get(URL_QUERY)).toList(),
                       everyItem(is(NonJaxRsCallbackAuthenticator.CAPABILITY_QUERY_PARAMETER + "=[REDACTED]")));
        } finally {
            server.stop();
        }
    }
}
