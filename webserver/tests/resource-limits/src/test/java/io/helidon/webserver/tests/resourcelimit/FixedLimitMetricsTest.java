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

import io.helidon.common.concurrency.limits.FixedLimit;
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
class FixedLimitMetricsTest {

    private static final String[] METRIC_NAMES = {
            "fixed_queue_length",
            "fixed_rejected_requests",
            "fixed_rtt_seconds",
            "fixed_rtt_seconds_max",
            "fixed_queue_wait_time_seconds",
            "fixed_queue_wait_time_seconds_max",
            "fixed_concurrent_requests"
    };

    private final WebClient webClient;

    FixedLimitMetricsTest(WebClient webClient) {
        this.webClient = webClient;
    }

    @SetUpServer
    static void serverSetup(WebServerConfig.Builder builder) {
        ObserveFeature observe = ObserveFeature.builder()
                .addObserver(MetricsObserver.create())
                .build();
        builder.concurrencyLimit(FixedLimit.builder()
                                         .permits(1)
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
        Optional<Timer> rtt = meterRegistry.timer("fixed_rtt", Collections.emptyList());
        assertThat(rtt.isPresent(), is(true));
        assertThat(rtt.get().count(), is(greaterThan(0L)));
    }
}
