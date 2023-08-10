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

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.helidon.common.context.Context;
import io.helidon.http.Http;
import io.helidon.config.Config;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.tracing.WebClientTracing;
import io.helidon.webserver.WebServerConfig;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.providers.opentracing.OpenTracing;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test tracing integration.
 */
@ServerTest
class TracingPropagationTest {
    private static MockTracer tracer;
    private final Http1Client client;
    private final URI uri;

    TracingPropagationTest(URI uri) {
        Tracer tracer = OpenTracing.create(this.tracer);
        this.uri = uri.resolve("/greet");
        this.client = Http1Client.builder()
                .baseUri(this.uri)
                .config(Config.create().get("client"))
                .servicesDiscoverServices(false)
                .addService(WebClientTracing.create(tracer))
                .mediaContext(MediaContext.builder()
                        .addMediaSupport(JsonpSupport.create())
                        .build())
                .build();
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder server) {
        tracer = new MockTracer();
        Config config = Config.create();
        server.config(config);
        server.routing(routing -> Main.routing(routing, config, tracer));
    }

    @Test
    void testTracingSuccess() {
        Context context = Context.builder().id("tracing-unit-test").build();
        context.register(tracer);

        try (Http1ClientResponse response = client.get()
                .queryParam("some", "value")
                .fragment("fragment")
                .request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.entity().as(JsonObject.class), notNullValue());
        }

        List<MockSpan> mockSpans = tracer.finishedSpans();

        // the server traces asynchronously, some spans may be written after we receive the response.
        // we need to try to wait for such spans
        assertThat("There should be 2 spans reported", tracer.finishedSpans(), hasSize(2));

        // we need the first span - parentId 0
        MockSpan clientSpan = findSpanWithParentId(mockSpans, 0);
        assertThat(clientSpan.operationName(), is("GET-" + uri));
        List<MockSpan.LogEntry> logEntries = clientSpan.logEntries();
        assertThat(logEntries, empty());
        Map<String, Object> tags = clientSpan.tags();
        assertThat(tags.get(Tags.HTTP_STATUS.getKey()), is(200));

        // now we want to test first child - first WebServer span
        MockSpan wsSpan = findSpanWithParentId(mockSpans, clientSpan.context().spanId());
        assertThat(wsSpan.operationName(), is("HTTP Request"));
        tags = wsSpan.tags();
        assertThat(tags.get(Tags.HTTP_METHOD.getKey()), is("GET"));
        assertThat(tags.get(Tags.HTTP_URL.getKey()), is(uri.toString()));
        assertThat(tags.get(Tags.HTTP_STATUS.getKey()), is(200));
        assertThat(tags.get(Tags.COMPONENT.getKey()), is("helidon-webserver"));
    }

    private MockSpan findSpanWithParentId(List<MockSpan> mockSpans, long parentId) {
        return mockSpans
                .stream()
                .filter(it -> it.parentId() == parentId)
                .findFirst()
                .orElseGet(() -> fail("Could not find span with parent id " + parentId + " in " + mockSpans));
    }
}
