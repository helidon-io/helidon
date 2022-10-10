/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.reactive.media.jsonp.JsonpSupport;
import io.helidon.reactive.webclient.WebClient;
import io.helidon.reactive.webclient.WebClientRequestBuilder;
import io.helidon.reactive.webclient.WebClientResponse;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.Service;
import io.helidon.reactive.webserver.WebServer;

import jakarta.json.JsonObject;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class TestServer {

    private static final Logger LOGGER = Logger.getLogger(TestServer.class.getName());
    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(Integer.getInteger("io.helidon.test.clientTimeoutSec", 10));
    private static final String[] EXPECTED_NO_CACHE_HEADER_SETTINGS = {"no-cache", "no-store", "must-revalidate", "no-transform"};

    private static WebServer webServer;

    private static final MetricsSupport.Builder NORMAL_BUILDER = MetricsSupport.builder();

    private static MetricsSupport metricsSupport;
    private static WebClient.Builder webClientBuilder;

    @BeforeAll
    public static void startup() {
        metricsSupport = NORMAL_BUILDER.build();
        webServer = startServer(metricsSupport, new GreetService());
        webClientBuilder = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/")
                .addMediaSupport(JsonpSupport.create());
    }

    @AfterAll
    public static void shutdown() {
        shutdownServer(webServer);
    }

    static WebServer startServer(Service... services) {
        WebServer server = WebServer.builder(
                Routing.builder()
                    .register(services)
                    .build())
                .port(0)
                .build()
                .start()
                .await(Duration.ofSeconds(20));
        LOGGER.log(Level.INFO, "Started server at: https://localhost:{0}", server.port());
        return server;
    }

    static void shutdownServer(WebServer server) {
        server.shutdown();
    }

    @Test
    public void checkNormalURL() {
        WebClientResponse response = webClientBuilder
                .build()
                .get()
                .accept(MediaTypes.APPLICATION_JSON)
                .path("metrics")
                .submit()
                .await(CLIENT_TIMEOUT);

        assertThat("Normal metrics URL HTTP response", response.status().code(), is(200));

        JsonObject metrics = response.content().as(JsonObject.class).await(CLIENT_TIMEOUT);
        assertThat("Vendor metrics in returned entity", metrics.containsKey("vendor"), is(true));
    }

    @Test
    public void checkVendorURL() {
        WebClientResponse response = webClientBuilder
                .build()
                .get()
                .accept(MediaTypes.APPLICATION_JSON)
                .path("metrics/vendor")
                .submit()
                .await(CLIENT_TIMEOUT);

        assertThat("Normal metrics/vendor URL HTTP response", response.status().code(), is(200));

        JsonObject metrics = response.content().as(JsonObject.class).await(CLIENT_TIMEOUT);
        if (System.getenv("MP_METRICS_TAGS") == null) {
            // MP_METRICS_TAGS causes metrics to add tags to metric IDs. Just do this check in the simple case, without tags.
            assertThat("Vendor metrics requests.count in returned entity", metrics.containsKey("requests.count"), is(true));
            assertThat("Vendor metrics requests.meter in returned entity", metrics.containsKey("requests.meter"), is(true));

            // Even accesses to the /metrics endpoint should affect the metrics. Make sure.
            int count = metrics.getInt("requests.count");
            assertThat("requests.count", count, is(greaterThan(0)));

            JsonObject meter = metrics.getJsonObject("requests.meter");
            int meterCount = meter.getInt("count");
            assertThat("requests.meter count", meterCount, is(greaterThan(0)));

            double meterRate = meter.getJsonNumber("meanRate").doubleValue();
            assertThat("requests.meter meanRate", meterRate, is(greaterThan(0.0)));
        }
    }

    @Test
    void checkKPIDisabledByDefault() {
        boolean isKPIEnabled = metricsSupport.keyPerformanceIndicatorMetricsConfig().isExtended();

        MetricRegistry vendorRegistry = io.helidon.metrics.api.RegistryFactory.getInstance()
                .getRegistry(MetricRegistry.Type.VENDOR);

        Optional<ConcurrentGauge> inflightRequests =
                vendorRegistry.getConcurrentGauges((metricID, metric) -> metricID.getName().endsWith(
                        KeyPerformanceIndicatorMetricsImpls.INFLIGHT_REQUESTS_NAME))
                        .values().stream()
                        .findAny();
        assertThat("In-flight concurrent gauge metric exists", inflightRequests.isPresent(), is(isKPIEnabled));
    }

    @Test
    void checkMetricsForExecutorService() throws InterruptedException {

        // Because ThreadPoolExecutor methods are documented as reporting approximations of task counts, etc., we should
        // not depend on the values changing in a reasonable time period...or at all. So this test simply makes sure that
        // an expected metric is present.
        String jsonKeyForCompleteTaskCountInThreadPool =
                "executor-service.completed-task-count;poolIndex=0;supplierCategory=my-thread-thread-pool-1;supplierIndex=0";

        WebClientRequestBuilder metricsRequestBuilder = webClientBuilder
                .build()
                .get()
                .accept(MediaTypes.APPLICATION_JSON)
                .path("metrics/vendor");

        WebClientResponse response = metricsRequestBuilder
                .submit()
                .await(CLIENT_TIMEOUT);

        assertThat("Normal metrics/vendor URL HTTP response", response.status().code(), is(200));

        JsonObject metrics = response.content().as(JsonObject.class).await(CLIENT_TIMEOUT);

        assertThat("JSON metrics results before accessing slow endpoint",
                   metrics,
                   hasKey(jsonKeyForCompleteTaskCountInThreadPool));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/base", "/vendor", "/application"})
    void testCacheSuppression(String pathSuffix) {
        String requestPath = "/metrics" + pathSuffix;

        WebClientResponse response = webClientBuilder
                .build()
                .get()
                .accept(MediaTypes.APPLICATION_JSON)
                .path(requestPath)
                .submit()
                .await(CLIENT_TIMEOUT);

        assertThat("Headers suppressing caching",
                   response.headers().values(Http.Header.CACHE_CONTROL),
                   containsInAnyOrder(EXPECTED_NO_CACHE_HEADER_SETTINGS));

        response = webClientBuilder
                .build()
                .options()
                .accept(MediaTypes.APPLICATION_JSON)
                .path(requestPath)
                .submit()
                .await(CLIENT_TIMEOUT);

        assertThat ("Headers suppressing caching in OPTIONS request",
                    response.headers().values(Http.Header.CACHE_CONTROL),
                    not(containsInAnyOrder(EXPECTED_NO_CACHE_HEADER_SETTINGS)));
    }
}
