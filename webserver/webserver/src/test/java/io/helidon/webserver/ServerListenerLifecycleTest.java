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

import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.task.InterruptableTask;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.spi.ServerConnection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerListenerLifecycleTest {
    @Test
    void webServerStartFailureThrowsAndRunsCleanup() throws Exception {
        LifecycleService service = new LifecycleService("default");
        try (ServerSocket blocker = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            WebServer server = WebServer.builder()
                    .shutdownHook(false)
                    .address(InetAddress.getLoopbackAddress())
                    .port(blocker.getLocalPort())
                    .routing(routing -> routing.register(service))
                    .build();

            IllegalStateException failure = assertThrows(IllegalStateException.class, server::start);

            assertThat(server.isRunning(), is(false));
            assertThat(failure.getCause(), instanceOf(UncheckedIOException.class));
            assertThat(service.beforeStarts(), is(1));
            assertThat(service.afterStops(), is(1));
        }
    }

    @Test
    void webServerBeforeStartFailureThrowsAndRunsCleanup() {
        LifecycleService service = new LifecycleService("default", true, false, false);
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .routing(routing -> routing.register(service))
                .build();

        RuntimeException failure = assertThrows(RuntimeException.class, server::start);

        assertThat(server.isRunning(), is(false));
        assertThat(service.beforeStarts(), is(1));
        assertThat(service.afterStops(), is(0));
        assertThat(containsMessage(failure, "beforeStart failed default"), is(true));
    }

    @Test
    void interruptedStartWaitFailsStartupAndRunsCleanup() throws Exception {
        CountDownLatch beforeStartEntered = new CountDownLatch(2);
        CountDownLatch releaseBeforeStart = new CountDownLatch(1);
        CountDownLatch afterStopInvoked = new CountDownLatch(2);
        CountDownLatch beforeStartExited = new CountDownLatch(2);
        AtomicReference<Throwable> startFailure = new AtomicReference<>();
        AtomicBoolean startThreadInterrupted = new AtomicBoolean();
        AtomicBoolean blockedStartupExitedBeforeFailure = new AtomicBoolean();
        LifecycleService defaultService = new LifecycleService("default");
        BlockingBeforeStartService adminService = new BlockingBeforeStartService(beforeStartEntered,
                                                                                 releaseBeforeStart,
                                                                                 afterStopInvoked,
                                                                                 beforeStartExited);
        BlockingBeforeStartService monitorService = new BlockingBeforeStartService(beforeStartEntered,
                                                                                   releaseBeforeStart,
                                                                                   afterStopInvoked,
                                                                                   beforeStartExited);
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .routing(routing -> routing.register(defaultService))
                .putSocket("admin", listener -> listener
                        .address(InetAddress.getLoopbackAddress())
                        .port(0)
                        .routing(routing -> routing.register(adminService)))
                .putSocket("monitor", listener -> listener
                        .address(InetAddress.getLoopbackAddress())
                        .port(0)
                        .routing(routing -> routing.register(monitorService)))
                .build();

        Thread startThread = Thread.ofPlatform()
                .name("test-interrupted-start")
                .start(() -> {
                    try {
                        server.start();
                    } catch (RuntimeException | Error e) {
                        blockedStartupExitedBeforeFailure.set(beforeStartExited.getCount() == 0);
                        startFailure.set(e);
                        startThreadInterrupted.set(Thread.currentThread().isInterrupted());
                    }
                });

        try {
            assertThat(beforeStartEntered.await(5, TimeUnit.SECONDS), is(true));
            int defaultPort = awaitPort(server, WebServer.DEFAULT_SOCKET_NAME);

            startThread.interrupt();
            startThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(startThread.isAlive(), is(false));
            assertThat(startFailure.get(), notNullValue());
            assertThat(containsMessage(startFailure.get(), "Interrupted while waiting for listener"), is(true));
            assertThat(startThreadInterrupted.get(), is(true));
            assertThat(blockedStartupExitedBeforeFailure.get(), is(true));
            assertThat(server.isRunning(), is(false));
            assertThat(defaultService.afterStops(), is(1));
            assertThat(afterStopInvoked.getCount(), is(2L));
            assertPortCanBind(defaultPort);
        } finally {
            releaseBeforeStart.countDown();
            if (startThread.isAlive()) {
                startThread.interrupt();
            }
            stopUntilStopped(server);
        }
    }

    @Test
    void webServerStartFailureThrowsAndStopsAlreadyStartedListeners() throws Exception {
        CountDownLatch adminBeforeStartEntered = new CountDownLatch(1);
        CountDownLatch releaseAdminBeforeStart = new CountDownLatch(1);
        CountDownLatch adminAfterStopInvoked = new CountDownLatch(1);
        AtomicReference<Throwable> startFailure = new AtomicReference<>();
        LifecycleService defaultService = new LifecycleService("default");
        BlockingBeforeStartService adminService = new BlockingBeforeStartService(adminBeforeStartEntered,
                                                                                 releaseAdminBeforeStart,
                                                                                 adminAfterStopInvoked);
        try (ServerSocket blocker = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            WebServer server = WebServer.builder()
                    .shutdownHook(false)
                    .address(InetAddress.getLoopbackAddress())
                    .port(0)
                    .routing(routing -> routing.register(defaultService))
                    .putSocket("admin", listener -> listener
                            .address(InetAddress.getLoopbackAddress())
                            .port(blocker.getLocalPort())
                            .routing(routing -> routing.register(adminService)))
                    .build();

            Thread startThread = Thread.ofPlatform()
                    .name("test-partial-start-cleanup")
                    .start(() -> {
                        try {
                            server.start();
                        } catch (RuntimeException | Error e) {
                            startFailure.set(e);
                        }
            });

            try {
                assertThat(adminBeforeStartEntered.await(5, TimeUnit.SECONDS), is(true));
                int defaultPort = awaitPort(server, WebServer.DEFAULT_SOCKET_NAME);
                assertThat(defaultPort > 0, is(true));

                releaseAdminBeforeStart.countDown();
                startThread.join(TimeUnit.SECONDS.toMillis(5));

                assertThat(startThread.isAlive(), is(false));
                assertThat(startFailure.get(), instanceOf(IllegalStateException.class));
                assertThat(server.isRunning(), is(false));
                assertThat(containsMessage(startFailure.get(), "Failed to start server"), is(true));
                assertThat(defaultService.beforeStarts(), is(1));
                assertThat(defaultService.afterStops(), is(1));
                assertThat(adminAfterStopInvoked.await(5, TimeUnit.SECONDS), is(true));
                assertPortCanBind(defaultPort);
            } finally {
                releaseAdminBeforeStart.countDown();
                if (startThread.isAlive()) {
                    startThread.interrupt();
                }
                stopUntilStopped(server);
            }
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void unixSocketBindFailureDoesNotDeleteExistingPath(@TempDir Path tempDir) throws Exception {
        Path socketPath = tempDir.resolve("server.sock");
        Files.writeString(socketPath, "existing");

        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .bindAddress(UnixDomainSocketAddress.of(socketPath))
                .routing(routing -> routing.register(new LifecycleService("default")))
                .build();

        IllegalStateException failure = assertThrows(IllegalStateException.class, server::start);

        assertThat(containsMessage(failure, "Failed to start server"), is(true));
        assertThat(Files.readString(socketPath), is("existing"));
    }

    @Test
    void webServerAfterStartFailureThrowsAndRunsCleanup() {
        LifecycleService service = new LifecycleService("default", false, true);
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .routing(routing -> routing.register(service))
                .build();

        try {
            RuntimeException failure = assertThrows(RuntimeException.class, server::start);

            assertThat(server.isRunning(), is(false));
            assertThat(service.beforeStarts(), is(1));
            assertThat(service.afterStarts(), is(1));
            assertThat(service.afterStops(), is(1));
            assertThat(containsMessage(failure, "afterStart failed default"), is(true));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void webServerAfterStartFailureStopsAllListeners() throws Exception {
        AtomicInteger defaultPort = new AtomicInteger(-1);
        AtomicInteger adminPort = new AtomicInteger(-1);
        LifecycleService defaultService = new LifecycleService("default");
        LifecycleService adminService = new LifecycleService("admin", false, false, true, webServer -> {
            defaultPort.set(webServer.port(WebServer.DEFAULT_SOCKET_NAME));
            adminPort.set(webServer.port("admin"));
        });
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .routing(routing -> routing.register(defaultService))
                .putSocket("admin", listener -> listener
                        .address(InetAddress.getLoopbackAddress())
                        .port(0)
                        .routing(routing -> routing.register(adminService)))
                .build();

        try {
            RuntimeException failure = assertThrows(RuntimeException.class, server::start);

            assertThat(server.isRunning(), is(false));
            assertThat(adminService.afterStarts(), is(1));
            assertThat(defaultService.afterStops(), is(1));
            assertThat(adminService.afterStops(), is(1));
            assertThat(containsMessage(failure, "afterStart failed admin"), is(true));
            assertPortCanBind(defaultPort.get());
            assertPortCanBind(adminPort.get());
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void completionExceptionAfterStartPreservesCleanupFailure() {
        LifecycleService service = new LifecycleService("default", false, true, false, _ -> {
            throw new CompletionException(new IllegalStateException("afterStart wrapped default"));
        });
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .routing(routing -> routing.register(service))
                .build();

        RuntimeException failure = assertThrows(RuntimeException.class, server::start);

        assertThat(server.isRunning(), is(false));
        assertThat(service.afterStarts(), is(1));
        assertThat(service.afterStops(), is(1));
        assertThat(containsMessage(failure, "afterStart wrapped default"), is(true));
        assertThat(containsMessage(failure, "afterStop failed default"), is(true));
    }

    @Test
    void interruptedExecutorShutdownStillFinishesListenerCleanup() throws Exception {
        CountDownLatch sharedTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseSharedTask = new CountDownLatch(1);
        CountDownLatch sharedTaskInterrupted = new CountDownLatch(1);
        CountDownLatch sharedExecutorAwaitStarted = new CountDownLatch(1);
        CountDownLatch forceCloseInvoked = new CountDownLatch(1);
        AtomicReference<Throwable> stopFailure = new AtomicReference<>();
        LifecycleService service = new LifecycleService("default");

        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .shutdownGracePeriod(Duration.ofSeconds(30))
                .routing(routing -> routing.register(service))
                .build()
                .start();

        Future<?> sharedTask = null;
        Thread stopThread = null;
        try {
            ServerListener listener = server.listener(WebServer.DEFAULT_SOCKET_NAME);
            assertThat(listener, notNullValue());
            listener.activeConnection("test", new CloseTrackingConnection(forceCloseInvoked));
            listener.beforeSharedExecutorAwait(sharedExecutorAwaitStarted::countDown);
            sharedTask = listener.executor().submit(() -> {
                sharedTaskStarted.countDown();
                try {
                    releaseSharedTask.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    sharedTaskInterrupted.countDown();
                    throw new IllegalStateException("shared task interrupted", e);
                }
            });
            assertThat(sharedTaskStarted.await(5, TimeUnit.SECONDS), is(true));

            stopThread = Thread.ofPlatform()
                    .name("test-interrupted-stop")
                    .start(() -> {
                        try {
                            server.stop();
                        } catch (RuntimeException | Error e) {
                            stopFailure.set(e);
                        }
                    });

            assertThat(sharedExecutorAwaitStarted.await(5, TimeUnit.SECONDS), is(true));

            stopThread.interrupt();
            assertThat(forceCloseInvoked.await(5, TimeUnit.SECONDS), is(true));
            assertThat(sharedTaskInterrupted.await(5, TimeUnit.SECONDS), is(true));
            stopThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(stopThread.isAlive(), is(false));
            assertThat(stopFailure.get(), notNullValue());
            assertThat(containsMessage(stopFailure.get(), "Interrupted while shutting down listener executor"), is(true));
            assertThat(server.isRunning(), is(false));
            assertThat(service.afterStops(), is(1));
        } finally {
            releaseSharedTask.countDown();
            if (sharedTask != null) {
                sharedTask.cancel(true);
            }
            if (stopThread != null && stopThread.isAlive()) {
                stopThread.interrupt();
            }
            stopUntilStopped(server);
        }
    }

    @Test
    void interruptedReaderExecutorShutdownFailsStopAndForcesShutdown() throws Exception {
        CountDownLatch readerTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseReaderTask = new CountDownLatch(1);
        CountDownLatch readerTaskInterrupted = new CountDownLatch(1);
        CountDownLatch readerExecutorTerminateStarted = new CountDownLatch(1);
        AtomicReference<Throwable> stopFailure = new AtomicReference<>();
        LifecycleService service = new LifecycleService("default");

        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .shutdownGracePeriod(Duration.ofSeconds(30))
                .routing(routing -> routing.register(service))
                .build()
                .start();

        Future<?> readerTask = null;
        Thread stopThread = null;
        try {
            ServerListener listener = server.listener(WebServer.DEFAULT_SOCKET_NAME);
            assertThat(listener, notNullValue());
            listener.beforeReaderExecutorTerminate(readerExecutorTerminateStarted::countDown);
            readerTask = listener.readerTask(new BlockingReaderTask(readerTaskStarted,
                                                                    releaseReaderTask,
                                                                    readerTaskInterrupted));
            assertThat(readerTaskStarted.await(5, TimeUnit.SECONDS), is(true));

            stopThread = Thread.ofPlatform()
                    .name("test-interrupted-reader-stop")
                    .start(() -> {
                        try {
                            server.stop();
                        } catch (RuntimeException | Error e) {
                            stopFailure.set(e);
                        }
                    });

            assertThat(readerExecutorTerminateStarted.await(5, TimeUnit.SECONDS), is(true));

            stopThread.interrupt();
            assertThat(readerTaskInterrupted.await(5, TimeUnit.SECONDS), is(true));
            stopThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(stopThread.isAlive(), is(false));
            assertThat(stopFailure.get(), notNullValue());
            assertThat(containsMessage(stopFailure.get(), "Interrupted while shutting down listener reader executor"), is(true));
            assertThat(server.isRunning(), is(false));
            assertThat(service.afterStops(), is(1));
        } finally {
            releaseReaderTask.countDown();
            if (readerTask != null) {
                readerTask.cancel(true);
            }
            if (stopThread != null && stopThread.isAlive()) {
                stopThread.interrupt();
            }
            stopUntilStopped(server);
        }
    }

    @Test
    void executorShutdownTestHookFailureDoesNotSkipForcedShutdown() throws Exception {
        CountDownLatch sharedTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseSharedTask = new CountDownLatch(1);
        CountDownLatch sharedTaskInterrupted = new CountDownLatch(1);
        LifecycleService service = new LifecycleService("default");

        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .shutdownGracePeriod(Duration.ofMillis(1))
                .routing(routing -> routing.register(service))
                .build()
                .start();

        Future<?> sharedTask = null;
        try {
            ServerListener listener = server.listener(WebServer.DEFAULT_SOCKET_NAME);
            assertThat(listener, notNullValue());
            listener.beforeSharedExecutorAwait(() -> {
                throw new IllegalStateException("before await failed");
            });
            sharedTask = listener.executor().submit(() -> {
                sharedTaskStarted.countDown();
                try {
                    releaseSharedTask.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    sharedTaskInterrupted.countDown();
                    throw new IllegalStateException("shared task interrupted", e);
                }
            });
            assertThat(sharedTaskStarted.await(5, TimeUnit.SECONDS), is(true));

            RuntimeException failure = assertThrows(RuntimeException.class, server::stop);

            assertThat(containsMessage(failure, "before await failed"), is(true));
            assertThat(sharedTaskInterrupted.await(5, TimeUnit.SECONDS), is(true));
            assertThat(server.isRunning(), is(false));
            assertThat(service.afterStops(), is(1));
        } finally {
            releaseSharedTask.countDown();
            if (sharedTask != null) {
                sharedTask.cancel(true);
            }
            stopUntilStopped(server);
        }
    }

    @Test
    void activeConnectionCloseFailuresDoNotSkipRemainingConnections() {
        AtomicInteger closeCalls = new AtomicInteger();
        LifecycleService service = new LifecycleService("default");

        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .routing(routing -> routing.register(service))
                .build()
                .start();

        try {
            ServerListener listener = server.listener(WebServer.DEFAULT_SOCKET_NAME);
            assertThat(listener, notNullValue());
            listener.activeConnection("first", new ThrowingCloseConnection(closeCalls));
            listener.activeConnection("second", new ThrowingCloseConnection(closeCalls));

            RuntimeException failure = assertThrows(RuntimeException.class, server::stop);

            assertThat(containsMessage(failure, "connection close failed"), is(true));
            assertThat(closeCalls.get(), is(4));
            assertThat(service.afterStops(), is(1));
            assertThat(server.isRunning(), is(false));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void webServerStopFailureThrowsAfterStoppingAllListeners() {
        LifecycleService defaultService = new LifecycleService("default", true);
        LifecycleService adminService = new LifecycleService("admin", true);
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .routing(routing -> routing.register(defaultService))
                .putSocket("admin", listener -> listener.port(0)
                        .routing(routing -> routing.register(adminService)))
                .build()
                .start();

        try {
            RuntimeException failure = assertThrows(RuntimeException.class, server::stop);

            assertThat(server.isRunning(), is(false));
            assertThat(defaultService.afterStops(), is(1));
            assertThat(adminService.afterStops(), is(1));
            assertThat(containsMessage(failure, "afterStop failed default"), is(true));
            assertThat(containsMessage(failure, "afterStop failed admin"), is(true));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void webServerSuspendFailureStopsAllListenersAndPreservesCleanupFailure() {
        LifecycleService defaultService = new LifecycleService("default");
        LifecycleService adminService = new LifecycleService("admin", true);
        LifecycleService monitorService = new LifecycleService("monitor");
        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .routing(routing -> routing.register(defaultService))
                .putSocket("admin", listener -> listener.port(0)
                        .routing(routing -> routing.register(adminService)))
                .putSocket("monitor", listener -> listener.port(0)
                        .routing(routing -> routing.register(monitorService)))
                .build()
                .start();

        try {
            ServerListener listener = server.listener(WebServer.DEFAULT_SOCKET_NAME);
            assertThat(listener, notNullValue());
            listener.activeConnection("failing",
                                      new ThrowingCloseConnection(new AtomicInteger(), "suspend close failed"));

            RuntimeException failure = assertThrows(RuntimeException.class, server::suspend);

            assertThat(failure.getMessage(), is("suspend close failed"));
            assertThat(server.isRunning(), is(false));
            assertThat(suppressedContainsMessage(failure, "afterStop failed admin"), is(true));
            assertThat(defaultService.afterStops(), is(1));
            assertThat(adminService.afterStops(), is(1));
            assertThat(monitorService.afterStops(), is(1));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void webServerResumeFailureStopsAllListenersAndPreservesCleanupFailure(@TempDir Path tempDir) throws Exception {
        LifecycleService defaultService = new LifecycleService("default");
        LifecycleService adminService = new LifecycleService("admin");
        LifecycleService monitorService = new LifecycleService("monitor", true);
        Path socketPath = tempDir.resolve("admin.sock");
        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .routing(routing -> routing.register(defaultService))
                .putSocket("admin", listener -> listener
                        .bindAddress(UnixDomainSocketAddress.of(socketPath))
                        .routing(routing -> routing.register(adminService)))
                .putSocket("monitor", listener -> listener
                        .address(InetAddress.getLoopbackAddress())
                        .port(0)
                        .routing(routing -> routing.register(monitorService)))
                .build()
                .start();

        try {
            server.suspend();
            Files.writeString(socketPath, "existing");

            RuntimeException failure = assertThrows(RuntimeException.class, server::resume);

            assertThat(failure, instanceOf(UncheckedIOException.class));
            assertThat(containsMessage(failure, "Failed to start server"), is(true));
            assertThat(server.isRunning(), is(false));
            assertThat(suppressedContainsMessage(failure, "afterStop failed monitor"), is(true));
            assertThat(defaultService.afterStops(), is(1));
            assertThat(adminService.afterStops(), is(1));
            assertThat(monitorService.afterStops(), is(1));
            assertThat(Files.readString(socketPath), is("existing"));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void shutdownHandlerStopFailureStopsAllListeners() {
        LifecycleService defaultService = new LifecycleService("default", true);
        LifecycleService adminService = new LifecycleService("admin", true);
        LoomServer server = (LoomServer) WebServer.builder()
                .port(0)
                .routing(routing -> routing.register(defaultService))
                .putSocket("admin", listener -> listener.port(0)
                        .routing(routing -> routing.register(adminService)))
                .build()
                .start();

        try {
            server.shutdownFromShutdownHook();

            assertThat(server.isRunning(), is(false));
            assertThat(defaultService.afterStops(), is(1));
            assertThat(adminService.afterStops(), is(1));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void duplicateLifecycleFailureInstanceDoesNotAbortStopCleanup() {
        CachedFailureLifecycleService service = new CachedFailureLifecycleService();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .routing(routing -> routing.register(service))
                .putSocket("admin", listener -> listener.port(0)
                        .routing(routing -> routing.register(service)))
                .build()
                .start();

        RuntimeException failure = assertThrows(RuntimeException.class, server::stop);

        assertThat(server.isRunning(), is(false));
        assertThat(service.afterStops(), is(2));
        assertThat(containsMessage(failure, "cached afterStop failure"), is(true));
    }

    private static boolean containsMessage(Throwable failure, String message) {
        if (failure.getMessage() != null && failure.getMessage().contains(message)) {
            return true;
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            if (containsMessage(suppressed, message)) {
                return true;
            }
        }
        Throwable cause = failure.getCause();
        return cause != null && containsMessage(cause, message);
    }

    private static boolean suppressedContainsMessage(Throwable failure, String message) {
        for (Throwable suppressed : failure.getSuppressed()) {
            if (containsMessage(suppressed, message)) {
                return true;
            }
        }
        return false;
    }

    private static void stopUntilStopped(WebServer server) {
        for (int i = 0; i < 3 && server.isRunning(); i++) {
            try {
                server.stop();
            } catch (RuntimeException _) {
            }
        }
    }

    private static void assertPortCanBind(int port) throws Exception {
        assertThat(port > 0, is(true));
        try (ServerSocket _ = new ServerSocket(port, 50, InetAddress.getLoopbackAddress())) {
            // validates that failed startup cleanup released the socket
        }
    }

    private static int awaitPort(WebServer server, String socketName) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        int port;
        do {
            port = server.port(socketName);
            if (port > 0) {
                return port;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        } while (System.nanoTime() < deadline);
        return port;
    }

    private record CloseTrackingConnection(CountDownLatch forceCloseInvoked) implements ServerConnection {
        @Override
        public void handle(io.helidon.common.concurrency.limits.Limit limit) {
        }

        @Override
        public Duration idleTime() {
            return Duration.ZERO;
        }

        @Override
        public void close(boolean interrupt) {
            if (interrupt) {
                forceCloseInvoked.countDown();
            }
        }
    }

    private record ThrowingCloseConnection(AtomicInteger closeCalls, String message) implements ServerConnection {
        private ThrowingCloseConnection(AtomicInteger closeCalls) {
            this(closeCalls, "connection close failed");
        }

        @Override
        public void handle(io.helidon.common.concurrency.limits.Limit limit) {
        }

        @Override
        public Duration idleTime() {
            return Duration.ZERO;
        }

        @Override
        public void close(boolean interrupt) {
            closeCalls.incrementAndGet();
            throw new IllegalStateException(message);
        }
    }

    private record BlockingReaderTask(CountDownLatch started,
                                      CountDownLatch release,
                                      CountDownLatch interrupted) implements InterruptableTask<Void> {
        @Override
        public boolean canInterrupt() {
            return false;
        }

        @Override
        public Void call() throws Exception {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                interrupted.countDown();
                throw e;
            }
            return null;
        }
    }

    private record BlockingBeforeStartService(CountDownLatch beforeStartEntered,
                                              CountDownLatch releaseBeforeStart,
                                              CountDownLatch afterStopInvoked,
                                              CountDownLatch beforeStartExited) implements HttpService {
        private BlockingBeforeStartService(CountDownLatch beforeStartEntered,
                                           CountDownLatch releaseBeforeStart,
                                           CountDownLatch afterStopInvoked) {
            this(beforeStartEntered, releaseBeforeStart, afterStopInvoked, new CountDownLatch(0));
        }

        @Override
        public void routing(HttpRules rules) {
        }

        @Override
        public void beforeStart() {
            beforeStartEntered.countDown();
            try {
                releaseBeforeStart.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("beforeStart interrupted", e);
            } finally {
                beforeStartExited.countDown();
            }
        }

        @Override
        public void afterStop() {
            afterStopInvoked.countDown();
        }
    }

    private static final class CachedFailureLifecycleService implements HttpService {
        private final RuntimeException failure = new IllegalStateException("cached afterStop failure");
        private final AtomicInteger afterStops = new AtomicInteger();

        @Override
        public void routing(HttpRules rules) {
        }

        @Override
        public void afterStop() {
            afterStops.incrementAndGet();
            throw failure;
        }

        private int afterStops() {
            return afterStops.get();
        }
    }

    private static final class LifecycleService implements HttpService {
        private final String name;
        private final boolean failBeforeStart;
        private final boolean failAfterStop;
        private final boolean failAfterStart;
        private final Consumer<WebServer> afterStartAction;
        private final AtomicInteger beforeStarts = new AtomicInteger();
        private final AtomicInteger afterStarts = new AtomicInteger();
        private final AtomicInteger afterStops = new AtomicInteger();

        private LifecycleService(String name) {
            this(name, false);
        }

        private LifecycleService(String name, boolean failAfterStop) {
            this(name, failAfterStop, false);
        }

        private LifecycleService(String name, boolean failAfterStop, boolean failAfterStart) {
            this(name, false, failAfterStop, failAfterStart);
        }

        private LifecycleService(String name, boolean failBeforeStart, boolean failAfterStop, boolean failAfterStart) {
            this(name, failBeforeStart, failAfterStop, failAfterStart, _ -> {
            });
        }

        private LifecycleService(String name,
                                 boolean failBeforeStart,
                                 boolean failAfterStop,
                                 boolean failAfterStart,
                                 Consumer<WebServer> afterStartAction) {
            this.name = name;
            this.failBeforeStart = failBeforeStart;
            this.failAfterStop = failAfterStop;
            this.failAfterStart = failAfterStart;
            this.afterStartAction = afterStartAction;
        }

        @Override
        public void routing(HttpRules rules) {
        }

        @Override
        public void beforeStart() {
            beforeStarts.incrementAndGet();
            if (failBeforeStart) {
                throw new IllegalStateException("beforeStart failed " + name);
            }
        }

        @Override
        public void afterStart(WebServer webServer) {
            afterStarts.incrementAndGet();
            afterStartAction.accept(webServer);
            if (failAfterStart) {
                throw new IllegalStateException("afterStart failed " + name);
            }
        }

        @Override
        public void afterStop() {
            afterStops.incrementAndGet();
            if (failAfterStop) {
                throw new IllegalStateException("afterStop failed " + name);
            }
        }

        private int beforeStarts() {
            return beforeStarts.get();
        }

        private int afterStarts() {
            return afterStarts.get();
        }

        private int afterStops() {
            return afterStops.get();
        }
    }
}
