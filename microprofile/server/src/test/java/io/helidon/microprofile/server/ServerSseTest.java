/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.server;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import javax.ws.rs.sse.SseEventSource;

import io.helidon.common.reactive.Multi;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link ServerImpl} SSE.
 */
class ServerSseTest {
    private static Client client;

    private CompletableFuture<Void> connClosedFuture = new CompletableFuture<>();
    private CompletableFuture<Void> multiTestFuture = new CompletableFuture<>();

    @BeforeAll
    static void initClass() {
        client = ClientBuilder.newClient();
    }

    @AfterAll
    static void destroyClass() {
        client.close();
    }

    @Test
    void testSse() throws Exception {
        innerTest("test1", connClosedFuture);
    }

    @Test
    void testSseMulti() throws Exception {
        innerTest("test2", multiTestFuture);
    }

    private void innerTest(String endpoint, CompletableFuture<Void> future) throws InterruptedException {
        Server server = Server.builder()
                .addApplication("/", new TestApplication1())
                .build();
        server.start();

        try {
            // Set up SSE event source
            WebTarget target = client.target("http://localhost:" + server.port()).path(endpoint).path("sse");
            SseEventSource sseEventSource = SseEventSource.target(target).build();
            CountDownLatch count = new CountDownLatch(4);
            sseEventSource.register(event -> {
                event.readData(String.class);
                count.countDown();
            });

            // Open SSE source for a few millis and then close it
            sseEventSource.open();
            assertThat("Await method should have not timeout", count.await(250, TimeUnit.MILLISECONDS));
            sseEventSource.close();

            // Wait for server to detect connection closed
            try {
                future.get(2000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                fail("Closing of SSE connection not detected!");
            }
        } finally {
            server.stop();
        }
    }

    private final class TestApplication1 extends Application {
        @Override
        public Set<Object> getSingletons() {
            return Set.of(new TestResource1(), new TestResource2());
        }
    }

    @Path("/test1")
    public final class TestResource1 {
        @GET
        @Path("sse")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void listenToEvents(@Context SseEventSink eventSink, @Context Sse sse) {
            while (true) {
                try {
                    eventSink.send(sse.newEvent("hello")).thenAccept(t -> {
                        if (t != null) {
                            System.out.println(t);
                            connClosedFuture.complete(null);
                        }
                    });
                    TimeUnit.MILLISECONDS.sleep(5);
                } catch (InterruptedException e) {
                    // falls through
                } catch (IllegalStateException e) {
                    //https://github.com/oracle/helidon/issues/1290
                    connClosedFuture.complete(null);
                }
            }
        }
    }

    @Path("/test2")
    public final class TestResource2 {
        @GET
        @Path("sse")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void listenToEvents(@Context Flow.Subscriber<String> sub, @Context Sse sse) {
            Multi.interval(5, TimeUnit.MILLISECONDS, Executors.newScheduledThreadPool(1))
                    .map(String::valueOf)
                    .onCancel(() -> multiTestFuture.complete(null))
                    .subscribe(sub);
        }
    }
}
