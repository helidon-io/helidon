/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.examples.translator;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.examples.translator.backend.Main.createBackendWebServer;
import static io.helidon.webserver.examples.translator.frontend.Main.createFrontendWebServer;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.StringContains.containsString;

/**
 * The TranslatorTest.
 */
public class TranslatorTest {

    private static WebServer webServerFrontend;
    private static WebServer webServerBackend;
    private static Client client;
    private static WebTarget target;

    @BeforeAll
    public static void setUp() throws Exception {
        CompletionStage<WebServer> backendStage =
                createBackendWebServer(null).start();
        webServerBackend = backendStage.toCompletableFuture().get(10, TimeUnit.SECONDS);

        CompletionStage<WebServer> frontendStage =
                createFrontendWebServer(null, "localhost", webServerBackend.port()).start();
        webServerFrontend = frontendStage.toCompletableFuture().get(10, TimeUnit.SECONDS);

        client = ClientBuilder.newClient();
        target = client.target("http://localhost:" + webServerFrontend.port());
    }

    @AfterAll
    public static void tearDown() throws Exception {
        webServerFrontend.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);
        webServerBackend.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);

        if (client != null) {
            client.close();
        }
    }

    @Test
    public void e2e() throws Exception {

        Response response = target.path("translator")
                                  .queryParam("q", "cloud")
                                  .request()
                                  .get();

        assertThat("Unexpected response! Status code: " + response.getStatus(),
                          response.readEntity(String.class),
                          allOf(containsString("oblak"),
                                containsString("nube")));
    }
}
