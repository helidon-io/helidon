/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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
import io.helidon.common.http.Http.Header;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * The MultiPortTest.
 */
class MultiPortTest {
    private static final Http1Client CLIENT = WebClient.builder()
            .build();
    private Handler commonHandler;
    private WebServer server;

    @BeforeEach
    void init() {
        commonHandler = new Handler() {
            private volatile int counter = 0;

            @Override
            public void handle(ServerRequest req, ServerResponse res) {
                res.send("Root! " + ++counter);
            }
        };
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void programmaticNoCompound() {
        WebServer server1 = WebServer.builder()
                .routing(routing -> routing.get("/", commonHandler)
                        .get("/variable", (req, res) -> res.send("Variable 1")))
                .start();

        WebServer server2 = WebServer.builder()
                .routing(routing -> routing.get("/", commonHandler)
                        .get("/variable", (req, res) -> res.send("Variable 2")))
                .start();

        try {
            assertResponse(server2.port(), "/", is("Root! 1"));
            assertResponse(server1.port(), "/", is("Root! 2"));
            assertResponse(server2.port(), "/", is("Root! 3"));
            assertResponse(server1.port(), "/", is("Root! 4"));

            assertResponse(server2.port(), "/variable", is("Variable 2"));
            assertResponse(server1.port(), "/variable", is("Variable 1"));
        } finally {
            try {
                server1.stop();
            } finally {
                server2.stop();
            }
        }
    }

    @Test
    void compositeInlinedServer() {
        server = WebServer.builder()
                .routing(routing -> routing.get("/overridden", (req, res) -> res.send("Overridden 2"))
                        .get("/", commonHandler)
                        .get("/variable", (req, res) -> res.send("Variable 2"))
                        .build())
                .socket("plain", (socket, routing) -> routing.addRouting(HttpRouting.builder()
                                                                                 .get("/overridden",
                                                                                      (req, res) -> res.send("Overridden 1"))
                                                                                 .get("/", commonHandler)
                                                                                 .get("/variable",
                                                                                      (req, res) -> res.send("Variable 1"))))
                .start();

        assertResponse(server.port(), "/", is("Root! 1"));
        assertResponse(server.port("plain"), "/", is("Root! 2"));
        assertResponse(server.port(), "/", is("Root! 3"));
        assertResponse(server.port("plain"), "/", is("Root! 4"));

        assertResponse(server.port(), "/variable", is("Variable 2"));
        assertResponse(server.port("plain"), "/variable", is("Variable 1"));
    }

    @Test
    void compositeSingleRoutingServer() {
        // start all of the servers
        server = WebServer.builder()
                .routing(routing -> routing.get("/overridden", (req, res) -> res.send("Overridden BOTH"))
                        .get("/", commonHandler)
                        .get("/variable", (req, res) -> res.send("Variable BOTH")))
                .socket("second", builder -> {
                })
                .start();

        assertResponse(server.port("second"), "/", is("Root! 1"));
        assertResponse(server.port(), "/", is("Root! 2"));
        assertResponse(server.port("second"), "/", is("Root! 3"));
        assertResponse(server.port(), "/", is("Root! 4"));

        assertResponse(server.port("second"), "/variable", is("Variable BOTH"));
        assertResponse(server.port(), "/variable", is("Variable BOTH"));
    }

    @Test
    void compositeRedirectServer() {
        // start all of the servers
        server = WebServer.builder()
                .routing(routing -> routing.get("/foo", (req, res) -> res.send("Root! 1")))
                .socket("redirect", (socket, router) -> {
                    router.addRouting(HttpRouting.builder()
                                              .any((req, res) -> {
                                                  res.status(Http.Status.MOVED_PERMANENTLY_301)
                                                          .header(Header.LOCATION.withValue(
                                                                  String.format("http://%s:%s%s",
                                                                                host(req.authority()),
                                                                                server.port(),
                                                                                req.path().absolute().rawPathNoParams())))
                                                          .send();
                                              }));
                })
                .start();

        assertResponse(server.port(), "/foo", is("Root! 1"));

        try (Http1ClientResponse response = CLIENT.get("http://localhost:" + server.port("redirect") + "/foo")
                .request()) {
            assertThat(response.status(), is(Http.Status.MOVED_PERMANENTLY_301));
            assertThat(response.headers(),
                       hasHeader(Header.LOCATION.withValue("http://localhost:" + server.port() + "/foo")));
        }
    }

    private String host(String authority) {
        int index = authority.indexOf(':');
        if (index == -1) {
            return authority;
        }
        return authority.substring(0, index);
    }

    private void assertResponse(int port, String path, Matcher<String> matcher) {
        try (Http1ClientResponse response = CLIENT.get()
                .uri("http://localhost:" + port)
                .path(path)
                .request()) {

            assertAll(() -> assertThat(response.status(), is(Http.Status.OK_200)),
                      () -> assertThat(response.as(String.class), matcher));
        }
    }
}
