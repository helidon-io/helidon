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
package io.helidon.examples.se.httpstatuscount;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StatusTest {

    private static WebServer webServer;
    private static WebClient webClient;

    private final Counter[] STATUS_COUNTERS = new Counter[6];

    @BeforeAll
    static void init() {
        Routing.Builder routingBuilder = Main.createRouting(Config.create());
        routingBuilder.register("/status", new StatusService());

        webServer = Main.startServer(routingBuilder).await();

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @BeforeEach
    void findStatusMetrics() {
        MetricRegistry metricRegistry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);
        for (int i = 1; i < STATUS_COUNTERS.length; i++) {
            STATUS_COUNTERS[i] = metricRegistry.counter(new MetricID(HttpStatusMetricService.STATUS_COUNTER_NAME,
                                                                     new Tag(HttpStatusMetricService.STATUS_TAG_NAME, i + "xx")));
        }
    }

    @Test
    void checkStatusMetrics() throws ExecutionException, InterruptedException {
        checkAfterStatus(171);
        checkAfterStatus(200);
        checkAfterStatus(201);
        checkAfterStatus(204);
        checkAfterStatus(301);
        checkAfterStatus(401);
        checkAfterStatus(404);
    }

    @Test
    void checkStatusAfterGreet() throws ExecutionException, InterruptedException {
        long[] before = new long[6];
        for (int i = 1; i < 6; i++) {
            before[i] = STATUS_COUNTERS[i].getCount();
        }
        WebClientResponse response = webClient.get()
                .path("/greet")
                .accept(MediaType.APPLICATION_JSON)
                .request()
                .get();
        assertThat("Status of /greet", response.status().code(), is(Http.Status.OK_200.code()));
        checkCounters(response.status().code(), before);
    }

    void checkAfterStatus(int status) throws ExecutionException, InterruptedException {
        long[] before = new long[6];
        for (int i = 1; i < 6; i++) {
            before[i] = STATUS_COUNTERS[i].getCount();
        }
        WebClientResponse response = webClient.get()
                .path("/status/" + status)
                .accept(MediaType.APPLICATION_JSON)
                .request()
                .get();
        assertThat("Response status", response.status().code(), is(status));
        checkCounters(status, before);
    }

    private void checkCounters(int status, long[] before) {
        int family = status / 100;
        for (int i = 1; i < 6; i++) {
            long expectedDiff = i == family ? 1 : 0;
            assertThat("Diff in counter " + family + "xx", STATUS_COUNTERS[i].getCount() - before[i], is(expectedDiff));
        }
    }
}
