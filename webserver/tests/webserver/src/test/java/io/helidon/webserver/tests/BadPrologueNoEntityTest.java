/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.List;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Verifies that 400 bad request is returned when a bad prologue is sent. Response
 * entities must be empty given that {@link io.helidon.webserver.ErrorHandling#includeEntity()}
 * is {@code false} by default.
 */
@ServerTest
class BadPrologueNoEntityTest {
    private final Http1Client client;
    private final SocketHttpClient socketClient;

    BadPrologueNoEntityTest(Http1Client client, SocketHttpClient socketClient) {
        this.client = client;
        this.socketClient = socketClient;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.route(Http1Route.route(Method.GET,
                                       "/",
                                       (req, res) -> {
                                           HttpPrologue prologue = req.prologue();
                                           String fragment = prologue.fragment().hasValue()
                                                   ? prologue.fragment().rawValue()
                                                   : "";
                                           res.send("path: " + prologue.uriPath().rawPath()
                                                            + ", query: " + prologue.query().rawValue()
                                                            + ", fragment: " + fragment);
                                       }));
    }

    @Test
    void testOk() {
        String response = client.method(Method.GET)
                .requestEntity(String.class);

        assertThat(response, is("path: /, query: , fragment: "));
    }

    @Test
    void testBadQuery() {
        String response = socketClient.sendAndReceive(Method.GET,
                                                      "/?a=<a%20href='/bad-uri.com'/>bad</a>",
                                                      null,
                                                      List.of());

        assertThat(response, containsString("400 Bad Request"));
        // beginning of message to the first double quote
        assertThat(response, not(containsString("Query contains invalid char: ")));
        // end of message from double quote, index of bad char, and bad char
        assertThat(response, not(containsString(", index: 2, char: 0x3c")));
        assertThat(response, not(containsString(">")));
    }

    @Test
    void testBadQueryCurly() {
        String response = socketClient.sendAndReceive(Method.GET,
                                                      "/?name=test1{{",
                                                      null,
                                                      List.of());

        assertThat(response, containsString("400 Bad Request"));
        // beginning of message to the first double quote
        assertThat(response, not(containsString("Query contains invalid char: ")));
        // end of message from double quote, index of bad char, and bad char
        assertThat(response, not(containsString(", index: 10, char: 0x7b")));
    }

    @Test
    void testBadPath() {
        String response = socketClient.sendAndReceive(Method.GET,
                                                      "/name{{{{{{{Sdsds<Dhttps:--www.example.com",
                                                      null,
                                                      List.of());

        assertThat(response, containsString("400 Bad Request"));
        // for path we are on the safe side, and never return it back (even HTML encoded)
        assertThat(response, not(containsString("Bad request, see server log for more information")));
    }

    @Test
    void testBadFragment() {
        String response = socketClient.sendAndReceive(Method.GET,
                                                      "/?a=b#invalid-fragment>",
                                                      null,
                                                      List.of());

        assertThat(response, containsString("400 Bad Request"));
        // beginning of message to the first double quote
        assertThat(response, not(containsString("Fragment contains invalid char: ")));
        // end of message from double quote, index of bad char, and bad char
        assertThat(response, not(containsString(", index: 16, char: 0x3e")));
        assertThat(response, not(containsString(">")));
    }
}
