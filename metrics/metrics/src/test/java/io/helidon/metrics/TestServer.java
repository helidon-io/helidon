/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.metrics;

import javax.json.JsonObject;
import javax.json.JsonValue;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.MediaType;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

public class TestServer {

    private static final Logger LOGGER = Logger.getLogger(TestServer.class.getName());

    private static WebServer webServer;

    private static final MetricsSupport.Builder NORMAL_BUILDER = MetricsSupport.builder();

    private static MetricsSupport metricsSupport;

    private WebClient.Builder webClientBuilder;

    @BeforeAll
    public static void startup() throws InterruptedException, ExecutionException, TimeoutException {
        metricsSupport = NORMAL_BUILDER.build();
        webServer = startServer(metricsSupport, new GreetService());
    }

    @BeforeEach
    public void prepareWebClientBuilder() {
        webClientBuilder = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/")
                .addMediaSupport(JsonpSupport.create());
    }

    @AfterAll
    public static void shutdown() {
        shutdownServer(webServer);
    }

    static WebServer startServer(
            Service... services) throws
            InterruptedException, ExecutionException, TimeoutException {
        WebServer result = WebServer.builder(
                Routing.builder()
                    .register(services)
                    .build())
                .port(0)
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        LOGGER.log(Level.INFO, "Started server at: https://localhost:{0}", result.port());
        return result;
    }

    static void shutdownServer(WebServer server) {
        server.shutdown();
    }

    @Test
    public void checkNormalURL() throws ExecutionException, InterruptedException {
        WebClientResponse response = webClientBuilder
                .build()
                .get()
                .accept(MediaType.APPLICATION_JSON)
                .path("metrics")
                .submit()
                .await();

        assertThat("Normal metrics URL HTTP response", response.status().code(), is(200));

        JsonObject metrics = response.content().as(JsonObject.class).await();
        assertThat("Vendor metrics in returned entity", metrics.containsKey("vendor"), is(true));
    }

    @Test
    public void checkVendorURL() {
        WebClientResponse response = webClientBuilder
                .build()
                .get()
                .accept(MediaType.APPLICATION_JSON)
                .path("metrics/vendor")
                .submit()
                .await();

        assertThat("Normal metrics/vendor URL HTTP response", response.status().code(), is(200));

        JsonObject metrics = response.content().as(JsonObject.class).await();
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
    void checkMetricsForExecutorService() {

        String jsonKeyForCompleteTaskCountInThreadPool =
                "executor-service.completed-task-count;poolIndex=0;supplierCategory=helidon-thread-pool-1;supplierIndex=0";

        WebClientRequestBuilder metricsRequestBuilder = webClientBuilder
                .build()
                .get()
                .accept(MediaType.APPLICATION_JSON)
                .path("metrics/vendor");

        WebClientResponse response = metricsRequestBuilder
                .submit()
                .await();

        assertThat("Normal metrics/vendor URL HTTP response", response.status().code(), is(200));

        JsonObject metrics = response.content().as(JsonObject.class).await();

        int completedTaskCount =
                metrics.getInt(jsonKeyForCompleteTaskCountInThreadPool);
        assertThat("Completed task count before accessing slow endpoint", completedTaskCount, is(0));

        WebClientResponse slowGreetResponse = webClientBuilder
                .build()
                .get()
                .accept(MediaType.TEXT_PLAIN)
                .path("greet/slow")
                .submit()
                .await();

        assertThat("Slow greet access response status", slowGreetResponse.status().code(), is(200));

        WebClientResponse secondMetricsResponse = metricsRequestBuilder
                .submit()
                .await();

        assertThat("Second access to metrics", secondMetricsResponse.status().code(), is(200));

        JsonObject secondMetrics = secondMetricsResponse.content().as(JsonObject.class).await();

        int secondCompletedTaskCount = secondMetrics.getInt(jsonKeyForCompleteTaskCountInThreadPool);

        assertThat("Completed task count after accessing slow endpoint", secondCompletedTaskCount, is(1));
    }
}
