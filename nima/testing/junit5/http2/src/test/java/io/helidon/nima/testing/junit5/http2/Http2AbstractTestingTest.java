/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.testing.junit5.http2;

import io.helidon.common.http.Http;
import io.helidon.nima.http2.webclient.Http2Client;
import io.helidon.nima.http2.webserver.Http2Route;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.Socket;
import io.helidon.nima.webclient.api.ClientResponseTyped;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

abstract class Http2AbstractTestingTest {
    private final Http2Client httpClient;

    Http2AbstractTestingTest(Http2Client httpClient) {
        this.httpClient = httpClient;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.route(Http2Route.route(Http.Method.GET, "/greet", (req, res) -> res.send("hello")));
    }

    @SetUpRoute("custom")
    static void customRouting(HttpRules rules) {
        rules.route(Http2Route.route(Http.Method.GET, "/greet", (req, res) -> res.send("custom")));
    }

    @Test
    void testDefaultPort() {
        ClientResponseTyped<String> response = httpClient.get("/greet")
                .request(String.class);

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.entity(), is("hello"));
    }

    @Test
    void testCustomPort(@Socket("custom") Http2Client customClient) {
        ClientResponseTyped<String> response = customClient.get("/greet")
                .request(String.class);

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.entity(), is("custom"));
    }
}
