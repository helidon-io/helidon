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
import io.helidon.metrics.api.Meter;
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
    static void checkMetrics(MetricsFactory metricsFactory) {
        MeterRegistry meterRegistry = metricsFactory.globalRegistry();
        Tag okTag = okStatusTag(metricsFactory);

        for (Tag tag : grpcMethodTags(metricsFactory)) {
            Optional<Counter> counter = meter(meterRegistry,
                                              Counter.class,
                                              Meter.Type.COUNTER,
                                              CALL_STARTED,
                                              List.of(tag));
            assertThat(counter.isPresent(), is(true));
            assertThat(counter.get().count(), is(20L));

            Optional<Timer> timer = meter(meterRegistry,
                                          Timer.class,
                                          Meter.Type.TIMER,
                                          CALL_DURATION,
                                          List.of(tag, okTag));
            assertThat(timer.isPresent(), is(true));
            assertThat(timer.get().count(), is(20L));

            Optional<DistributionSummary> summary;
            summary = meter(meterRegistry,
                            DistributionSummary.class,
                            Meter.Type.DISTRIBUTION_SUMMARY,
                            SENT_MESSAGE_SIZE,
                            List.of(tag, okTag));
            assertThat(summary.isPresent(), is(true));
            assertThat(summary.get().count(), is(20L));
            assertThat(summary.get().max(), greaterThan(0.0));

            summary = meter(meterRegistry,
                            DistributionSummary.class,
                            Meter.Type.DISTRIBUTION_SUMMARY,
                            RCVD_MESSAGE_SIZE,
                            List.of(tag, okTag));
            assertThat(summary.isPresent(), is(true));
            assertThat(summary.get().count(), is(20L));
            assertThat(summary.get().max(), greaterThan(0.0));
        }
    }

    private static <M extends Meter> Optional<M> meter(MeterRegistry meterRegistry,
                                                       Class<M> meterClass,
                                                       Meter.Type meterType,
                                                       String name,
                                                       List<Tag> tags) {
        for (Meter meter : meterRegistry.meters(List.of(Meter.Scope.VENDOR))) {
            if (meterClass.isInstance(meter)
                    && meter.type() == meterType
                    && meter.id().name().equals(name)
                    && containsTags(meter, tags)) {
                return Optional.of(meterClass.cast(meter));
            }
        }
        return Optional.empty();
    }

    private static boolean containsTags(Meter meter, List<Tag> tags) {
        return tags.stream()
                .allMatch(tag -> tag.value().equals(meter.id().tagsMap().get(tag.key())));
    }
}
