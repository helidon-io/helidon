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
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.grpc.GrpcConfig;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.AfterAll;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class GrpcMetricsTest extends BaseStringServiceTest {

    private static final Tag OK_TAG = Tag.create("grpc.status", "OK");
    private static final Tag[] METHOD_TAGS = {
            Tag.create("grpc.method", "StringService/Upper"),
            Tag.create("grpc.method", "StringService/Lower"),
            Tag.create("grpc.method", "StringService/Echo"),
            Tag.create("grpc.method", "StringService/Split"),
            Tag.create("grpc.method", "StringService/Join")
    };
    private static final String CALL_STARTED = "grpc.server.call.started";
    private static final String CALL_DURATION = "grpc.server.call.duration";
    private static final String SENT_MESSAGE_SIZE = "grpc.server.call.sent_total_compressed_message_size";
    private static final String RCVD_MESSAGE_SIZE = "grpc.server.call.rcvd_total_compressed_message_size";

    GrpcMetricsTest(WebServer server) {
        super(server);
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder serverBuilder) {
        serverBuilder.addProtocol(GrpcConfig.builder().enableMetrics(true).build());
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new StringService()));
    }

    @AfterAll
    static void checkMetrics() {
        MeterRegistry meterRegistry = MetricsFactory.getInstance().globalRegistry();

        for (Tag tag : METHOD_TAGS) {
            Optional<Counter> counter = meterRegistry.counter(CALL_STARTED, List.of(tag));
            assertThat(counter.isPresent(), is(true));
            assertThat(counter.get().count(), is(20L));

            Optional<Timer> timer = meterRegistry.timer(CALL_DURATION, List.of(tag, OK_TAG));
            assertThat(timer.isPresent(), is(true));
            assertThat(timer.get().count(), is(20L));

            Optional<DistributionSummary> summary = meterRegistry.summary(SENT_MESSAGE_SIZE, List.of(tag, OK_TAG));
            assertThat(summary.isPresent(), is(true));
            assertThat(summary.get().count(), is(20L));
            assertThat(summary.get().max(), greaterThan(0.0));

            summary = meterRegistry.summary(RCVD_MESSAGE_SIZE, List.of(tag, OK_TAG));
            assertThat(summary.isPresent(), is(true));
            assertThat(summary.get().count(), is(20L));
            assertThat(summary.get().max(), greaterThan(0.0));
        }
    }
}
