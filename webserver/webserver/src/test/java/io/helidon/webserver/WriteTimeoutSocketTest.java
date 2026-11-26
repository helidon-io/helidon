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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.common.socket.TlsNioSocket;

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
import static org.mockito.Mockito.withSettings;

class WriteTimeoutSocketTest {
    @Test
    void eachWriteGetsItsOwnDeadline() {
        var fixture = new Fixture();
        doAnswer(_ -> {
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
        doAnswer(_ -> {
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
        doAnswer(_ -> {
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
        doAnswer(_ -> {
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
        doAnswer(_ -> {
            fixture.socket.close();
            return null;
        }).when(fixture.delegate).write(any());
        doAnswer(_ -> {
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
        doAnswer(_ -> {
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
        doAnswer(_ -> {
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
        doAnswer(_ -> {
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
        doAnswer(_ -> {
            aborted.countDown();
            return null;
        }).when(fixture.abort).run();
        fixture.socket.write(BufferData.create("first"));
        fixture.clock.set(9);
        when(fixture.scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenAnswer(_ -> {
                    fixture.clock.set(19);
                    throw new RejectedExecutionException("stopped");
                });
        doAnswer(_ -> {
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

    @Test
    void closeNotifyKeepsOneDeadlineAcrossPartialWrites() throws Exception {
        var tls = new TlsFixture(true);
        var fixture = tls.fixture;
        var aborted = new CountDownLatch(1);
        doAnswer(_ -> {
            aborted.countDown();
            return null;
        }).when(fixture.abort).run();
        when(tls.channel.write(any(ByteBuffer.class))).thenAnswer(invocation -> {
            ByteBuffer buffer = invocation.getArgument(0);
            if (fixture.clock.addAndGet(4) >= 10) {
                fixture.tasks.getFirst().run();
                assertThat("deadline must abort while the final part is still writing",
                           aborted.await(5, TimeUnit.SECONDS), is(true));
            }
            buffer.get();
            return 1;
        });

        var failure = assertThrows(SocketWriterException.class, fixture.socket::close);

        assertThat(failure.getCause(), instanceOf(SocketTimeoutException.class));
        assertThat(fixture.clock.get(), is(12L));
        verify(fixture.abort).run();
    }

    @Test
    void successfulCloseControlWriteCancelsItsTimer() throws Exception {
        var tls = new TlsFixture(true);
        var fixture = tls.fixture;
        when(tls.channel.write(any(ByteBuffer.class))).thenAnswer(invocation -> {
            fixture.clock.set(9);
            return drain(invocation.getArgument(0));
        });

        fixture.socket.close();
        fixture.clock.set(100);
        fixture.tasks.getFirst().run();

        verify(fixture.future).cancel(false);
        verify(fixture.abort, never()).run();
    }

    @Test
    void readDrivenControlWriteHasItsOwnDeadline() throws Exception {
        var tls = new TlsFixture(false);
        var fixture = tls.fixture;
        when(tls.channel.read(any(ByteBuffer.class))).thenAnswer(invocation -> {
            // Time spent waiting for peer input precedes the outbound control buffer.
            fixture.clock.set(100);
            assertThat(fixture.tasks.size(), is(0));
            ByteBuffer buffer = invocation.getArgument(0);
            buffer.put((byte) 1);
            return 1;
        });
        when(tls.channel.write(any(ByteBuffer.class))).thenAnswer(invocation -> {
            fixture.clock.set(110);
            fixture.tasks.getFirst().run();
            return drain(invocation.getArgument(0));
        });

        var failure = assertThrows(SocketWriterException.class, fixture.socket::get);

        assertThat(failure.getCause(), instanceOf(SocketTimeoutException.class));
        verify(fixture.abort, timeout(5000)).run();
    }

    @Test
    void earlierApplicationDeadlineExpiresDuringControlWrite() throws Exception {
        var tls = new TlsFixture(false);
        var fixture = tls.fixture;
        when(tls.engine.getHandshakeStatus()).thenAnswer(_ -> {
            fixture.clock.set(5);
            return SSLEngineResult.HandshakeStatus.NEED_WRAP;
        });
        when(tls.channel.write(any(ByteBuffer.class))).thenAnswer(invocation -> {
            fixture.clock.set(10);
            fixture.tasks.getFirst().run();
            return drain(invocation.getArgument(0));
        });

        assertThrows(SocketWriterException.class, () -> fixture.socket.write(BufferData.create("data")));

        verify(fixture.abort, timeout(5000)).run();
    }

    private static int drain(ByteBuffer buffer) {
        int remaining = buffer.remaining();
        buffer.position(buffer.limit());
        return remaining;
    }

    private static final class Fixture {
        private final HelidonSocket delegate;
        private final Runnable abort = mock(Runnable.class);
        private final AtomicLong clock = new AtomicLong();
        private final List<Runnable> tasks = new ArrayList<>();
        private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
        private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        private final WriteTimeoutSocket socket;

        private Fixture() {
            this(mock(HelidonSocket.class));
        }

        private Fixture(HelidonSocket delegate) {
            this.delegate = delegate;
            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS))).thenAnswer(invocation -> {
                tasks.add(invocation.getArgument(0));
                return future;
            });
            socket = new WriteTimeoutSocket(delegate, Duration.ofNanos(10), scheduler, abort, clock::get);
        }
    }

    private static final class TlsFixture {
        // Initialize the channel superclass so its final close method can run during TLS teardown.
        private final SocketChannel channel = mock(SocketChannel.class,
                                                   withSettings().useConstructor(SelectorProvider.provider()));
        private final SSLEngine engine = mock(SSLEngine.class);
        private final Fixture fixture;

        private TlsFixture(boolean closing) throws Exception {
            SSLSession session = mock(SSLSession.class);
            when(engine.getSession()).thenReturn(session);
            when(session.getPacketBufferSize()).thenReturn(8);
            when(session.getApplicationBufferSize()).thenReturn(8);
            when(engine.unwrap(any(ByteBuffer.class), any(ByteBuffer.class))).thenAnswer(invocation -> {
                ByteBuffer buffer = invocation.getArgument(0);
                buffer.get();
                return new SSLEngineResult(SSLEngineResult.Status.OK,
                                           SSLEngineResult.HandshakeStatus.NEED_WRAP, 1, 0);
            });
            when(engine.wrap(any(ByteBuffer.class), any(ByteBuffer.class))).thenAnswer(invocation -> {
                ByteBuffer buffer = invocation.getArgument(1);
                buffer.put(new byte[] {1, 2, 3});
                return new SSLEngineResult(closing ? SSLEngineResult.Status.CLOSED : SSLEngineResult.Status.OK,
                                           SSLEngineResult.HandshakeStatus.FINISHED, 0, 3);
            });
            fixture = new Fixture(TlsNioSocket.server(channel, engine, "connection", "listener"));
        }
    }
}
