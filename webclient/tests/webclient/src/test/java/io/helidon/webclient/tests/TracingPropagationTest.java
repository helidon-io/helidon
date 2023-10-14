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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.providers.opentracing.OpenTracing;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test tracing integration.
 */
@ServerTest
class TracingPropagationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final AtomicReference<CountDownLatch> SPAN_COUNT_LATCH = new AtomicReference<>();
    // 2+1 after re-introduction of content-write span
    private static final int EXPECTED_NUMBER_OF_SPANS = 3;
    private static MockTracer tracer;
    private final Http1Client client;
    private final URI uri;

    TracingPropagationTest(URI uri) {
        Tracer tracer = OpenTracing.create(TracingPropagationTest.tracer);
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
    static void server(WebServerConfig.Builder server) {
        tracer = new MockTracer() {
            @Override
            protected void onSpanFinished(MockSpan mockSpan) {
                SPAN_COUNT_LATCH.get().countDown();
            }
        };
        server.addFeature(ObserveFeature.builder()
                                  .addObserver(TracingObserver.create(OpenTracing.create(tracer)))
                                  .build());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder http) {
        http.register("/greet", new GreetService());
    }

    @Test
    void testTracingSuccess() throws InterruptedException {
        SPAN_COUNT_LATCH.set(new CountDownLatch(EXPECTED_NUMBER_OF_SPANS));
        Context context = Context.builder().id("tracing-unit-test").build();
        context.register(tracer);

        try (Http1ClientResponse response = client.get()
                .queryParam("some", "value")
                .fragment("fragment")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(JsonObject.class), notNullValue());
        }

        assertTrue(SPAN_COUNT_LATCH.get().await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                   "Expected number of spans wasn't reported in time!");

        List<MockSpan> mockSpans = tracer.finishedSpans();

        // the server traces asynchronously, some spans may be written after we receive the response.
        /*
        There should bet:
        - webclient GET span
        - webserver HTTP Request
        - webserver content-write
         */
        assertThat("There should be 3 spans reported", tracer.finishedSpans(), hasSize(EXPECTED_NUMBER_OF_SPANS));

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
