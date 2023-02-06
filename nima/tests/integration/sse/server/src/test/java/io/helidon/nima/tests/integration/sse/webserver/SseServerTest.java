/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.server;

import io.helidon.common.http.Http;
import io.helidon.nima.sse.webserver.SseEvent;
import io.helidon.nima.sse.webserver.SseResponse;
import io.helidon.nima.testing.junit5.webserver.ServerTest;

import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class SseServerTest {

    private final Http1Client client;

    SseServerTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/sse1", SseServerTest::sse1Handler);
        rules.get("/sse2", SseServerTest::sse2Handler);
    }

    private static void sse1Handler(ServerRequest req, ServerResponse res) {
        try (SseResponse sseRes = SseResponse.create(res)) {
            sseRes.send(SseEvent.create("hello"))
                    .send(SseEvent.create("world"));
        }
    }

    private static void sse2Handler(ServerRequest req, ServerResponse res) throws InterruptedException {
        SseResponse sseRes = SseResponse.create(res);
        for (int i = 1; i <= 3; i++) {
            sseRes.send(SseEvent.create(Integer.toString(i)));
            Thread.sleep(50);      // simulates messages over time
        }
        sseRes.close();
    }

    @Test
    void testSse1() {
        try (Http1ClientResponse response = client.get("/sse1").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.as(String.class), is("data:hello\n\ndata:world\n\n"));
        }
    }

    @Test
    void testSse2() {
        try (Http1ClientResponse response = client.get("/sse2").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.as(String.class), is("data:1\n\ndata:2\n\ndata:3\n\n"));
        }
    }
}
