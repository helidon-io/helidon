/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.server;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ReasonPhraseTest {
    private static final String CUSTOM_PHRASE = "Custom error";
    private static final Http.Status CUSTOM_STATUS = Http.Status.create(400, CUSTOM_PHRASE);

    private final Http1Client client;

    ReasonPhraseTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules routing) {
        routing.get("/default", (req, res) -> res.status(Http.Status.BAD_REQUEST_400).send())
                .get("/custom", (req, res) -> res.status(CUSTOM_STATUS).send());
    }

    @Test
    void testDefaultPhrase() {
        try (Http1ClientResponse response = client.get("/default")
                .request()) {
            assertThat(response.status(), is(Http.Status.BAD_REQUEST_400));
        }
    }

    @Test
    void testCustomPhrase() {
        try (Http1ClientResponse response = client.get("/custom")
                .request()) {
            assertThat(response.status(), is(CUSTOM_STATUS));
            assertThat(response.status().code(), is(CUSTOM_STATUS.code()));
            assertThat(response.status().reasonPhrase(), is(CUSTOM_PHRASE));
        }
    }
}
