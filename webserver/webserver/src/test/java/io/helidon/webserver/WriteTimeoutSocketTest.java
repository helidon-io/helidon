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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
import static org.mockito.Mockito.times;
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
        verify(fixture.future, times(2)).cancel(false);
    }

    @Test
    void lateTimerCannotAbortSubsequentWrite() {
        var fixture = new Fixture();
        fixture.socket.write(BufferData.create("first"));
        doAnswer(invocation -> {
            fixture.tasks.getFirst().run();
            return null;
        }).when(fixture.delegate).write(any());

        fixture.socket.write(BufferData.create("second"));

        verify(fixture.abort, never()).run();
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

    private static final class Fixture {
        private final HelidonSocket delegate = mock(HelidonSocket.class);
        private final Runnable abort = mock(Runnable.class);
        private final AtomicLong clock = new AtomicLong();
        private final List<Runnable> tasks = new ArrayList<>();
        private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
        private final WriteTimeoutSocket socket;

        private Fixture() {
            var scheduler = mock(ScheduledExecutorService.class);
            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS))).thenAnswer(invocation -> {
                tasks.add(invocation.getArgument(0));
                return future;
            });
            socket = new WriteTimeoutSocket(delegate, Duration.ofNanos(10), scheduler, abort, clock::get);
        }
    }
}
