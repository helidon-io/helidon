/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.examples.mtls;

import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test of mutual TLS example.
 */
@ServerTest
public class MutualTlsExampleBuilderTest {

    private final WebServer server;

    public MutualTlsExampleBuilderTest(WebServer server) {
        this.server = server;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        ServerBuilderMain.setup(server);
    }

    @Test
    public void testBuilderAccessSuccessful() {
        Http1Client client = ClientBuilderMain.createClient();
        assertThat(ClientBuilderMain.callUnsecured(client, server.port()), is("Hello world unsecured!"));
        assertThat(ClientBuilderMain.callSecured(client, server.port("secured")), is("Hello Helidon-client!"));
    }
}