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

import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.sse.SseSink;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class SseBaseTest {

    private static final System.Logger LOGGER = System.getLogger(SseBaseTest.class.getName());
    private final WebServer webServer;

    SseBaseTest() {
        this.webServer = null;
    }

    SseBaseTest(WebServer webServer) {
        this.webServer = webServer;
    }

    protected WebServer webServer() {
        return webServer;
    }

    static void sseString1(ServerRequest req, ServerResponse res) {
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create("hello"))
                    .emit(SseEvent.create("world"));
        }
    }

    static void sseString2(ServerRequest req, ServerResponse res) throws InterruptedException {
        SseSink sseSink = res.sink(SseSink.TYPE);
        for (int i = 1; i <= 3; i++) {
            sseSink.emit(SseEvent.create(Integer.toString(i)));
            Thread.sleep(50);      // simulates messages over time
        }
        sseSink.close();
    }

    static void sseJson1(ServerRequest req, ServerResponse res, CountDownLatch latch) {
        JsonObject json = Json.createObjectBuilder()
                .add("hello", "world")
                .build();
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create(json));
            boolean notReceived = true;
            try {
                notReceived = latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (notReceived) {
                LOGGER.log(System.Logger.Level.ERROR, "Client did not receive the event in time");
            }
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

    static void sseJson2(ServerRequest req, ServerResponse res, CountDownLatch latch) {
        SseServerTest.HelloWorld json = new SseServerTest.HelloWorld();
        json.setHello("world");
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create(json));
            boolean notReceived = true;
            try {
                notReceived = latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (notReceived) {
                LOGGER.log(System.Logger.Level.ERROR, "Client did not receive the event in time");
            }
        }
    }

    static void sseMixed(ServerRequest req, ServerResponse res) {
        JsonObject jsonp = Json.createObjectBuilder()
                .add("hello", "world")
                .build();
        SseServerTest.HelloWorld jsonb = new SseServerTest.HelloWorld();
        jsonb.setHello("world");

        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create("hello"))
                    .emit(SseEvent.create("world"))
                    .emit(SseEvent.create(jsonp))
                    .emit(SseEvent.create(jsonb));
        }
    }

    static void sseIdComment(ServerRequest req, ServerResponse res) {
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            SseEvent event = SseEvent.builder()
                    .id("1")
                    .comment("This is a comment")
                    .data("hello")
                    .build();
            sseSink.emit(event);
        }
    }

    protected void testSse(String path, String... events) throws Exception {
        assert webServer != null;
        try (SimpleSseClient sseClient = SimpleSseClient.create(webServer.port(), path)) {
            for (String e : events) {
                assertThat(sseClient.nextEvent(), is(e));
            }
            assertThat(sseClient.nextEvent(), is(nullValue()));
        }
    }
}
