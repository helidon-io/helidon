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

package io.helidon.nima.webclient.http1;

import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class CookieTest {

    private final String baseURI;
    private static Http1Client client = null;

    CookieTest(WebServer webServer) {
        baseURI = "http://localhost:" + webServer.port();
    }

    /**
     * Share client instance across tests in order to share cookie store.
     */
    @BeforeAll
    static void setUp() {
        client = Http1Client.builder()
                .enableAutomaticCookieStore(true)
                .defaultCookies(Map.of("flavor3", "strawberry", "flavor4", "raspberry"))
                .build();
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/cookie", CookieTest::getHandler);
        rules.put("/cookie", CookieTest::putHandler);
    }

    @Test
    @Order(1)
    void testCookieGet() {
        Http1ClientResponse response = client.get(baseURI + "/cookie").request();
        assertThat(response.status(), is(Http.Status.OK_200));
    }

    @Test
    @Order(2)
    void testCookiePut() {
        Http1ClientResponse response = client.put(baseURI + "/cookie").request();
        assertThat(response.status(), is(Http.Status.OK_200));
    }

    private static void getHandler(ServerRequest req, ServerResponse res) {
        Http.HeaderValue cookies = req.headers().get(Http.Header.COOKIE);
        assertThat(cookies.allValues(), hasItem("flavor3=strawberry"));
        assertThat(cookies.allValues(), hasItem("flavor4=raspberry"));
        res.header(Http.Header.SET_COOKIE, "flavor1=chocolate", "flavor2=vanilla");
        res.send("ok");
    }

    private static void putHandler(ServerRequest req, ServerResponse res) {
        Http.HeaderValue cookies = req.headers().get(Http.Header.COOKIE);
        assertThat(cookies.allValues(), hasItem("flavor1=chocolate"));
        assertThat(cookies.allValues(), hasItem("flavor2=vanilla"));
        assertThat(cookies.allValues(), hasItem("flavor3=strawberry"));
        assertThat(cookies.allValues(), hasItem("flavor4=raspberry"));
        res.send("ok");
    }
}
