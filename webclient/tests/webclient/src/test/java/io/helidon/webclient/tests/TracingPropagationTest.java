/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.tracing.WebClientTracing;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.tracing.TracingObserver;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test tracing integration.
 */
@ServerTest
class TracingPropagationTest {
    // 2+1 after re-introduction of content-write span
    private static final int EXPECTED_NUMBER_OF_SPANS = 3;
    private static TestTracingSupport tracing;
    private final Http1Client client;
    private final URI uri;

    TracingPropagationTest(URI uri) {
        this.uri = uri.resolve("/greet");
        this.client = Http1Client.builder()
                .baseUri(this.uri)
                .config(Config.create().get("client"))
                .servicesDiscoverServices(false)
                .addService(WebClientTracing.create(tracing.tracer()))
                .mediaContext(MediaContext.builder()
                        .addMediaSupport(JsonpSupport.create())
                        .build())
                .build();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        tracing = new TestTracingSupport();
        server.addFeature(ObserveFeature.builder()
                .addObserver(TracingObserver.create(tracing.tracer()))
                .build());
    }

    @AfterAll
    static void tearDownTracing() {
        if (tracing != null) {
            tracing.close();
            tracing = null;
        }
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder http) {
        http.register("/greet", new GreetService());
    }

    @Test
    void testTracingSuccess() {
        try (Http1ClientResponse response = client.get()
                .queryParam("some", "value")
                .fragment("fragment")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(JsonObject.class), notNullValue());
        }

        List<SpanData> spanData = tracing.spanData(EXPECTED_NUMBER_OF_SPANS);

        // the server traces asynchronously, some spans may be written after we receive the response.
        /*
        There should bet:
        - webclient GET span
        - webserver HTTP Request
        - webserver content-write
         */
        assertThat("There should be 3 spans reported", spanData, hasSize(EXPECTED_NUMBER_OF_SPANS));

        SpanData clientSpan = findSpanWithName(spanData, "GET-" + uri);
        assertThat(clientSpan.getName(), is("GET-" + uri));
        Map<AttributeKey<?>, Object> tags = clientSpan.getAttributes().asMap();
        assertThat(tags.get(AttributeKey.longKey("http.status_code")), is(200L));

        SpanData wsSpan = findSpanWithName(spanData, "HTTP Request");
        assertThat(wsSpan.getName(), is("HTTP Request"));
        assertThat(wsSpan.getParentSpanId(), is(clientSpan.getSpanContext().getSpanId()));
        tags = wsSpan.getAttributes().asMap();
        assertThat(tags.get(AttributeKey.stringKey("http.method")), is("GET"));
        assertThat(tags.get(AttributeKey.stringKey("http.url")), is(uri.toString()));
        assertThat(tags.get(AttributeKey.longKey("http.status_code")), is(200L));
        assertThat(tags.get(AttributeKey.stringKey("component")), is("helidon-webserver"));
    }

    private SpanData findSpanWithName(List<SpanData> spanData, String spanName) {
        return spanData
                .stream()
                .filter(it -> it.getName().equals(spanName))
                .findFirst()
                .orElseGet(() -> fail("Could not find span " + spanName + " in " + spanData));
    }
}
