/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.grpc;

import java.util.List;
import java.util.Optional;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.grpc.GrpcConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.AfterAll;

import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

@ServerTest
class GrpcEnabledMetricsTest extends GrpcBaseMetricsTest {

    GrpcEnabledMetricsTest(WebServer server) {
        super(server);
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder serverBuilder) {
        serverBuilder.addProtocol(GrpcConfig.builder().enableMetrics(true).build());        // enable metrics
    }

    @AfterAll
    static void checkMetrics(MetricsFactory metricsFactory, MeterRegistry meterRegistry) {
        Tag okTag = okStatusTag(metricsFactory);

        for (Tag tag : grpcMethodTags(metricsFactory)) {
            Optional<Counter> counter = meterRegistry.counter(CALL_STARTED, List.of(tag));
            assertThat(counter.isPresent(), is(true));
            assertThat(counter.get().count(), is(20L));

            Optional<Timer> timer = meterRegistry.timer(CALL_DURATION, List.of(tag, okTag));
            assertThat(timer.isPresent(), is(true));
            assertThatWithRetry("gRPC call duration metric for " + tag, timer.get()::count, is(20L));

            Optional<DistributionSummary> sentSummary = meterRegistry.summary(SENT_MESSAGE_SIZE, List.of(tag, okTag));
            assertThat(sentSummary.isPresent(), is(true));
            assertThatWithRetry("gRPC sent message size metric for " + tag, sentSummary.get()::count, is(20L));
            assertThat(sentSummary.get().max(), greaterThan(0.0));

            Optional<DistributionSummary> receivedSummary = meterRegistry.summary(RCVD_MESSAGE_SIZE, List.of(tag, okTag));
            assertThat(receivedSummary.isPresent(), is(true));
            assertThatWithRetry("gRPC received message size metric for " + tag, receivedSummary.get()::count, is(20L));
            assertThat(receivedSummary.get().max(), greaterThan(0.0));
        }
    }
}
