/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.Collections;
import java.util.Optional;

import io.helidon.common.concurrency.limits.AimdLimit;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.metrics.MetricsObserver;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

@ServerTest
class AimdLimitMetricsTest {

    private static final String[] METRIC_NAMES = {
            "aimd_queue_length",
            "aimd_rejected_requests",
            "aimd_rtt_seconds",
            "aimd_rtt_seconds_max",
            "aimd_queue_wait_time_seconds",
            "aimd_queue_wait_time_seconds_max",
            "aimd_concurrent_requests",
            "aimd_limit"
    };

    private final WebClient webClient;

    AimdLimitMetricsTest(WebClient webClient) {
        this.webClient = webClient;
    }

    @SetUpServer
    static void serverSetup(WebServerConfig.Builder builder) {
        ObserveFeature observe = ObserveFeature.builder()
                .addObserver(MetricsObserver.create())
                .build();
        builder.concurrencyLimit(AimdLimit.builder()
                                         .minLimit(1)
                                         .initialLimit(1)
                                         .maxLimit(1)
                                         .queueLength(1)
                                         .enableMetrics(true)
                                         .build())
                .addFeature(observe);
    }

    @SetUpRoute
    static void routeSetup(HttpRules rules) {
        rules.get("/greet", (req, res) -> {
            res.send("hello");
        });
    }

    @Test
    void testMetrics() {
        try (HttpClientResponse res = webClient.get("/greet").request()) {
            assertThat(res.status().code(), is(200));
        }

        try (HttpClientResponse res = webClient.get("/observe/metrics").request()) {
            String s = res.as(String.class);
            for (String metricName : METRIC_NAMES) {
                assertThat(s, containsString(metricName));
            }
            assertThat(res.status().code(), is(200));
        }

        MeterRegistry meterRegistry = MetricsFactory.getInstance().globalRegistry();
        Optional<Timer> rtt = meterRegistry.timer("aimd_rtt", Collections.emptyList());
        assertThat(rtt.isPresent(), is(true));
        assertThat(rtt.get().count(), is(greaterThan(0L)));
    }
}
