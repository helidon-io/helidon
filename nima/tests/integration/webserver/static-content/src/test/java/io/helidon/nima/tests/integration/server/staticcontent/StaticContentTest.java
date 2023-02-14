/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.server.staticcontent;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.staticcontent.StaticContentService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class StaticContentTest {
    private final Http1Client client;

    StaticContentTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.register("/files", StaticContentService.builder("static")
                        .welcomeFileName("welcome.txt")
                        .build())
                .get("/files/default", (req, res) -> res.send("Nexted"));
    }

    @Test
    void testWelcomeFile() {
        String response = client.get("/files/")
                .request(String.class);
        assertThat(response, is("Welcome"));
    }

    @Test
    void testStaticContent() {
        String response = client.get("/files/static-content.txt")
                .request(String.class);
        assertThat(response, is("Hi"));
    }

    @Test
    void testNexted() {
        String response = client.get("/files/default")
                .request(String.class);
        assertThat(response, is("Nexted"));
    }

    @Test
    void testNotFound() {
        try (Http1ClientResponse response = client.get("/files/notThere")
                .request()) {
            assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
        }
    }

    @Test
    void testOutOfDirectory() {
        try (Http1ClientResponse response = client.get("/files/../logging-test.properties")
                .request()) {
            assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
        }
    }
}
