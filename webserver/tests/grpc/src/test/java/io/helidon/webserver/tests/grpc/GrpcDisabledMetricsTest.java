/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class GrpcDisabledMetricsTest extends GrpcBaseMetricsTest {

    GrpcDisabledMetricsTest(WebServer server) {
        super(server);
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder serverBuilder) {
        serverBuilder.addProtocol(GrpcConfig.builder().build());        // default is false for gRPC metrics
    }

    @AfterAll
    static void checkMetrics() {
        MeterRegistry meterRegistry = MetricsFactory.getInstance().globalRegistry();

        for (Tag tag : METHOD_TAGS) {
            Optional<Counter> counter = meterRegistry.counter(CALL_STARTED, List.of(tag));
            assertThat(counter.isEmpty(), is(true));

            Optional<Timer> timer = meterRegistry.timer(CALL_DURATION, List.of(tag, OK_TAG));
            assertThat(timer.isEmpty(), is(true));

            Optional<DistributionSummary> summary = meterRegistry.summary(SENT_MESSAGE_SIZE, List.of(tag, OK_TAG));
            assertThat(summary.isEmpty(), is(true));

            summary = meterRegistry.summary(RCVD_MESSAGE_SIZE, List.of(tag, OK_TAG));
            assertThat(summary.isEmpty(), is(true));
        }
    }
}
