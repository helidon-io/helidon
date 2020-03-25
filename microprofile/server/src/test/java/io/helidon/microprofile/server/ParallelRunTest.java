/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Running multiple servers in parallel.
 */
class ParallelRunTest {
    private Server server;

    @BeforeEach
    void startFirstServer() {
        server = Server.builder()
                .port(0)
                .build();

        server.start();
    }

    @AfterEach
    void stopFirstServer() {
        server.stop();
    }

    @Test
    void testParallelFails() {
        assertThrows(IllegalStateException.class, Server::builder);
    }
}
