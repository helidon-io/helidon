/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.sse;

import java.util.concurrent.CountDownLatch;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HeaderValues.ACCEPT_JSON;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class SseServerTest extends SseBaseTest {

    SseServerTest(WebServer webServer) {
        super(webServer);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/sseString1", SseServerTest::sseString1);
        rules.get("/sseString2", SseServerTest::sseString2);
        rules.get("/sseJson1", (req, res) -> {
            SseServerTest.sseJson1(req, res, new CountDownLatch(0));
        });
        rules.get("/sseJson2", (req, res) -> {
            SseServerTest.sseJson2(req, res, new CountDownLatch(0));
        });
        rules.get("/sseMixed", SseServerTest::sseMixed);
        rules.get("/sseIdComment", SseServerTest::sseIdComment);
    }

    @Test
    void testSseString1() throws Exception {
        testSse("/sseString1", "data:hello", "data:world");
    }

    @Test
    void testSseString2() throws Exception {
        testSse("/sseString2", "data:1", "data:2", "data:3");
    }

    @Test
    void testSseJson1() throws Exception {
        testSse("/sseJson1", "data:{\"hello\":\"world\"}");
    }

    @Test
    void testSseJson2() throws Exception {
        testSse("/sseJson2", "data:{\"hello\":\"world\"}");
    }

    @Test
    void testSseMixed() throws Exception {
        testSse("/sseMixed", "data:hello", "data:world",
                "data:{\"hello\":\"world\"}", "data:{\"hello\":\"world\"}");
    }

    @Test
    void testIdComment() throws Exception {
        testSse("/sseIdComment", ":This is a comment\nid:1\ndata:hello");
    }

    @Test
    void testWrongAcceptType() {
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + webServer().port())
                .build();
        try (Http1ClientResponse response = client.get("/sseString1").header(ACCEPT_JSON).request()) {
            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
        }
    }
}
