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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.Builder;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
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
            assertThat("Vendor metrics in returned entity", metrics.containsKey("requests.count"), is(true));
        }
    }

    @Test
    void checkInflightRequests() throws InterruptedException, ExecutionException {
        ConcurrentGauge inflightRequests = metricsSupport.inflightRequests();
        long inflightBefore = inflightRequests.getCount();

        GreetService.initSlowRequest();
        Single<String> response = webClientBuilder
                .build()
                .get()
                .accept(MediaType.APPLICATION_JSON)
                .path("greet/slow")
                .request(String.class);
        GreetService.awaitSlowRequestStarted();
        long inflightDuring = inflightRequests.getCount();

        String result = response.get();

        assertThat("Returned result", result, is(GreetService.GREETING_RESPONSE));
        assertThat("Change in inflight requests during invocation", inflightDuring - inflightBefore, is(1L));
        assertThat("Net change in inflight requests after invocation", inflightRequests.getCount(), is(inflightBefore));

    }
}
