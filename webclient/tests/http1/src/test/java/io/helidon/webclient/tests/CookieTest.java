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

package io.helidon.webclient.tests;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CookieTest {
    private static WebServer server;
    private static Http1Client client;

    @BeforeAll
    void beforeAll() {
        // because we need to share instance of webclient between test methods, we cannot use the default approach
        // of @ServerTest, as that only supports instance per method
        server = WebServer.builder()
                .routing(rules -> rules.get("/cookie", CookieTest::getHandler)
                        .put("/cookie", CookieTest::putHandler))
                .build()
                .start();

        Config config = Config.builder()
                .addSource(ConfigSources.classpath("cookies.yaml"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        client = Http1Client.builder()
                .baseUri("http://localhost:" + server.port())
                .config(config.get("client"))
                .build();
    }

    @AfterAll
    void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @Order(1)
    void testCookieGet() {
        try (Http1ClientResponse response = client.get("/cookie").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    @Order(2)
    void testCookiePut() {
        try (Http1ClientResponse response = client.put("/cookie").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    @Order(3)
    void testCookieGetAfterPut() {
        try (Http1ClientResponse response = client.get("/cookie").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    private static void getHandler(ServerRequest req, ServerResponse res) {
        if (req.headers().contains(HeaderNames.COOKIE)) {
            Header cookies = req.headers().get(HeaderNames.COOKIE);
            if (cookies.allValues().size() == 2
                    && cookies.allValues().contains("flavor3=strawberry")       // in application.yaml
                    && cookies.allValues().contains("flavor4=raspberry")) {     // in application.yaml
                res.header(HeaderNames.SET_COOKIE, "flavor1=chocolate", "flavor2=vanilla");
                res.status(Status.OK_200).send();
            } else {
                res.status(Status.BAD_REQUEST_400).send();
            }
        } else {
            res.status(Status.BAD_REQUEST_400).send();
        }
    }

    private static void putHandler(ServerRequest req, ServerResponse res) {
        if (req.headers().contains(HeaderNames.COOKIE)) {
            Header cookies = req.headers().get(HeaderNames.COOKIE);
            if (cookies.allValues().size() == 4
                    && cookies.allValues().contains("flavor1=chocolate")
                    && cookies.allValues().contains("flavor2=vanilla")
                    && cookies.allValues().contains("flavor3=strawberry")
                    && cookies.allValues().contains("flavor4=raspberry")) {
                // clear flavor1 and flavor2
                res.header(HeaderNames.SET_COOKIE,
                           "flavor1=; Expires=Thu, 01-Jan-1970 00:00:10 GMT; Max-Age=0",
                           "flavor2=; Expires=Thu, 01-Jan-1970 00:00:10 GMT; Max-Age=0");
                res.status(Status.OK_200).send();
            } else {
                res.status(Status.BAD_REQUEST_400).send();
            }
        } else {
            res.status(Status.BAD_REQUEST_400).send();
        }
    }
}