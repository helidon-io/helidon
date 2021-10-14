/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.DirectHandler.TransportResponse;
import io.helidon.webserver.utils.SocketHttpClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.collection.IsMapContaining.hasEntry;

/**
 * Problem Description
 * If the requested URL contains invalid characters, e.g. { (which are not encoded), Helidon returns HTTP 400 with message
 * Illegal character in query at index ... This response comes from io.helidon.webserver.ForwardingHandler, line 112. The
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
class Gh1893 {
    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final String CUSTOM_REASON_PHRASE = "Custom-bad-request";
    public static final String CUSTOM_ENTITY = "There we go";
    private static WebServer webServer;
    private static WebClient webClient;

    @BeforeAll
    static void startServer() {
        webServer = WebServer.builder()
                .host("localhost")
                .directHandler(Gh1893::badRequestHandler, DirectHandler.EventType.BAD_REQUEST)
                .routing(Routing.builder()
                                 .get("/", (req, res) -> res.send("Hi")))
                .build()
                .start()
                .await(TIMEOUT);

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .build();
    }

    private static TransportResponse badRequestHandler(DirectHandler.TransportRequest request,
                                                       DirectHandler.EventType eventType,
                                                       Http.ResponseStatus defaultStatus,
                                                       String message) {
        if (request.uri().equals("/redirect")) {
            return TransportResponse.builder()
                    .status(Http.Status.TEMPORARY_REDIRECT_307)
                    .header(Http.Header.LOCATION, "/errorPage")
                    .build();
        }
        return TransportResponse.builder()
                .status(Http.ResponseStatus.create(Http.Status.BAD_REQUEST_400.code(),
                                                   CUSTOM_REASON_PHRASE))
                .entity(CUSTOM_ENTITY)
                .build();
    }

    @AfterAll
    static void stopServer() {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIMEOUT);
        }
        webServer = null;
        webClient = null;
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
        String response = SocketHttpClient.sendAndReceive("/",
                                                          Http.Method.GET,
                                                          null,
                                                          List.of(Http.Header.CONTENT_LENGTH + ": 47a"),
                                                          webServer);

        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testInvalidRequestWithRedirect() throws Exception {
        // wrong content length
        String response = SocketHttpClient.sendAndReceive("/redirect",
                                                          Http.Method.GET,
                                                          null,
                                                          List.of(Http.Header.CONTENT_LENGTH + ": 47a"),
                                                          webServer);

        assertThat(SocketHttpClient.statusFromResponse(response), is(Http.Status.TEMPORARY_REDIRECT_307));

        Map<String, String> headers = SocketHttpClient.headersFromResponse(response);
        assertThat(headers, hasEntry(equalToIgnoringCase("Location"), is("/errorPage")));
    }

    @Test
    void testInvalidUri() throws Exception {
        // must fail on creation of bare request impl (URI.create())
        String response = SocketHttpClient.sendAndReceive("/bad{",
                                                          Http.Method.GET,
                                                          null,
                                                          webServer);

        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }

    @Test
    void testInvalidFirstLine() throws Exception {
        SocketHttpClient client = new SocketHttpClient(webServer);

        client.request("GET IT", "/", "HTTP/1.1", null, null, null);
        String response = client.receive();

        assertThat(response, containsString("400 " + CUSTOM_REASON_PHRASE));
        assertThat(response, containsString(CUSTOM_ENTITY));
    }
}
