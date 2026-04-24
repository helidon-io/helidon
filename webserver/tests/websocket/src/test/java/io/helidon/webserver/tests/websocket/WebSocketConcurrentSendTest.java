/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

@ServerTest
class WebSocketConcurrentSendTest {
    private static final int SENDERS = 2;
    private static final int SEND_ITERATIONS = 200;
    private static final int PAYLOAD_SIZE = 32 * 1024;
    private static final long TIMEOUT_SECONDS = 30;
    private static final String FIRST_PAYLOAD = "1" + "a".repeat(PAYLOAD_SIZE - 1);
    private static final String SECOND_PAYLOAD = "2" + "b".repeat(PAYLOAD_SIZE - 1);
    private static final ConcurrentSendService SERVICE = new ConcurrentSendService();

    private final int port;
    private final HttpClient client;

    WebSocketConcurrentSendTest(WebServer server) {
        this.port = server.port();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpRoute
    static void router(Router.RouterBuilder<?> router) {
        router.addRouting(WsRouting.builder().endpoint("/race", SERVICE));
    }

    @Test
    void testConcurrentServerSends() throws Exception {
        SERVICE.reset();

        ClientListener listener = new ClientListener();
        client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/race"), listener)
                .get(5, TimeUnit.SECONDS);

        boolean completed = SERVICE.awaitCompletion();
        assertThat("timed out waiting for server senders to finish", completed, is(true));

        ClientResult result = listener.result();
        assertThat("server-side sender failed: " + SERVICE.failure(), SERVICE.failure(), is(nullValue()));
        assertThat("unexpected client payload: " + result.unexpectedMessage(), result.unexpectedMessage(), is(nullValue()));
        assertThat(result.closeCode(), is(WsCloseCodes.NORMAL_CLOSE));
        assertThat(result.closeReason(), is("done"));
        assertThat(result.receivedMessages(), is(SENDERS * SEND_ITERATIONS));
    }

    private static final class ConcurrentSendService implements WsListener {
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicBoolean started = new AtomicBoolean();
        private volatile CountDownLatch completion = new CountDownLatch(1);

        void reset() {
            failure.set(null);
            started.set(false);
            completion = new CountDownLatch(1);
        }

        Throwable failure() {
            return failure.get();
        }

        boolean awaitCompletion() throws InterruptedException {
            return completion.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        @Override
        public void onOpen(WsSession session) {
            if (!started.compareAndSet(false, true)) {
                failure.compareAndSet(null, new IllegalStateException("Concurrent send test service reused unexpectedly"));
                completion.countDown();
                return;
            }

            CountDownLatch ready = new CountDownLatch(SENDERS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(SENDERS);
            AtomicBoolean stop = new AtomicBoolean();

            startSender("ws-send-1", session, FIRST_PAYLOAD, ready, start, done, stop);
            startSender("ws-send-2", session, SECOND_PAYLOAD, ready, start, done, stop);

            Thread controller = new Thread(() -> finishRun(session, ready, start, done, stop), "ws-send-controller");
            controller.setDaemon(true);
            controller.start();
        }

        @Override
        public void onError(WsSession session, Throwable t) {
            failure.compareAndSet(null, t);
        }

        private void startSender(String name,
                                 WsSession session,
                                 String payload,
                                 CountDownLatch ready,
                                 CountDownLatch start,
                                 CountDownLatch done,
                                 AtomicBoolean stop) {
            Thread sender = new Thread(() -> {
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new TimeoutException("Timed out waiting for concurrent send start");
                    }
                    for (int i = 0; i < SEND_ITERATIONS && !stop.get(); i++) {
                        session.send(payload, true);
                    }
                } catch (Throwable t) {
                    stop.set(true);
                    failure.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            }, name);
            sender.setDaemon(true);
            sender.start();
        }

        private void finishRun(WsSession session,
                               CountDownLatch ready,
                               CountDownLatch start,
                               CountDownLatch done,
                               AtomicBoolean stop) {
            try {
                if (!ready.await(10, TimeUnit.SECONDS)) {
                    throw new TimeoutException("Timed out waiting for sender startup");
                }
                start.countDown();
                if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new TimeoutException("Timed out waiting for concurrent send completion");
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                stop.set(true);
                try {
                    session.close(WsCloseCodes.NORMAL_CLOSE, "done");
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
                completion.countDown();
            }
        }
    }

    private static final class ClientListener implements WebSocket.Listener {
        private final StringBuilder buffered = new StringBuilder();
        private final CompletableFuture<ClientResult> result = new CompletableFuture<>();
        private int receivedMessages;
        private String unexpectedMessage;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffered.append(data);
            if (last) {
                String message = buffered.toString();
                buffered.setLength(0);
                receivedMessages++;
                if (!FIRST_PAYLOAD.equals(message) && !SECOND_PAYLOAD.equals(message) && unexpectedMessage == null) {
                    unexpectedMessage = message;
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            result.complete(new ClientResult(statusCode, reason, receivedMessages, unexpectedMessage));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            result.completeExceptionally(error);
        }

        ClientResult result() throws ExecutionException, InterruptedException, TimeoutException {
            return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private record ClientResult(int closeCode, String closeReason, int receivedMessages, String unexpectedMessage) {
    }
}
