/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.examples.webserver.basic;

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Unit test for {@link io.helidon.security.examples.webserver.basic.BasicExampleConfigMain}.
 */
public class BasicExampleConfigTest extends BasicExampleTest {

    private static WebServer server;

    @BeforeAll
    public static void startServer() {
        // start the test
        server = BasicExampleConfigMain.startServer();
    }

    @AfterAll
    public static void stopServer() {
        stopServer(server);
    }

    @Override
    String getServerBase() {
        return "http://localhost:" + server.port();
    }
}
