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

import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.BeforeAll;

abstract class GrpcBaseMetricsTest extends BaseStringServiceTest {

    static final Tag OK_TAG = Tag.create("grpc.status", "OK");
    static final Tag[] METHOD_TAGS = {
            Tag.create("grpc.method", "StringService/Upper"),
            Tag.create("grpc.method", "StringService/Lower"),
            Tag.create("grpc.method", "StringService/Echo"),
            Tag.create("grpc.method", "StringService/Split"),
            Tag.create("grpc.method", "StringService/Join")
    };
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
    static void initialize() {
        MeterRegistry meterRegistry = MetricsFactory.getInstance().globalRegistry();
        for (Tag tag : METHOD_TAGS) {
            meterRegistry.remove(CALL_STARTED, List.of(tag));
            meterRegistry.remove(CALL_DURATION, List.of(tag, OK_TAG));
            meterRegistry.remove(SENT_MESSAGE_SIZE, List.of(tag, OK_TAG));
            meterRegistry.remove(RCVD_MESSAGE_SIZE, List.of(tag, OK_TAG));
        }
    }
}
