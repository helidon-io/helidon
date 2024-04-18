/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.List;

import io.helidon.common.http.Http;
import io.helidon.webserver.utils.SocketHttpClient;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.MetricsSupport;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Tests bad types in Accept header
 */
public class BadRequestTest {

    private static WebServer server;

    @BeforeAll
    static void createAndStartServer() {
        server = WebServer.builder()
                .addMediaSupport(JsonpSupport.create())
                .addRouting(Routing.builder()
                        .register(MetricsSupport.create())
                        .post("/echo", Handler.create(JsonObject.class,
                                (req, res, entity) -> res.status(Http.Status.OK_200).send(entity))))
                .build();
        server.start().await();
    }

    @AfterAll
    static void stopServer() {
        server.shutdown().await();
    }

    @Test
    void testBadAcceptType() throws Exception {
        try (SocketHttpClient c = new SocketHttpClient(server)) {
            c.request(Http.Method.POST, "/echo", "{ }", List.of("Accept: application.json"));
            String result = c.receive();
            assertThat(result, containsString("400 Bad Request"));
        }
    }

    @Test
    void testBadAcceptTypeMetrics() throws Exception {
        try (SocketHttpClient c = new SocketHttpClient(server)) {
            c.request(Http.Method.GET, "/metrics", "", List.of("Accept: application.json"));
            String result = c.receive();
            assertThat(result, containsString("400 Bad Request"));
        }
    }
}
