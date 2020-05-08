/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.media.jsonp.common.JsonpSupport;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;

/**
 * Parent class for integration tests.
 */
class TestParent {

    protected static final Config CONFIG = Config.create();

    protected static WebServer webServer;
    protected static WebClient webClient;

    @BeforeAll
    public static void startTheServer() throws Exception {
        webServer = Main.startServer().toCompletableFuture().get();

        long timeout = 2000; // 2 seconds should be enough to start the server
        long now = System.currentTimeMillis();

        while (!webServer.isRunning()) {
            Thread.sleep(100);
            if ((System.currentTimeMillis() - now) > timeout) {
                Assertions.fail("Failed to start webserver");
            }
        }

        webClient = createNewClient();
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    protected static WebClient createNewClient(WebClientService... clientServices) {
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder().build())
                .build();

        SecurityContext securityContext = security.createContext("unit-test");

        Context context = Context.builder().id("unit-test").build();
        context.register(securityContext);

        WebClient.Builder builder = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/greet")
                .config(CONFIG.get("client"))
                .context(context)
                .addMediaSupport(JsonpSupport.create());

        Stream.of(clientServices).forEach(builder::register);
        return builder.build();
    }

}
