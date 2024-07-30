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
package io.helidon.docs.se;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.sse.SseEvent;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.sse.SseSource;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.sse.SseSink;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import static io.helidon.http.HeaderValues.ACCEPT_EVENT_STREAM;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("ALL")
class SseSnippets {

    void snippet_1(ServerRequest req, ServerResponse res) {
        // tag::snippet_1[]
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create("hello"))
                    .emit(SseEvent.create("world"));
        }
        // end::snippet_1[]
    }

    void snippet_2(ServerRequest req, ServerResponse res) {
        // tag::snippet_2[]
        JsonObject json = Json.createObjectBuilder()
                .add("hello", "world")
                .build();
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create(json));
        }
        // end::snippet_2[]
    }

    // tag::snippet_3[]
    class HelloWorld {

        private String hello;

        public String getHello() {
            return hello;
        }

        public void setHello(String hello) {
            this.hello = hello;
        }
    }

    void handle(ServerRequest req, ServerResponse res) {
        HelloWorld json = new HelloWorld();
        json.setHello("world");
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create(json));
        }
    }
    // end::snippet_3[]

    void snippet_4(Http1Client client) {
        // tag::snippet_4[]
        try (Http1ClientResponse r = client.get("/sseJson")
                .header(ACCEPT_EVENT_STREAM)
                .request()) {
            CountDownLatch latch = new CountDownLatch(1);
            r.source(SseSource.TYPE, event -> {
                // ...
                latch.countDown();
            });
        }
        // end::snippet_4[]
    }

    void snippet_5(Http1Client client) throws InterruptedException {
        // tag::snippet_5[]
        try (Http1ClientResponse r = client.get("/sseString")
                .header(ACCEPT_EVENT_STREAM)
                .request()) {
            CountDownLatch latch = new CountDownLatch(1);
            r.source(SseSource.TYPE, new SseSource() {
                @Override
                public void onEvent(SseEvent event) {
                    // ...
                }

                @Override
                public void onClose() {
                    latch.countDown();
                }
            });
            assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
            // end::snippet_5[]
        }
    }

    void snippet_6(Http1Client client) {
        // tag::snippet_6[]
        try (Http1ClientResponse r = client.get("/sseJson")
                .header(ACCEPT_EVENT_STREAM)
                .request()) {
            CountDownLatch latch = new CountDownLatch(1);
            r.source(SseSource.TYPE, event -> {
                HelloWorld json = event.data(HelloWorld.class, MediaTypes.APPLICATION_JSON);
                // ...
                latch.countDown();
            });
        }
        // end::snippet_6[]
    }
}
