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

import java.util.concurrent.TimeUnit;

import io.helidon.webserver.WebServer;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

class BaseServiceTest {

    private final int port;
    protected ManagedChannel channel;

    BaseServiceTest(WebServer server) {
        this.port = server.port();
    }

    @BeforeEach
    void beforeEach() {
        channel = channel(port);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        channel.shutdown();
        if (!channel.awaitTermination(10, TimeUnit.SECONDS)) {
            System.err.println("Failed to terminate channel");
        }
        if (!channel.isTerminated()) {
            System.err.println("Channel is not terminated!!!");
        }
    }

    ManagedChannel channel(int port) {
        return ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
    }
}
