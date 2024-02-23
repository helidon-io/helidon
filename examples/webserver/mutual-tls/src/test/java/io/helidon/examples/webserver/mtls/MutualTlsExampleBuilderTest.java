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
package io.helidon.examples.webserver.mtls;

import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;

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
        server.port(0);
        server.putSocket("secured", it -> it
                .from(server.sockets().get("secured"))
                .port(0));
        ServerBuilderMain.setup(server);
    }

    @Test
    public void testBuilderAccessSuccessful() {
        Http1Client client = ClientBuilderMain.createClient();
        MatcherAssert.assertThat(ClientBuilderMain.callUnsecured(client, server.port()), is("Hello world unsecured!"));
        MatcherAssert.assertThat(ClientBuilderMain.callSecured(client, server.port("secured")), is("Hello Helidon-client!"));
    }
}
