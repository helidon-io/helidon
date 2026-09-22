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

package io.helidon.webserver.tests.http3;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3UdpOnlyListenerTest {
    @Test
    void shouldServeHttp3OnUdpOnlyListener() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = Http3TestSupport.udpOnlyListener(Http3UdpOnlyListenerTest::routing)) {
            HttpClient client = environment.http3Client();
            HttpResponse<String> response = client.send(Http3TestSupport.http3Get(environment.port(), "/hello"), ofString());

            assertThat(response.statusCode(), is(200));
            assertThat(response.version(), is(HTTP_3));
            assertThat(response.body(), is("hello"));
        }
    }

    @Test
    void shouldRejectHttp1OnUdpOnlyListener() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = Http3TestSupport.udpOnlyListener(Http3UdpOnlyListenerTest::routing)) {
            HttpClient client = environment.http1Client();

            assertThrows(IOException.class, () -> client.send(Http3TestSupport.http1Get(environment.port(), "/hello"),
                                                              ofString()));
        }
    }

    private static void routing(HttpRouting.Builder router) {
        router.get("/hello", (_, res) -> res.send("hello"));
    }
}
