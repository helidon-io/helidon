/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.tracing.Span;
import io.helidon.tracing.jersey.client.ClientTracingFilter;
import io.helidon.tracing.providers.opentracing.OpenTracing;
import io.helidon.tracing.providers.zipkin.ZipkinTracer;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.tracing.TracingObserver;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * What do we expect to get:
 * 1. Client creates a "client-call" new trace - top level span (parent is null) - in this test
 * 2. Client creates a "get" request span - parent is (1.), same trace ID - client filter
 * 3. Server creates a "http request" span - parent is (1.), same trace ID - server
 *      (I thought parent should be 2, but this is how brave tracer injects/extracts the headers)
 * 4. Server creates a "content-write" span - parent is (3.), same trace ID - server
 *
 */
@ServerTest
class OpenTraceableClientE2ETest {
    /**
     * We expect two client spans and two server spans.
     */
    private static final int EXPECTED_TRACE_EVENTS_COUNT = 4;
    private static final Logger LOGGER = System.getLogger(OpenTraceableClientE2ETest.class.getName());
    private static final Logger.Level LEVEL = Logger.Level.DEBUG;
    private static final List<zipkin2.Span> CLIENT_SPANS = Collections.synchronizedList(new ArrayList<>());
    private static final List<zipkin2.Span> SERVER_SPANS = Collections.synchronizedList(new ArrayList<>());

    private static Client client;
    private static CountDownLatch eventsLatch;
    private final WebServer server;

    OpenTraceableClientE2ETest(WebServer server) {
        this.server = server;
    }
    @SetUpServer
    static void setup(WebServerConfig.Builder serverBuilder) {
        serverBuilder
                .addFeature(ObserveFeature.builder()
                                    .addObserver(TracingObserver.create(tracer("test-server")))
                                    .build())
                .routing(routing -> routing
                .any((req, res) -> res.send("OK")));
        client = ClientBuilder.newClient(new ClientConfig(ClientTracingFilter.class));
    }

    @AfterAll
    static void stopAndClose() {
        if (client != null) {
            client.close();
        }
    }

    @BeforeEach
    void resetTraces() {
        CLIENT_SPANS.clear();
        SERVER_SPANS.clear();
        eventsLatch = new CountDownLatch(EXPECTED_TRACE_EVENTS_COUNT);
    }

    @Test
    void e2e() throws Exception {
        io.helidon.tracing.Tracer tracer = tracer("test-client");
        Span clientSpan = tracer.spanBuilder("client-call")
                .kind(Span.Kind.CLIENT)
                .start();
        Response response = client.target("http://localhost:" + server.port())
                .property(ClientTracingFilter.TRACER_PROPERTY_NAME, tracer)
                .property(ClientTracingFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME, clientSpan.context())
                .request()
                .get();

        assertThat(response.getStatus(), is(200));

        clientSpan.end();

        if (!eventsLatch.await(10, TimeUnit.SECONDS)) {
            fail("Timed out waiting to detect expected "
                    + EXPECTED_TRACE_EVENTS_COUNT
                    + "; remaining latch count: "
                    + eventsLatch.getCount()
                    + ", server spans: " + printSpans(SERVER_SPANS)
                    + ", client spans: " + printSpans(CLIENT_SPANS));
        }

        assertThat("Client spans reported. Client: " + printSpans(CLIENT_SPANS) + ", Server: " + printSpans(SERVER_SPANS),
                CLIENT_SPANS,
                hasSize(2));
        assertThat("Server spans reported. Client: " + printSpans(CLIENT_SPANS) + ", Server: " + printSpans(SERVER_SPANS),
                SERVER_SPANS,
                hasSize(2));

        TraceContext traceContext = ((BraveSpanContext) clientSpan.unwrap(io.opentracing.Span.class).context()).unwrap();

        /*
         Validate client spans
         "client-call" - our explicit span with no parent
         "get" - client GET request, has "client-call" as parent
         */
        var spansByName = spansByName(CLIENT_SPANS);
        var spansById = spansById(CLIENT_SPANS);

        // top level client span
        zipkin2.Span clientTopLevelSpan = spansById.get(traceContext.spanIdString());
        assertThat("Manual client span with id " + traceContext.spanIdString() + " was not found in "
                + printSpans(spansById), clientTopLevelSpan, notNullValue());
        assertAll("Manual client span",
                () -> assertThat("Should not have a parent", clientTopLevelSpan.parentId(), nullValue()),
                () -> assertThat("Correct name", clientTopLevelSpan.name(), is("client-call")),
                () -> assertThat("Trace ID is not null", clientTopLevelSpan.traceId(), notNullValue())
        );

        String manualClientId = clientTopLevelSpan.id();
        String traceId = clientTopLevelSpan.traceId();

        // JAR-RS client span
        var clientJaxRsSpan = spansByName.get("get");
        assertThat("JAX-RS GET client span was not found in "
                + printSpans(spansByName), clientJaxRsSpan, notNullValue());
        assertAll("JAX-RS GET client span",
                () -> assertThat("Parent should be manual span", clientJaxRsSpan.parentId(), is(manualClientId)),
                () -> assertThat("TraceID should be the same for all spans", clientJaxRsSpan.traceId(), is(traceId)),
                () -> assertThat("Correct name", clientJaxRsSpan.name(), is("get"))
        );

        /*
         Validate server spans
         "http request" - top level WebServer span
         "content-write" - WebServer span for writing entity
         */
        spansByName = spansByName(SERVER_SPANS);

        // WebServer span
        var serverRequestSpan = spansByName.get("http request");
        assertThat("Server \"http request\" span was not found in "
                + printSpans(spansByName), serverRequestSpan, notNullValue());
        assertAll("Server \"http request\" span",
                () -> assertThat("Parent should be manual span", serverRequestSpan.parentId(), is(manualClientId)),
                () -> assertThat("TraceID should be the same for all spans", serverRequestSpan.traceId(), is(traceId)),
                () -> assertThat("Correct name", serverRequestSpan.name(), is("http request"))
        );

        String serverRequestId = serverRequestSpan.id();

        // WebServer span
        var serverContentSpan = spansByName.get("content-write");
        assertThat("Server \"content-write\" span was not found in "
                + printSpans(spansByName), serverContentSpan, notNullValue());
        assertAll("Server \"content-write\" span",
                () -> assertThat("Parent should be server request span", serverContentSpan.parentId(), is(serverRequestId)),
                () -> assertThat("TraceID should be the same for all spans", serverContentSpan.traceId(), is(traceId)),
                () -> assertThat("Correct name", serverContentSpan.name(), is("content-write"))
        );
    }

