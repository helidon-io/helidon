/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import io.helidon.http.BadRequestException;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.DirectHandler;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class BadRequestTest {
    public static final String CUSTOM_REASON_PHRASE = "Custom-bad-request";
    public static final String CUSTOM_ENTITY = "There we go";
    private static final Header LOCATION_ERROR_PAGE = HeaderValues.create(HeaderNames.LOCATION, "/errorPage");

    private final Http1Client client;
    private final SocketHttpClient socketClient;

    BadRequestTest(Http1Client client, SocketHttpClient socketClient) {
        this.client = client;
        this.socketClient = socketClient;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.get("/bad-request", (req, res) -> {
            throw new BadRequestException("Bad request in routing");
        });
        builder.route(Http1Route.route(Method.GET,
                                       "/",
                                       (req, res) -> res.send("Hi")))
                // error handler for bad requests throw during routing
                .error(BadRequestException.class, (req, res, e) ->
                        res.send(e.getMessage()));
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder builder) {
        builder.directHandlers(DirectHandlers.builder()
                                       .addHandler(DirectHandler.EventType.BAD_REQUEST, BadRequestTest::badRequestHandler)
                                       .build());
    }

    @Test
    void testOk() {
        String response = client.method(Method.GET)
                .requestEntity(String.class);

        assertThat(response, is("Hi"));
    }

    @Test
    void testInvalidRequest() {
        // wrong content length
        String response = socketClient.sendAndReceive(Method.GET,
                                                      "/",
                                                      null,
                                                      List.of("Content-Length: 47a"));

        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testInvalidRequestWithRedirect() {
        // wrong content length
        String response = socketClient.sendAndReceive(Method.GET,
                                                      "/redirect",
                                                      null,
                                                      List.of("Content-Length: 47a"));

        assertThat(SocketHttpClient.statusFromResponse(response), is(Status.TEMPORARY_REDIRECT_307));

        ClientResponseHeaders headers = SocketHttpClient.headersFromResponse(response);
        assertThat(headers, hasHeader(LOCATION_ERROR_PAGE));
    }

    @Test
    void testInvalidUri() {
        // must fail on creation of bare request impl (URI.create())
        String response = socketClient.sendAndReceive(Method.GET,
                                                      "/bad{",
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

    @Test
    void testCrWithoutLf() {
        socketClient.requestRaw("GET / HTTP/1.1\r\nhost: localhost:8080\rcustom: value\r\n");

        String response = socketClient.receive();
        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testLfWithoutCr() {
        socketClient.requestRaw("GET / HTTP/1.1\r\nhost: localhost:8080\ncustom: value\r\n");

        String response = socketClient.receive();
        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testKeepAliveAndMissingLf() {
        socketClient.request(Method.GET, "/", null, List.of("Accept: text/plain", "Connection: keep-alive"));
        String response = socketClient.receive();
        assertThat(response, containsString("200 OK"));
        assertThat(response, containsString("Hi"));

        socketClient.requestRaw("GET / HTTP/1.1\r\nhost: localhost:8080\rcustom: value\r\n");

        response = socketClient.receive();
        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString("Connection: close"));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testBadRequestInRouting() {
        var response = client.get("/bad-request")
                .request(String.class);

        String entity = response.entity();

        // should have been handled by routing error handler
        assertThat(entity, is("Bad request in routing"));
        assertThat(response.status(), is(Status.OK_200));
    }

    private static DirectHandler.TransportResponse badRequestHandler(DirectHandler.TransportRequest request,
                                                                     DirectHandler.EventType eventType,
                                                                     Status httpStatus,
                                                                     ServerResponseHeaders responseHeaders,
                                                                     String message) {
        if (request.path().equals("/redirect")) {
            return DirectHandler.TransportResponse.builder()
                    .status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/errorPage")
                    .build();
        }
        return DirectHandler.TransportResponse.builder()
                .status(Status.create(Status.BAD_REQUEST_400.code(),
                                      CUSTOM_REASON_PHRASE))
                .headers(responseHeaders)
                .entity(CUSTOM_ENTITY)
                .build();
    }
}
