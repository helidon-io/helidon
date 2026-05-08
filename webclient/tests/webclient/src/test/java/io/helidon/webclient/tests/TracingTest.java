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

import java.util.List;
import java.util.Map;

import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.tracing.Tracer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.tracing.WebClientTracing;
import io.helidon.webserver.WebServer;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.iterableWithSize;

/**
 * Test tracing integration.
 */
class TracingTest extends TestParent {

    TracingTest(WebServer server) {
        super(server);
    }

    @Test
    void testTracingNoServerSuccess() {
        try (TestTracingSupport tracing = new TestTracingSupport()) {
            String uri = "http://localhost:" + server.port() + "/greet";
            Http1Client client = client(uri, tracing.tracer());

            try (Http1ClientResponse response = client.get().request()) {
                // we must fully read entity for tracing to finish
                response.entity().as(JsonObject.class);
            }

            List<SpanData> spanData = tracing.spanData(1);
            assertThat(spanData, iterableWithSize(1));

            SpanData theSpan = spanData.getFirst();
            assertThat(theSpan.getName(), is("GET-" + uri));
            assertThat(theSpan.getEvents(), empty());

            Map<AttributeKey<?>, Object> attributes = theSpan.getAttributes().asMap();
            assertThat(attributes.get(AttributeKey.longKey("http.status_code")), is(200L));
            assertThat(attributes.get(AttributeKey.stringKey("http.method")), is("GET"));
            assertThat(attributes.get(AttributeKey.stringKey("http.url")), is(uri));
            assertThat(attributes.get(AttributeKey.stringKey("component")), is("helidon-webclient"));
        }
    }

    @Test
    void testTracingNoServerFailure() {
        try (TestTracingSupport tracing = new TestTracingSupport()) {
            String uri = "http://localhost:" + server.port() + "/greet";
            Http1Client client = client(uri, tracing.tracer());

            try (Http1ClientResponse response = client.get("/error").request()) {
                // we must fully read entity, as otherwise tracing does not finish
                response.entity().as(String.class);
            }

            List<SpanData> spanData = tracing.spanData(1);
            assertThat(spanData, iterableWithSize(1));

            SpanData theSpan = spanData.getFirst();
            assertThat(theSpan.getName(), is("GET-" + uri + "/error"));

            assertThat(theSpan.getEvents(), iterableWithSize(1));
            EventData event = theSpan.getEvents().getFirst();
            assertThat(event.getName(), is("error"));
            assertThat(event.getAttributes().get(AttributeKey.stringKey("message")),
                       is("Response HTTP status: 404 Not Found"));
            assertThat(event.getAttributes().get(AttributeKey.stringKey("error.kind")), is("ClientError"));

            assertThat(theSpan.getAttributes().get(AttributeKey.longKey("http.status_code")), is(404L));
        }
    }

    private Http1Client client(String uri, Tracer tracer) {
        return Http1Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .addService(WebClientTracing.create(tracer))
                .mediaContext(MediaContext.builder()
                        .addMediaSupport(JsonpSupport.create())
                        .build())
                .config(CONFIG.get("client"))
                .build();
    }
}
