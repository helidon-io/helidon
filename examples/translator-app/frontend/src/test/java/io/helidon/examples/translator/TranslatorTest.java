/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.examples.translator;

import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.examples.translator.backend.Main.startBackendServer;
import static io.helidon.examples.translator.frontend.Main.startFrontendServer;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The TranslatorTest.
 */
public class TranslatorTest {

    private static WebServer webServerFrontend;
    private static WebServer webServerBackend;
    private static Client client;
    private static WebTarget target;

    @BeforeAll
    public static void setUp() {
        webServerBackend = startBackendServer().await(10, TimeUnit.SECONDS);
        webServerFrontend = startFrontendServer().await(10, TimeUnit.SECONDS);
        client = ClientBuilder.newClient();
        target = client.target("http://localhost:" + webServerFrontend.port());
    }

    @AfterAll
    public static void tearDown() {
        webServerFrontend.shutdown().await(10, TimeUnit.SECONDS);
        webServerBackend.shutdown().await(10, TimeUnit.SECONDS);
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testCzech() {
        try (Response response = target.queryParam("q", "cloud")
                                  .queryParam("lang", "czech")
                                  .request()
                                  .get()) {
            assertThat("Unexpected response! Status code: " + response.getStatus(),
                    response.readEntity(String.class), is("oblak\n"));
        }
    }

    @Test
    public void testItalian() {
        try (Response response = target.queryParam("q", "cloud")
                                  .queryParam("lang", "italian")
                                  .request()
                                  .get()) {
            assertThat("Unexpected response! Status code: " + response.getStatus(),
                    response.readEntity(String.class), is("nube\n"));
        }
    }

    @Test
    public void testFrench() {
        try (Response response = target.queryParam("q", "cloud")
                                  .queryParam("lang", "french")
                                  .request()
                                  .get()) {
            assertThat("Unexpected response! Status code: " + response.getStatus(),
                    response.readEntity(String.class), is("nuage\n"));
        }
    }
}
