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
package io.helidon.examples.mp.httpstatuscount;

import io.helidon.common.http.Http;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(StatusResource.class)
public class StatusTest {

    @Inject
    private WebTarget webTarget;

    @Inject
    private MetricRegistry metricRegistry;

    private final Counter[] STATUS_COUNTERS = new Counter[6];

    @BeforeEach
    void findStatusMetrics() {
        for (int i = 1; i < STATUS_COUNTERS.length; i++) {
            STATUS_COUNTERS[i] = metricRegistry.counter(new MetricID(HttpStatusMetricFilter.STATUS_COUNTER_NAME,
                                                                     new Tag(HttpStatusMetricFilter.STATUS_TAG_NAME, i + "xx")));
        }
    }

    @Test
    void checkStatusMetrics() {
        checkAfterStatus(171);
        checkAfterStatus(200);
        checkAfterStatus(201);
        checkAfterStatus(204);
        checkAfterStatus(301);
        checkAfterStatus(401);
        checkAfterStatus(404);
    }

    @Test
    void checkStatusAfterGreet() {
        long[] before = new long[6];
        for (int i = 1; i < 6; i++) {
            before[i] = STATUS_COUNTERS[i].getCount();
        }
        Response response = webTarget.path("/greet")
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertThat("Status of /greet", response.getStatus(), is(Http.Status.OK_200.code()));
        checkCounters(response.getStatus(), before);
    }

    void checkAfterStatus(int status) {
        String path = "/status/" + status;
        long[] before = new long[6];
        for (int i = 1; i < 6; i++) {
            before[i] = STATUS_COUNTERS[i].getCount();
        }
        Response response = webTarget.path(path)
                .request(MediaType.TEXT_PLAIN_TYPE)
                .get();
        assertThat("Response status", response.getStatus(), is(status));
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
