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

import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
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
    static void checkMetrics(MetricsFactory metricsFactory, MeterRegistry meterRegistry) {
        Tag okTag = okStatusTag(metricsFactory);
        Tag grpcTarget = metricsFactory.tagCreate("grpc.target", grpcClient.prototype().baseUri().orElseThrow().toString());

        for (Tag grpcMethod : grpcMethodTags(metricsFactory)) {
            Optional<Counter> counter = meterRegistry.counter(ATTEMPT_STARTED, List.of(grpcMethod, grpcTarget));
            assertThat(counter.isPresent(), is(true));
            assertThat(counter.get().count(), is(20L));

            Optional<Timer> timer = meterRegistry.timer(ATTEMPT_DURATION, List.of(grpcMethod, grpcTarget, okTag));
            assertThat(timer.isPresent(), is(true));
            assertThatWithRetry("gRPC attempt duration metric for " + grpcMethod, timer.get()::count, is(20L));

            Optional<DistributionSummary> sentSummary = meterRegistry.summary(SENT_MESSAGE_SIZE,
                                                                               List.of(grpcMethod, grpcTarget, okTag));
            assertThat(sentSummary.isPresent(), is(true));
            assertThatWithRetry("gRPC sent message size metric for " + grpcMethod,
                                sentSummary.get()::count,
                                is(20L));
            assertThat(sentSummary.get().max(), greaterThan(0.0));

            Optional<DistributionSummary> receivedSummary = meterRegistry.summary(RCVD_MESSAGE_SIZE,
                                                                                   List.of(grpcMethod, grpcTarget, okTag));
            assertThat(receivedSummary.isPresent(), is(true));
            assertThatWithRetry("gRPC received message size metric for " + grpcMethod,
                                receivedSummary.get()::count,
                                is(20L));
            assertThat(receivedSummary.get().max(), greaterThan(0.0));
        }
    }
}
