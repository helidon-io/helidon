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

package io.helidon.declarative.tests.compatibility.app;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.declarative.tests.compatibility.v44.LegacyFeatureEndpoint;
import io.helidon.declarative.tests.compatibility.v44.LegacyRestClient;
import io.helidon.declarative.tests.compatibility.v44.LegacyScheduledTask;
import io.helidon.declarative.tests.compatibility.v44.LegacyWsClientEndpoint;
import io.helidon.declarative.tests.compatibility.v44.LegacyWsClientEndpointFactory;
import io.helidon.declarative.tests.compatibility.v44.LegacyWsEndpoint;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.validation.ValidationException;
import io.helidon.webclient.api.RestClient;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.websocket.WsClient;
import io.helidon.webserver.WebServer;
import io.helidon.websocket.WsCloseCodes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings({"helidon:api:incubating", "helidon:api:preview"})
class DeclarativeCompatibilityTest {
    private static final String HOST = "127.0.0.1";

    private static ServiceRegistryManager manager;
    private static ServiceRegistry registry;
    private static Http1Client client;
    private static WebClient webClient;
    private static TestSpanExporter spanExporter;
    private static int port;

    @BeforeAll
    static void startApplication() {
        System.setProperty("compatibility.server.port", "0");

        manager = ServiceRegistryManager.start(ApplicationBinding.create());
        registry = manager.registry();

        WebServer server = registry.get(WebServer.class);
        port = server.port();
        assertThat(port, greaterThan(0));

        String httpUri = "http://" + HOST + ":" + port;

        client = Http1Client.builder()
                .baseUri(httpUri)
                .build();
        webClient = WebClient.builder()
                .baseUri(httpUri)
                .build();
        spanExporter = registry.get(TestTracerFactory.class).exporter();

    }

    @AfterAll
    static void stopApplication() {
        try {
            if (manager != null) {
                manager.shutdown();
            }
        } finally {
            System.clearProperty("compatibility.server.port");
        }
    }

    @Test
    @Order(1)
    void testHttpServerAndRestClient() {
        var response = client.get("/legacy/hello/Ada")
                .queryParam("prefix", "Hola")
                .header(HeaderValues.create("X-Legacy", "direct"))
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Bonjour Hola Ada direct Hola"));
        assertThat(response.headers().value(HeaderNames.create("X-Legacy-Static")).orElseThrow(), is("legacy-static"));
        assertThat(response.headers().value(HeaderNames.create("X-Legacy-Computed")).orElseThrow(), is("legacy-computed"));

        var optional = client.get("/legacy/optional").request(String.class);
        assertThat(optional.status(), is(Status.OK_200));
        assertThat(optional.entity(), is("optional"));

        LegacyRestClient typedClient = registry.get(Lookup.builder()
                                                          .addContract(LegacyRestClient.class)
                                                          .addQualifier(Qualifier.create(RestClient.Client.class))
                                                          .build());

        assertThat(typedClient.hello("Bob", "typed"), is("Bonjour Hello Bob typed Hello"));
        assertThat(typedClient.entity("payload"), is("entity:payload"));
        assertThat(typedClient.clientHeader(), is("legacy-client"));

        assertThat(typedClient.fallback(), is("fallback"));
        assertThat(typedClient.retry(), is("retry:2"));
        HttpException exception = assertThrows(HttpException.class, typedClient::circuit);
        assertThat(exception.status(), is(Status.FORBIDDEN_403));
        assertThat(typedClient.timeout(Optional.empty()), is("timeout"));
        assertThat(typedClient.bulkhead(), is("bulkhead"));
    }

    @Test
    @Order(2)
    void testValidationAndScheduling() throws Exception {
        LegacyFeatureEndpoint endpoint = registry.get(LegacyFeatureEndpoint.class);
        assertThat(endpoint.validated("ok"), is("validated:ok"));
        assertThrows(ValidationException.class, () -> endpoint.validated(""));

        LegacyScheduledTask scheduledTask = registry.get(LegacyScheduledTask.class);
        assertThat(scheduledTask.cronLatch().await(10, TimeUnit.SECONDS), is(true));
        assertThat(scheduledTask.fixedRateLatch().await(10, TimeUnit.SECONDS), is(true));
        assertThat(scheduledTask.executions(), greaterThan(0));
    }

