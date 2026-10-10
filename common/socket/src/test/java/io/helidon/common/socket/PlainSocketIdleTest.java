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

package io.helidon.common.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlainSocketIdleTest {

    @Test
    void unlimitedIdleMonitorSurvivesMultiplePollingTimeoutsAndRestoresTimeout() throws Exception {
        var polls = new AtomicInteger();
        var timeout = new AtomicInteger();
        var monitorReadStarted = new CountDownLatch(1);
        var releaseMonitor = new CountDownLatch(1);
        InputStream inputStream = mock(InputStream.class);
        doAnswer(_ -> {
            if (polls.incrementAndGet() <= 3) {
                throw new SocketTimeoutException("Expected idle polling timeout");
            }
            monitorReadStarted.countDown();
            await(releaseMonitor);
            return 42;
        }).when(inputStream).read();
        doAnswer(_ -> {
            releaseMonitor.countDown();
            return null;
        }).when(inputStream).close();
        PlainSocket socket = socket(inputStream, timeout);
        var result = new CompletableFuture<byte[]>();
        Thread caller = null;

        try {
            socket.idle();
            assertThat("Unlimited monitor stopped before its fourth poll",
                       monitorReadStarted.await(5, TimeUnit.SECONDS), is(true));
            caller = Thread.ofPlatform().name("unlimited-idle-read").start(() -> {
                try {
                    result.complete(socket.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
            awaitWaiting(caller);
            releaseMonitor.countDown();

            assertThat("Read lost the byte obtained by the idle monitor",
                       result.get(5, TimeUnit.SECONDS), is(new byte[] {42}));
            assertThat("Idle polling count", polls.get(), is(4));
            assertThat("Unlimited socket timeout was not restored", timeout.get(), is(0));
        } finally {
            releaseMonitor.countDown();
            socket.close();
            if (caller != null) {
                caller.interrupt();
                assertThat("Unlimited idle read caller did not terminate", caller.join(Duration.ofSeconds(5)), is(true));
            }
        }
    }

    @Test
    void cancellingOnlyIdlePollPreservesReadAndRestoresTimeout() throws Exception {
        assertCancellingFinalIdlePollPreservesReadAndRestoresTimeout(101);
    }

    @Test
    void cancellingSecondIdlePollPreservesReadAndRestoresTimeout() throws Exception {
        assertCancellingFinalIdlePollPreservesReadAndRestoresTimeout(202);
    }

    @Test
    void uncancelledFiniteIdleTimeoutStillExpires() throws Exception {
        var expected = new SocketTimeoutException("Expected finite idle timeout");
        var pollStarted = new CountDownLatch(1);
        var polls = new AtomicInteger();
        InputStream inputStream = mock(InputStream.class);
        doAnswer(_ -> {
            polls.incrementAndGet();
            pollStarted.countDown();
            throw expected;
        }).when(inputStream).read();
        PlainSocket socket = socket(inputStream, new AtomicInteger(101));

        try {
            socket.idle();
            assertThat("Finite idle poll did not start", pollStarted.await(5, TimeUnit.SECONDS), is(true));
            // Wait for observable closure before get(), so this read cannot cancel an unfinished poll.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (socket.isConnected() && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            assertThat("Finite idle timeout did not close the socket", socket.isConnected(), is(false));

            UncheckedIOException actual = assertThrows(UncheckedIOException.class, socket::get);
            assertThat(actual.getCause(), sameInstance(expected));
            assertThat("Finite idle timeout exceeded its polling budget", polls.get(), is(1));
        } finally {
            socket.close();
        }
    }

    @Test
    void idleMonitorIoFailurePropagatesWithoutAdditionalWrapping() throws Exception {
        var monitorReadStarted = new CountDownLatch(1);
        var expected = new IOException("Expected monitor failure");
        InputStream inputStream = mock(InputStream.class);
        doAnswer(_ -> {
            monitorReadStarted.countDown();
            throw expected;
        }).when(inputStream).read();
        PlainSocket socket = socket(inputStream);

        try {
            socket.idle();
            assertThat("Socket monitor did not start", monitorReadStarted.await(10, TimeUnit.SECONDS), is(true));

            UncheckedIOException actual = assertThrows(UncheckedIOException.class, socket::get);

            assertThat(actual.getCause(), sameInstance(expected));
        } finally {
            socket.close();
        }
    }

    @Test
    void interruptedIdleWaitRestoresFlagAndPropagatesAsIo() throws Exception {
        var monitorReadStarted = new CountDownLatch(1);
        var releaseMonitor = new CountDownLatch(1);
        InputStream inputStream = mock(InputStream.class);
        doAnswer(_ -> {
            monitorReadStarted.countDown();
            try {
                releaseMonitor.await();
                return -1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }).when(inputStream).read();
        doAnswer(_ -> {
            releaseMonitor.countDown();
            return null;
        }).when(inputStream).close();
        PlainSocket socket = socket(inputStream);
        UncheckedIOException actual;
        boolean interrupted;

        try {
            socket.idle();
            assertThat("Socket monitor did not start", monitorReadStarted.await(10, TimeUnit.SECONDS), is(true));

            Thread.currentThread().interrupt();
            actual = assertThrows(UncheckedIOException.class, socket::get);
            interrupted = Thread.currentThread().isInterrupted();
        } finally {
            Thread.interrupted();
            socket.close();
        }

        assertThat("Interrupted status was not restored", interrupted, is(true));
        assertThat(actual.getCause(), instanceOf(InterruptedIOException.class));
        assertThat(actual.getCause().getCause(), instanceOf(InterruptedException.class));
    }

    private static void assertCancellingFinalIdlePollPreservesReadAndRestoresTimeout(int initialTimeout) throws Exception {
        var polls = new AtomicInteger();
        var bulkReads = new AtomicInteger();
        var timeout = new AtomicInteger(initialTimeout);
        var finalPollStarted = new CountDownLatch(1);
        var releaseFinalPoll = new CountDownLatch(1);
        int finalPoll = initialTimeout / 101;
        InputStream inputStream = mock(InputStream.class);
        doAnswer(_ -> {
            if (polls.incrementAndGet() == finalPoll) {
                finalPollStarted.countDown();
                await(releaseFinalPoll);
            }
            throw new SocketTimeoutException("Expected final idle polling timeout");
        }).when(inputStream).read();
        doAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(0);
            int offset = invocation.getArgument(1);
            bytes[offset] = 42;
            bulkReads.incrementAndGet();
            return 1;
        }).when(inputStream).read(any(byte[].class), anyInt(), anyInt());
        doAnswer(_ -> {
            releaseFinalPoll.countDown();
            return null;
        }).when(inputStream).close();
        PlainSocket socket = socket(inputStream, timeout);
        var result = new CompletableFuture<byte[]>();
        Thread caller = null;

        try {
            socket.idle();
            assertThat("Idle monitor did not reach the final poll", finalPollStarted.await(5, TimeUnit.SECONDS), is(true));
            caller = Thread.ofPlatform().name("cancel-final-idle-poll").start(() -> {
                try {
                    result.complete(socket.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
            // This fresh caller has no other waits: blocking means its socket read is awaiting the monitor.
            awaitWaiting(caller);
            releaseFinalPoll.countDown();

            assertThat("Cancelled polling timeout prevented the application read",
                       result.get(5, TimeUnit.SECONDS), is(new byte[] {42}));
            assertThat("Read did not resume on the underlying input", bulkReads.get(), is(1));
            assertThat("Idle polling count", polls.get(), is(finalPoll));
            assertThat("Finite socket timeout was not restored", timeout.get(), is(initialTimeout));
        } finally {
            releaseFinalPoll.countDown();
            socket.close();
            if (caller != null) {
                caller.interrupt();
                assertThat("Cancelled idle read caller did not terminate", caller.join(Duration.ofSeconds(5)), is(true));
            }
        }
    }

    private static PlainSocket socket(InputStream inputStream) throws IOException {
        return socket(inputStream, new AtomicInteger(30_000));
    }

    private static PlainSocket socket(InputStream inputStream, AtomicInteger timeout) throws IOException {
        Socket delegate = mock(Socket.class);
        when(delegate.getInputStream()).thenReturn(inputStream);
        when(delegate.getOutputStream()).thenReturn(OutputStream.nullOutputStream());
        when(delegate.getSoTimeout()).thenAnswer(_ -> timeout.get());
        doAnswer(invocation -> {
            timeout.set(invocation.getArgument(0));
            return null;
        }).when(delegate).setSoTimeout(anyInt());
        return PlainSocket.client(delegate, "test");
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for test input gate");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted test input gate", failure);
        }
    }

    private static void awaitWaiting(Thread caller) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (caller.getState() != Thread.State.WAITING && caller.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertThat("Application socket read did not await the in-progress idle monitor", caller.getState(),
                   is(Thread.State.WAITING));
    }
}
