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
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.context.Context;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.spi.TransportBinding;
import io.helidon.webserver.spi.TransportBindingFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
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
        assertThat(failureMessages(failure), containsString("beforeStart failed default"));
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
            assertThat(failureMessages(startFailure.get()), containsString("Interrupted while waiting for listener"));
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
                assertThat(defaultPort, greaterThan(0));

                releaseAdminBeforeStart.countDown();
                startThread.join(TimeUnit.SECONDS.toMillis(5));

                assertThat(startThread.isAlive(), is(false));
                assertThat(startFailure.get(), instanceOf(IllegalStateException.class));
                assertThat(server.isRunning(), is(false));
                assertThat(failureMessages(startFailure.get()), containsString("Failed to start server"));
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
                .addBinding(disabledTcpBinding())
                .addBinding(udsBinding(socketPath))
                .routing(routing -> routing.register(new LifecycleService("default")))
                .build();

        IllegalStateException failure = assertThrows(IllegalStateException.class, server::start);

        assertThat(failureMessages(failure), containsString("Failed to start server"));
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
            assertThat(failureMessages(failure), containsString("afterStart failed default"));
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
            assertThat(failureMessages(failure), containsString("afterStart failed admin"));
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
        assertThat(failureMessages(failure), containsString("afterStart wrapped default"));
        assertThat(failureMessages(failure), containsString("afterStop failed default"));
    }

    @Test
    void activeConnectionCloseFailuresDoNotSkipRemainingConnections() throws Exception {
        AtomicInteger firstCloseCalls = new AtomicInteger();
        AtomicInteger secondCloseCalls = new AtomicInteger();
        ThrowingBlockingConnection first = new ThrowingBlockingConnection(firstCloseCalls, "connection close failed");
        ThrowingBlockingConnection second = new ThrowingBlockingConnection(secondCloseCalls, "connection close failed");
        QueueingConnectionSelector selector = new QueueingConnectionSelector(first, second);
        LifecycleService service = new LifecycleService("default");

        LoomServer server = startServer(selector, Duration.ZERO, service);

        try (Socket firstSocket = new Socket(InetAddress.getLoopbackAddress(), server.port());
             Socket secondSocket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            firstSocket.getOutputStream().write('x');
            secondSocket.getOutputStream().write('x');
            assertThat(first.awaitHandling(), is(true));
            assertThat(second.awaitHandling(), is(true));

            RuntimeException failure = assertThrows(RuntimeException.class, server::stop);

            assertThat(failureMessages(failure), containsString("connection close failed"));
            assertThat(firstCloseCalls.get(), is(2));
            assertThat(secondCloseCalls.get(), is(2));
            assertThat(service.afterStops(), is(1));
            assertThat(server.isRunning(), is(false));
        } finally {
            first.release();
            second.release();
            stopUntilStopped(server);
        }
    }

    @Test
    void tcpForcedShutdownDoesNotWaitSecondReaderExecutorGracePeriod() throws Exception {
        BlockingConnection connection = new BlockingConnection();
        QueueingConnectionSelector selector = new QueueingConnectionSelector(connection);
        Duration gracePeriod = Duration.ofSeconds(2);
        LoomServer server = startServer(selector, gracePeriod);

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.getOutputStream().write('x');
            waitFor(Duration.ofSeconds(5), connection::handlingStarted, "connection handling did not start");

            long started = System.nanoTime();
            server.stop();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(connection.gracefulCloses(), is(1));
            assertThat(connection.forcedCloses(), is(1));
            assertThat("stop should not wait a second reader-executor grace period",
                       elapsedMillis, lessThan(3_500L));
        } finally {
            connection.release();
            stopUntilStopped(server);
        }
    }

    @Test
    void checkpointSuspendCloseFailuresDoNotSkipRemainingConnections() throws Exception {
        AtomicInteger firstCloseCalls = new AtomicInteger();
        AtomicInteger secondCloseCalls = new AtomicInteger();
        ThrowingBlockingConnection first = new ThrowingBlockingConnection(firstCloseCalls, "connection close failed");
        ThrowingBlockingConnection second = new ThrowingBlockingConnection(secondCloseCalls, "connection close failed");
        QueueingConnectionSelector selector = new QueueingConnectionSelector(first, second);
        LifecycleService service = new LifecycleService("default");

        LoomServer server = startServer(selector, Duration.ZERO, service);

        try (Socket firstSocket = new Socket(InetAddress.getLoopbackAddress(), server.port());
             Socket secondSocket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            firstSocket.getOutputStream().write('x');
            secondSocket.getOutputStream().write('x');
            assertThat(first.awaitHandling(), is(true));
            assertThat(second.awaitHandling(), is(true));

            RuntimeException failure = assertThrows(RuntimeException.class, server::suspend);

            assertThat(failureMessages(failure), containsString("connection close failed"));
            assertThat(firstCloseCalls.get(), greaterThanOrEqualTo(2));
            assertThat(secondCloseCalls.get(), greaterThanOrEqualTo(2));
        } finally {
            first.release();
            second.release();
            stopUntilStopped(server);
        }
    }

    @Test
    void gracefulShutdownClosesAcceptedSocketDuringSocketConfiguration() throws Exception {
        BlockingSocketOptions socketOptions = new BlockingSocketOptions();
        AtomicReference<Throwable> stopFailure = new AtomicReference<>();
        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .shutdownGracePeriod(Duration.ofMinutes(1))
                .connectionOptions(socketOptions)
                .build()
                .start();
        Thread stopThread = null;

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5_000);
            assertThat(socketOptions.awaitConfigure(), is(true));

            stopThread = Thread.ofPlatform()
                    .name("test-graceful-socket-config-stop")
                    .start(() -> {
                        try {
                            server.stop();
                        } catch (RuntimeException | Error e) {
                            stopFailure.set(e);
                        }
                    });

            assertSocketClosed(socket);
            socketOptions.release();
            stopThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(stopThread.isAlive(), is(false));
            assertThat(stopFailure.get(), nullValue());
        } finally {
            socketOptions.release();
            if (stopThread != null && stopThread.isAlive()) {
                stopThread.interrupt();
            }
            stopUntilStopped(server);
        }
    }

    @Test
    void gracefulShutdownClosesAcceptedSocketBeforeConnectionRegistration() throws Exception {
        PreRegistrationConnectionSelector selector = new PreRegistrationConnectionSelector();
        AtomicReference<Throwable> stopFailure = new AtomicReference<>();
        LoomServer server = startServer(selector, Duration.ofMinutes(1));
        Thread stopThread = null;

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write('x');
            assertThat(selector.awaitSupports(), is(true));

            stopThread = Thread.ofPlatform()
                    .name("test-graceful-pre-registration-stop")
                    .start(() -> {
                        try {
                            server.stop();
                        } catch (RuntimeException | Error e) {
                            stopFailure.set(e);
                        }
                    });

            assertSocketClosed(socket);
            assertThat(selector.connection().handlingStarted(), is(false));

            selector.release();
            stopThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(stopThread.isAlive(), is(false));
            assertThat(stopFailure.get(), nullValue());
            assertThat(selector.connection().gracefulCloses(), is(1));
            assertThat(selector.connection().forcedCloses(), is(0));
            assertThat(selector.connection().handlingStarted(), is(false));
        } finally {
            selector.release();
            BlockingConnection connection = selector.connection();
            if (connection != null) {
                connection.release();
            }
            if (stopThread != null && stopThread.isAlive()) {
                stopThread.interrupt();
            }
            stopUntilStopped(server);
        }
    }

    @Test
    void gracefulShutdownClosesConnectionCreatedAfterCloseRequest() throws Exception {
        ConnectionCreationBlockingSelector selector = new ConnectionCreationBlockingSelector();
        AtomicReference<Throwable> stopFailure = new AtomicReference<>();
        LoomServer server = startServer(selector, Duration.ofMinutes(1));
        Thread stopThread = null;

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write('x');
            assertThat(selector.awaitConnectionCreation(), is(true));

            stopThread = Thread.ofPlatform()
                    .name("test-graceful-connection-created-after-close")
                    .start(() -> {
                        try {
                            server.stop();
                        } catch (RuntimeException | Error e) {
                            stopFailure.set(e);
                        }
                    });

            assertSocketClosed(socket);
            selector.release();
            stopThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(stopThread.isAlive(), is(false));
            assertThat(stopFailure.get(), nullValue());
            assertThat(selector.connection().handlingStarted(), is(false));
            assertThat(selector.connection().gracefulCloses(), is(1));
            assertThat(selector.connection().forcedCloses(), is(0));
        } finally {
            selector.release();
            BlockingConnection connection = selector.connection();
            if (connection != null) {
                connection.release();
            }
            if (stopThread != null && stopThread.isAlive()) {
                stopThread.interrupt();
            }
            stopUntilStopped(server);
        }
    }

    @Test
    void forcedShutdownClosesAcceptedSocketBeforeConnectionRegistration() throws Exception {
        PreRegistrationConnectionSelector selector = new PreRegistrationConnectionSelector();
        LoomServer server = startServer(selector, Duration.ZERO);

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write('x');
            assertThat(selector.awaitSupports(), is(true));

            server.stop();

            assertSocketClosed(socket);
            assertThat(selector.connection().handlingStarted(), is(false));

            selector.release();
            waitFor(Duration.ofSeconds(5),
                    () -> selector.connection().forcedCloses() == 1,
                    "accepted connection was not force closed");
            assertThat(selector.connection().gracefulCloses(), is(0));
            assertThat(selector.connection().forcedCloses(), is(1));
            assertThat(selector.connection().handlingStarted(), is(false));
        } finally {
            selector.release();
            selector.connection().release();
            stopUntilStopped(server);
        }
    }

    @Test
    void suspendClosesAcceptedSocketBeforeConnectionRegistration() throws Exception {
        PreRegistrationConnectionSelector selector = new PreRegistrationConnectionSelector();
        AtomicReference<Throwable> suspendFailure = new AtomicReference<>();
        LoomServer server = startServer(selector, Duration.ZERO);
        Thread suspendThread = null;

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write('x');
            assertThat(selector.awaitSupports(), is(true));

            suspendThread = Thread.ofPlatform()
                    .name("test-suspend-pre-registration")
                    .start(() -> {
                        try {
                            server.suspend();
                        } catch (RuntimeException | Error e) {
                            suspendFailure.set(e);
                        }
                    });

            assertSocketClosed(socket);
            assertThat(selector.connection().handlingStarted(), is(false));

            suspendThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(suspendThread.isAlive(), is(false));
            assertThat(suspendFailure.get(), nullValue());

            selector.release();
            waitFor(Duration.ofSeconds(5),
                    () -> selector.connection().forcedCloses() == 1,
                    "accepted connection was not force closed");
            assertThat(selector.connection().gracefulCloses(), is(0));
            assertThat(selector.connection().forcedCloses(), is(1));
            assertThat(selector.connection().handlingStarted(), is(false));
        } finally {
            selector.release();
            selector.connection().release();
            if (suspendThread != null && suspendThread.isAlive()) {
                suspendThread.interrupt();
            }
            stopUntilStopped(server);
        }
    }

    @Test
    void idleTimeoutIgnoresAcceptedSocketBeforeConnectionHandling() throws Exception {
        PreRegistrationThenIdleConnectionSelector selector = new PreRegistrationThenIdleConnectionSelector();
        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .idleConnectionTimeout(Duration.ofMillis(1))
                .idleConnectionPeriod(Duration.ofMillis(10))
                .addConnectionSelector(selector)
                .build()
                .start();

        try (Socket blockedSocket = new Socket(InetAddress.getLoopbackAddress(), server.port());
                Socket idleSocket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            blockedSocket.setSoTimeout(250);
            blockedSocket.getOutputStream().write('x');
            assertThat(selector.awaitSupports(), is(true));

            idleSocket.getOutputStream().write('x');
            assertThat(selector.idleConnection().awaitHandling(), is(true));
            assertThat(selector.idleConnection().awaitGracefulClose(), is(true));

            assertThrows(SocketTimeoutException.class, () -> blockedSocket.getInputStream().read());
            assertThat(selector.preRegistrationConnection().handlingStarted(), is(false));
            assertThat(selector.preRegistrationConnection().gracefulCloses(), is(0));
            assertThat(selector.preRegistrationConnection().forcedCloses(), is(0));
            assertThat(selector.idleConnection().forcedCloses(), is(0));
        } finally {
            selector.release();
            selector.preRegistrationConnection().release();
            selector.idleConnection().release();
            stopUntilStopped(server);
        }
    }

    @Test
    void idleTimeoutClosesHandledConnection() throws Exception {
        IdleBlockingConnection connection = new IdleBlockingConnection();
        QueueingConnectionSelector selector = new QueueingConnectionSelector(connection);
        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .idleConnectionTimeout(Duration.ofMillis(1))
                .idleConnectionPeriod(Duration.ofMillis(10))
                .addConnectionSelector(selector)
                .build()
                .start();

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.getOutputStream().write('x');
            assertThat(connection.awaitHandling(), is(true));
            assertThat(connection.awaitGracefulClose(), is(true));
            assertThat(connection.forcedCloses(), is(0));
        } finally {
            connection.release();
            stopUntilStopped(server);
        }
    }

    @Test
    void stopForcesConnectionsBeforeWaitingForRunningIdleTimeoutTask() throws Exception {
        CountDownLatch stopStarted = new CountDownLatch(1);
        CountDownLatch stopDone = new CountDownLatch(1);
        AtomicReference<Throwable> stopFailure = new AtomicReference<>();
        BlockingIdleCloseConnection connection = new BlockingIdleCloseConnection();
        QueueingConnectionSelector selector = new QueueingConnectionSelector(connection);
        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .idleConnectionTimeout(Duration.ofMillis(1))
                .idleConnectionPeriod(Duration.ofMillis(10))
                .addConnectionSelector(selector)
                .build()
                .start();
        Thread stopThread = null;
        int port = server.port();

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.getOutputStream().write('x');
            assertThat(connection.awaitHandling(), is(true));
            assertThat(connection.awaitIdleClose(), is(true));

            stopThread = Thread.ofPlatform()
                    .name("test-stop-forces-before-idle-timeout-wait")
                    .start(() -> {
                        stopStarted.countDown();
                        try {
                            server.stop();
                        } catch (RuntimeException | Error e) {
                            stopFailure.set(e);
                        } finally {
                            stopDone.countDown();
                        }
                    });

            assertThat(stopStarted.await(5, TimeUnit.SECONDS), is(true));
            assertThat(connection.awaitForcedClose(), is(true));
            assertThat("stop should wait for the running idle timeout task",
                       stopDone.getCount(),
                       is(1L));
            assertPortRefusesConnections(port);

            connection.releaseIdleClose();
            stopThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(stopThread.isAlive(), is(false));
            assertThat(stopFailure.get(), nullValue());
        } finally {
            connection.releaseIdleClose();
            connection.releaseHandling();
            if (stopThread != null && stopThread.isAlive()) {
                stopThread.interrupt();
            }
            stopUntilStopped(server);
        }
    }

    @Test
    void suspendForcesConnectionsBeforeWaitingForRunningIdleTimeoutTask() throws Exception {
        CountDownLatch suspendStarted = new CountDownLatch(1);
        CountDownLatch suspendDone = new CountDownLatch(1);
        AtomicReference<Throwable> suspendFailure = new AtomicReference<>();
        BlockingIdleCloseConnection connection = new BlockingIdleCloseConnection();
        QueueingConnectionSelector selector = new QueueingConnectionSelector(connection);
        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .idleConnectionTimeout(Duration.ofMillis(1))
                .idleConnectionPeriod(Duration.ofMillis(10))
                .addConnectionSelector(selector)
                .build()
                .start();
        Thread suspendThread = null;
        int port = server.port();

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.getOutputStream().write('x');
            assertThat(connection.awaitHandling(), is(true));
            assertThat(connection.awaitIdleClose(), is(true));

            suspendThread = Thread.ofPlatform()
                    .name("test-suspend-forces-before-idle-timeout-wait")
                    .start(() -> {
                        suspendStarted.countDown();
                        try {
                            server.suspend();
                        } catch (RuntimeException | Error e) {
                            suspendFailure.set(e);
                        } finally {
                            suspendDone.countDown();
                        }
                    });

            assertThat(suspendStarted.await(5, TimeUnit.SECONDS), is(true));
            assertThat(connection.awaitForcedClose(), is(true));
            assertThat("suspend should wait for the running idle timeout task",
                       suspendDone.getCount(),
                       is(1L));
            assertPortRefusesConnections(port);

            connection.releaseIdleClose();
            suspendThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(suspendThread.isAlive(), is(false));
            assertThat(suspendFailure.get(), nullValue());
        } finally {
            connection.releaseIdleClose();
            connection.releaseHandling();
            if (suspendThread != null && suspendThread.isAlive()) {
                suspendThread.interrupt();
            }
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
            assertThat(failureMessages(failure), containsString("afterStop failed default"));
            assertThat(failureMessages(failure), containsString("afterStop failed admin"));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void webServerReportsListenerPortAfterBindingExtraction() {
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .putSocket("admin", listener -> listener.port(0))
                .build()
                .start();

        try {
            assertThat(server.port(WebServer.DEFAULT_SOCKET_NAME), is(server.port()));
            assertThat(server.port("admin"), greaterThan(0));
            assertThat(server.port("missing"), is(-1));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void explicitTcpTransportBindingStartsServer() {
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.builder()
                                    .required(true)
                                    .buildPrototype())
                .build()
                .start();

        try {
            assertThat(server.port(), greaterThan(0));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void explicitUdsTransportBindingStartsServer(@TempDir Path tempDir) {
        Path socketPath = tempDir.resolve("server.sock");
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .addBinding(disabledTcpBinding())
                .addBinding(udsBinding(socketPath))
                .routing(routing -> routing.register(new LifecycleService("default")))
                .build()
                .start();

        try {
            assertThat(server.port(), is(-1));
            assertThat(Files.exists(socketPath), is(true));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void udsTransportBindingRebindsAndAcceptsAfterSuspendResume(@TempDir Path tempDir) throws Exception {
        Path socketPath = tempDir.resolve("server.sock");
        BlockingConnection connection = new BlockingConnection();
        QueueingConnectionSelector selector = new QueueingConnectionSelector(connection);
        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(disabledTcpBinding())
                .addBinding(udsBinding(socketPath))
                .addConnectionSelector(selector)
                .build()
                .start();

        try {
            assertThat(Files.exists(socketPath), is(true));

            server.suspend();
            assertThat(Files.exists(socketPath), is(false));

            server.resume();
            assertThat(Files.exists(socketPath), is(true));

            try (SocketChannel socket = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                socket.connect(UnixDomainSocketAddress.of(socketPath));
                socket.write(ByteBuffer.wrap(new byte[] {'x'}));
                assertThat(connection.awaitHandling(Duration.ofSeconds(5)), is(true));
            }
        } finally {
            connection.release();
            stopUntilStopped(server);
        }
    }

    @Test
    void udsTransportBindingWithoutSocketIsInactive() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(disabledTcpBinding())
                .addBinding(UdsTransportConfig.create())
                .build()
                .start());

        assertThat(failureMessages(failure), containsString("has no active transport bindings"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void udsTransportBindingRequiresNio(@TempDir Path tempDir) {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .useNio(false)
                .addBinding(disabledTcpBinding())
                .addBinding(udsBinding(tempDir.resolve("server.sock")))
                .build()
                .start());

        assertThat(failureMessages(failure), containsString("requires use-nio=true"));
    }

    @Test
    void defaultTcpTransportBindingStartsWhenBindingDiscoveryIsDisabled() {
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .bindingsDiscoverServices(false)
                .build()
                .start();

        try {
            assertThat(server.port(), greaterThan(0));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void webServerBuilderCanBuildAfterPrototypeAccessWithDiscoveredDefaultTcpBinding() {
        WebServerConfig.Builder builder = WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0);
        builder.buildPrototype();

        WebServer server = builder.build()
                .start();

        try {
            assertThat(server.port(), greaterThan(0));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void explicitTcpBindingOverlaysDiscoveredDefaultTcpBinding() {
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .addBinding(TcpTransportConfig.builder()
                                    .required(true)
                                    .buildPrototype())
                .build()
                .start();

        try {
            assertThat(server.port(), greaterThan(0));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void discoveredDefaultTcpTransportBindingDoesNotReorderExplicitTransportBinding() {
        TestTransportBindingProvider.reset();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .addBinding(new TestTransportBindingConfig(TestTransportBindingConfig.TYPE, true))
                .build()
                .start();

        try {
            assertThat(TestTransportBindingProvider.portAtStart(TestTransportBindingConfig.TYPE), is(-1));
            ListenerConfig listenerConfig = TestTransportBindingProvider.listenerConfigAtPlan(TestTransportBindingConfig.TYPE);
            assertThat(listenerConfig, notNullValue());
            assertThat(listenerConfig.name(), is(WebServer.DEFAULT_SOCKET_NAME));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void transportBindingContextCanQueryBoundPortDuringPlanning() {
        TestTransportBindingProvider.reset();
        WebServer.builder()
                .shutdownHook(false)
                .addBinding(new TestTransportBindingConfig(TestTransportBindingConfig.TYPE, true))
                .build();

        assertThat(TestTransportBindingProvider.portAtCreate(TestTransportBindingConfig.TYPE), is(-1));
    }

    @Test
    void bindingPlanContextExposesListenerConfig() {
        TestTransportBindingProvider.reset();
        InetAddress address = InetAddress.getLoopbackAddress();
        WebServer.builder()
                .shutdownHook(false)
                .address(address)
                .port(0)
                .addBinding(new TestTransportBindingConfig(TestTransportBindingConfig.TYPE, true))
                .build();

        ListenerConfig listenerConfig = TestTransportBindingProvider.listenerConfigAtPlan(TestTransportBindingConfig.TYPE);
        assertThat(listenerConfig, notNullValue());
        assertThat(listenerConfig.address(), is(address));
        assertThat(TestTransportBindingProvider.configuredAddress(TestTransportBindingConfig.TYPE),
                   is(new InetSocketAddress(address, 0)));
    }

    @Test
    void transportBindingsShareListenerConnectionLimit() {
        TestTransportBindingProvider.reset();
        WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .maxConnections(2)
                .addBinding(disabledTcpBinding())
                .addBinding(new TestTransportBindingConfig(TestTransportBindingConfig.TYPE, true))
                .addBinding(TestTransportBindingConfig.alternate("second", true))
                .build();

        Limit first = TestTransportBindingProvider.connectionLimit(TestTransportBindingConfig.TYPE);
        Limit second = TestTransportBindingProvider.connectionLimit("second");

        assertThat(first, notNullValue());
        assertThat(second, sameInstance(first));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void finiteConnectionLimitPreservesConcentratedTcpCapacityWithUdsBinding(@TempDir Path tempDir) throws Exception {
        Path socketPath = tempDir.resolve("server.sock");
        LatchingConnectionLimit connectionLimit = new LatchingConnectionLimit(3);
        AtomicInteger releasesAtThirdHandling = new AtomicInteger(-1);
        BlockingConnection first = new BlockingConnection();
        BlockingConnection second = new BlockingConnection();
        BlockingConnection third = new BlockingConnection(() -> releasesAtThirdHandling.set(
                connectionLimit.releaseCount()));
        QueueingConnectionSelector selector = new QueueingConnectionSelector(first, second, third);
        InetAddress address = InetAddress.getLoopbackAddress();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .address(address)
                .port(0)
                .bindingsDiscoverServices(false)
                .connectionSelectorProvidersDiscoverServices(false)
                .addConnectionSelector(selector)
                .buildPrototype();
        TestTransportBindingContext context = new TestTransportBindingContext(new InetSocketAddress(address, 0),
                                                                              listenerConfig,
                                                                              connectionLimit);
        Timer timer = new Timer("test-concentrated-connection-capacity", true);
        UdsTransportBinding udsBinding = new UdsTransportBinding(context, udsBinding(socketPath), timer);
        TcpTransportBinding tcpBinding = new TcpTransportBinding(context, timer);

        try {
            udsBinding.start();
            tcpBinding.start();

            assertThat(Files.exists(socketPath), is(true));
            assertThat(connectionLimit.awaitIdleReservations(Duration.ofSeconds(5)), is(true));

            try (Socket firstSocket = new Socket(address, tcpBinding.port())) {
                firstSocket.getOutputStream().write('x');
                assertThat(first.awaitHandling(Duration.ofSeconds(5)), is(true));

                try (Socket secondSocket = new Socket(address, tcpBinding.port())) {
                    secondSocket.getOutputStream().write('x');
                    assertThat(second.awaitHandling(Duration.ofSeconds(5)), is(true));

                    assertThat(connectionLimit.awaitFourthAcquireBlocked(Duration.ofSeconds(5)), is(true));
                    assertThat(connectionLimit.availablePermits(), is(0));
                    assertThat(connectionLimit.releaseCount(), is(0));

                    try (Socket thirdSocket = new Socket(address, tcpBinding.port())) {
                        thirdSocket.getOutputStream().write('x');

                        first.release();
                        assertThat(third.awaitHandling(Duration.ofSeconds(5)), is(true));
                        assertThat(releasesAtThirdHandling.get(), is(1));
                    }
                }
            }
            assertThat(context.bindingFailure(), nullValue());
        } finally {
            first.release();
            second.release();
            third.release();
            try {
                tcpBinding.stop(Duration.ofSeconds(5));
            } finally {
                try {
                    udsBinding.stop(Duration.ofSeconds(5));
                } finally {
                    timer.cancel();
                    context.close();
                }
            }
        }
    }

    @Test
    void listenerRejectsIdlePermitBindingsThatExceedMaxConnections() {
        TestTransportBindingProvider.reset();

        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .maxConnections(1)
                .addBinding(disabledTcpBinding())
                .addBinding(TestTransportBindingConfig.idlePermit("first"))
                .addBinding(TestTransportBindingConfig.alternateIdlePermit("second"))
                .build());

        assertThat(failureMessages(failure), containsString("max-connections=1"));
        assertThat(failureMessages(failure), containsString("2 active transport bindings"));
        assertThat(failureMessages(failure), containsString("reserve an idle connection permit"));
    }

    @Test
    void eventDrivenBindingDoesNotConsumeIdlePermitCapacity() {
        TestTransportBindingProvider.reset();

        WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .maxConnections(1)
                .addBinding(disabledTcpBinding())
                .addBinding(TestTransportBindingConfig.idlePermit("idle"))
                .addBinding(TestTransportBindingConfig.alternate("event-driven", true))
                .build();
    }

    @Test
    void explicitTcpTransportBindingOrderControlsPortOwner() {
        TestTransportBindingProvider.reset();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .addBinding(new TestTransportBindingConfig(TestTransportBindingConfig.TYPE, true))
                .addBinding(TcpTransportConfig.builder()
                                    .required(true)
                                    .buildPrototype())
                .build()
                .start();

        try {
            assertThat(TestTransportBindingProvider.portAtStart(TestTransportBindingConfig.TYPE), is(-1));
            assertThat(server.port(), greaterThan(0));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void explicitTcpTransportBindingOrderControlsPortOwnerWithPrototype() {
        TestTransportBindingProvider.reset();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .addBinding(new TestTransportBindingConfig(TestTransportBindingConfig.TYPE, true))
                .addBinding(TcpTransportConfig.create())
                .build()
                .start();

        try {
            assertThat(TestTransportBindingProvider.portAtStart(TestTransportBindingConfig.TYPE), is(-1));
            assertThat(server.port(), greaterThan(0));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void duplicateTcpTransportBindingsFail() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.create())
                .addBinding(TcpTransportConfig.builder()
                                    .required(true)
                                    .buildPrototype())
                .build()
                .start());

        assertThat(failure.getMessage(), containsString("Multiple configured provider instances of type \"tcp\""));
        assertThat(failure.getMessage(), containsString("provider identity TYPE_ONLY permits one instance per type"));
    }

    @Test
    void tcpTransportReusesEarlierPortBindingRandomPort() throws Exception {
        InetAddress address = InetAddress.getLoopbackAddress();
        WebServer server = startTcpPortReuseServerWithRetry(address);

        try {
            assertThat(server.port(), is(TestTransportBindingProvider.boundPort("test")));
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address, server.port()), 500);
            }
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void failedTransportBindingStartStopsOnlyStartAttemptedBindings() {
        TestTransportBindingProvider.reset();

        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(new TestTransportBindingConfig("failing", true, true))
                .addBinding(TestTransportBindingConfig.alternate("not-started", true))
                .build()
                .start());

        assertThat(failureMessages(failure), containsString("test transport start failed failing"));
        assertThat(TestTransportBindingProvider.starts("failing"), is(1));
        assertThat(TestTransportBindingProvider.stops("failing"), is(1));
        assertThat(TestTransportBindingProvider.starts("not-started"), is(0));
        assertThat(TestTransportBindingProvider.stops("not-started"), is(0));
    }

    @Test
    void transportBindingStopIsBoundByGracePeriod() {
        TestTransportBindingProvider.reset();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .shutdownGracePeriod(Duration.ofMillis(1))
                .addBinding(new TestTransportBindingConfig("hanging", true, false, false, true, false))
                .build()
                .start();

        try {
            RuntimeException failure = assertThrows(RuntimeException.class, server::stop);

            assertThat(failureMessages(failure),
                       containsString("Timed out waiting for transport binding type \"test-transport\""));
        } finally {
            TestTransportBindingProvider.completeStop("hanging");
            stopUntilStopped(server);
        }
    }

    @Test
    void timedOutTransportBindingStopDoesNotWaitSecondSharedExecutorGrace() {
        TestTransportBindingProvider.reset();
        Duration gracePeriod = Duration.ofSeconds(2);
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .shutdownGracePeriod(gracePeriod)
                .addBinding(TestTransportBindingConfig.ignoreStopInterrupt("hanging"))
                .build()
                .start();

        long started = System.nanoTime();
        try {
            RuntimeException failure = assertThrows(RuntimeException.class, server::stop);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(failureMessages(failure),
                       containsString("Timed out waiting for transport binding type \"test-transport\""));
            assertThat("stop should not wait a second binding-stop grace period",
                       elapsedMillis, lessThan(3_500L));
        } finally {
            TestTransportBindingProvider.completeStop("hanging");
            stopUntilStopped(server);
        }
    }

    @Test
    void forcedTransportBindingStopDoesNotWaitSharedExecutorGrace() throws Exception {
        TestTransportBindingProvider.reset();
        Duration gracePeriod = Duration.ofSeconds(2);
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .shutdownGracePeriod(gracePeriod)
                .addBinding(TestTransportBindingConfig.forceStopWithBlockedExecutor("forced"))
                .build()
                .start();

        assertThat(TestTransportBindingProvider.awaitExecutorTask("forced"), is(true));

        long started = System.nanoTime();
        try {
            server.stop();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(TestTransportBindingProvider.stops("forced"), is(1));
            assertThat("forced binding stop should not wait shared executor grace period",
                       elapsedMillis, lessThan(1_500L));
        } finally {
            TestTransportBindingProvider.completeExecutorTask("forced");
            stopUntilStopped(server);
        }
    }

    @Test
    void fatalTransportBindingFailureStopsServer() throws Exception {
        TestTransportBindingProvider.reset();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(TestTransportBindingConfig.fatalAfterStart("fatal"))
                .build()
                .start();

        try {
            waitFor(Duration.ofSeconds(5), () -> !server.isRunning(), "server did not stop after fatal binding failure");

            assertThat(TestTransportBindingProvider.stops("fatal"), is(1));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void listenerInvokesMandatoryBindingCheckpointLifecycle() {
        TestTransportBindingProvider.reset();
        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(disabledTcpBinding())
                .addBinding(new TestTransportBindingConfig("test", true))
                .build()
                .start();

        try {
            server.suspend();
            assertThat(TestTransportBindingProvider.suspends("test"), is(1));

            server.resume();
            assertThat(TestTransportBindingProvider.resumes("test"), is(1));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void transportBindingStopsShareListenerGracePeriod() {
        TestTransportBindingProvider.reset();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .shutdownGracePeriod(Duration.ofMillis(1))
                .addBinding(new TestTransportBindingConfig("first", true, false, false, true, false))
                .addBinding(TestTransportBindingConfig.alternate("second", true, false, false, true, false))
                .build()
                .start();

        try {
            RuntimeException failure = assertThrows(RuntimeException.class, server::stop);

            assertThat(failureMessages(failure),
                       containsString("Timed out waiting for transport binding type \"test-transport\""));
            assertThat(failureMessages(failure),
                       containsString("Timed out waiting for transport binding type \"alternate-test-transport\""));
        } finally {
            TestTransportBindingProvider.completeStop("first");
            TestTransportBindingProvider.completeStop("second");
            stopUntilStopped(server);
        }
    }

    @Test
    void concurrentListenerStopsRunCleanupOnlyOnce() throws Exception {
        TestTransportBindingProvider.reset();
        BlockingAfterStopRouter router = new BlockingAfterStopRouter();
        Timer timer = new Timer("test-listener-concurrent-stop", true);
        ServerListener listener = new ServerListener(WebServer.DEFAULT_SOCKET_NAME,
                                                     listenerConfigWithoutTcp(),
                                                     router,
                                                     Context.builder()
                                                             .id("listener-concurrent-stop-test")
                                                             .build(),
                                                     timer,
                                                     MediaContext.create(),
                                                     ContentEncodingContext.create(),
                                                     DirectHandlers.create(),
                                                     (failedListener, _) -> failedListener.stop());
        AtomicReference<Throwable> firstStopFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondStopFailure = new AtomicReference<>();
        Thread firstStop = null;
        Thread secondStop = null;

        try {
            listener.start();

            firstStop = Thread.ofPlatform()
                    .name("test-listener-stop-first")
                    .start(() -> stopListener(listener, firstStopFailure));
            assertThat(router.awaitAfterStop(), is(true));

            secondStop = Thread.ofPlatform()
                    .name("test-listener-stop-second")
                    .start(() -> stopListener(listener, secondStopFailure));
            secondStop.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(secondStop.isAlive(), is(false));
            assertThat(secondStopFailure.get(), nullValue());
            assertThat(TestTransportBindingProvider.stops("test"), is(1));
            assertThat(router.afterStops(), is(1));
        } finally {
            router.releaseAfterStop();
            if (firstStop != null) {
                firstStop.join(TimeUnit.SECONDS.toMillis(5));
                if (firstStop.isAlive()) {
                    firstStop.interrupt();
                }
            }
            if (secondStop != null && secondStop.isAlive()) {
                secondStop.interrupt();
            }
            listener.stop();
            timer.cancel();
        }

        assertThat(firstStopFailure.get(), nullValue());
    }

    @Test
    void listenerPlanningFailsWhenDefaultTcpTransportBindingIsDisabled() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.builder()
                                    .enabled(false)
                                    .buildPrototype())
                .build()
                .start());

        assertThat(failureMessages(failure), containsString("has no active transport bindings"));
    }

    @Test
    void listenerPlanningFailsWhenConfigDisablesTcpTransportBinding() {
        Config config = Config.just("""
                server:
                  bindings:
                    tcp:
                      enabled: false
                """, MediaTypes.APPLICATION_YAML);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .config(config.get("server"))
                .build()
                .start());

        assertThat(failureMessages(failure), containsString("has no active transport bindings"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void explicitUdsTransportBindingCanBeConfigured(@TempDir Path tempDir) {
        Path socketPath = tempDir.resolve("server.sock");
        Config config = Config.just("""
                server:
                  bindings:
                    tcp:
                      enabled: false
                    uds:
                      socket: "%s"
                """.formatted(socketPath), MediaTypes.APPLICATION_YAML);

        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .config(config.get("server"))
                .routing(routing -> routing.register(new LifecycleService("default")))
                .build()
                .start();

        try {
            assertThat(server.port(), is(-1));
            assertThat(Files.exists(socketPath), is(true));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void listenerConfigFailsForDuplicateTransportBindingType() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(new TestTransportBindingConfig("test", true))
                .addBinding(new TestTransportBindingConfig("custom-test", true))
                .build()
                .start());

        assertThat(failure.getMessage(),
                   containsString("Multiple configured provider instances of type \"test-transport\""));
        assertThat(failure.getMessage(), containsString("provider identity TYPE_ONLY permits one instance per type"));
    }

    @Test
    void listenerPlanningRejectsFactoryProviderKeyMismatch() {
        TransportBindingFactory factory = new TransportBindingFactory() {
            @Override
            public String type() {
                return "factory-type";
            }

            @Override
            public String name() {
                return "provider-key";
            }

            @Override
            public boolean canBind(BindingPlanContext context) {
                return true;
            }

            @Override
            public TransportBinding create(TransportBindingContext context) {
                throw new AssertionError("Factory must not create a binding after identity validation fails");
            }
        };

        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(disabledTcpBinding())
                .addBinding(factory)
                .build());

        assertThat(failure.getMessage(), containsString("factory type \"factory-type\""));
        assertThat(failure.getMessage(), containsString("provider key \"provider-key\""));
    }

    @Test
    void listenerPlanningRejectsRuntimeBindingTypeMismatch() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(disabledTcpBinding())
                .addBinding(TestTransportBindingConfig.runtimeTypeMismatch("mismatch"))
                .build());

        assertThat(failure.getMessage(), containsString("factory type \"test-transport\""));
        assertThat(failure.getMessage(), containsString("created binding type \"alternate-test-transport\""));
    }

    @Test
    void listenerConfigurationRejectsBindingListForm() {
        Config config = Config.just("""
                server:
                  bindings:
                    - tcp:
                        required: true
                    - uds:
                        enabled: false
                """, MediaTypes.APPLICATION_YAML);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .config(config.get("server"))
                .build()
                .start());

        assertThat(failure.getMessage(), containsString("Configured providers at server.bindings"));
        assertThat(failure.getMessage(), containsString("must use object form"));
    }

    @Test
    void listenerTlsRejectsUnprotectedTransportBindings() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .tls(Tls.builder()
                             .trustAll(true)
                             .build())
                .bindingsDiscoverServices(false)
                .addBinding(new TestTransportBindingConfig("test", true))
                .build()
                .start());

        assertThat(failureMessages(failure), containsString("has TLS enabled"));
        assertThat(failureMessages(failure), containsString("is unprotected"));
    }

    @Test
    void listenerTlsRejectsTransportBindingWithNullSecurity() {
        TestTransportBindingProvider.reset();
        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .tls(Tls.builder()
                             .trustAll(true)
                             .build())
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.builder()
                                    .enabled(false)
                                    .buildPrototype())
                .addBinding(TestTransportBindingConfig.nullSecurity("test"))
                .build()
                .start());

        assertThat(failureMessages(failure), containsString("Transport binding returned null security"));
        assertThat(TestTransportBindingProvider.starts("test"), is(0));
    }

    @Test
    void listenerTlsAllowsTlsEquivalentTransportBindingsAndReportsConfiguredTls() {
        TestTransportBindingProvider.reset();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .tls(Tls.builder()
                             .trustAll(true)
                             .build())
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.builder()
                                    .enabled(false)
                                    .buildPrototype())
                .addBinding(TestTransportBindingConfig.tlsEquivalent("test"))
                .build()
                .start();

        try {
            assertThat(TestTransportBindingProvider.starts("test"), is(1));
            assertThat(server.hasTls(), is(true));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void tlsEquivalentBindingWithoutListenerTlsDoesNotReportConfiguredTls() {
        TestTransportBindingProvider.reset();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(disabledTcpBinding())
                .addBinding(TestTransportBindingConfig.tlsEquivalent("test"))
                .build()
                .start();

        try {
            assertThat(TestTransportBindingProvider.starts("test"), is(1));
            assertThat(server.hasTls(), is(false));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void listenerTlsAllowsTcpAndTlsEquivalentBindingsTogether() {
        TestTransportBindingProvider.reset();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .tls(Tls.builder()
                             .trustAll(true)
                             .build())
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.create())
                .addBinding(TestTransportBindingConfig.tlsEquivalent("test"))
                .build()
                .start();

        try {
            assertThat(TestTransportBindingProvider.starts("test"), is(1));
            assertThat(server.hasTls(), is(true));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void virtualHostTlsRejectsTlsEquivalentBinding() {
        TestTransportBindingProvider.reset();
        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .tls(Tls.builder()
                             .trustAll(true)
                             .build())
                .addVirtualHost(virtualHost -> virtualHost.host("api.example.com")
                        .tls(Tls.builder()
                                     .trustAll(true)
                                     .build()))
                .bindingsDiscoverServices(false)
                .addBinding(disabledTcpBinding())
                .addBinding(TestTransportBindingConfig.tlsEquivalent("test"))
                .build());

        assertThat(failureMessages(failure), containsString("TLS virtual hosts configured"));
        assertThat(failureMessages(failure), containsString("does not use listener TLS"));
    }

    @Test
    void listenerTlsProtectedTransportBindingCanUseSharedTlsContext() {
        TestTransportBindingProvider.reset();
        Tls listenerTls = Tls.builder()
                .trustAll(true)
                .build();
        Tls virtualHostTls = Tls.builder()
                .trustAll(true)
                .build();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .tls(listenerTls)
                .useNio(true)
                .addVirtualHost(virtualHost -> virtualHost.host("api.example.com")
                        .tls(virtualHostTls))
                .bindingsDiscoverServices(false)
                .addBinding(TestTransportBindingConfig.listenerTls("test"))
                .build()
                .start();

        try {
            ListenerTlsContext listenerTlsContext = TestTransportBindingProvider.listenerTlsContext("test");

            assertThat(listenerTlsContext, notNullValue());
            assertThat(listenerTlsContext.tls(), is(listenerTls));
            assertThat(listenerTlsContext.virtualHostsEnabled(), is(true));
            assertThat(listenerTlsContext.select("api.example.com").tls(), is(virtualHostTls));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void listenerWithoutTlsRejectsTlsProtectedTransportBinding() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(TestTransportBindingConfig.listenerTls("test"))
                .build()
                .start());

        assertThat(failureMessages(failure), containsString("does not have TLS enabled"));
        assertThat(failureMessages(failure), containsString("requires listener TLS"));
    }

    @Test
    void protocolTransportBindingRequirementMustBeActive() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .addProtocol(new TestRequiredTransportProtocolConfig("test-protocol",
                                                                     Set.of(TestTransportBindingConfig.TYPE)))
                .build()
                .start());

        assertThat(failureMessages(failure), containsString("requires transport binding type(s) [test-transport]"));
        assertThat(failureMessages(failure), containsString("active binding type(s) [tcp]"));
    }

    @Test
    void tcpTransportSkipsProtocolsForOtherTransports() {
        TestRequiredTransportConnectionSelectorProvider.reset();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .addBinding(new TestTransportBindingConfig(TestTransportBindingConfig.TYPE, true))
                .addProtocol(new TestRequiredTransportProtocolConfig("test-protocol",
                                                                     Set.of(TestTransportBindingConfig.TYPE)))
                .build()
                .start();

        try {
            assertThat(TestRequiredTransportConnectionSelectorProvider.creates(), is(0));
        } finally {
            stopUntilStopped(server);
        }
    }

    @Test
    void builderNamedSocketUsesMapKeyAsListenerName() {
        TestRequiredTransportConnectionSelectorProvider.reset();
        WebServerConfig serverConfig = WebServer.builder()
                .shutdownHook(false)
                .putSocket("admin", socket -> socket
                        .address(InetAddress.getLoopbackAddress())
                        .port(0)
                        .addProtocol(new TestRequiredTransportProtocolConfig("test-protocol",
                                                                             Set.of(TransportBindingTypes.TCP))))
                .buildPrototype();

        assertThat(serverConfig.sockets().get("admin").name(), is("admin"));

        WebServer.create(serverConfig);

        assertThat(TestRequiredTransportConnectionSelectorProvider.listenerName(), is("admin"));
    }

    @Test
    void webServerSuspendFailureStopsAllListenersAndPreservesCleanupFailure() throws Exception {
        ThrowingBlockingConnection connection = new ThrowingBlockingConnection(new AtomicInteger(), "suspend close failed");
        QueueingConnectionSelector selector = new QueueingConnectionSelector(connection);
        LifecycleService defaultService = new LifecycleService("default");
        LifecycleService adminService = new LifecycleService("admin", true);
        LifecycleService monitorService = new LifecycleService("monitor");
        LoomServer server = (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .port(0)
                .shutdownGracePeriod(Duration.ZERO)
                .addConnectionSelector(selector)
                .routing(routing -> routing.register(defaultService))
                .putSocket("admin", listener -> listener.port(0)
                        .routing(routing -> routing.register(adminService)))
                .putSocket("monitor", listener -> listener.port(0)
                        .routing(routing -> routing.register(monitorService)))
                .build()
                .start();

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.getOutputStream().write('x');
            assertThat(connection.awaitHandling(), is(true));

            RuntimeException failure = assertThrows(RuntimeException.class, server::suspend);

            assertThat(failure.getMessage(), is("suspend close failed"));
            assertThat(server.isRunning(), is(false));
            assertThat(suppressedFailureMessages(failure), containsString("afterStop failed admin"));
            assertThat(defaultService.afterStops(), is(1));
            assertThat(adminService.afterStops(), is(1));
            assertThat(monitorService.afterStops(), is(1));
        } finally {
            connection.release();
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
                        .addBinding(disabledTcpBinding())
                        .addBinding(udsBinding(socketPath))
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
            assertThat(failureMessages(failure), containsString("Failed to start server"));
            assertThat(server.isRunning(), is(false));
            assertThat(suppressedFailureMessages(failure), containsString("afterStop failed monitor"));
            assertThat(defaultService.afterStops(), is(1));
            assertThat(adminService.afterStops(), is(1));
            assertThat(monitorService.afterStops(), is(1));
            assertThat(Files.readString(socketPath), is("existing"));
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
        assertThat(failureMessages(failure), containsString("cached afterStop failure"));
    }

    private static String failureMessages(Throwable failure) {
        StringBuilder result = new StringBuilder();
        appendFailureMessages(result, failure);
        return result.toString();
    }

    private static String suppressedFailureMessages(Throwable failure) {
        StringBuilder result = new StringBuilder();
        for (Throwable suppressed : failure.getSuppressed()) {
            appendFailureMessages(result, suppressed);
        }
        return result.toString();
    }

    private static void appendFailureMessages(StringBuilder result, Throwable failure) {
        if (failure == null) {
            return;
        }
        if (!result.isEmpty()) {
            result.append(System.lineSeparator());
        }
        result.append(failure.getClass().getName())
                .append(": ")
                .append(failure.getMessage());
        for (Throwable suppressed : failure.getSuppressed()) {
            appendFailureMessages(result, suppressed);
        }
        appendFailureMessages(result, failure.getCause());
    }

    private static boolean containsType(Throwable failure, Class<? extends Throwable> type) {
        if (type.isInstance(failure)) {
            return true;
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            if (containsType(suppressed, type)) {
                return true;
            }
        }
        Throwable cause = failure.getCause();
        return cause != null && containsType(cause, type);
    }

    private static void stopUntilStopped(WebServer server) {
        for (int i = 0; i < 3 && server.isRunning(); i++) {
            try {
                server.stop();
            } catch (RuntimeException _) {
            }
        }
    }

    private static void stopListener(ServerListener listener, AtomicReference<Throwable> failure) {
        try {
            listener.stop();
        } catch (RuntimeException | Error e) {
            failure.set(e);
        }
    }

    private static WebServer startTcpPortReuseServerWithRetry(InetAddress address) {
        RuntimeException lastBindFailure = null;
        for (int i = 0; i < 10; i++) {
            TestTransportBindingProvider.reset();
            try {
                return WebServer.builder()
                        .shutdownHook(false)
                        .address(address)
                        .port(0)
                        .bindingsDiscoverServices(false)
                        .addBinding(new TestTransportBindingConfig("test", true, false, false, false, true))
                        .addBinding(TcpTransportConfig.builder()
                                            .required(true)
                                            .buildPrototype())
                        .build()
                        .start();
            } catch (RuntimeException e) {
                if (!containsType(e, BindException.class)) {
                    throw e;
                }
                lastBindFailure = e;
            }
        }
        throw lastBindFailure;
    }

    private static TcpTransportConfig disabledTcpBinding() {
        return TcpTransportConfig.builder()
                .enabled(false)
                .buildPrototype();
    }

    private static UdsTransportConfig udsBinding(Path socketPath) {
        return UdsTransportConfig.builder()
                .socket(UnixDomainSocketAddress.of(socketPath))
                .required(true)
                .buildPrototype();
    }

    private static WebServerConfig listenerConfigWithoutTcp() {
        return WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(disabledTcpBinding())
                .addBinding(new TestTransportBindingConfig("test", true))
                .buildPrototype();
    }

    private static void assertPortCanBind(int port) throws Exception {
        assertThat(port, greaterThan(0));
        try (ServerSocket _ = new ServerSocket(port, 50, InetAddress.getLoopbackAddress())) {
            // validates that failed startup cleanup released the socket
        }
    }

    private static void assertPortRefusesConnections(int port) throws Exception {
        assertThat(port, greaterThan(0));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 250);
            throw new AssertionError("Listener socket still accepted connections on port " + port);
        } catch (ConnectException | SocketTimeoutException _) {
            // validates that the listener socket is closed while shutdown waits for idle-timeout cleanup
        }
    }

    private static void assertSocketClosed(Socket socket) throws Exception {
        try {
            assertThat(socket.getInputStream().read(), is(-1));
        } catch (SocketException _) {
            // A reset socket is closed from the client perspective.
        }
    }

    private static void waitFor(Duration timeout, BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        throw new AssertionError(message);
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

    private static LoomServer startServer(ServerConnectionSelector selector, Duration shutdownGracePeriod) {
        return (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .shutdownGracePeriod(shutdownGracePeriod)
                .addConnectionSelector(selector)
                .build()
                .start();
    }

    private static LoomServer startServer(ServerConnectionSelector selector,
                                          Duration shutdownGracePeriod,
                                          LifecycleService service) {
        return (LoomServer) WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .shutdownGracePeriod(shutdownGracePeriod)
                .addConnectionSelector(selector)
                .routing(routing -> routing.register(service))
                .build()
                .start();
    }

    private static final class BlockingSocketOptions implements SocketOptions {
        private final SocketOptions delegate = SocketOptions.create();
        private final CountDownLatch configureEntered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void configureSocket(SocketChannel socket) {
            configureEntered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                    // Deliberately ignore interrupts to prove shutdown closes the accepted socket.
                }
            }
            delegate.configureSocket(socket);
        }

        @Override
        public Map<SocketOption<?>, Object> socketOptions() {
            return delegate.socketOptions();
        }

        @Override
        public Duration connectTimeout() {
            return delegate.connectTimeout();
        }

        @Override
        public Duration readTimeout() {
            return delegate.readTimeout();
        }

        @Override
        public Optional<Integer> socketReceiveBufferSize() {
            return delegate.socketReceiveBufferSize();
        }

        @Override
        public Optional<Integer> socketSendBufferSize() {
            return delegate.socketSendBufferSize();
        }

        @Override
        public boolean socketReuseAddress() {
            return delegate.socketReuseAddress();
        }

        @Override
        public boolean socketKeepAlive() {
            return delegate.socketKeepAlive();
        }

        @Override
        public boolean tcpNoDelay() {
            return delegate.tcpNoDelay();
        }

        private boolean awaitConfigure() throws InterruptedException {
            return configureEntered.await(5, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }
    }

    private static final class BlockingAfterStopRouter implements Router {
        private final CountDownLatch afterStopEntered = new CountDownLatch(1);
        private final CountDownLatch releaseAfterStop = new CountDownLatch(1);
        private final AtomicInteger afterStops = new AtomicInteger();

        @Override
        public <T extends Routing> T routing(Class<T> routingType, T defaultValue) {
            return defaultValue;
        }

        @Override
        public void afterStop() {
            afterStops.incrementAndGet();
            afterStopEntered.countDown();
            try {
                releaseAfterStop.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting in router afterStop", e);
            }
        }

        @Override
        public void beforeStart() {
        }

        @Override
        public List<? extends Routing> routings() {
            return List.of();
        }

        private boolean awaitAfterStop() throws InterruptedException {
            return afterStopEntered.await(5, TimeUnit.SECONDS);
        }

        private void releaseAfterStop() {
            releaseAfterStop.countDown();
        }

        private int afterStops() {
            return afterStops.get();
        }
    }

    private static final class QueueingConnectionSelector implements ServerConnectionSelector {
        private final ServerConnection[] connections;
        private final AtomicInteger index = new AtomicInteger();

        private QueueingConnectionSelector(ServerConnection... connections) {
            this.connections = connections;
        }

        @Override
        public int bytesToIdentifyConnection() {
            return 0;
        }

        @Override
        public Support supports(BufferData data) {
            return Support.SUPPORTED;
        }

        @Override
        public Set<String> supportedApplicationProtocols() {
            return Set.of("test-queueing");
        }

        @Override
        public ServerConnection connection(ConnectionContext ctx) {
            int current = index.getAndIncrement();
            if (current >= connections.length) {
                return connections[connections.length - 1];
            }
            return connections[current];
        }
    }

    private static final class TestTransportBindingContext
            implements TransportBindingContext, ListenerContext, ListenerTlsContext, AutoCloseable {
        private final SocketAddress configuredAddress;
        private final ListenerConfig listenerConfig;
        private final Limit connectionLimit;
        private final Limit requestLimit = FixedLimit.create();
        private final Router router = Router.empty();
        private final Context context = Context.builder()
                .id("test-transport-binding-context")
                .build();
        private final MediaContext mediaContext = MediaContext.create();
        private final ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();
        private final DirectHandlers directHandlers = DirectHandlers.create();
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final Tls tls = Tls.builder()
                .enabled(false)
                .build();
        private final AtomicReference<Throwable> bindingFailure = new AtomicReference<>();

        private TestTransportBindingContext(SocketAddress configuredAddress,
                                            ListenerConfig listenerConfig,
                                            Limit connectionLimit) {
            this.configuredAddress = configuredAddress;
            this.listenerConfig = listenerConfig;
            this.connectionLimit = connectionLimit;
        }

        @Override
        public SocketAddress configuredAddress() {
            return configuredAddress;
        }

        @Override
        public ListenerContext listenerContext() {
            return this;
        }

        @Override
        public Router router() {
            return router;
        }

        @Override
        public Limit requestLimit() {
            return requestLimit;
        }

        @Override
        public Limit connectionLimit() {
            return connectionLimit;
        }

        @Override
        public ListenerTlsContext listenerTls() {
            return this;
        }

        @Override
        public void fatalBindingFailure(TransportBinding binding, Throwable cause) {
            bindingFailure.compareAndSet(null, cause);
        }

        @Override
        public Context context() {
            return context;
        }

        @Override
        public MediaContext mediaContext() {
            return mediaContext;
        }

        @Override
        public ContentEncodingContext contentEncodingContext() {
            return contentEncodingContext;
        }

        @Override
        public DirectHandlers directHandlers() {
            return directHandlers;
        }

        @Override
        public ListenerConfig config() {
            return listenerConfig;
        }

        @Override
        public ExecutorService executor() {
            return executor;
        }

        @Override
        public Tls tls() {
            return tls;
        }

        @Override
        public boolean virtualHostsEnabled() {
            return false;
        }

        @Override
        public void validateVirtualHosts() {
        }

        @Override
        public Selection select(String presentedHost) {
            throw new AssertionError("TLS selection is not expected for an unprotected test binding");
        }

        @Override
        public Selection selectWithoutSni() {
            throw new AssertionError("TLS selection is not expected for an unprotected test binding");
        }

        private Throwable bindingFailure() {
            return bindingFailure.get();
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }

    private static final class LatchingConnectionLimit implements Limit {
        private static final String NAME = "test-connection-limit";
        private static final String TYPE = "test-fixed";

        private final int permits;
        private final Semaphore semaphore;
        private final CountDownLatch idleReservations = new CountDownLatch(2);
        private final CountDownLatch fourthAcquireBlocked = new CountDownLatch(1);
        private final AtomicInteger acquireAttempts = new AtomicInteger();
        private final AtomicInteger releases = new AtomicInteger();

        private LatchingConnectionLimit(int permits) {
            this.permits = permits;
            this.semaphore = new Semaphore(permits);
        }

        @Override
        public Limit copy() {
            return new LatchingConnectionLimit(permits);
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public LimitAlgorithm.Outcome tryAcquireOutcome(boolean wait) {
            int attempt = acquireAttempts.incrementAndGet();
            if (semaphore.tryAcquire()) {
                idleReservations.countDown();
                return LimitAlgorithm.Outcome.immediateAcceptance(NAME, TYPE, newToken());
            }
            if (!wait) {
                return LimitAlgorithm.Outcome.immediateRejection(NAME, TYPE);
            }

            long waitStarted = System.nanoTime();
            if (attempt == 4) {
                fourthAcquireBlocked.countDown();
            }
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return LimitAlgorithm.Outcome.deferredRejection(NAME, TYPE, waitStarted, System.nanoTime());
            }
            idleReservations.countDown();
            return LimitAlgorithm.Outcome.deferredAcceptance(NAME,
                                                             TYPE,
                                                             newToken(),
                                                             waitStarted,
                                                             System.nanoTime());
        }

        private LimitAlgorithm.Token newToken() {
            return new LimitAlgorithm.Token() {
                private final AtomicBoolean completed = new AtomicBoolean();

                @Override
                public void dropped() {
                    complete();
                }

                @Override
                public void ignore() {
                    complete();
                }

                @Override
                public void success() {
                    complete();
                }

                private void complete() {
                    if (completed.compareAndSet(false, true)) {
                        releases.incrementAndGet();
                        semaphore.release();
                    }
                }
            };
        }

        private boolean awaitIdleReservations(Duration timeout) throws InterruptedException {
            return idleReservations.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private boolean awaitFourthAcquireBlocked(Duration timeout) throws InterruptedException {
            return fourthAcquireBlocked.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private int availablePermits() {
            return semaphore.availablePermits();
        }

        private int releaseCount() {
            return releases.get();
        }
    }

    private static final class PreRegistrationThenIdleConnectionSelector implements ServerConnectionSelector {
        private final AtomicBoolean preRegistrationSelected = new AtomicBoolean();
        private final ThreadLocal<Boolean> preRegistrationHandler = ThreadLocal.withInitial(() -> false);
        private final CountDownLatch supportsEntered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final BlockingConnection preRegistrationConnection = new BlockingConnection();
        private final IdleBlockingConnection idleConnection = new IdleBlockingConnection();

        @Override
        public int bytesToIdentifyConnection() {
            return 0;
        }

        @Override
        public Support supports(BufferData data) {
            if (preRegistrationSelected.compareAndSet(false, true)) {
                preRegistrationHandler.set(true);
                supportsEntered.countDown();
                while (release.getCount() != 0) {
                    try {
                        release.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException _) {
                        // Deliberately ignore interrupts to prove idle timeout does not close the accepted socket.
                    }
                }
            }
            return Support.SUPPORTED;
        }

        @Override
        public Set<String> supportedApplicationProtocols() {
            return Set.of("test-pre-registration-then-idle");
        }

        @Override
        public ServerConnection connection(ConnectionContext ctx) {
            if (preRegistrationHandler.get()) {
                preRegistrationHandler.remove();
                return preRegistrationConnection;
            }
            return idleConnection;
        }

        private boolean awaitSupports() throws InterruptedException {
            return supportsEntered.await(5, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }

        private BlockingConnection preRegistrationConnection() {
            return preRegistrationConnection;
        }

        private IdleBlockingConnection idleConnection() {
            return idleConnection;
        }
    }

    private static final class ConnectionCreationBlockingSelector implements ServerConnectionSelector {
        private final CountDownLatch connectionCreationEntered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<BlockingConnection> connection = new AtomicReference<>();

        @Override
        public int bytesToIdentifyConnection() {
            return 0;
        }

        @Override
        public Support supports(BufferData data) {
            return Support.SUPPORTED;
        }

        @Override
        public Set<String> supportedApplicationProtocols() {
            return Set.of("test-connection-creation-blocking");
        }

        @Override
        public ServerConnection connection(ConnectionContext ctx) {
            BlockingConnection newConnection = new BlockingConnection();
            connection.set(newConnection);
            connectionCreationEntered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                    // Deliberately ignore interrupts to prove shutdown closes the just-created connection.
                }
            }
            return newConnection;
        }

        private boolean awaitConnectionCreation() throws InterruptedException {
            return connectionCreationEntered.await(5, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }

        private BlockingConnection connection() {
            return connection.get();
        }
    }

    private static final class PreRegistrationConnectionSelector implements ServerConnectionSelector {
        private final CountDownLatch supportsEntered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final BlockingConnection connection = new BlockingConnection();

        @Override
        public int bytesToIdentifyConnection() {
            return 0;
        }

        @Override
        public Support supports(BufferData data) {
            supportsEntered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                    // Deliberately ignore interrupts to prove shutdown closes the accepted socket.
                }
            }
            return Support.SUPPORTED;
        }

        @Override
        public Set<String> supportedApplicationProtocols() {
            return Set.of("test-pre-registration");
        }

        @Override
        public ServerConnection connection(ConnectionContext ctx) {
            return connection;
        }

        private boolean awaitSupports() throws InterruptedException {
            return supportsEntered.await(5, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }

        private BlockingConnection connection() {
            return connection;
        }
    }

    private static final class BlockingConnection implements ServerConnection {
        private final CountDownLatch handling = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger gracefulCloses = new AtomicInteger();
        private final AtomicInteger forcedCloses = new AtomicInteger();
        private final Runnable onHandling;

        private BlockingConnection() {
            this(() -> {});
        }

        private BlockingConnection(Runnable onHandling) {
            this.onHandling = onHandling;
        }

        @Override
        public void handle(io.helidon.common.concurrency.limits.Limit limit) {
            onHandling.run();
            handling.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                    // Deliberately ignore interrupts so shutdown must close the connection.
                }
            }
        }

        @Override
        public Duration idleTime() {
            return Duration.ZERO;
        }

        @Override
        public void close(boolean interrupt) {
            if (interrupt) {
                forcedCloses.incrementAndGet();
            } else {
                gracefulCloses.incrementAndGet();
            }
        }

        private boolean handlingStarted() {
            return handling.getCount() == 0;
        }

        private boolean awaitHandling(Duration timeout) throws InterruptedException {
            return handling.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private int gracefulCloses() {
            return gracefulCloses.get();
        }

        private int forcedCloses() {
            return forcedCloses.get();
        }

        private void release() {
            release.countDown();
        }
    }

    private static final class IdleBlockingConnection implements ServerConnection {
        private final CountDownLatch handling = new CountDownLatch(1);
        private final CountDownLatch gracefulClose = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger forcedCloses = new AtomicInteger();

        @Override
        public void handle(io.helidon.common.concurrency.limits.Limit limit) {
            handling.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                    // Deliberately ignore interrupts so shutdown must close the connection.
                }
            }
        }

        @Override
        public Duration idleTime() {
            return Duration.ofMinutes(1);
        }

        @Override
        public void close(boolean interrupt) {
            if (interrupt) {
                forcedCloses.incrementAndGet();
            } else {
                gracefulClose.countDown();
            }
        }

        private boolean awaitHandling() throws InterruptedException {
            return handling.await(5, TimeUnit.SECONDS);
        }

        private boolean awaitGracefulClose() throws InterruptedException {
            return gracefulClose.await(5, TimeUnit.SECONDS);
        }

        private int forcedCloses() {
            return forcedCloses.get();
        }

        private void release() {
            release.countDown();
        }
    }

    private static final class BlockingIdleCloseConnection implements ServerConnection {
        private final CountDownLatch handling = new CountDownLatch(1);
        private final CountDownLatch idleCloseEntered = new CountDownLatch(1);
        private final CountDownLatch forcedCloseEntered = new CountDownLatch(1);
        private final CountDownLatch releaseIdleClose = new CountDownLatch(1);
        private final CountDownLatch releaseHandling = new CountDownLatch(1);
        private final AtomicInteger gracefulCloses = new AtomicInteger();

        @Override
        public void handle(io.helidon.common.concurrency.limits.Limit limit) {
            handling.countDown();
            while (releaseHandling.getCount() != 0) {
                try {
                    releaseHandling.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                    // Deliberately ignore interrupts so shutdown must close the connection.
                }
            }
        }

        @Override
        public Duration idleTime() {
            return Duration.ofMinutes(1);
        }

        @Override
        public void close(boolean interrupt) {
            if (interrupt) {
                forcedCloseEntered.countDown();
                releaseHandling.countDown();
                return;
            }
            if (gracefulCloses.incrementAndGet() == 1) {
                idleCloseEntered.countDown();
                while (releaseIdleClose.getCount() != 0) {
                    try {
                        releaseIdleClose.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        private boolean awaitHandling() throws InterruptedException {
            return handling.await(5, TimeUnit.SECONDS);
        }

        private boolean awaitIdleClose() throws InterruptedException {
            return idleCloseEntered.await(5, TimeUnit.SECONDS);
        }

        private boolean awaitForcedClose() throws InterruptedException {
            return forcedCloseEntered.await(5, TimeUnit.SECONDS);
        }

        private void releaseIdleClose() {
            releaseIdleClose.countDown();
        }

        private void releaseHandling() {
            releaseHandling.countDown();
        }
    }

    private static final class ThrowingBlockingConnection implements ServerConnection {
        private final CountDownLatch handling = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger closeCalls;
        private final String message;

        private ThrowingBlockingConnection(AtomicInteger closeCalls, String message) {
            this.closeCalls = closeCalls;
            this.message = message;
        }

        @Override
        public void handle(io.helidon.common.concurrency.limits.Limit limit) {
            handling.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                    // Deliberately ignore interrupts so shutdown must close the connection.
                }
            }
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

        private boolean awaitHandling() throws InterruptedException {
            return handling.await(5, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
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
