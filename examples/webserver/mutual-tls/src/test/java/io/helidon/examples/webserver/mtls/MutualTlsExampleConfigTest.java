/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.OverrideSources;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;

import org.junit.jupiter.api.Test;

import static io.helidon.examples.webserver.mtls.ClientConfigMain.callSecured;
import static io.helidon.examples.webserver.mtls.ClientConfigMain.callUnsecured;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test of mutual TLS example.
 */
@ServerTest
public class MutualTlsExampleConfigTest {

    private static Config config;
    private final WebServer server;
    private final Http1Client client;

    public MutualTlsExampleConfigTest(WebServer server) {
        this.server = server;
        this.client = Http1Client.builder().config(config.get("client")).build();
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        config = Config.builder()
                .sources(ConfigSources.classpath("application.yaml"))
                .overrides(() -> OverrideSources.create(Map.of(
                        "server.port", "0",
                        "server.sockets.*.port", "0"
                )))
                .build();
        ServerConfigMain.setup(server, config.get("server"));
    }

    @Test
    public void testConfigAccessSuccessful() {
        assertThat(callUnsecured(client, server.port()), is("Hello world unsecured!"));
        assertThat(callSecured(client, server.port("secured")), is("Hello Helidon-client!"));
    }
}