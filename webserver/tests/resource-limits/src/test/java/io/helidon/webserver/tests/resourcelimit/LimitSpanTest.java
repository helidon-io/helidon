/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.resourcelimit;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.tracing.Tracer;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.tracing.TracingObserver;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@ServerTest
class LimitSpanTest {

    private static final String NO_PARENT_SPAN_ID = "no-parent-span-id";

    private static volatile CountDownLatch firstRequestReceivedByServer;

    private final SocketHttpClient client;
    private final WebClient webClient;

    LimitSpanTest(SocketHttpClient client, WebClient webClient) {
        this.client = client;
        this.webClient = webClient;
    }

    @BeforeEach
    void beforeEach() {
        firstRequestReceivedByServer = new CountDownLatch(1);
        TestSpanExporterProvider.exporter().clear();
    }

    @AfterEach
    void afterEach() {
        TestSpanExporterProvider.exporter().close();
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        ObserveFeature observe = ObserveFeature.builder()
                .addObserver(TracingObserver.create(Tracer.global()))
                .build();
        builder.concurrencyLimit(FixedLimit.builder()
                                         .permits(1)
                                         .queueLength(5)
                                         .queueTimeout(Duration.ofSeconds(10))
                                         .enableTracing(true)
                                         .build())
                .addFeature(observe);
    }

    @SetUpRoute
    static void routeSetup(HttpRules rules) {
        rules.get("/span", (req, res) -> {
            firstRequestReceivedByServer.countDown();
            Thread.sleep(1000); // Give the second request time to be sent--and queued.
            res.send("done");
        });
    }

    @Test
    void testParentSpan() throws Exception {
        String parentTraceId = "00000000000000000000000000000001";
        String parentSpanId = "0000000000000002";
        String traceParentHeaderValue = "00-" + parentTraceId + "-" + parentSpanId + "-01";

        CompletableFuture<Void> firstResponse = CompletableFuture.supplyAsync(() -> {
            client.request(Method.GET, "/span", null, List.of("Connection:keep-alive"));
            return null;
        });
        firstRequestReceivedByServer.await();

        try (HttpClientResponse secondResponse = webClient.get("/span")
                .header(HeaderNames.create("traceparent"), traceParentHeaderValue)
                .request()) {
            // Second request should be queued and, therefore, trigger a limit span.
            assertThat("First request response status", client.receive(), containsString("200 OK"));
            assertThat("Second HTTP response status", secondResponse.status().code(), is(200));

            var spanData = TestSpanExporterProvider.exporter().spanData(5);
            var queueingTimeSpanData = spanData.stream()
                    .filter(sd -> sd.getName().equals("@default-fixed-limit-span"))
                    .findFirst()
                    .orElseThrow();
            // A child span's trace ID is always the same as its parent's.
            assertThat("Queueing time span trace", queueingTimeSpanData.getTraceId(), is(parentTraceId));
            assertThat("Queueing time span parent span", queueingTimeSpanData.getParentSpanId(), is(parentSpanId));
        } finally {
            client.close();
        }
    }

}
