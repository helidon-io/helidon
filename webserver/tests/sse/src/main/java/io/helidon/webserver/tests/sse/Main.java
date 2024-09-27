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

package io.helidon.webserver.tests.sse;

import java.util.stream.IntStream;

import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.sse.SseSink;

/**
 * Simple SSE server that can be used to manually test interop with other
 * clients such as Postman.
 */
public class Main {

    static void sse(ServerRequest req, ServerResponse res) {
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            IntStream.range(0, 1000).forEach(i -> sseSink.emit(SseEvent.create("hello world " + i)));
        }
    }

    public static void main(String[] args) {
        WebServer.builder()
                .port(8080)
                .routing(HttpRouting.builder().get("/sse", Main::sse))
                .build()
                .start();
    }
}
