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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.nima.sse.SseEvent;
import io.helidon.nima.sse.webclient.SseSource;
import io.helidon.nima.sse.webserver.SseSink;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.HeaderValues.ACCEPT_EVENT_STREAM;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class SseClientTest {

    private final Http1Client client;

    SseClientTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/sseString1", SseClientTest::sseString1);
    }

    private static void sseString1(ServerRequest req, ServerResponse res) {
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.builder()
                            .comment("first line")
                            .name("first")
                            .data("hello")
                            .build())
                    .emit(SseEvent.builder()
                            .name("second")
                            .data("world")
                            .build());
        }
    }

    @Test
    void testSseString1() throws InterruptedException {
        try (Http1ClientResponse r = client.get("/sseString1").header(ACCEPT_EVENT_STREAM).request()) {
            CountDownLatch latch = new CountDownLatch(1);
            r.source(SseSource.TYPE, new SseSource() {
                private int state = 0;

                @Override
                public void onEvent(SseEvent event) {
                    switch (state) {
                        case 0 -> {
                            assertThat(event.comment().isPresent(), is(true));
                            assertThat(event.comment().get(), is("first line"));
                            assertThat(event.name().isPresent(), is(true));
                            assertThat(event.name().get(), is("first"));
                            assertThat(event.data(), is("hello"));
                        }
                        case 1 -> {
                            assertThat(event.name().isPresent(), is(true));
                            assertThat(event.name().get(), is("second"));
                            assertThat(event.data(), is("world"));
                        }
                    }
                    state++;
                }

                @Override
                public void onClose() {
                    latch.countDown();
                }
            });
            assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
        }
    }

    @Test
    void testFunctionalInterface() throws InterruptedException {
        try (Http1ClientResponse r = client.get("/sseString1").header(ACCEPT_EVENT_STREAM).request()) {
            CountDownLatch latch = new CountDownLatch(2);
            r.source(SseSource.TYPE, event -> {
                assertThat(event.name().isPresent(), is(true));
                assertThat(event.data(), is(notNullValue()));
                latch.countDown();
            });
            assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
        }
    }
}
