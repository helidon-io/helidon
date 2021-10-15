/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.webserver.utils.SocketHttpClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.collection.IsMapContaining.hasKey;

public class TestNettyRejectRequest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static WebServer server;

    @BeforeAll
    public static void createAndStartServer() {
        server = WebServer.builder()
                .routing(Routing.builder()
                                 .get((req, res) -> res.send("test")))
                .port(0)
                .build()
                .start()
                .await(TIMEOUT);
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (server != null) {
            server.shutdown()
                    .await(TIMEOUT);
        }
    }

    @Test
    public void testBadHeader() throws Exception {
        // Cannot use WebClient or HttpURLConnection for this test because they use Netty's DefaultHttpHeaders
        // which prevents bad headers from being sent to the server.

        String response = SocketHttpClient.sendAndReceive("/any",
                                        Http.Method.GET,
                                        null,
                                        List.of("Accept: text/plain", "Bad=Header: anything"),
                                        server);

        Map<String, String> headers = SocketHttpClient.headersFromResponse(response);
        Http.ResponseStatus status = SocketHttpClient.statusFromResponse(response);
        String entity = SocketHttpClient.entityFromResponse(response, false);

        assertThat(headers, hasKey(equalToIgnoringCase("content-length")));
        assertThat(status, is(Http.Status.BAD_REQUEST_400));
        assertThat(entity, containsString("prohibited characters"));
    }
}
