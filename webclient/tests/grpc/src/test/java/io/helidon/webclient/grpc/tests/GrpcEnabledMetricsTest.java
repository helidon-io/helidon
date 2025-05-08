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

package io.helidon.webclient.grpc.tests;

import java.util.List;
import java.util.Optional;

import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.AfterAll;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

@ServerTest
class GrpcEnabledMetricsTest extends GrpcBaseMetricsTest {

    GrpcEnabledMetricsTest(WebServer server) {
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        grpcClient = GrpcClient.builder()
                .tls(clientTls)
                .baseUri("https://localhost:" + server.port())
                .enableMetrics(true)
                .build();
    }

    @AfterAll
    static void checkMetrics() {
        MeterRegistry meterRegistry = MetricsFactory.getInstance().globalRegistry();
        Tag grpcTarget = Tag.create("grpc.target", grpcClient.prototype().baseUri().orElseThrow().toString());

        for (Tag grpcMethod : METHOD_TAGS) {
            Optional<Counter> counter = meterRegistry.counter(ATTEMPT_STARTED, List.of(grpcMethod, grpcTarget));
            assertThat(counter.isPresent(), is(true));
            assertThat(counter.get().count(), is(20L));

            Optional<Timer> timer = meterRegistry.timer(ATTEMPT_DURATION, List.of(grpcMethod, grpcTarget, OK_TAG));
            assertThat(timer.isPresent(), is(true));
            assertThat(timer.get().count(), is(20L));

            Optional<DistributionSummary> summary;
            summary = meterRegistry.summary(SENT_MESSAGE_SIZE, List.of(grpcMethod, grpcTarget, OK_TAG));
            assertThat(summary.isPresent(), is(true));
            assertThat(summary.get().count(), is(20L));
            assertThat(summary.get().max(), greaterThan(0.0));

            summary = meterRegistry.summary(RCVD_MESSAGE_SIZE, List.of(grpcMethod, grpcTarget, OK_TAG));
            assertThat(summary.isPresent(), is(true));
            assertThat(summary.get().count(), is(20L));
            assertThat(summary.get().max(), greaterThan(0.0));
        }
    }
}
