/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.tutorial;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link Main}.
 */
@ServerTest
public class MainTest {

    private final WebServer server;
    private final Http1Client client;

    public MainTest(WebServer server, Http1Client client) {
        this.server = server;
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        Main.setup(server);
    }

    @Test
    public void testShutDown() {
        try (Http1ClientResponse response = client.post("/mgmt/shutdown").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(server.isRunning(), is(false));
        }
    }
}
