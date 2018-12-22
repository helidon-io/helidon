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

package io.helidon.security.examples.webserver.digest;

import java.io.IOException;

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Unit test for {@link DigestExampleBuilderMain}.
 */
public class DigestExampleBuilderTest extends DigestExampleTest {

    private static WebServer server;

    @BeforeAll
    public static void startServer() throws IOException {
        // start the test
        DigestExampleBuilderMain.main(new String[0]);
        server = DigestExampleBuilderMain.getServer();
    }

    @AfterAll
    public static void stopServer() throws InterruptedException {
        stopServer(server);
    }

    @Override
    String getServerBase() {
        return "http://localhost:" + server.port();
    }
}