    @Test
    @Order(3)
    void testMetricsAndTracing() throws Exception {
        var counted = client.get("/legacy/metrics/counted").request(String.class);
        assertThat(counted.status(), is(Status.OK_200));
        assertThat(counted.entity(), is("counted"));

        var timed = client.get("/legacy/metrics/timed").request(String.class);
        assertThat(timed.status(), is(Status.OK_200));
        assertThat(timed.entity(), is("timed"));

        var gauge = client.post("/legacy/metrics/gauge").submit("42", String.class);
        assertThat(gauge.status(), is(Status.OK_200));
        assertThat(gauge.entity(), is("gauge:42"));

        var metricsResponse = client.get("/observe/metrics")
                .header(HeaderValues.ACCEPT_JSON)
                .request(JsonObject.class);
        assertThat(metricsResponse.status(), is(Status.OK_200));

        JsonObject appMetrics = metricsResponse.entity().objectValue("application").orElseThrow();
        assertMetric(appMetrics, "LegacyFeatureEndpoint.counted", 1);
        assertMetric(appMetrics, "LegacyFeatureEndpoint.gaugeValue", 42);
        assertThat(appMetrics.objectValue("legacy-timed").orElse(null), notNullValue());

        spanExporter.clear();
        var traced = client.get("/legacy/tracing/greet")
                .header(HeaderNames.USER_AGENT, "compatibility-test")
                .request(String.class);
        assertThat(traced.status(), is(Status.OK_200));
        assertThat(traced.entity(), is("traced:compatibility-test"));

        TestSpanExporter.RecordedSpan span = spanExporter.awaitSpan("legacy-4.4.1-traced");
        assertThat(span.kind(), is(io.helidon.tracing.Span.Kind.SERVER));
        assertThat(span.tags().get("module"), is("4.4.1"));
        assertThat(span.tags().get("userAgent"), is("compatibility-test"));
    }

    @Test
    @Order(4)
    void testCors() {
        var allowed = client.options("/legacy/cors")
                .header(HeaderNames.ORIGIN, "http://allowed.example")
                .header(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS, "X-Legacy")
                .request(Void.class);

        assertThat(allowed.status(), is(Status.OK_200));
        assertThat(allowed.headers().value(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN).orElseThrow(),
                   is("http://allowed.example"));
        assertThat(allowed.headers().value(HeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS).orElseThrow(), is("true"));
        assertThat(allowed.headers().value(HeaderNames.ACCESS_CONTROL_ALLOW_METHODS).orElseThrow(), containsString("POST"));
        assertThat(allowed.headers().value(HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS).orElseThrow(), containsString("X-Legacy"));
    }

    @Test
    @Order(5)
    void testWebSocketServerAndClient() throws Exception {
        WsClient wsClient = webClient.client(WsClient.PROTOCOL);
        LegacyWsClientEndpointFactory clientFactory = registry.get(LegacyWsClientEndpointFactory.class);
        LegacyWsClientEndpoint clientEndpoint = registry.get(LegacyWsClientEndpoint.class);
        LegacyWsEndpoint serverEndpoint = registry.get(LegacyWsEndpoint.class);

        clientEndpoint.reset();
        serverEndpoint.reset();

        clientFactory.connect(wsClient, "tester", 7);
        assertThat(clientEndpoint.latch().await(10, TimeUnit.SECONDS), is(true));

        assertThat(clientEndpoint.lastError(), is((Throwable) null));
        assertThat(clientEndpoint.lastUser(), is("tester"));
        assertThat(clientEndpoint.lastShard(), is(7));
        assertThat(clientEndpoint.lastText(), is("Hello 4.4.1"));
        assertThat(clientEndpoint.lastBytes().readString(clientEndpoint.lastBytes().available()), is("Bytes 4.4.1"));
        assertThat(clientEndpoint.lastClose(), is(new LegacyWsEndpoint.Close("normal", WsCloseCodes.NORMAL_CLOSE)));

        assertThat(serverEndpoint.lastError(), is((Throwable) null));
        assertThat(serverEndpoint.lastUser(), is("tester"));
        assertThat(serverEndpoint.lastShard(), is(7));
        assertThat(serverEndpoint.lastClose(), is(new LegacyWsEndpoint.Close("legacy-client-done", WsCloseCodes.NORMAL_CLOSE)));
        assertThat(serverEndpoint.lastHttpPrologue(), notNullValue());
    }

    private static void assertMetric(JsonObject metrics, String prefix, int expectedValue) {
        String key = metrics.keysAsStrings()
                .stream()
                .filter(it -> it.startsWith(prefix))
                .findFirst()
                .orElseThrow();
        BigDecimal value = metrics.numberValue(key).orElseThrow();
        assertThat(value.intValue(), is(expectedValue));
    }

}
