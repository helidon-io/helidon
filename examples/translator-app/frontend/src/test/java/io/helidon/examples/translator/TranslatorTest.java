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

package io.helidon.examples.translator;

import io.helidon.examples.translator.backend.TranslatorBackendService;
import io.helidon.examples.translator.frontend.TranslatorFrontendService;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServerConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The TranslatorTest.
 */
@SuppressWarnings("SpellCheckingInspection")
@ServerTest
public class TranslatorTest {

    private final Http1Client client;

    public TranslatorTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    public static void setUp(WebServerConfig.Builder builder) {
        builder.routing(routing -> routing.
                        register(new TranslatorFrontendService("localhost", 9080)))
                .putSocket("backend", socket -> socket
                        .port(9080)
                        .routing(routing -> routing.register(new TranslatorBackendService())));
    }

    @Test
    public void testCzech() {
        try (Http1ClientResponse response = client.get()
                .queryParam("q", "cloud")
                .queryParam("lang", "czech")
                .request()) {

            assertThat("Unexpected response! Status code: " + response.status(),
                    response.entity().as(String.class), is("oblak\n"));
        }
    }

    @Test
    public void testItalian() {
        try (Http1ClientResponse response = client.get()
                .queryParam("q", "cloud")
                .queryParam("lang", "italian")
                .request()) {

            assertThat("Unexpected response! Status code: " + response.status(),
                    response.entity().as(String.class), is("nube\n"));
        }
    }

    @Test
    public void testFrench() {
        try (Http1ClientResponse response = client.get()
                .queryParam("q", "cloud")
                .queryParam("lang", "french")
                .request()) {

            assertThat("Unexpected response! Status code: " + response.status(),
                    response.entity().as(String.class), is("nuage\n"));
        }
    }
}
