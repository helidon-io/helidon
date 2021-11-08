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
 */
package io.helidon.metrics;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class TestServerWithKeyPerformanceIndicatorMetrics {

    private static WebServer webServer;

    private static final MetricsSupport.Builder KPI_ENABLED_BUILDER = MetricsSupport.builder()
            .keyPerformanceIndicatorsMetricsSettings(KeyPerformanceIndicatorMetricsSettings.builder()
                    .extended(true));

    private static MetricsSupport metricsSupport;

    private WebClient.Builder webClientBuilder;

    @BeforeAll
    public static void startup() throws InterruptedException, ExecutionException, TimeoutException {
        metricsSupport = KPI_ENABLED_BUILDER.build();
        webServer = TestServer.startServer(metricsSupport, new GreetService());
    }

    @BeforeEach
    public void prepareWebClientBuilder() {
        webClientBuilder = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/")
                .addMediaSupport(JsonpSupport.create());
    }

    @AfterAll
    public static void shutdown() {
        TestServer.shutdownServer(webServer);
    }

    @Test
    void checkInflightRequests() throws InterruptedException, ExecutionException {

        boolean isKPIEnabled = metricsSupport.keyPerformanceIndicatorMetricsConfig().isExtended();

        MetricRegistry vendorRegistry = io.helidon.metrics.api.RegistryFactory.getInstance()
                .getRegistry(MetricRegistry.Type.VENDOR);

        Optional<ConcurrentGauge> inflightRequests =
                vendorRegistry.getConcurrentGauges((metricID, metric) -> metricID.getName().endsWith(
                        KeyPerformanceIndicatorMetricsImpls.INFLIGHT_REQUESTS_NAME))
                        .values().stream()
                        .findAny();
        assertThat("In-flight concurrent gauge metric exists", inflightRequests.isPresent(), is(isKPIEnabled));

        long inflightBefore = inflightRequests.get().getCount();

        GreetService.initSlowRequest();
        Single<String> response = webClientBuilder
                .build()
                .get()
                .accept(MediaType.APPLICATION_JSON)
                .path("greet/slow")
                .request(String.class);
        GreetService.awaitSlowRequestStarted();
        long inflightDuring = inflightRequests.get().getCount();

        String result = response.get();

        assertThat("Returned result", result, is(GreetService.GREETING_RESPONSE));
        assertThat("Change in inflight requests during invocation", inflightDuring - inflightBefore, is(1L));
        assertThat("Net change in inflight requests after invocation", inflightRequests.get().getCount(), is(inflightBefore));

    }
}
