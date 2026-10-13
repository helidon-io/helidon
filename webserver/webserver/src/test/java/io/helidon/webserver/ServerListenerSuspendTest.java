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

package io.helidon.webserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.configurable.AllowList;
import io.helidon.http.HttpTransportObserver;
import io.helidon.webserver.spi.ServerFeature;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerListenerSuspendTest {
    private static final int SOCKET_TIMEOUT_MILLIS = 5000;
    private static final int NO_ADMISSION_MILLIS = 1000;
    private static final String PROXY_HEADER = "PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n";

    @Test
    void failedProxyProtocolSetupReleasesConnectionLimit() throws Exception {
        WebServer server = WebServer.builder()
                .port(0)
                .proxyProtocol(it -> it.trustedProxies(AllowList.builder().allowAll(true).build()))
                .maxTcpConnections(1)
                .routing(routing -> routing.get("/", (req, res) -> res.send("ok")))
                .build()
                .start();

        try (var _ = new ServerLifecycle(server)) {
            try (Socket malformed = new Socket()) {
                connect(malformed, server);
                malformed.getOutputStream().write("PROXY \r\n".getBytes(StandardCharsets.US_ASCII));
                malformed.getOutputStream().flush();
                assertClosed(malformed, "malformed PROXY connection must close");
            }

            try (Socket valid = new Socket()) {
                connect(valid, server);
                sendRequest(valid, PROXY_HEADER, "close");
                assertResponse(valid, "request after malformed PROXY setup must recover the connection permit");
            }
        }
    }

    @Test
    void connectionLimitStopsAdmissionUntilConnectionCloses() throws Exception {
        WebServer server = WebServer.builder()
                .port(0)
                .maxConnections(1)
                .routing(routing -> routing.get("/", (req, res) -> res.send("ok")))
                .build()
                .start();

        try (var _ = new ServerLifecycle(server);
             Socket admitted = new Socket();
             Socket queued = new Socket()) {
            connect(admitted, server);
            sendRequest(admitted, "", "keep-alive");
            assertResponse(admitted, "first connection must be admitted before testing the limit");

            connect(queued, server);
            sendRequest(queued, "", "close");
            // TCP connect can succeed in the OS backlog without admission to the application.
            queued.setSoTimeout(NO_ADMISSION_MILLIS);
            assertThrows(SocketTimeoutException.class, () -> queued.getInputStream().read(),
                         "queued request must not receive a response while the only connection slot is occupied");

            // The admitted connection must still work while a new connection is waiting.
            sendRequest(admitted, "", "keep-alive");
            assertResponse(admitted, "connection limit must not stop requests on an admitted connection");
            admitted.close();
            assertResponse(queued, "queued request must progress after the admitted connection closes");
        }
    }

    @Test
    void suspendStopsListenerWaitingOnConnectionLimitDuringCheckpoint() throws Exception {
        var admissions = new ConnectionAdmissions();
        LoomServer server = (LoomServer) WebServer.builder()
                .port(0)
                .addFeature(admissions)
                .proxyProtocol(it -> it.trustedProxies(AllowList.builder().allowAll(true).build()))
                .maxConnections(1)
                .routing(routing -> routing.get("/", (req, res) -> res.send("ok")))
                .build()
                .start();

        try (var lifecycle = new ServerLifecycle(server)) {
            try (Socket stalled = new Socket(); Socket queued = new Socket()) {
                connect(stalled, server);
                stalled.getOutputStream().write("PROXY ".getBytes(StandardCharsets.US_ASCII));
                stalled.getOutputStream().flush();
                assertThat("incomplete PROXY connection must be accepted before testing checkpoint suspension",
                           admissions.opened.poll(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(true));

                connect(queued, server);
                sendRequest(queued, PROXY_HEADER, "close");
                assertThat("listener must not admit the queued connection while PROXY setup holds the only slot",
                           admissions.opened.poll(NO_ADMISSION_MILLIS, TimeUnit.MILLISECONDS), nullValue());

                lifecycle.run(server::suspend, "checkpoint suspension while the connection limit is reached");
                assertClosed(stalled, "checkpoint suspension must close the incomplete PROXY connection");
            }
            lifecycle.run(server::resume, "resume after checkpoint suspension");
            try (Socket valid = new Socket()) {
                connect(valid, server);
                sendRequest(valid, PROXY_HEADER, "close");
                assertResponse(valid, "request after checkpoint suspension must recover the connection permit");
            }
        }
    }

    @Test
    void suspendResumeReleasesConnectionLimitPermitHeldByAccept() throws Exception {
        LoomServer server = (LoomServer) WebServer.builder()
                .port(0)
                .maxConnections(1)
                .routing(routing -> routing.get("/", (req, res) -> res.send("ok")))
                .build()
                .start();

        try (var lifecycle = new ServerLifecycle(server)) {
            // Exercise idle suspension repeatedly without depending on where or how the listener parks.
            for (int cycle = 1; cycle <= 5; cycle++) {
                try (Socket valid = new Socket()) {
                    connect(valid, server);
                    sendRequest(valid, "", "close");
                    assertResponse(valid, "request before idle suspension cycle " + cycle);
                    assertThat("connection must close before idle suspension cycle " + cycle,
                               valid.getInputStream().read(), is(-1));
                }
                lifecycle.run(server::suspend, "idle suspension cycle " + cycle);
                lifecycle.run(server::resume, "idle resume cycle " + cycle);
                try (Socket valid = new Socket()) {
                    connect(valid, server);
                    sendRequest(valid, "", "close");
                    assertResponse(valid, "request after idle resume cycle " + cycle + " must recover the connection permit");
                }
            }
        }
    }

    private static void connect(Socket socket, WebServer server) throws IOException {
        socket.connect(new InetSocketAddress("127.0.0.1", server.port()), SOCKET_TIMEOUT_MILLIS);
        socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
    }

    private static void sendRequest(Socket socket, String prefix, String connection) throws IOException {
        socket.getOutputStream().write((prefix + "GET / HTTP/1.1\r\n"
                                               + "Host: localhost\r\n"
                                               + "Connection: " + connection + "\r\n\r\n")
                                              .getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static void assertClosed(Socket socket, String message) {
        try {
            assertThat(message, socket.getInputStream().read(), is(-1));
        } catch (SocketException _) {
            // A reset is also an acceptable close signal when setup is incomplete or invalid.
        } catch (IOException e) {
            throw new AssertionError(message, e);
        }
    }

    private static void assertResponse(Socket socket, String message) throws IOException {
        var responseBytes = new ByteArrayOutputStream();
        long deadline = System.nanoTime() + Duration.ofMillis(SOCKET_TIMEOUT_MILLIS).toNanos();
        String response = "";
        try {
            while (!response.endsWith("\r\n\r\nok") && responseBytes.size() < 8192) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new SocketTimeoutException("response deadline expired");
                }
                socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
                int read = socket.getInputStream().read();
                if (read == -1) {
                    break;
                }
                responseBytes.write(read);
                response = responseBytes.toString(StandardCharsets.US_ASCII);
            }
        } catch (IOException e) {
            throw new AssertionError(message + "; response received: " + response, e);
        } finally {
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
        }
        assertThat(message, response, startsWith("HTTP/1.1 200 OK\r\n"));
        assertThat(message, response, endsWith("\r\n\r\nok"));
    }

    private static final class ServerLifecycle implements AutoCloseable {
        private final WebServer server;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());

        private ServerLifecycle(WebServer server) {
            this.server = server;
        }

        @Override
        public void close() throws Exception {
            // Client sockets close first, so a timed-out suspend can finish before stop takes the lifecycle lock.
            try {
                run(server::stop, "server cleanup");
            } finally {
                executor.shutdownNow();
                assertThat("server lifecycle executor must terminate",
                           executor.awaitTermination(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(true));
            }
        }

        private void run(Runnable operation, String message) throws Exception {
            var future = executor.submit(operation);
            try {
                future.get(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError(message + " did not finish within " + SOCKET_TIMEOUT_MILLIS + " ms", e);
            } catch (ExecutionException e) {
                throw new AssertionError(message + " failed", e.getCause());
            }
        }
    }

    private static final class ConnectionAdmissions
            implements ServerFeature, HttpTransportObserverSupport.ObserverLifecycle, HttpTransportObserver {
        private final BlockingQueue<Boolean> opened = new LinkedBlockingQueue<>();

        @Override
        public String name() {
            return "test-connection-admissions";
        }

        @Override
        public String type() {
            return "test-connection-admissions";
        }

        @Override
        public void setup(ServerFeatureContext featureContext) {
            assertThat("transport observation must be registered",
                       HttpTransportObserverSupport.addObserver(featureContext, WebServer.DEFAULT_SOCKET_NAME, this), is(true));
        }

        @Override
        public HttpTransportObserver start() {
            return this;
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake) {
            opened.add(true);
            return ConnectionObservation.noop();
        }
    }
}