    private static Map<String, zipkin2.Span> spansByName(List<zipkin2.Span> spans) {
        Map<String, zipkin2.Span> result = new HashMap<>();

        for (zipkin2.Span span : spans) {
            zipkin2.Span existing = result.putIfAbsent(span.name(), span);
            assertThat("There should not be two spans named the same", existing, nullValue());
        }

        return result;
    }

    private static Map<String, zipkin2.Span> spansById(List<zipkin2.Span> spans) {
        Map<String, zipkin2.Span> result = new HashMap<>();

        for (zipkin2.Span span : spans) {
            zipkin2.Span existing = result.putIfAbsent(span.id(), span);
            assertThat("There should not be two spans with the same id", existing, nullValue());
        }

        return result;
    }

    /**
     * Use custom {@link Tracer} that adds events to {@link #CLIENT_SPANS} or {@link #SERVER_SPANS}.
     */
    private static io.helidon.tracing.Tracer tracer(String serviceName) {
        Tracing braveTracing = Tracing.newBuilder()
                .localServiceName(serviceName)
                .spanReporter(span -> {
                    if (span.kind() == zipkin2.Span.Kind.CLIENT) {
                        CLIENT_SPANS.add(span);
                    } else {
                        SERVER_SPANS.add(span);
                    }

                    eventsLatch.countDown();
                    if (LOGGER.isLoggable(LEVEL)) {
                        LOGGER.log(LEVEL, String.format(
                                """
                                        Service %10s recorded span %14s/%s, %s kind, parent %s, trace %s; \
                                        client map size: %d; server map size: %d; remaining latch count: %d \
                                        """,
                                serviceName,
                                span.name(),
                                span.id(),
                                span.kind(),
                                span.parentId(),
                                span.traceId(),
                                CLIENT_SPANS.size(),
                                SERVER_SPANS.size(),
                                eventsLatch.getCount()));
                    }
                })
                .build();

        // use this to create an OpenTracing Tracer
        return OpenTracing.create(new ZipkinTracer(BraveTracer.create(braveTracing), List.of()));
    }

    private String printSpans(Map<String, zipkin2.Span> spans) {
        return printSpans(spans.values());
    }

    private String printSpans(Collection<zipkin2.Span> spans) {
        StringBuilder sb = new StringBuilder();
        for (zipkin2.Span span : spans) {
            sb.append("id: ").append(span.id()).append("\n");
            sb.append("parent id: ").append(span.parentId()).append("\n");
            sb.append("trace id: ").append(span.traceId()).append("\n");
            sb.append("name: ").append(span.name()).append("\n");
            sb.append("local service: ").append(span.localServiceName()).append("\n");
            sb.append("remote service: ").append(span.remoteServiceName()).append("\n");
            sb.append("=====\n");
        }
        return sb.toString();
    }

}
