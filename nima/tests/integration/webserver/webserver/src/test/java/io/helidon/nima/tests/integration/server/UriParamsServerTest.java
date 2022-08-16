/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class UriParamsServerTest {
    private final Http1Client client;

    UriParamsServerTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/shop", (req, res) -> res.send("shop"))
                .get("/admin", (req, res) -> res.send("admin"));
    }

    @Test
    void testEndpoints() {
        String s = client.get("/shop")
                .request(String.class);
        assertThat(s, is("shop"));

        s = client.get()
                .path("/admin")
                .request(String.class);
        assertThat(s, is("admin"));
    }

    @Test
    void testEndpointsWithParams() {
        String s = client.get()
                .path("/shop;a=b")
                .request(String.class);
        assertThat(s, is("shop"));
        s = client.get()
                .path("/admin;a=b")
                .request(String.class);
        assertThat(s, is("admin"));
    }
}
