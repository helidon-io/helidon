/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

import io.helidon.common.reactive.Multi;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import jakarta.ws.rs.sse.SseEventSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link ServerImpl} SSE.
 */
class ServerSseTest {
    private static Client client;

    private final CompletableFuture<Void> connClosedFuture = new CompletableFuture<>();
    private final CompletableFuture<Void> multiTestFuture = new CompletableFuture<>();
    private final CompletableFuture<Void> retryAfterFuture = new CompletableFuture<>();

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
        Server server = Server.builder()
                .addApplication("/", new TestApplication1())
                .port(0)
                .build();
        server.start();
        try {
            innerTest("test1", connClosedFuture, 4, server.port());
            // Give some time to close
            TimeUnit.SECONDS.sleep(5);
            assertThat(TestResource1.closed, is(true));
        } finally {
            server.stop();
        }
    }

    @Test // succeeds
    void testSseMulti() throws Exception {
        Server server = Server.builder()
                .addApplication("/", new TestApplication1())
                .port(0)
                .build();
        server.start();
        try {
            innerTest("test2", multiTestFuture, 4, server.port());
        } finally {
            server.stop();
        }
    }

    @Test
    void testSseSingleEvent() throws Exception {
        Server server = Server.builder()
                .addApplication("/", new TestApplication1())
                .port(0)
                .build();
        server.start();
        try {
            innerTest("test3", null, 1, server.port());
        } finally {
            server.stop();
        }
    }

    @Test
    void testRetryAfter() throws Exception {
        Server server = Server.builder()
                .addApplication("/", new TestApplication1())
                .port(0)
                .build();
        server.start();
        try {
            WebTarget target = client.target("http://localhost:" + server.port())
                    .path("test4").path("sse");
            SseEventSource sseEventSource = SseEventSource.target(target).build();
            sseEventSource.register(event -> event.readData(String.class));
            sseEventSource.open();
            retryAfterFuture.get(5000, TimeUnit.MILLISECONDS);
            assertThat(sseEventSource.close(5, TimeUnit.SECONDS), is(true));
        } finally {
            server.stop();
        }
    }

    private void innerTest(String endpoint, CompletableFuture<Void> future, int eventNum, int port) {
        // Set up SSE event source
        WebTarget target = client.target("http://localhost:" + port).path(endpoint).path("sse");
        SseEventSource sseEventSource = SseEventSource.target(target).build();
        CountDownLatch count = new CountDownLatch(eventNum);
        sseEventSource.register(event -> {
                try {
                    event.readData(String.class);
                } finally {
                    count.countDown();
                }
            },
            exception -> {
                count.countDown();
            }
        );

        // Open SSE source for a few millis and then close it
        assertThat(sseEventSource.isOpen(), is(false));
        try {
            sseEventSource.open(); // hangs indefinitely for test3?
        } catch (IllegalStateException e) {
            fail("sseEventSource.open() should have worked", e);
        }
        try {
                assertThat(count.await(250, TimeUnit.MILLISECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Await method should not have timed out", e);
        } finally {
            assertThat(sseEventSource.close(5, TimeUnit.SECONDS), is(true));
        }
        assertThat(count.getCount(), is(0L));

        // Wait for server to detect connection closed
        if (future != null) {
            try {
                future.get(2000, TimeUnit.MILLISECONDS);
            } catch (RuntimeException | Error e) {
                fail("Closing of SSE connection not detected!", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Closing of SSE connection not detected!", e);
            } catch (ExecutionException | TimeoutException e) {
                fail("Closing of SSE connection not detected!", e);
            }
        }
    }

    private final class TestApplication1 extends Application {
        @Override
        public Set<Object> getSingletons() {
            return Set.of(new TestResource1(), new TestResource2(), new TestResource3(), new TestResource4());
        }
    }

    @Path("/test1")
    public final class TestResource1 {
        private static volatile boolean closed;
        @GET
        @Path("sse")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void listenToEvents(@Context SseEventSink eventSink, @Context Sse sse) {
            while (true) {
                closed = eventSink.isClosed();
                try {
                    eventSink.send(sse.newEvent("hello")).thenAccept(t -> {
                        if (t != null) {
                            System.out.println(t);
                            connClosedFuture.complete(null);
                        }
                    });
                    TimeUnit.MILLISECONDS.sleep(5);
                } catch (RuntimeException | Error e) {
                    //https://github.com/oracle/helidon/issues/1290
                    connClosedFuture.complete(null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // falls through
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

    @Path("/test3")
    public final class TestResource3 {
        @GET
        @Path("sse")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void listenToEvents(@Context SseEventSink eventSink, @Context Sse sse) {
            eventSink.send(sse.newEvent("hello"))
                .exceptionally(e -> fail(e))
                .thenRun(() -> {
                    try {
                        eventSink.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }); // critical
        }
    }

    @Path("/test4")
    public final class TestResource4 {
        private static final int TIME_SECONDS = 2;
        private boolean fail = true;

        @GET
        @Path("sse")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void listenToEvents(@Context SseEventSink eventSink, @Context Sse sse) {
            if (fail) {
                fail = false;
                throw new WebApplicationException(Response.status(503)
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(TIME_SECONDS)).build());
            } else {
                eventSink.send(sse.newEvent("success")).thenAccept(i -> retryAfterFuture.complete(null));
            }
        }
    }
}
