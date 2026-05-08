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

import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class ServerListenerSuspendTest {
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
            ServerListener listener = server.listener(WebServer.DEFAULT_SOCKET_NAME);
            Thread listenerThread = listener.serverThreads();
            stalledConnection = new Socket("127.0.0.1", server.port());
            queuedConnection = new Socket("127.0.0.1", server.port());

            waitFor(Duration.ofSeconds(5),
                    () -> listenerThread.getState() == Thread.State.TIMED_WAITING,
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
