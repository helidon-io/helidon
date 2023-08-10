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
import io.helidon.common.http.Http.HeaderNames;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ReroutingAndNextingTest {
    private static final HeaderValue NEXTED_HEADER = HeaderNames.create(Http.HeaderNames.create("NEXTED"), "yes");

    private final Http1Client client;

    ReroutingAndNextingTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.get("/direct", (req, res) -> res.send("Direct"))
                .get("/reroute", (req, res) -> res.header(NEXTED_HEADER).next())
                .get("/reroute", (req, res) -> res.reroute("/direct"));
    }

    @Test
    void testDirect() {
        String response = client.get("/direct")
                .request()
                .as(String.class);

        assertThat(response, is("Direct"));
    }

    @Test
    void testReroute() {
        try (Http1ClientResponse response = client.get("/reroute")
                .request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.headers().contains(NEXTED_HEADER), is(true));
            assertThat(response.as(String.class), is("Direct"));
        }
    }
}
