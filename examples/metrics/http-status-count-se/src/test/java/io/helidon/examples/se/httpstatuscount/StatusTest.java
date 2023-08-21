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

import java.util.Set;

import io.helidon.http.Http.Status;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Tag;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.examples.se.httpstatuscount.HttpStatusMetricService.STATUS_COUNTER_NAME;
import static io.helidon.examples.se.httpstatuscount.HttpStatusMetricService.STATUS_TAG_NAME;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
@Disabled
public class StatusTest {

    private final Counter[] STATUS_COUNTERS = new Counter[6];
    private final Http1Client client;

    public StatusTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder server) {
        server.routing(r -> {
            Main.routing(r, Config.create());
            r.register("/status", new StatusService());
        });
    }

    @BeforeEach
    void findStatusMetrics() {
        MeterRegistry meterRegistry = Metrics.globalRegistry();
        for (int i = 1; i < STATUS_COUNTERS.length; i++) {
            STATUS_COUNTERS[i] = meterRegistry.getOrCreate(Counter.builder(STATUS_COUNTER_NAME)
                                                                    .tags(Set.of(Tag.create(STATUS_TAG_NAME, i + "xx"))));
        }
    }

    @Test
    void checkStatusMetrics() throws InterruptedException {
        checkAfterStatus(Status.create(171));
        checkAfterStatus(Status.OK_200);
        checkAfterStatus(Status.CREATED_201);
        checkAfterStatus(Status.NO_CONTENT_204);
        checkAfterStatus(Status.MOVED_PERMANENTLY_301);
        checkAfterStatus(Status.UNAUTHORIZED_401);
        checkAfterStatus(Status.NOT_FOUND_404);
    }

    @Test
    void checkStatusAfterGreet() throws InterruptedException {
        long[] before = new long[6];
        for (int i = 1; i < 6; i++) {
            before[i] = STATUS_COUNTERS[i].count();
        }
        try (Http1ClientResponse response = client.get("/greet")
                .accept(MediaTypes.APPLICATION_JSON)
                .request()) {
            assertThat("Status of /greet", response.status(), is(Status.OK_200));
            String entity = response.as(String.class);
            assertThat(entity, not(isEmptyString()));
            checkCounters(response.status(), before);
        }
    }

    void checkAfterStatus(Status status) throws InterruptedException {
        long[] before = new long[6];
        for (int i = 1; i < 6; i++) {
            before[i] = STATUS_COUNTERS[i].count();
        }
        try (Http1ClientResponse response = client.get("/status/" + status.code())
                .accept(MediaTypes.APPLICATION_JSON)
                .request()) {
            assertThat("Response status", response.status(), is(status));
            checkCounters(status, before);
        }
    }

    @SuppressWarnings("BusyWait")
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
            assertThat("Diff in counter " + family + "xx", STATUS_COUNTERS[i].count() - before[i], is(expectedDiff));
        }
    }
}
