/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.tracing.tests.it1;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.tracing.Span;
import io.helidon.tracing.jersey.client.ClientTracingFilter;
import io.helidon.tracing.opentracing.OpenTracing;
import io.helidon.tracing.zipkin.ZipkinTracer;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import brave.Tracing;
import brave.opentracing.BraveSpanContext;
import brave.opentracing.BraveTracer;
import brave.propagation.TraceContext;
import io.opentracing.Tracer;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The ZipkinClientTest.
 */
class OpentraceableClientE2ETest {

    private static final int EXPECTED_TRACE_EVENTS_COUNT = 4;
    private static final CountDownLatch EVENTS_LATCH = new CountDownLatch(EXPECTED_TRACE_EVENTS_COUNT);
    private static final Map<String, zipkin2.Span> EVENTS_MAP = new ConcurrentHashMap<>();
    private static WebServer server;
    private static Client client;

    @BeforeAll
    static void startServerInitClient() {
        server = startWebServer();
        client = ClientBuilder.newClient(new ClientConfig(ClientTracingFilter.class));
    }

    @AfterAll
    static void stopAndClose() throws Exception {
        if (server != null) {
            server.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }

        if (client != null) {
            client.close();
        }
    }

    @BeforeEach
    void resetTraces() {
        EVENTS_MAP.clear();
    }

    @Test
    void e2e() throws Exception {
        io.helidon.tracing.Tracer tracer = tracer("test-client");
        Span start = tracer.spanBuilder("client-call")
                .start();
        Response response = client.target("http://localhost:" + server.port())
                .property(ClientTracingFilter.TRACER_PROPERTY_NAME, tracer)
                .property(ClientTracingFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME, start.context())
                .request()
                .get();

        assertThat(response.getStatus(), is(200));

        start.end();

        if (!EVENTS_LATCH.await(10, TimeUnit.SECONDS)) {
            fail("Expected " + EXPECTED_TRACE_EVENTS_COUNT + " trace events but received only: " + EVENTS_MAP.size());
        }

        TraceContext traceContext = ((BraveSpanContext) start.unwrap(io.opentracing.Span.class).context()).unwrap();

        zipkin2.Span reportedSpan = EVENTS_MAP.remove(traceContext.traceIdString());
        assertThat("Span with id " + reportedSpan.traceId() + " was not found in "
                           + printSpans(EVENTS_MAP), reportedSpan, notNullValue());
        assertSpanChain(reportedSpan, EVENTS_MAP);
        assertThat(EVENTS_MAP.entrySet(), hasSize(0));
    }

    /**
     * Use custom {@link Tracer} that adds events to {@link #EVENTS_MAP} map.
     */
    private static io.helidon.tracing.Tracer tracer(String serviceName) {
        Tracing braveTracing = Tracing.newBuilder()
                .localServiceName(serviceName)
                .spanReporter(span -> {
                    EVENTS_MAP.put(span.traceId(), span);
                    EVENTS_LATCH.countDown();
                })
                .build();

        // use this to create an OpenTracing Tracer
        return OpenTracing.create(new ZipkinTracer(BraveTracer.create(braveTracing), List.of()));
    }

    private static WebServer startWebServer() {
        return WebServer.builder()
                .host("localhost")
                .routing(Routing.builder()
                                 .any((req, res) -> res.send("OK")))
                .tracer(tracer("test-server"))
                .build()
                .start()
                .await(Duration.ofSeconds(10));
    }

    private String printSpans(Map<String, zipkin2.Span> spans) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, zipkin2.Span> span : spans.entrySet()) {
            sb.append("id: ").append(span.getValue().id()).append("\n");
            sb.append("parent id: ").append(span.getValue().parentId()).append("\n");
            sb.append("trace id: ").append(span.getValue().traceId()).append("\n");
            sb.append("name: ").append(span.getValue().name()).append("\n");
            sb.append("local service: ").append(span.getValue().localServiceName()).append("\n");
            sb.append("remote service: ").append(span.getValue().remoteServiceName()).append("\n");
            sb.append("=====\n");
        }
        return sb.toString();
    }

    /**
     * Assert that all the spans are in a strict {@code parent-child-grandchild-[grandgrandchild]-[...]} relationship.
     */
    private void assertSpanChain(zipkin2.Span topSpan, Map<String, zipkin2.Span> spans) {
        if (spans.isEmpty()) {
            // end the recursion
            return;
        }
        Optional<zipkin2.Span> removeSpan = findAndRemoveSpan(topSpan.id(), spans);
        assertSpanChain(removeSpan.orElseThrow(
                                () -> new AssertionError("Span with parent ID not found: " + topSpan.id() + " at: " + printSpans(spans))),
                        spans);
    }

    private Optional<zipkin2.Span> findAndRemoveSpan(String id, Map<String, zipkin2.Span> spans) {
        Optional<zipkin2.Span> span = spans.entrySet()
                .stream()
                .filter(entry -> id.equals(entry.getValue().parentId()))
                .map(Map.Entry::getValue)
                .findFirst();

        span.ifPresent(span1 -> spans.remove(span1.id()));
        return span;
    }

}
