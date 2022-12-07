/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.tracing.jaeger;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.Http;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientRequestHeaders;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;

import com.oracle.bedrock.runtime.LocalPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;

/**
 * Makes sure that variation in the case of incoming headers does not affect B3 header propagation.
 */
class B3HeadersPropagationCaseTest {

    private static final LocalPlatform localPlatform = LocalPlatform.get();

    private static final String PARENT_SPAN_ID = "51b3b1a413dce011";
    private static final String SPAN_ID = "521c61ede905945f";
    private static final String TRACE_ID = "0000816c055dc421";

    private WebServer webServer;
    private WebClient selfClient;

    @BeforeEach
    void init() {
        webServer = startServerWithTracing();
        selfClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .build();
    }

    @AfterEach
    void cleanup() {
        webServer.shutdown().await(5, TimeUnit.SECONDS);
    }

    @Test
    void testB3HeaderCase() throws ExecutionException, InterruptedException, TimeoutException {
        WebClientRequestBuilder requestBuilder = selfClient.get()
                .path("/hello");

        // Convince tracing that a span is already in progress using oddly-cased header names.
        addB3HeadersWithNamesInOddCases(requestBuilder.headers());

        WebClientResponse response = requestBuilder
                .submit()
                .await(15, TimeUnit.SECONDS);

        try {
            String traceIdSeenByEndpoint = response.content().as(String.class).get(5, TimeUnit.SECONDS);
            assertThat("Hello request status (with msg " + traceIdSeenByEndpoint + ")",
                       response.status().code(),
                       is(200));
            assertThat("Trace ID",
                       traceIdSeenByEndpoint,
                       endsWith(TRACE_ID)); // endsWith because of leading zeros
        } finally {
            response.close();
        }
    }

    private WebServer startServerWithTracing() {
        int serverPort = localPlatform.getAvailablePorts().next();

        TracerBuilder<?> tracerBuilder = TracerBuilder.create("case-test-service");

        tracerBuilder.serviceName("test-service")
                .collectorPort(serverPort);
        tracerBuilder.unwrap(JaegerTracerBuilder.class)
                .samplerParam(1)
                .samplerType(JaegerTracerBuilder.SamplerType.CONSTANT);

        Tracer tracer = tracerBuilder.build();

        WebServer server = WebServer.builder()
                .port(serverPort)
                .tracer(tracer)
                .addRouting(prepareRouting())
                .build();

        return server.start().await(15, TimeUnit.SECONDS);
    }

    private Routing.Builder prepareRouting() {
        return Routing.builder()
                .get("/hello", this::replyWithB3);
    }

    private void replyWithB3(ServerRequest req, ServerResponse res) {

        Optional<SpanContext> spanContext = req.context().get(SpanContext.class);
        if (spanContext.isEmpty()) {
            res.status(Http.Status.EXPECTATION_FAILED_417)
                    .send("No span found in endpoint");
            return;
        }

        res.send(spanContext.get().traceId());
    }

    private void addB3HeadersWithNamesInOddCases(WebClientRequestHeaders headers) {
        String mixedB3ParentSpanIdKey = "x-B3-ParentSpanId";
        String upperB3SpanIdKey = "X-B3-SPANID";
        String wildB3TraceIdKey = "X-b3-tRaCeiD";

        headers.add(mixedB3ParentSpanIdKey, PARENT_SPAN_ID);
        headers.add(upperB3SpanIdKey, SPAN_ID);
        headers.add(wildB3TraceIdKey, TRACE_ID);
    }
}