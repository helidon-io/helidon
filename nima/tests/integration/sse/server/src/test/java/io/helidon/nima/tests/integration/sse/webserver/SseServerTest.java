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

package io.helidon.nima.tests.integration.sse.webserver;

import io.helidon.common.http.Http;
import io.helidon.nima.sse.webserver.SseEvent;
import io.helidon.nima.sse.webserver.SseSink;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.HeaderValues.ACCEPT_EVENT_STREAM;
import static io.helidon.common.http.Http.HeaderValues.ACCEPT_JSON;
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
        rules.get("/sseString1", SseServerTest::sseString1);
        rules.get("/sseString2", SseServerTest::sseString2);
        rules.get("/sseJson1", SseServerTest::sseJson1);
        rules.get("/sseJson2", SseServerTest::sseJson2);
        rules.get("/sseMixed", SseServerTest::sseMixed);
        rules.get("/sseIdComment", SseServerTest::sseIdComment);
    }

    private static void sseString1(ServerRequest req, ServerResponse res) {
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create("hello"))
                    .emit(SseEvent.create("world"));
        }
    }

    private static void sseString2(ServerRequest req, ServerResponse res) throws InterruptedException {
        SseSink sseSink = res.sink(SseSink.TYPE);
        for (int i = 1; i <= 3; i++) {
            sseSink.emit(SseEvent.create(Integer.toString(i)));
            Thread.sleep(50);      // simulates messages over time
        }
        sseSink.close();
    }

    private static void sseJson1(ServerRequest req, ServerResponse res) {
        JsonObject json = Json.createObjectBuilder()
                .add("hello", "world")
                .build();
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create(json));
        }
    }

    public static class HelloWorld {

        private String hello;

        public String getHello() {
            return hello;
        }

        public void setHello(String hello) {
            this.hello = hello;
        }
    }

    private static void sseJson2(ServerRequest req, ServerResponse res) {
        HelloWorld json = new HelloWorld();
        json.setHello("world");
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create(json));
        }
    }

    private static void sseMixed(ServerRequest req, ServerResponse res) {
        JsonObject jsonp = Json.createObjectBuilder()
                .add("hello", "world")
                .build();
        HelloWorld jsonb = new HelloWorld();
        jsonb.setHello("world");

        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create("hello"))
                    .emit(SseEvent.create("world"))
                    .emit(SseEvent.create(jsonp))
                    .emit(SseEvent.create(jsonb));
        }
    }

    private static void sseIdComment(ServerRequest req, ServerResponse res) {
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            SseEvent event = SseEvent.builder()
                    .id("1")
                    .comment("This is a comment")
                    .data("hello")
                    .build();
            sseSink.emit(event);
        }
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
            assertThat(response.status(), is(Http.Status.NOT_ACCEPTABLE_406));
        }
    }

    private void testSse(String path, String result) {
        try (Http1ClientResponse response = client.get(path).header(ACCEPT_EVENT_STREAM).request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.as(String.class), is(result));
        }
    }
}
