/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Optional;

import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.metrics.MetricsObserver;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.Socket;

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
    private final WebClient adminClient;
    private final MetricsFactory metricsFactory;
    private final MeterRegistry meterRegistry;
    private final Tag adminSocketTag;

    FixedLimitMetricsTest(WebClient webClient,
                          @Socket("admin") WebClient adminClient,
                          MetricsFactory metricsFactory,
                          MeterRegistry meterRegistry) {
        this.webClient = webClient;
        this.adminClient = adminClient;
        this.metricsFactory = metricsFactory;
        this.meterRegistry = meterRegistry;
        this.adminSocketTag = metricsFactory.tagCreate("socketName", "admin");
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

    @SetUpRoute("admin")
    static void adminRouteSetup(ListenerConfig.Builder listener, HttpRules rules) {
        listener.concurrencyLimit(FixedLimit.builder()
                                          .permits(1)
                                          .enableMetrics(true)
                                          .build());
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

        Optional<Timer> rtt = timer(meterRegistry, Collections.emptyList());
        assertThat(rtt.isPresent(), is(true));
        assertThat(rtt.get().count(), is(greaterThan(0L)));
    }

    @Test
    void testNamedSocketMetricsTag() {
        try (HttpClientResponse res = adminClient.get("/greet").request()) {
            assertThat(res.status().code(), is(200));
        }

        Optional<Timer> rtt = timer(meterRegistry, List.of(adminSocketTag));
        assertThat(rtt.isPresent(), is(true));

        try (HttpClientResponse res = webClient.get("/observe/metrics").request()) {
            String s = res.as(String.class);
            assertThat(s, containsString("fixed_rtt_seconds_count"));
            assertThat(res.status().code(), is(200));
        }

        Optional<Timer> defaultTaggedRtt = timer(meterRegistry, Collections.emptyList());
        assertThat(defaultTaggedRtt.isPresent(), is(true));
    }

    private static Optional<Timer> timer(MeterRegistry meterRegistry, List<Tag> tags) {
        for (Meter meter : meterRegistry.meters(List.of(Meter.Scope.VENDOR))) {
            if (meter instanceof Timer timer
                    && meter.id().name().equals("fixed_rtt")
                    && containsTags(meter, tags)) {
                return Optional.of(timer);
            }
        }
        return Optional.empty();
    }

    private static boolean containsTags(Meter meter, List<Tag> tags) {
        return tags.stream()
                .allMatch(tag -> tag.value().equals(meter.id().tagsMap().get(tag.key())));
    }
}
