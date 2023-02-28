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

import io.helidon.nima.sse.SseEvent;
import io.helidon.nima.sse.webserver.SseSink;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import jakarta.json.Json;
import jakarta.json.JsonObject;

class SseBaseTest {

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

    static void sseJson1(ServerRequest req, ServerResponse res) {
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

    static void sseJson2(ServerRequest req, ServerResponse res) {
        SseServerTest.HelloWorld json = new SseServerTest.HelloWorld();
        json.setHello("world");
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create(json));
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
}
