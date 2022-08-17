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

import java.util.List;
import java.util.Map;

import io.helidon.common.http.HeadersServerResponse;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.testing.junit5.webserver.SocketHttpClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.SimpleHandler;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http1.Http1Route;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.collection.IsMapContaining.hasEntry;

@ServerTest
class BadRequestTest {
    public static final String CUSTOM_REASON_PHRASE = "Custom-bad-request";
    public static final String CUSTOM_ENTITY = "There we go";

    private final Http1Client client;
    private final SocketHttpClient socketClient;

    BadRequestTest(Http1Client client, SocketHttpClient socketClient) {
        this.client = client;
        this.socketClient = socketClient;
    }

    @SetUpRoute
    static void routing(HttpRules builder) {
        builder.route(Http1Route.route(Http.Method.GET,
                                       "/",
                                       (req, res) -> res.send("Hi")));
    }

    @SetUpServer
    static void setUpServer(WebServer.Builder builder) {
        builder.simpleHandler(BadRequestTest::badRequestHandler, SimpleHandler.EventType.BAD_REQUEST);
    }

    // no need to try with resources when reading as string
    @SuppressWarnings("resource")
    @Test
    void testOk() {
        String response = client.method(Http.Method.GET)
                .request()
                .as(String.class);

        assertThat(response, is("Hi"));
    }

    @Test
    void testInvalidRequest() {
        // wrong content length
        String response = socketClient.sendAndReceive("/",
                                                      Http.Method.GET,
                                                      null,
                                                      List.of("Content-Length: 47a"));

        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testInvalidRequestWithRedirect() {
        // wrong content length
        String response = socketClient.sendAndReceive("/redirect",
                                                      Http.Method.GET,
                                                      null,
                                                      List.of("Content-Length: 47a"));

        assertThat(SocketHttpClient.statusFromResponse(response), is(Http.Status.TEMPORARY_REDIRECT_307));

        Map<String, String> headers = SocketHttpClient.headersFromResponse(response);
        assertThat(headers, hasEntry(equalToIgnoringCase("Location"), is("/errorPage")));
    }

    @Test
    void testInvalidUri() {
        // must fail on creation of bare request impl (URI.create())
        String response = socketClient.sendAndReceive("/bad{",
                                                      Http.Method.GET,
                                                      null);

        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testInvalidFirstLine() {
        socketClient.request("GET IT", "/", "HTTP/1.1", null, null, null);
        String response = socketClient.receive();

        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testBadHeaderEquals() {
        List<String> headers = List.of("Accept: text/plain", "Bad=Header: anything");
        socketClient.request("GET IT", "/", "HTTP/1.1", null, headers, null);
        String response = socketClient.receive();

        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testBadHeaderWhitespace() {
        List<String> headers = List.of("Accept: text/plain", "Bad\tHeader: anything");
        socketClient.request("GET IT", "/", "HTTP/1.1", null, headers, null);
        String response = socketClient.receive();

        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    private static SimpleHandler.SimpleResponse badRequestHandler(SimpleHandler.SimpleRequest request,
                                                                  SimpleHandler.EventType eventType,
                                                                  Http.Status httpStatus,
                                                                  HeadersServerResponse responseHeaders,
                                                                  String message) {
        if (request.path().equals("/redirect")) {
            return SimpleHandler.SimpleResponse.builder()
                    .status(Http.Status.TEMPORARY_REDIRECT_307)
                    .header(Header.LOCATION, "/errorPage")
                    .build();
        }
        return SimpleHandler.SimpleResponse.builder()
                .status(Http.Status.create(Http.Status.BAD_REQUEST_400.code(),
                                           CUSTOM_REASON_PHRASE))
                .headers(responseHeaders)
                .message(CUSTOM_ENTITY)
                .build();
    }
}
