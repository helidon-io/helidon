/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpForward.Scheme;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.POST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;

@ServerTest
class HttpMockProxyTest {

    private static final int PROXY_PORT = 1080;
    private final WebClient webClient;
    private final WebClient directClient;
    private final Proxy mockProxy;
    private final int serverPort;
    private ClientAndServer mockServer;

    HttpMockProxyTest(WebServer server) {
        this.serverPort = server.port();
        this.mockProxy = Proxy.builder().host("localhost").port(PROXY_PORT).build();
        this.webClient = WebClient.builder()
                .baseUri("http://localhost:" + serverPort)
                .proxy(mockProxy)
                .build();
        this.directClient = WebClient.builder()
                .baseUri("http://localhost:" + serverPort)
                .proxy(Proxy.noProxy())
                .build();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(GET, "/get", Routes::get);
        router.route(POST, "/stream-redirect-302", (req, res) ->
                res.status(Status.FOUND_302).header(HeaderNames.LOCATION, "/stream-target-get").send());
        router.route(GET, "/stream-target-get", (req, res) -> res.send("followed"));
        router.route(POST, "/stream-redirect-307", (req, res) -> {
            req.content().as(String.class);
            res.status(Status.TEMPORARY_REDIRECT_307).header(HeaderNames.LOCATION, "/stream-target").send();
        });
        router.route(POST, "/stream-redirect-308", (req, res) -> {
            req.content().as(String.class);
            res.status(Status.PERMANENT_REDIRECT_308).header(HeaderNames.LOCATION, "/stream-target").send();
        });
        router.route(POST, "/stream-target", (req, res) -> res.send(req.content().as(String.class)));
    }

    @BeforeEach
    public void before() {
        mockServer = startClientAndServer(PROXY_PORT);
        mockServer.when(request()).forward(forward().withScheme(Scheme.HTTP).withHost("localhost").withPort(serverPort)); 
    }

    @AfterEach
    public void after() {
        mockServer.stop();
    }

    @Test
    public void issue9022() {
        try (HttpClientResponse response = webClient.get("/get").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
        }
    }

    @ParameterizedTest
    @CsvSource({"307, false", "308, false", "307, true", "308, true"})
    void streamedRedirectPreservesProxy(int redirectStatus, boolean requestProxyOverride) {
        String body = "streamed proxy entity";
        String redirectPath = "/stream-redirect-" + redirectStatus;
        var request = (requestProxyOverride ? directClient : webClient).post(redirectPath);
        if (requestProxyOverride) {
            request.proxy(mockProxy);
        }

        try (HttpClientResponse response = request.outputStream(output -> {
            output.write(body.getBytes(StandardCharsets.UTF_8));
            output.close();
        })) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is(body));
        }

        assertProxiedPost(redirectPath, body);
        assertProxiedPost("/stream-target", body);
    }

    @Test
    void earlyStreamedRedirectToGetPreservesProxyHeader() {
        try (HttpClientResponse response = webClient.post("/stream-redirect-302")
                .sendExpectContinue(true)
                .outputStream(output -> {
                    output.write('x');
                    output.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("followed"));
        }

        var initial = mockServer.retrieveRecordedRequests(request().withMethod("POST").withPath("/stream-redirect-302"));
        assertThat("Initial proxied POST count", initial.length, is(1));
        assertThat("Initial Proxy-Connection", initial[0].getFirstHeader("Proxy-Connection"), is("keep-alive"));

        var redirected = mockServer.retrieveRecordedRequests(request().withMethod("GET").withPath("/stream-target-get"));
        assertThat("Redirected proxied GET count", redirected.length, is(1));
        assertThat("Redirected Proxy-Connection", redirected[0].getFirstHeader("Proxy-Connection"), is("keep-alive"));
    }

    private void assertProxiedPost(String path, String body) {
        var recorded = mockServer.retrieveRecordedRequests(request().withMethod("POST").withPath(path));
        assertThat("Proxy request count for " + path, recorded.length, is(1));
        assertThat(recorded[0].getMethod().getValue(), is("POST"));
        assertThat(recorded[0].getFirstHeader("Proxy-Connection"), is("keep-alive"));
        assertThat(recorded[0].getBodyAsString(), is(body));
    }

    private static class Routes {
        private static String get() {
            return "Hello";
        }
    }
}
