/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.jersey.connector;

import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import jakarta.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.client.ClientConfig;

/**
 * Tests HTTP/2 integration of Jakarta REST client with the Helidon connector that uses
 * WebClient to execute HTTP requests. Upgrades connection from HTTP/1.1 to HTTP/2.
 */
@ServerTest
class ConnectorHttp2UpgradeTest extends ConnectorBase {

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        serverBuilder.addProtocol(Http2Config.create());
    }

    ConnectorHttp2UpgradeTest(WebServer server) {
        int port = server.port();

        ClientConfig config = new ClientConfig();
        config.connectorProvider(HelidonConnectorProvider.create());       // use Helidon's provider

        client(ClientBuilder.newClient(config));
        baseURI("http://localhost:" + port);
        protocolId(Http2Client.PROTOCOL_ID);       // HTTP/2 negotiation from 1.1
    }
}
