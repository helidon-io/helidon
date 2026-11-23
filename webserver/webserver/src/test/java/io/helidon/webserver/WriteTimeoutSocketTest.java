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

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.SocketWriterException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WriteTimeoutSocketTest {
    @Test
    void eachWriteGetsItsOwnDeadline() {
        var fixture = new Fixture();
        doAnswer(invocation -> {
            fixture.clock.addAndGet(9);
            return null;
        }).when(fixture.delegate).write(any());

        fixture.socket.write(BufferData.create("first"));
        fixture.socket.write(BufferData.create("second"));

        assertThat(fixture.clock.get(), is(18L));
        verify(fixture.abort, never()).run();
        assertThat(fixture.tasks.size(), is(1));
        verify(fixture.future, never()).cancel(false);
    }

    @Test
    void lateTimerCannotAbortSubsequentWrite() {
        var fixture = new Fixture();
        fixture.socket.write(BufferData.create("first"));
        fixture.clock.set(9);
        doAnswer(invocation -> {
            fixture.clock.set(10);
            fixture.tasks.getFirst().run();
            return null;
        }).when(fixture.delegate).write(any());

        fixture.socket.write(BufferData.create("second"));

        verify(fixture.abort, never()).run();
        assertThat(fixture.tasks.size(), is(2));
    }

    @Test
    void expiryAbortsAndFailsWrite() {
        var fixture = new Fixture();
        doAnswer(invocation -> {
            fixture.clock.set(10);
            fixture.tasks.getFirst().run();
            return null;
        }).when(fixture.delegate).write(any());

        var failure = assertThrows(SocketWriterException.class,
                                   () -> fixture.socket.write(BufferData.create("data")));

        assertThat(failure.getCause(), instanceOf(SocketTimeoutException.class));
        verify(fixture.abort, timeout(5000)).run();
        assertThrows(SocketWriterException.class, () -> fixture.socket.write(BufferData.create("more")));
        verify(fixture.delegate).write(any());
    }

    @Test
    void completedWritePastDeadlineStillFailsWhenTimerIsDelayed() {
        var fixture = new Fixture();
        doAnswer(invocation -> {
            fixture.clock.set(11);
            return null;
        }).when(fixture.delegate).write(any());

        assertThrows(SocketWriterException.class, () -> fixture.socket.write(BufferData.create("data")));
        fixture.tasks.getFirst().run();

        verify(fixture.abort).run();
    }

    @Test
    void ordinaryWriteFailureIsPreserved() {
        var fixture = new Fixture();
        var failure = new IllegalStateException("failed");
        doThrow(failure).when(fixture.delegate).write(any());

        assertThat(assertThrows(IllegalStateException.class,
                                () -> fixture.socket.write(BufferData.create("data"))), sameInstance(failure));
        fixture.tasks.getFirst().run();

        verify(fixture.abort, never()).run();
    }

    @Test
    void idleTimerStopsUntilTheNextWrite() {
        var fixture = new Fixture();
        fixture.socket.write(BufferData.create("first"));
        fixture.clock.set(10);

        fixture.tasks.getFirst().run();

        assertThat(fixture.tasks.size(), is(1));
        fixture.socket.write(BufferData.create("second"));
        assertThat(fixture.tasks.size(), is(2));
        verify(fixture.abort, never()).run();
    }

    @Test
    void closeCancelsTimerAndStaleCallbackCannotAbort() {
        var fixture = new Fixture();
        fixture.socket.write(BufferData.create("data"));

        fixture.socket.close();
        fixture.clock.set(100);
        fixture.tasks.getFirst().run();

        verify(fixture.future).cancel(false);
        verify(fixture.delegate).close();
        verify(fixture.abort, never()).run();
        assertThat(fixture.tasks.size(), is(1));
    }

    @Test
    void closeKeepsActiveWriteDeadlineUntilDelegateCloseReturns() {
        var fixture = new Fixture();
        doAnswer(invocation -> {
            fixture.socket.close();
            return null;
        }).when(fixture.delegate).write(any());
        doAnswer(invocation -> {
            fixture.clock.set(10);
            fixture.tasks.getFirst().run();
            return null;
        }).when(fixture.delegate).close();

        assertThrows(SocketWriterException.class, () -> fixture.socket.write(BufferData.create("data")));

        verify(fixture.abort, timeout(5000)).run();
    }

    @Test
    void failedCloseKeepsActiveWriteDeadline() {
        var fixture = new Fixture();
        doThrow(new IllegalStateException("close failed")).when(fixture.delegate).close();
        doAnswer(invocation -> {
            assertThrows(IllegalStateException.class, fixture.socket::close);
            fixture.clock.set(10);
            fixture.tasks.getFirst().run();
            return null;
        }).when(fixture.delegate).write(any());

        assertThrows(SocketWriterException.class, () -> fixture.socket.write(BufferData.create("data")));

        verify(fixture.abort, timeout(5000)).run();
    }

    @Test
    void oldestOverlappingWriteKeepsItsDeadline() {
        var fixture = new Fixture();
        doAnswer(invocation -> {
            if (fixture.clock.get() == 0) {
                fixture.clock.set(1);
                assertThrows(SocketWriterException.class,
                             () -> fixture.socket.write(BufferData.create("second")));
            } else {
                fixture.clock.set(10);
                fixture.tasks.getFirst().run();
            }
            return null;
        }).when(fixture.delegate).write(any());

        assertThrows(SocketWriterException.class, () -> fixture.socket.write(BufferData.create("first")));

        verify(fixture.abort, timeout(5000)).run();
        assertThat(fixture.tasks.size(), is(1));
    }

    @Test
    void overlappingWriteRetainsDeadlineAfterFirstWriteCompletes() throws Exception {
        var fixture = new Fixture();
        var secondStarted = new CountDownLatch(1);
        var finishSecond = new CountDownLatch(1);
        var writes = new AtomicInteger();
        var secondWrite = new FutureTask<Void>(() -> {
            fixture.socket.write(BufferData.create("second"));
            return null;
        });
        var secondThread = Thread.ofVirtual().unstarted(secondWrite);
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 1) {
                fixture.clock.set(1);
                secondThread.start();
                assertThat("Second write started", secondStarted.await(5, TimeUnit.SECONDS), is(true));
            } else {
                secondStarted.countDown();
                assertThat("Second write released", finishSecond.await(5, TimeUnit.SECONDS), is(true));
            }
            return null;
        }).when(fixture.delegate).write(any());
        try {
            fixture.socket.write(BufferData.create("first"));
            fixture.clock.set(10);
            fixture.tasks.getFirst().run();
            assertThat(fixture.tasks.size(), is(2));
            verify(fixture.abort, never()).run();

            fixture.clock.set(11);
            fixture.tasks.get(1).run();
            finishSecond.countDown();

            ExecutionException failure = assertThrows(ExecutionException.class,
                                                      () -> secondWrite.get(5, TimeUnit.SECONDS));
            assertThat(failure.getCause(), instanceOf(SocketWriterException.class));
            verify(fixture.abort, timeout(5000)).run();
        } finally {
            finishSecond.countDown();
            secondThread.join(Duration.ofSeconds(5));
            assertThat("Second writer terminated", secondThread.isAlive(), is(false));
        }
    }

    @Test
    void rejectedRearmPreservesActiveWriteDeadline() {
        var fixture = new Fixture();
        var aborted = new CountDownLatch(1);
        doAnswer(invocation -> {
            aborted.countDown();
            return null;
        }).when(fixture.abort).run();
        fixture.socket.write(BufferData.create("first"));
        fixture.clock.set(9);
        when(fixture.scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenAnswer(invocation -> {
                    fixture.clock.set(19);
                    throw new RejectedExecutionException("stopped");
                });
        doAnswer(invocation -> {
            fixture.clock.set(10);
            fixture.tasks.getFirst().run();
            assertThat("Fallback timer aborted active write", aborted.await(5, TimeUnit.SECONDS), is(true));
            return null;
        }).when(fixture.delegate).write(any());

        var failure = assertThrows(SocketWriterException.class,
                                   () -> fixture.socket.write(BufferData.create("second")));

        assertThat(failure.getCause(), instanceOf(SocketTimeoutException.class));
        verify(fixture.abort, timeout(5000)).run();
    }

    @Test
    void rejectedInitialScheduleDoesNotWriteOrAbort() {
        var fixture = new Fixture();
        when(fixture.scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new RejectedExecutionException("stopped"));

        assertThrows(RejectedExecutionException.class, () -> fixture.socket.write(BufferData.create("data")));

        verify(fixture.delegate, never()).write(any());
        verify(fixture.abort, never()).run();
    }

    private static final class Fixture {
        private final HelidonSocket delegate = mock(HelidonSocket.class);
        private final Runnable abort = mock(Runnable.class);
        private final AtomicLong clock = new AtomicLong();
        private final List<Runnable> tasks = new ArrayList<>();
        private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
        private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        private final WriteTimeoutSocket socket;

        private Fixture() {
            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS))).thenAnswer(invocation -> {
                tasks.add(invocation.getArgument(0));
                return future;
            });
            socket = new WriteTimeoutSocket(delegate, Duration.ofNanos(10), scheduler, abort, clock::get);
        }
    }
}
