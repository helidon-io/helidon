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
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class ServerListenerSuspendTest {
    private static final int SOCKET_TIMEOUT_MILLIS = 5000;

    @Test
    void failedProxyProtocolSetupReleasesConnectionLimit() throws Exception {
        WebServer server = WebServer.builder()
                .port(0)
                .enableProxyProtocol(true)
                .maxTcpConnections(1)
                .routing(routing -> routing.get("/", (req, res) -> res.send("ok")))
                .build()
                .start();

        try {
            try (Socket malformed = new Socket("127.0.0.1", server.port())) {
                malformed.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                malformed.getOutputStream().write("PROXY \r\n".getBytes(StandardCharsets.US_ASCII));
                malformed.getOutputStream().flush();
                try {
                    assertThat(malformed.getInputStream().read(), is(-1));
                } catch (SocketException e) {
                    // Connection reset is also an acceptable close signal for the malformed socket.
                }
            }

            String response = "";
            try (Socket valid = new Socket("127.0.0.1", server.port())) {
                valid.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                valid.getOutputStream()
                        .write(("PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n"
                                + "GET / HTTP/1.1\r\n"
                                + "Host: localhost\r\n"
                                + "Connection: close\r\n"
                                + "\r\n").getBytes(StandardCharsets.US_ASCII));
                valid.getOutputStream().flush();
                ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[256];
                do {
                    int read = valid.getInputStream().read(buffer);
                    if (read == -1) {
                        break;
                    }
                    responseBytes.write(buffer, 0, read);
                    response = responseBytes.toString(StandardCharsets.US_ASCII);
                } while (!response.contains("ok"));
            }

            assertThat(response, containsString("200 OK"));
            assertThat(response, containsString("ok"));
        } finally {
            server.stop();
        }
    }

    @Test
    void suspendStopsListenerWaitingOnConnectionLimitDuringCheckpoint() throws Exception {
        LoomServer server = (LoomServer) WebServer.builder()
                .port(0)
                .enableProxyProtocol(true)
                .maxTcpConnections(1)
                .build()
                .start();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Socket stalledConnection = null;
        Socket queuedConnection = null;
        boolean suspended = false;

        try {
            stalledConnection = new Socket("127.0.0.1", server.port());
            queuedConnection = new Socket("127.0.0.1", server.port());

            waitFor(Duration.ofSeconds(5),
                    () -> listenerThreadState(WebServer.DEFAULT_SOCKET_NAME) == Thread.State.TIMED_WAITING,
                    "listener did not block on the TCP connection limit");

            Future<?> suspendFuture = executor.submit(server::suspend);
            try {
                suspendFuture.get(2, TimeUnit.SECONDS);
                suspended = true;
            } catch (TimeoutException e) {
                closeQuietly(stalledConnection);
                closeQuietly(queuedConnection);
                awaitSuspendForCleanup(suspendFuture);
                suspended = true;
                fail("Suspend blocked while the listener was waiting for a TCP connection slot", e);
            }
        } finally {
            closeQuietly(stalledConnection);
            closeQuietly(queuedConnection);
            if (suspended) {
                server.resume();
            }
            server.stop();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void awaitSuspendForCleanup(Future<?> suspendFuture) throws Exception {
        try {
            suspendFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("Suspend still did not finish after releasing the stalled connection", e);
        } catch (ExecutionException e) {
            throw unwrapExecutionException(e);
        }
    }

    private static Exception unwrapExecutionException(ExecutionException e) {
        if (e.getCause() instanceof Exception exception) {
            return exception;
        }
        if (e.getCause() instanceof Error error) {
            throw error;
        }
        return e;
    }

    private static Thread.State listenerThreadState(String socketName) {
        String threadName = "server-" + socketName + "-listener";
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (threadName.equals(thread.getName())) {
                return thread.getState();
            }
        }
        return null;
    }

    private static void waitFor(Duration timeout, BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        fail(message);
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}
