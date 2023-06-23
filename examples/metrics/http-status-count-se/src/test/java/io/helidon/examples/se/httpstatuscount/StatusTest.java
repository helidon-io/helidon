/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http.Status;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.reactive.media.jsonp.JsonpSupport;
import io.helidon.reactive.webclient.WebClient;
import io.helidon.reactive.webclient.WebClientResponse;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.WebServer;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.junit.jupiter.api.Assertions.fail;

public class StatusTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static WebServer webServer;
    private static WebClient webClient;

    private final Counter[] STATUS_COUNTERS = new Counter[6];

    @BeforeAll
    static void init() {
        Routing.Builder routingBuilder = Main.createRouting(Config.create());
        routingBuilder.register("/status", new StatusService());

        webServer = Main.startServer(routingBuilder).await(TIMEOUT);

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIMEOUT);
        }
    }

    @BeforeEach
    void findStatusMetrics() {
        MetricRegistry metricRegistry = RegistryFactory.getInstance().getRegistry(Registry.APPLICATION_SCOPE);
        for (int i = 1; i < STATUS_COUNTERS.length; i++) {
            STATUS_COUNTERS[i] = metricRegistry.counter(new MetricID(HttpStatusMetricService.STATUS_COUNTER_NAME,
                                                                     new Tag(HttpStatusMetricService.STATUS_TAG_NAME, i + "xx")));
        }
    }

    @Test
    void checkStatusMetrics() throws ExecutionException, InterruptedException {
        checkAfterStatus(Status.create(171));
        checkAfterStatus(Status.OK_200);
        checkAfterStatus(Status.CREATED_201);
        checkAfterStatus(Status.NO_CONTENT_204);
        checkAfterStatus(Status.MOVED_PERMANENTLY_301);
        checkAfterStatus(Status.UNAUTHORIZED_401);
        checkAfterStatus(Status.NOT_FOUND_404);
    }

    @Test
    void checkStatusAfterGreet() throws ExecutionException, InterruptedException {
        long[] before = new long[6];
        for (int i = 1; i < 6; i++) {
            before[i] = STATUS_COUNTERS[i].getCount();
        }
        WebClientResponse response = webClient.get()
                .path("/greet")
                .accept(MediaTypes.APPLICATION_JSON)
                .request()
                .get();
        assertThat("Status of /greet", response.status(), is(Status.OK_200));
        String entity = response.content().as(String.class).await(TIMEOUT);
        assertThat(entity, not(isEmptyString()));
        checkCounters(response.status(), before);
    }

    void checkAfterStatus(Status status) throws ExecutionException, InterruptedException {
        long[] before = new long[6];
        for (int i = 1; i < 6; i++) {
            before[i] = STATUS_COUNTERS[i].getCount();
        }
        WebClientResponse response = webClient.get()
                .path("/status/" + status.code())
                .accept(MediaTypes.APPLICATION_JSON)
                .request()
                .get();
        assertThat("Response status", response.status(), is(status));
        response.content().as(String.class).await(TIMEOUT);
        checkCounters(status, before);
    }

    private void checkCounters(Status status, long[] before) throws InterruptedException {
        // first make sure we do not have a request in progress
        long now = System.currentTimeMillis();

        while (HttpStatusMetricService.isInProgress()) {
            Thread.sleep(50);
            if (System.currentTimeMillis() - now > 5000) {
                fail("Timed out while waiting for monitoring to finish");
            }
        }

        int family = status.code() / 100;
        for (int i = 1; i < 6; i++) {
            long expectedDiff = i == family ? 1 : 0;
            assertThat("Diff in counter " + family + "xx", STATUS_COUNTERS[i].getCount() - before[i], is(expectedDiff));
        }
    }
}
