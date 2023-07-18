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

package io.helidon.tests.integration.webclient;

import java.util.stream.Stream;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1Client.Http1ClientBuilder;
import io.helidon.nima.webclient.security.WebClientSecurity;
import io.helidon.nima.webclient.spi.WebClientService;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.security.Security;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;

/**
 * Parent class for integration tests.
 */
@ServerTest
class TestParent {

    protected static final Config CONFIG = Config.create();

    protected final WebServer server;

    protected Http1Client client;
    protected Context context;

    TestParent(WebServer server) {
        this.server = server;
        this.client = createNewClient();
    }

    @SetUpServer
    static void startTheServer(WebServerConfig.Builder builder) {
        Main.setup(builder, null);
    }

    protected Http1Client createNewClient(WebClientService... clientServices) {
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder()
                                     .addOutboundTarget(OutboundTarget.builder("all")
                                                                .build())
                                     .build())
                .build();

        Http1ClientBuilder builder = Http1Client.builder()
                .useSystemServiceLoader(false)
                .addService(WebClientSecurity.create(security))
                .baseUri("http://localhost:" + server.port() + "/greet")
                .config(CONFIG.get("client"));

        Stream.of(clientServices).forEach(builder::addService);

        client = builder.build();
        return client;
    }

}
