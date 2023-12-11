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

package io.helidon.webclient.tests;

import java.util.stream.Stream;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientConfig;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

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

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.register("/greet", new GreetService());
    }

    protected WebClient noServiceClient(WebClientService... services) {
        return WebClient.builder()
                .update(it -> Stream.of(services).forEach(it::addService))
                .servicesDiscoverServices(false)
                .baseUri("http://localhost:" + server.port() + "/greet")
                .config(CONFIG.get("client"))
                .build();
    }

    protected Http1Client createNewClient(WebClientService... clientServices) {
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder()
                                     .addOutboundTarget(OutboundTarget.builder("all")
                                                                .build())
                                     .build())
                .build();

        Http1ClientConfig.Builder builder = Http1Client.builder()
                .servicesDiscoverServices(false)
                .addService(WebClientSecurity.create(security))
                .baseUri("http://localhost:" + server.port() + "/greet")
                .config(CONFIG.get("client"));

        Stream.of(clientServices).forEach(builder::addService);

        client = builder.build();
        return client;
    }

}
