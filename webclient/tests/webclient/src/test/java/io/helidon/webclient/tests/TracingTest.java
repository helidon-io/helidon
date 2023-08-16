/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.context.Context;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.tracing.WebClientTracing;
import io.helidon.webserver.WebServer;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.providers.opentracing.OpenTracing;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
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
        MockTracer mockTracer = new MockTracer();
        String uri = "http://localhost:" + server.port() + "/greet";
        Context context = Context.builder().id("tracing-unit-test").build();
        Tracer tracer = OpenTracing.create(mockTracer);
        context.register(tracer);

        Http1Client client = Http1Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .addService(WebClientTracing.create(tracer))
                .mediaContext(MediaContext.builder()
                        .addMediaSupport(JsonpSupport.create())
                        .build())
                .config(CONFIG.get("client"))
                .build();

        try (Http1ClientResponse response = client.get().request()) {

            // we must fully read entity for tracing to finish
            response.entity().as(JsonObject.class);
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        assertThat(mockSpans, iterableWithSize(1));

        MockSpan theSpan = mockSpans.get(0);

        assertThat(theSpan.operationName(), is("GET-" + uri));

        List<MockSpan.LogEntry> logEntries = theSpan.logEntries();
        assertThat(logEntries, empty());

        Map<String, Object> tags = theSpan.tags();
        assertThat(tags.get(Tags.HTTP_STATUS.getKey()), is(200));
        assertThat(tags.get(Tags.HTTP_METHOD.getKey()), is("GET"));
        assertThat(tags.get(Tags.HTTP_URL.getKey()), is(uri));
        assertThat(tags.get(Tags.COMPONENT.getKey()), is("helidon-webclient"));
    }

    @Test
    void testTracingNoServerFailure() {
        MockTracer mockTracer = new MockTracer();
        Context context = Context.builder().id("tracing-unit-test").build();
        Tracer tracer = OpenTracing.create(mockTracer);
        context.register(tracer);
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + server.port() + "/greet")
                .servicesDiscoverServices(false)
                .addService(WebClientTracing.create(tracer))
                .mediaContext(MediaContext.builder()
                        .addMediaSupport(JsonpSupport.create())
                        .build())
                .config(CONFIG.get("client"))
                .build();

        try (Http1ClientResponse response = client.get("/error").request()) {
            // we must fully read entity, as otherwise tracing does not finish
            String ignored = response.entity().as(String.class);
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        assertThat(mockSpans, iterableWithSize(1));

        MockSpan theSpan = mockSpans.get(0);

        assertThat(theSpan.operationName(), is("GET-http://localhost:" + server.port() + "/greet/error"));

        List<MockSpan.LogEntry> logEntries = theSpan.logEntries();
        assertThat(logEntries, iterableWithSize(1));
        MockSpan.LogEntry logEntry = logEntries.get(0);
        Map<String, ?> fields = logEntry.fields();
        assertThat(fields.get("event"), is("error"));
        assertThat(fields.get("message"), is("Response HTTP status: 404 Not Found"));
        assertThat(fields.get("error.kind"), is("ClientError"));

        Map<String, Object> tags = theSpan.tags();
        assertThat(tags.get(Tags.HTTP_STATUS.getKey()), is(404));
    }
}
