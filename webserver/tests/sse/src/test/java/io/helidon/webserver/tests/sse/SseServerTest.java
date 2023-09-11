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

package io.helidon.webserver.tests.sse;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HeaderValues.ACCEPT_EVENT_STREAM;
import static io.helidon.http.HeaderValues.ACCEPT_JSON;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class SseServerTest extends SseBaseTest {

    private final Http1Client client;

    SseServerTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/sseString1", SseServerTest::sseString1);
        rules.get("/sseString2", SseServerTest::sseString2);
        rules.get("/sseJson1", SseServerTest::sseJson1);
        rules.get("/sseJson2", SseServerTest::sseJson2);
        rules.get("/sseMixed", SseServerTest::sseMixed);
        rules.get("/sseIdComment", SseServerTest::sseIdComment);
    }

    @Test
    void testSseString1() {
        testSse("/sseString1", "data:hello\n\ndata:world\n\n");
    }

    @Test
    void testSseString2() {
        testSse("/sseString2", "data:1\n\ndata:2\n\ndata:3\n\n");
    }

    @Test
    void testSseJson1() {
        testSse("/sseJson1", "data:{\"hello\":\"world\"}\n\n");
    }

    @Test
    void testSseJson2() {
        testSse("/sseJson2", "data:{\"hello\":\"world\"}\n\n");
    }

    @Test
    void testSseMixed() {
        testSse("/sseMixed",
                "data:hello\n\ndata:world\n\n" +
                        "data:{\"hello\":\"world\"}\n\n" +
                        "data:{\"hello\":\"world\"}\n\n");
    }

    @Test
    void testIdComment() {
        testSse("/sseIdComment", ":This is a comment\nid:1\ndata:hello\n\n");
    }

    @Test
    void testWrongAcceptType() {
        try (Http1ClientResponse response = client.get("/sseString1").header(ACCEPT_JSON).request()) {
            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
        }
    }

    private void testSse(String path, String result) {
        try (Http1ClientResponse response = client.get(path).header(ACCEPT_EVENT_STREAM).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(result));
        }
    }
}
