/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
import io.helidon.common.http.Http;
import io.helidon.common.testing.http.junit5.SocketHttpClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TestNettyRejectRequest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static WebServer server;
    private static SocketHttpClient client;

    @BeforeAll
    static void createAndStartServer() {
        server = WebServer.builder()
                .host("localhost")
                .routing(Routing.builder()
                                 .get((req, res) -> res.send("test")))
                .port(0)
                .build()
                .start()
                .await(TIMEOUT);
        client = SocketHttpClient.create(server.port());
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.shutdown()
                    .await(TIMEOUT);
        }
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testBadHeader() throws Exception {
        // Cannot use WebClient or HttpURLConnection for this test because they use Netty's DefaultHttpHeaders
        // which prevents bad headers from being sent to the server.

        String response = client.sendAndReceive("/any",
                                                Http.Method.GET,
                                                null,
                                                List.of("Accept: text/plain", "Bad=Header: anything"));

        ClientResponseHeaders headers = SocketHttpClient.headersFromResponse(response);
        Http.Status status = SocketHttpClient.statusFromResponse(response);
        String entity = SocketHttpClient.entityFromResponse(response, false);

        assertThat(headers, hasHeader(Http.Header.CONTENT_LENGTH));
        assertThat(status, is(Http.Status.BAD_REQUEST_400));
        assertThat(entity, containsString("invalid character"));
    }
}
