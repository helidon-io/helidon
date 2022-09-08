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

package io.helidon.reactive.webserver;

import java.time.Duration;
import java.util.List;

import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.DirectHandler.TransportResponse;
import io.helidon.common.http.Http;
import io.helidon.common.http.ServerResponseHeaders;
import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.reactive.webclient.WebClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Problem Description
 * If the requested URL contains invalid characters, e.g. { (which are not encoded), Helidon returns HTTP 400 with message
 * Illegal character in query at index ... This response comes from io.helidon.reactive.webserver.ForwardingHandler, line 112. The
 * exception containing this message is throw from constructor of BareRequestImpl, because the passed URI violates the
 * corresponding RFC.
 *
 * We use Helidon as a web server that serves static files (UI application). If user opens URL
 * https://<host>/tnt/flow/page=overview, browser shows the application. But when user puts (by accident) e.g. { to the URL
 * (https://<host>/tnt/flow?page=overview{) and hits enter to reload the page, he can see blank screen containing error message
 * Illegal character in query at index ....
 *
 * When URL isn't valid, we want the user to be redirected to an application-specific error page, e.g. to
 * https://<host>/tnt/page-does-not-exist.
 *
 * Steps to reproduce
 * This problem can be reproduced from curl. It's important to append -g parameter so that curl doesn't encode the special
 * characters.
 *
 * Sample request: curl -g "http://hostname/tnt/page2{
 */
class Gh1893Test {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String CUSTOM_REASON_PHRASE = "Custom-bad-request";
    private static final String CUSTOM_ENTITY = "There we go";

    private static WebServer webServer;
    private static WebClient webClient;
    private static SocketHttpClient socketClient;

    @BeforeAll
    static void startServer() {
        webServer = WebServer.builder()
                .defaultSocket(s -> s
                        .host("localhost")
                )
                .directHandler(Gh1893Test::badRequestHandler, DirectHandler.EventType.BAD_REQUEST)
                .routing(r -> r.get("/", (req, res) -> res.send("Hi")))
                .build()
                .start()
                .await(TIMEOUT);

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .build();

        socketClient = SocketHttpClient.create(webServer.port());
    }

    private static TransportResponse badRequestHandler(DirectHandler.TransportRequest request,
                                                       DirectHandler.EventType eventType,
                                                       Http.Status defaultStatus,
                                                       ServerResponseHeaders headers,
                                                       String message) {
        if (request.path().equals("/redirect")) {
            return TransportResponse.builder()
                    .status(Http.Status.TEMPORARY_REDIRECT_307)
                    .header(Http.Header.LOCATION, "/errorPage")
                    .build();
        }
        return TransportResponse.builder()
                .status(Http.Status.create(Http.Status.BAD_REQUEST_400.code(),
                                                   CUSTOM_REASON_PHRASE))
                .entity(CUSTOM_ENTITY)
                .build();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIMEOUT);
        }
        if (socketClient != null) {
            socketClient.close();
        }
        webServer = null;
        webClient = null;
    }

    @BeforeEach
    void resetSocketClient() {
        socketClient.disconnect();
        socketClient.connect();
    }

    @Test
    void testOk() {
        String response = webClient.get()
                .request(String.class)
                .await(TIMEOUT);

        assertThat(response, is("Hi"));
    }

    @Test
    void testInvalidRequest() throws Exception {
        // wrong content length
        String response = socketClient.sendAndReceive("/",
                                                      Http.Method.GET,
                                                      null,
                                                      List.of(Http.Header.CONTENT_LENGTH.defaultCase() + ": 47a"));

        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testInvalidRequestWithRedirect() {
        // wrong content length
        String response = socketClient.sendAndReceive("/redirect",
                                                      Http.Method.GET,
                                                      null,
                                                      List.of(Http.Header.CONTENT_LENGTH.defaultCase() + ": 47a"));

        assertThat(SocketHttpClient.statusFromResponse(response), is(Http.Status.TEMPORARY_REDIRECT_307));

        ClientResponseHeaders headers = SocketHttpClient.headersFromResponse(response);
        assertThat(headers, HttpHeaderMatcher.hasHeader(Http.HeaderValue.create(Http.Header.LOCATION, "/errorPage")));
    }

    @Test
    void testInvalidUri() throws Exception {
        // must fail on creation of bare request impl (URI.create())
        String response = socketClient.sendAndReceive("/bad{",
                                                      Http.Method.GET,
                                                      null);

        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testInvalidFirstLine() throws Exception {
        try (SocketHttpClient client = SocketHttpClient.create(webServer.port())) {

            client.request("GET IT", "/", "HTTP/1.1", null, null, null);
            String response = client.receive();

            assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
            assertThat(response, containsString(CUSTOM_ENTITY));
        }
    }

    private static TransportResponse badRequestHandler(DirectHandler.TransportRequest request,
                                                       DirectHandler.EventType eventType,
                                                       Http.Status defaultStatus,
                                                       String message) {
        if (request.path().equals("/redirect")) {
            return TransportResponse.builder()
                    .status(Http.Status.TEMPORARY_REDIRECT_307)
                    .header(Http.Header.LOCATION, "/errorPage")
                    .build();
        }
        return TransportResponse.builder()
                .status(Http.Status.create(Http.Status.BAD_REQUEST_400.code(),
                                           CUSTOM_REASON_PHRASE))
                .entity(CUSTOM_ENTITY)
                .build();
    }
}
