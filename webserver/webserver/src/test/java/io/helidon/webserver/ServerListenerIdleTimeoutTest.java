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

import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.spi.ServerConnectionSelector;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ServerListenerIdleTimeoutTest {
    @Test
    void idleTimeoutTaskStartsAndStopsWithListener() throws Exception {
        RecordingTimer timer = new RecordingTimer();
        ServerListener listener = newServerListener(timer);

        assertThat(timer.scheduledTasks(), is(0));

        try {
            listener.start();
            TimerTask task = timer.awaitTask(0);

            listener.stop();

            assertThat("idle timeout task should be cancelled by listener stop", wasAlreadyCancelled(task), is(true));
            listener.stop();
        } finally {
            listener.stop();
            timer.cancel();
        }
    }

    @Test
    void suspendCancelsIdleTimeoutTaskAndResumeStartsNewTask() throws Exception {
        RecordingTimer timer = new RecordingTimer();
        ServerListener listener = newServerListener(timer);

        try {
            listener.start();
            TimerTask firstTask = timer.awaitTask(0);

            listener.suspend();

            assertThat("idle timeout task should be cancelled by listener suspend",
                       wasAlreadyCancelled(firstTask),
                       is(true));

            listener.resume();
            TimerTask secondTask = timer.awaitTask(1);

            listener.stop();

            assertThat("resumed idle timeout task should be cancelled by listener stop",
                       wasAlreadyCancelled(secondTask),
                       is(true));
        } finally {
            listener.stop();
            timer.cancel();
        }
    }

    @Test
    void stopCancelsIdleTimeoutTaskBeforeClosingConnections() throws Exception {
        lifecycleCancelsIdleTimeoutTaskBeforeClosingConnections(false);
    }

    @Test
    void suspendCancelsIdleTimeoutTaskBeforeClosingConnections() throws Exception {
        lifecycleCancelsIdleTimeoutTaskBeforeClosingConnections(true);
    }

    @Test
    void stopWaitsForRunningIdleTimeoutTaskWhenTimerPurgeFails() throws Exception {
        lifecycleWaitsForRunningIdleTimeoutTaskWhenTimerPurgeFails(false);
    }

    @Test
    void suspendWaitsForRunningIdleTimeoutTaskWhenTimerPurgeFails() throws Exception {
        lifecycleWaitsForRunningIdleTimeoutTaskWhenTimerPurgeFails(true);
    }

    @Test
    void cancelWaitsForRunningIdleTimeoutTask() throws Exception {
        RecordingTimer timer = new RecordingTimer();
        CountDownLatch blockingCloseEntered = new CountDownLatch(1);
        CountDownLatch releaseBlockingClose = new CountDownLatch(1);
        IdleTimeoutHandler handler = new IdleTimeoutHandler(timer,
                                                            listenerConfig(),
                                                            () -> List.of(blockingConnectionHandler(blockingCloseEntered,
                                                                                                    releaseBlockingClose)));
        CountDownLatch cancelDone = new CountDownLatch(1);

        Thread runThread = Thread.ofPlatform()
                .name("test-idle-timeout-run")
                .start(handler::run);
        assertThat(blockingCloseEntered.await(5, TimeUnit.SECONDS), is(true));

        Thread cancelThread = Thread.ofPlatform()
                .name("test-idle-timeout-cancel")
                .start(() -> {
                    handler.cancelAndAwait();
                    cancelDone.countDown();
                });

        try {
            waitFor(Duration.ofSeconds(5),
                    () -> handler.awaitingRunCompletion() || cancelDone.getCount() == 0,
                    "idle timeout cancellation did not try to wait for the running task");
            assertThat("idle timeout cancellation should wait for the running task",
                       handler.awaitingRunCompletion(),
                       is(true));
            assertThat(cancelDone.getCount(), is(1L));

            releaseBlockingClose.countDown();
            runThread.join(TimeUnit.SECONDS.toMillis(5));
            cancelThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(runThread.isAlive(), is(false));
            assertThat(cancelThread.isAlive(), is(false));
            assertThat(cancelDone.getCount(), is(0L));
        } finally {
            releaseBlockingClose.countDown();
            timer.cancel();
        }
    }

    private static void lifecycleWaitsForRunningIdleTimeoutTaskWhenTimerPurgeFails(boolean suspend) throws Exception {
        ThrowingPurgeTimer timer = new ThrowingPurgeTimer();
        PurgeFailureConnection connection = new PurgeFailureConnection();
        ServerListener listener = newServerListener(timer, new SingleConnectionSelector(connection));
        AtomicReference<Throwable> lifecycleFailure = new AtomicReference<>();
        CountDownLatch lifecycleDone = new CountDownLatch(1);
        String lifecycle = suspend ? "suspend" : "stop";
        Thread runThread = null;
        Thread lifecycleThread = null;

        try {
            listener.start();
            IdleTimeoutHandler handler = (IdleTimeoutHandler) timer.awaitTask(0);

            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
                socket.getOutputStream().write('x');
                assertThat(connection.awaitHandling(), is(true));

                runThread = Thread.ofPlatform()
                        .name("test-idle-timeout-purge-failure-run")
                        .start(handler::run);
                assertThat(connection.awaitIdleClose(), is(true));

                lifecycleThread = Thread.ofPlatform()
                        .name("test-idle-timeout-purge-failure-" + lifecycle)
                        .start(() -> {
                            try {
                                if (suspend) {
                                    listener.suspend();
                                } else {
                                    listener.stop();
                                }
                            } catch (RuntimeException | Error e) {
                                lifecycleFailure.set(e);
                            } finally {
                                lifecycleDone.countDown();
                            }
                        });

                assertThat(connection.awaitForcedClose(), is(true));
                waitFor(Duration.ofSeconds(5),
                        () -> handler.awaitingRunCompletion() || lifecycleDone.getCount() == 0,
                        lifecycle + " did not try to wait for the running idle timeout task");
                assertThat(lifecycle + " should wait for the running idle timeout task after purge failure",
                           handler.awaitingRunCompletion(),
                           is(true));
                assertThat(lifecycleDone.getCount(), is(1L));

                connection.releaseIdleClose();
                runThread.join(TimeUnit.SECONDS.toMillis(5));
                lifecycleThread.join(TimeUnit.SECONDS.toMillis(5));

                assertThat(runThread.isAlive(), is(false));
                assertThat(lifecycleThread.isAlive(), is(false));
                assertThat(timer.purgeCalled(), is(true));
                assertThat(lifecycleFailure.get(), notNullValue());
            }
        } finally {
            connection.releaseIdleClose();
            connection.releaseHandling();
            if (runThread != null && runThread.isAlive()) {
                runThread.interrupt();
            }
            if (lifecycleThread != null && lifecycleThread.isAlive()) {
                lifecycleThread.interrupt();
            }
            listener.stop();
            timer.cancel();
        }
    }

    private static void lifecycleCancelsIdleTimeoutTaskBeforeClosingConnections(boolean suspend) throws Exception {
        RecordingTimer timer = new RecordingTimer();
        AtomicReference<TimerTask> idleTimeoutTask = new AtomicReference<>();
        CancelObservedConnection connection =
                new CancelObservedConnection(() -> wasAlreadyCancelled(idleTimeoutTask.get()));
        ServerListener listener = newServerListener(timer, new SingleConnectionSelector(connection));
        AtomicReference<Throwable> lifecycleFailure = new AtomicReference<>();
        String lifecycle = suspend ? "suspend" : "stop";
        Thread lifecycleThread = null;

        try {
            listener.start();
            idleTimeoutTask.set(timer.awaitTask(0));

            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
                socket.getOutputStream().write('x');
                assertThat(connection.awaitHandling(), is(true));

                lifecycleThread = Thread.ofPlatform()
                        .name("test-idle-timeout-cancel-before-" + lifecycle + "-close")
                        .start(() -> {
                            try {
                                if (suspend) {
                                    listener.suspend();
                                } else {
                                    listener.stop();
                                }
                            } catch (RuntimeException | Error e) {
                                lifecycleFailure.set(e);
                            }
                        });

                assertThat(connection.awaitClose(), is(true));
                assertThat("idle timeout task should be cancelled before listener closes connections",
                           connection.closeSawCancelledIdleTimeoutTask(),
                           is(true));

                connection.releaseClose();
                lifecycleThread.join(TimeUnit.SECONDS.toMillis(5));

                assertThat(lifecycleThread.isAlive(), is(false));
                assertThat(lifecycleFailure.get(), nullValue());
            }
        } finally {
            connection.releaseClose();
            connection.releaseHandling();
            if (lifecycleThread != null && lifecycleThread.isAlive()) {
                lifecycleThread.interrupt();
            }
            listener.stop();
            timer.cancel();
        }
    }

    private static ServerListener newServerListener(RecordingTimer timer) throws Exception {
        WebServerConfig config = listenerConfig(null);

        return newServerListener(timer, config);
    }

    private static ServerListener newServerListener(RecordingTimer timer, ServerConnectionSelector selector) throws Exception {
        WebServerConfig config = listenerConfig(selector);

        return newServerListener(timer, config);
    }

    private static ServerListener newServerListener(RecordingTimer timer, WebServerConfig config) throws Exception {
        return new ServerListener(WebServer.DEFAULT_SOCKET_NAME,
                                  config,
                                  Router.empty(),
                                  Context.builder()
                                          .id("idle-timeout-lifecycle-test")
                                          .build(),
                                  timer,
                                  MediaContext.create(),
                                  ContentEncodingContext.create(),
                                  DirectHandlers.create(),
                                  (failedListener, _) -> failedListener.stop());
    }

    private static WebServerConfig listenerConfig() {
        return listenerConfig(null);
    }

    private static WebServerConfig listenerConfig(ServerConnectionSelector selector) {
        var builder = WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .shutdownGracePeriod(Duration.ZERO)
                .idleConnectionPeriod(Duration.ofDays(1));
        if (selector != null) {
            builder.addConnectionSelector(selector);
        }
        return builder.buildPrototype();
    }

    private static ConnectionHandler blockingConnectionHandler(CountDownLatch blockingCloseEntered,
                                                              CountDownLatch releaseBlockingClose) {
        ConnectionHandler handler = mock(ConnectionHandler.class);
        doAnswer(_ -> {
            blockingCloseEntered.countDown();
            assertThat(releaseBlockingClose.await(5, TimeUnit.SECONDS), is(true));
            return null;
        }).when(handler).closeIfIdle(any(Duration.class));
        return handler;
    }

    private static boolean wasAlreadyCancelled(TimerTask task) {
        return !task.cancel();
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

    private static class RecordingTimer extends Timer {
        private final List<TimerTask> tasks = new CopyOnWriteArrayList<>();

        private RecordingTimer() {
            super("idle-timeout-lifecycle-test", true);
        }

        @Override
        public void schedule(TimerTask task, long delay, long period) {
            tasks.add(task);
            super.schedule(task, delay, period);
        }

        private int scheduledTasks() {
            return tasks.size();
        }

        TimerTask awaitTask(int index) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                if (tasks.size() > index) {
                    return tasks.get(index);
                }
                TimeUnit.MILLISECONDS.sleep(10);
            }
            throw new AssertionError("Idle timeout task was not scheduled");
        }
    }

    private static final class ThrowingPurgeTimer extends RecordingTimer {
        private final CountDownLatch purgeCalled = new CountDownLatch(1);

        @Override
        public int purge() {
            purgeCalled.countDown();
            throw new IllegalStateException("timer purge failure");
        }

        private boolean purgeCalled() {
            return purgeCalled.getCount() == 0;
        }
    }

    private static final class SingleConnectionSelector implements ServerConnectionSelector {
        private final ServerConnection connection;

        private SingleConnectionSelector(ServerConnection connection) {
            this.connection = connection;
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
            return Set.of("test-idle-timeout-cancel-before-close");
        }

        @Override
        public ServerConnection connection(ConnectionContext ctx) {
            return connection;
        }
    }

    private static final class PurgeFailureConnection implements ServerConnection {
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
            return Duration.ofDays(1);
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

    private static final class CancelObservedConnection implements ServerConnection {
        private final BooleanSupplier idleTimeoutTaskCancelled;
        private final CountDownLatch handling = new CountDownLatch(1);
        private final CountDownLatch closeEntered = new CountDownLatch(1);
        private final CountDownLatch releaseClose = new CountDownLatch(1);
        private final CountDownLatch releaseHandling = new CountDownLatch(1);
        private final AtomicBoolean closeSawCancelledIdleTimeoutTask = new AtomicBoolean();

        private CancelObservedConnection(BooleanSupplier idleTimeoutTaskCancelled) {
            this.idleTimeoutTaskCancelled = idleTimeoutTaskCancelled;
        }

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
            return Duration.ZERO;
        }

        @Override
        public void close(boolean interrupt) {
            if (interrupt) {
                releaseClose.countDown();
                releaseHandling.countDown();
                return;
            }
            closeSawCancelledIdleTimeoutTask.set(idleTimeoutTaskCancelled.getAsBoolean());
            closeEntered.countDown();
            while (releaseClose.getCount() != 0) {
                try {
                    releaseClose.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private boolean awaitHandling() throws InterruptedException {
            return handling.await(5, TimeUnit.SECONDS);
        }

        private boolean awaitClose() throws InterruptedException {
            return closeEntered.await(5, TimeUnit.SECONDS);
        }

        private boolean closeSawCancelledIdleTimeoutTask() {
            return closeSawCancelledIdleTimeoutTask.get();
        }

        private void releaseClose() {
            releaseClose.countDown();
        }

        private void releaseHandling() {
            releaseHandling.countDown();
        }
    }
}
