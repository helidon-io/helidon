/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.Arrays;

import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.POST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class OutputStreamTest {

    private final HttpClient<?> client;

    OutputStreamTest(WebServer server) {
        String uri = "http://localhost:" + server.port();
        this.client = Http1Client.builder()
                .baseUri(uri)
                .build();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(POST, "/echo",
                     (req, res) -> res.send(req.content().as(String.class)));
    }

    @Test
    void verifyFirstPacket() {
        var req = client.post().path("/echo");
        req.header(HeaderNames.CONTENT_LENGTH, "4");    // first packet logic
        var res = req.outputStream(
                o -> {
                    byte[] bytes = new byte[2];
                    Arrays.fill(bytes, (byte) 'A');
                    o.write(bytes);
                    Arrays.fill(bytes, (byte) 'B');             // reuses byte array
                    o.write(bytes);
                    o.close();
                });
        String s = res.as(String.class);
        assertThat(s, is("AABB"));
        res.close();
    }
}
