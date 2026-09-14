/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Header;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ReroutingAndNextingTest {
    private static final Header NEXTED_HEADER = HeaderValues.create("NEXTED", "yes");

    private final Http1Client client;
    private final SocketHttpClient socketClient;

    ReroutingAndNextingTest(Http1Client client, SocketHttpClient socketClient) {
        this.client = client;
        this.socketClient = socketClient;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.get("/direct", (req, res) -> res.send("Direct"))
                .get("/reroute", (req, res) -> res.header(NEXTED_HEADER).next())
                .get("/reroute", (req, res) -> res.reroute("/direct"))
                .get("/empty-query", (req, res) -> res.reroute("/query-target"))
                .get("/query-target", (req, res) -> res.send(req.prologue().hasQuery() + ":" + req.query().rawValue()))
                .register("/prefix", new QueryService());
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

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().contains(NEXTED_HEADER), is(true));
            assertThat(response.as(String.class), is("Direct"));
        }
    }

    @Test
    void testReroutePreservesEmptyQueryDelimiter() {
        String response = socketClient.sendAndReceive(Method.GET, "/empty-query?", null);

        assertThat(SocketHttpClient.entityFromResponse(response, true), is("true:"));
    }

    @Test
    void testNestedRoutePreservesEmptyQueryDelimiter() {
        String response = socketClient.sendAndReceive(Method.GET, "/prefix/empty-query?", null);

        assertThat(SocketHttpClient.entityFromResponse(response, true), is("true:"));
    }

    private static final class QueryService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
            rules.get("/empty-query", (req, res) -> res.send(req.prologue().hasQuery() + ":" + req.query().rawValue()));
        }
    }
}
