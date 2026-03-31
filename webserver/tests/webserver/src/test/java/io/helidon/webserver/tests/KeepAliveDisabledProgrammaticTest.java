/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.RepeatedTest;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static io.helidon.http.Status.OK_200;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class KeepAliveDisabledProgrammaticTest {
    private final Http1Client webClient;

    KeepAliveDisabledProgrammaticTest(Http1Client client) {
        this.webClient = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        ServerConnectionSelector http1 = Http1ConnectionSelector.builder()
                .config(Http1Config.builder()
                                .sendKeepAliveHeader(false)
                                .build())
                .build();

        server.addConnectionSelector(http1);
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Method.PUT, "/plain", (req, res) -> {
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                while (in.read(buffer) > 0) {
                    // fully consume the request to keep the connection open
                }
                res.send("done");
            } catch (IOException e) {
                res.status(Status.INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        });
    }

    @RepeatedTest(100)
    void sendWithKeepAliveDoesNotSendConnectionHeader() {
        try (HttpClientResponse response = testCall(webClient, true)) {
            assertThat(response.status(), is(OK_200));
            assertThat(response.headers(), noHeader(HeaderNames.CONNECTION));
        }
    }

    @RepeatedTest(100)
    void sendWithoutKeepAliveStillCloses() {
        try (HttpClientResponse response = testCall(webClient, false)) {
            assertThat(response.status(), is(OK_200));
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_CLOSE));
        }
    }

    private static HttpClientResponse testCall(Http1Client client, boolean keepAlive) {
        Http1ClientRequest request = client.method(Method.PUT)
                .uri("/plain");

        if (!keepAlive) {
            request.header(HeaderValues.CONNECTION_CLOSE);
        }

        Http1ClientResponse response = request.outputStream(it -> {
            it.write("0123456789".getBytes(StandardCharsets.UTF_8));
            it.write("0123456789".getBytes(StandardCharsets.UTF_8));
            it.write("0123456789".getBytes(StandardCharsets.UTF_8));
            it.close();
        });

        assertThat(response.status(), is(Status.OK_200));
        return response;
    }
}
