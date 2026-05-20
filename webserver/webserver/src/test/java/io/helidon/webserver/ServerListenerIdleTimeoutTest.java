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
import java.time.Duration;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.context.Context;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.http.DirectHandlers;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
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
            assertThat(cancelDone.await(100, TimeUnit.MILLISECONDS), is(false));

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

    private static ServerListener newServerListener(RecordingTimer timer) throws Exception {
        WebServerConfig config = listenerConfig();

        return new ServerListener(WebServer.DEFAULT_SOCKET_NAME,
                                  config,
                                  Router.empty(),
                                  Context.builder()
                                          .id("idle-timeout-lifecycle-test")
                                          .build(),
                                  timer,
                                  MediaContext.create(),
                                  ContentEncodingContext.create(),
                                  DirectHandlers.create());
    }

    private static WebServerConfig listenerConfig() {
        return WebServer.builder()
                .shutdownHook(false)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .shutdownGracePeriod(Duration.ZERO)
                .idleConnectionPeriod(Duration.ofDays(1))
                .buildPrototype();
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

    private static final class RecordingTimer extends Timer {
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

        private TimerTask awaitTask(int index) throws InterruptedException {
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
}
