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

import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.BeforeAll;

abstract class GrpcBaseMetricsTest extends BaseStringServiceTest {

    static final String CALL_STARTED = "grpc.server.call.started";
    static final String CALL_DURATION = "grpc.server.call.duration";
    static final String SENT_MESSAGE_SIZE = "grpc.server.call.sent_total_compressed_message_size";
    static final String RCVD_MESSAGE_SIZE = "grpc.server.call.rcvd_total_compressed_message_size";

    GrpcBaseMetricsTest(WebServer server) {
        super(server);
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new StringService()));
    }

    @BeforeAll
    static void initialize(MetricsFactory metricsFactory) {
        MeterRegistry meterRegistry = metricsFactory.globalRegistry();
        Tag okTag = okStatusTag(metricsFactory);
        for (Tag tag : grpcMethodTags(metricsFactory)) {
            meterRegistry.remove(CALL_STARTED, List.of(tag));
            meterRegistry.remove(CALL_DURATION, List.of(tag, okTag));
            meterRegistry.remove(SENT_MESSAGE_SIZE, List.of(tag, okTag));
            meterRegistry.remove(RCVD_MESSAGE_SIZE, List.of(tag, okTag));
        }
    }

    static Tag okStatusTag(MetricsFactory metricsFactory) {
        return metricsFactory.tagCreate("grpc.status", "OK");
    }

    static Tag[] grpcMethodTags(MetricsFactory metricsFactory) {
        return new Tag[] {
                metricsFactory.tagCreate("grpc.method", "StringService/Upper"),
                metricsFactory.tagCreate("grpc.method", "StringService/Lower"),
                metricsFactory.tagCreate("grpc.method", "StringService/Echo"),
                metricsFactory.tagCreate("grpc.method", "StringService/Split"),
                metricsFactory.tagCreate("grpc.method", "StringService/Join")
        };
    }
}
