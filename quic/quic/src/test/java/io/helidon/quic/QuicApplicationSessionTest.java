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

package io.helidon.quic;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import io.helidon.common.buffers.BufferData;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamException;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class QuicApplicationSessionTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldCancelPendingStreamOpenWhenInterrupted(boolean bidi) {
        var opened = new CompletableFuture<QuicSenderStream>();
        QuicSession session = streamOpeningSession(bidi, Duration.ofSeconds(1), opened);

        try {
            Thread.currentThread().interrupt();
            QuicException failure = assertThrows(QuicException.class, () -> openStream(session, bidi));

            assertThat(failure.getMessage(), containsString("stream open interrupted"));
            assertThat(failure.getCause(), instanceOf(InterruptedException.class));
            assertThat(Thread.currentThread().isInterrupted(), is(true));
            assertThat(opened.isCancelled(), is(true));
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldCancelPendingStreamOpenWhenTimedOut(boolean bidi) {
        var opened = new CompletableFuture<QuicSenderStream>();
        QuicSession session = streamOpeningSession(bidi, Duration.ofNanos(1), opened);

        QuicException failure = assertThrows(QuicException.class, () -> openStream(session, bidi));

        assertThat(failure.getMessage(), containsString("stream open timed out"));
        assertThat(failure.getCause(), instanceOf(TimeoutException.class));
        assertThat(opened.isCancelled(), is(true));
    }

    @ParameterizedTest
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    void shouldDisposeUnclaimedStreamWhenCompletionWinsFailedWait(boolean bidi, boolean interrupted) {
        QuicSenderStream rawStream = bidi ? mock(QuicBidiStream.class) : mock(QuicSenderStream.class);
        var opened = new CompletedStreamFailedWait(rawStream, interrupted);
        QuicSession session = streamOpeningSession(bidi, Duration.ofSeconds(1), opened);

        try {
            QuicException failure = assertThrows(QuicException.class, () -> openStream(session, bidi));

            assertThat(failure.getCause(), instanceOf(interrupted ? InterruptedException.class : TimeoutException.class));
            assertThat(opened.isCancelled(), is(false));
            assertThat(opened.getNow(null), sameInstance(rawStream));
            assertThat(Thread.currentThread().isInterrupted(), is(interrupted));
            verify(rawStream).reset(0);
            if (bidi) {
                verify((QuicBidiStream) rawStream).requestStopSending(0);
            }
            verifyNoMoreInteractions(rawStream);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldStopReceivingAndRetainCleanupFailureWhenUnclaimedStreamResetFails() {
        QuicBidiStream rawStream = mock(QuicBidiStream.class);
        var resetFailure = new IllegalStateException("Test reset failure");
        doThrow(resetFailure).when(rawStream).reset(0);
        var opened = new CompletedStreamFailedWait(rawStream, true);
        QuicSession session = streamOpeningSession(true, Duration.ofSeconds(1), opened);

        try {
            QuicException failure = assertThrows(QuicException.class, session::openBidirectionalStream);

            assertThat(failure.getCause(), instanceOf(InterruptedException.class));
            assertThat(failure.getSuppressed(), arrayContaining(resetFailure));
            verify(rawStream).reset(0);
            verify(rawStream).requestStopSending(0);
            verifyNoMoreInteractions(rawStream);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldTranslateTransportFinAndCloseRegistration() {
        var terminated = new CompletableFuture<QuicTermination>();
        QuicConnection connection = connection(terminated);
        QuicReceiverStream rawStream = mock(QuicReceiverStream.class);
        var chunks = new ArrayDeque<BufferData>();
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        chunks.add(BufferData.createReadOnly(data, 0, data.length));
        chunks.add(QuicStreamReader.EOF);
        when(rawStream.connectReader(any())).thenAnswer(invocation ->
                new TestReader(rawStream, invocation.getArgument(0), chunks));
        QuicRemoteStreamRegistration registration = mock(QuicRemoteStreamRegistration.class);
        when(connection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            Predicate<? super QuicReceiverStream> listener = invocation.getArgument(0);
            assertThat(listener.test(rawStream), is(true));
            return registration;
        });

        QuicSession session = new QuicApplicationSession(connection, Duration.ofSeconds(1));
        QuicReceiveStream stream = session.acceptStream();

        byte[] received = stream.read().orElseThrow().readBytes();
        assertThat(new String(received, StandardCharsets.UTF_8), is("data"));
        assertThat(stream.read(), is(Optional.empty()));
        verify(registration).close();
    }

    @Test
    void shouldExposeStructuredPeerResetThroughReceiveStream() {
        var terminated = new CompletableFuture<QuicTermination>();
        QuicConnection connection = connection(terminated);
        QuicReceiverStream rawStream = mock(QuicReceiverStream.class);
        QuicStreamException internalFailure = mock(QuicStreamException.class);
        when(internalFailure.streamId()).thenReturn(12L);
        when(internalFailure.kind()).thenReturn(QuicStreamException.Kind.RESET_BY_PEER);
        when(internalFailure.errorCode()).thenReturn(OptionalLong.of(0x10c));
        when(internalFailure.getMessage()).thenReturn("QUIC stream 12 was reset by peer: errorCode 268");
        when(rawStream.connectReader(any())).thenAnswer(invocation -> {
            SequentialScheduler scheduler = invocation.getArgument(0);
            QuicStreamReader reader = mock(QuicStreamReader.class);
            when(reader.started()).thenReturn(false);
            when(reader.poll()).thenThrow(internalFailure);
            doAnswer(_ -> {
                scheduler.runOrSchedule();
                return null;
            }).when(reader).start();
            return reader;
        });
        when(connection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            Predicate<? super QuicReceiverStream> listener = invocation.getArgument(0);
            assertThat(listener.test(rawStream), is(true));
            return mock(QuicRemoteStreamRegistration.class);
        });

        QuicSession session = new QuicApplicationSession(connection, Duration.ofSeconds(1));
        QuicReceiveStream stream = session.acceptStream();

        QuicStreamTerminationException failure =
                assertThrows(QuicStreamTerminationException.class, stream::read);
        assertThat(failure.kind(), is(QuicStreamTerminationException.Kind.RESET_BY_PEER));
        assertThat(failure.streamId(), is(12L));
        assertThat(failure.applicationErrorCode(), is(OptionalLong.of(0x10c)));
        assertThat(failure.getCause(), sameInstance(internalFailure));
    }

    @Test
    void shouldApplyBackpressureUntilWriteDispatch() throws Exception {
        var terminated = new CompletableFuture<QuicTermination>();
        QuicConnection connection = connection(terminated);
        QuicSenderStream rawStream = mock(QuicSenderStream.class);
        QuicStreamWriter writer = mock(QuicStreamWriter.class);
        var dispatchReceipt = new CompletableFuture<Void>();
        var scheduled = new CountDownLatch(1);
        when(connection.openNewLocalUniStream(Duration.ofSeconds(1)))
                .thenReturn(CompletableFuture.completedFuture(rawStream));
        when(rawStream.connectWriter(any())).thenReturn(writer);
        when(rawStream.whenStopSendingReceived()).thenReturn(new CompletableFuture<>());
        when(writer.scheduleForWritingAndGetDispatchCompletion(any(), eq(false))).thenAnswer(_ -> {
            scheduled.countDown();
            return dispatchReceipt;
        });

        QuicSession session = new QuicApplicationSession(connection, Duration.ofSeconds(1));
        QuicSendStream stream = session.openUnidirectionalStream();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var write = executor.submit(() -> stream.write(BufferData.create("data".getBytes(StandardCharsets.UTF_8))));

            assertThat(scheduled.await(1, TimeUnit.SECONDS), is(true));
            assertThat(write.isDone(), is(false));

            dispatchReceipt.complete(null);
            write.get(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldMapInternalTerminationFailure() {
        var terminated = new CompletableFuture<QuicTermination>();
        QuicConnection connection = connection(terminated);
        clearInvocations(connection);
        QuicSession session = new QuicApplicationSession(connection, Duration.ofSeconds(1));
        CompletionStage<QuicSessionTermination> publicTermination = session.whenTerminated();

        assertThat(session.whenTerminated(), sameInstance(publicTermination));
        verify(connection).whenTerminated();

        terminated.completeExceptionally(new QuicConnectionException("connection failed"));

        ExecutionException failure =
                assertThrows(ExecutionException.class, publicTermination.toCompletableFuture()::get);
        assertThat(failure.getCause(), instanceOf(QuicException.class));
        assertThat(failure.getCause().getMessage(), equalTo("connection failed"));
    }

    @Test
    void shouldCacheStopSendingStage() {
        var terminated = new CompletableFuture<QuicTermination>();
        QuicConnection connection = connection(terminated);
        QuicSenderStream rawStream = mock(QuicSenderStream.class);
        var stopped = new CompletableFuture<Long>();
        when(connection.openNewLocalUniStream(Duration.ofSeconds(1)))
                .thenReturn(CompletableFuture.completedFuture(rawStream));
        when(rawStream.whenStopSendingReceived()).thenReturn(stopped);
        clearInvocations(rawStream);

        QuicSession session = new QuicApplicationSession(connection, Duration.ofSeconds(1));
        QuicSendStream stream = session.openUnidirectionalStream();
        CompletionStage<Long> first = stream.whenStopSendingReceived();

        assertThat(stream.whenStopSendingReceived(), sameInstance(first));
        verify(rawStream).whenStopSendingReceived();
    }

    @Test
    void shouldRejectConcurrentAccept() throws Exception {
        var terminated = new CompletableFuture<QuicTermination>();
        QuicConnection connection = connection(terminated);
        QuicReceiverStream rawStream = mock(QuicReceiverStream.class);
        QuicRemoteStreamRegistration registration = mock(QuicRemoteStreamRegistration.class);
        var listener = new AtomicReference<Predicate<? super QuicReceiverStream>>();
        var registered = new CountDownLatch(1);
        when(connection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            listener.set(invocation.getArgument(0));
            registered.countDown();
            return registration;
        });
        QuicSession session = new QuicApplicationSession(connection, Duration.ofSeconds(1));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstAccept = executor.submit(session::acceptStream);
            assertThat(registered.await(1, TimeUnit.SECONDS), is(true));

            IllegalStateException failure = assertThrows(IllegalStateException.class, session::acceptStream);
            assertThat(failure.getMessage(), equalTo("A QUIC remote stream accept is already pending"));

            assertThat(listener.get().test(rawStream), is(true));
            assertThat(firstAccept.get(1, TimeUnit.SECONDS).streamId(), is(0L));
        }
        verify(registration).close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldDisposeRemoteStreamWhenDeliveryWinsInterruptedAccept(boolean bidi) throws Exception {
        QuicConnection connection = connection(new CompletableFuture<>());
        QuicReceiverStream rawStream = bidi ? mock(QuicBidiStream.class) : mock(QuicReceiverStream.class);
        QuicRemoteStreamRegistration registration = mock(QuicRemoteStreamRegistration.class);
        var listener = new AtomicReference<Predicate<? super QuicReceiverStream>>();
        when(connection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            listener.set(invocation.getArgument(0));
            return registration;
        });
        QuicSession session = new QuicApplicationSession(connection, Duration.ofSeconds(1));

        interruptedAccept(session, () -> assertThat(listener.get().test(rawStream), is(true)));

        if (bidi) {
            verify((QuicBidiStream) rawStream).reset(0);
        }
        verify(rawStream).requestStopSending(0);
        verifyNoMoreInteractions(rawStream);
        verify(registration).close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldLeaveRemoteStreamAvailableWhenCancellationWinsInterruptedAccept(boolean bidi) throws Exception {
        QuicConnection connection = connection(new CompletableFuture<>());
        QuicReceiverStream rawStream = bidi ? mock(QuicBidiStream.class) : mock(QuicReceiverStream.class);
        QuicRemoteStreamRegistration interruptedRegistration = mock(QuicRemoteStreamRegistration.class);
        var listener = new AtomicReference<Predicate<? super QuicReceiverStream>>();
        when(connection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            listener.set(invocation.getArgument(0));
            return interruptedRegistration;
        });
        QuicSession session = new QuicApplicationSession(connection, Duration.ofSeconds(1));

        interruptedAccept(session, () -> { });

        assertThat(listener.get().test(rawStream), is(false));
        verifyNoMoreInteractions(rawStream);
        verify(interruptedRegistration).close();

        QuicRemoteStreamRegistration successfulRegistration = mock(QuicRemoteStreamRegistration.class);
        when(connection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            Predicate<? super QuicReceiverStream> nextListener = invocation.getArgument(0);
            assertThat(nextListener.test(rawStream), is(true));
            return successfulRegistration;
        });
        long streamId = bidi ? 5L : 7L;
        when(rawStream.streamId()).thenReturn(streamId);
        if (bidi) {
            when(((QuicBidiStream) rawStream).whenStopSendingReceived()).thenReturn(new CompletableFuture<>());
        }

        assertThat(session.acceptStream().streamId(), is(streamId));
        verify(successfulRegistration).close();
        verify(rawStream).streamId();
        if (bidi) {
            verify((QuicBidiStream) rawStream).whenStopSendingReceived();
        }
        verifyNoMoreInteractions(rawStream);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRetainCleanupFailureWhenRemoteStreamDeliveryWinsInterruptedAccept(boolean bidi) throws Exception {
        QuicConnection connection = connection(new CompletableFuture<>());
        QuicReceiverStream rawStream = bidi ? mock(QuicBidiStream.class) : mock(QuicReceiverStream.class);
        var cleanupFailure = new IllegalStateException("Test remote stream cleanup failure");
        if (bidi) {
            doThrow(cleanupFailure).when((QuicBidiStream) rawStream).reset(0);
        } else {
            doThrow(cleanupFailure).when(rawStream).requestStopSending(0);
        }
        QuicRemoteStreamRegistration registration = mock(QuicRemoteStreamRegistration.class);
        var listener = new AtomicReference<Predicate<? super QuicReceiverStream>>();
        when(connection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            listener.set(invocation.getArgument(0));
            return registration;
        });
        QuicSession session = new QuicApplicationSession(connection, Duration.ofSeconds(1));

        QuicException failure = interruptedAccept(session, () -> assertThat(listener.get().test(rawStream), is(true)));

        assertThat(failure.getSuppressed(), arrayContaining(cleanupFailure));
        if (bidi) {
            verify((QuicBidiStream) rawStream).reset(0);
        }
        verify(rawStream).requestStopSending(0);
        verifyNoMoreInteractions(rawStream);
        verify(registration).close();
    }

    @Test
    void shouldRetainReadOutcomeWhenInterruptedDuringDelivery() throws Exception {
        var terminated = new CompletableFuture<QuicTermination>();
        QuicConnection connection = connection(terminated);
        QuicReceiverStream rawStream = mock(QuicReceiverStream.class);
        QuicRemoteStreamRegistration registration = mock(QuicRemoteStreamRegistration.class);
        byte[] data = "retained".getBytes(StandardCharsets.UTF_8);
        var chunks = new ArrayDeque<BufferData>();
        chunks.add(BufferData.createReadOnly(data, 0, data.length));
        var pollStarted = new CountDownLatch(1);
        var continuePoll = new CountDownLatch(1);
        when(connection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            Predicate<? super QuicReceiverStream> listener = invocation.getArgument(0);
            assertThat(listener.test(rawStream), is(true));
            return registration;
        });

        QuicSession session = new QuicApplicationSession(connection, Duration.ofSeconds(1));
        QuicReceiveStream stream = session.acceptStream();
        var readingThread = new AtomicReference<Thread>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            when(rawStream.connectReader(any())).thenAnswer(invocation ->
                    new BlockingTestReader(rawStream,
                                           invocation.getArgument(0),
                                           chunks,
                                           executor,
                                           pollStarted,
                                           continuePoll));
            var interruptedRead = executor.submit(() -> {
                readingThread.set(Thread.currentThread());
                return stream.read();
            });

            try {
                assertThat(pollStarted.await(1, TimeUnit.SECONDS), is(true));
                readingThread.get().interrupt();
            } finally {
                continuePoll.countDown();
            }

            ExecutionException failure = assertThrows(ExecutionException.class,
                                                      () -> interruptedRead.get(1, TimeUnit.SECONDS));
            assertThat(failure.getCause(), instanceOf(QuicException.class));
            assertThat(failure.getCause().getMessage(), equalTo("QUIC stream read interrupted"));
            assertThat(new String(stream.read().orElseThrow().readBytes(), StandardCharsets.UTF_8), is("retained"));
        }
    }

    private static QuicException interruptedAccept(QuicSession session, Runnable onInterruptRestored) throws Exception {
        var outcome = new CompletableFuture<QuicException>();
        var thread = new InterruptRestoringThread(() -> {
            try {
                QuicException failure = assertThrows(QuicException.class, session::acceptStream);
                assertThat(failure.getMessage(), containsString("remote stream accept interrupted"));
                assertThat(failure.getCause(), instanceOf(InterruptedException.class));
                assertThat(Thread.currentThread().isInterrupted(), is(true));
                outcome.complete(failure);
            } catch (Throwable failure) {
                outcome.completeExceptionally(failure);
            } finally {
                Thread.interrupted();
            }
        }, onInterruptRestored);
        thread.start();
        try {
            return outcome.get(5, TimeUnit.SECONDS);
        } finally {
            thread.interrupt();
            thread.join(TimeUnit.SECONDS.toMillis(5));
            assertThat("accept thread finished", thread.isAlive(), is(false));
        }
    }

    private static QuicSession streamOpeningSession(boolean bidi,
                                                   Duration timeout,
                                                   CompletableFuture<? extends QuicSenderStream> opened) {
        QuicConnection connection = connection(new CompletableFuture<>());
        if (bidi) {
            doReturn(opened).when(connection).openNewLocalBidiStream(timeout);
        } else {
            doReturn(opened).when(connection).openNewLocalUniStream(timeout);
        }
        return new QuicApplicationSession(connection, timeout);
    }

    private static QuicSendStream openStream(QuicSession session, boolean bidi) {
        return bidi ? session.openBidirectionalStream() : session.openUnidirectionalStream();
    }

    private static QuicConnection connection(CompletableFuture<QuicTermination> terminated) {
        QuicConnection connection = mock(QuicConnection.class);
        when(connection.applicationProtocol()).thenReturn(Optional.of("example"));
        when(connection.quicVersion()).thenReturn(QuicVersion.QUIC_V1);
        when(connection.whenTerminated()).thenReturn(terminated);
        when(connection.termination()).thenReturn(Optional.empty());
        when(connection.isOpen()).thenReturn(true);
        return connection;
    }

    private static final class InterruptRestoringThread extends Thread {
        private final Runnable onInterruptRestored;

        private boolean restoreExpected;

        private InterruptRestoringThread(Runnable operation, Runnable onInterruptRestored) {
            super(operation);
            this.onInterruptRestored = onInterruptRestored;
        }

        @Override
        public void run() {
            super.interrupt();
            restoreExpected = true;
            super.run();
        }

        @Override
        public void interrupt() {
            if (Thread.currentThread() == this && restoreExpected) {
                restoreExpected = false;
                // Complete the real accept future after get() throws, before its cancellation is attempted.
                onInterruptRestored.run();
            }
            super.interrupt();
        }
    }

    private static final class CompletedStreamFailedWait extends CompletableFuture<QuicSenderStream> {
        private final QuicSenderStream stream;
        private final boolean interrupted;

        private CompletedStreamFailedWait(QuicSenderStream stream, boolean interrupted) {
            this.stream = stream;
            this.interrupted = interrupted;
        }

        @Override
        public QuicSenderStream get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
            complete(stream);
            if (interrupted) {
                throw new InterruptedException("Test stream completed while the wait was interrupted");
            }
            throw new TimeoutException("Test stream completed while the wait timed out");
        }
    }

    private static final class TestReader extends QuicStreamReader {
        private final QuicReceiverStream stream;
        private final ArrayDeque<BufferData> chunks;
        private boolean started;

        private TestReader(QuicReceiverStream stream,
                           SequentialScheduler scheduler,
                           ArrayDeque<BufferData> chunks) {
            super(scheduler);
            this.stream = stream;
            this.chunks = chunks;
        }

        @Override
        public QuicReceiverStream.ReceivingStreamState receivingState() {
            return QuicReceiverStream.ReceivingStreamState.RECV;
        }

        @Override
        public Optional<BufferData> poll() {
            return Optional.ofNullable(chunks.poll());
        }

        @Override
        public Optional<BufferData> peek() {
            return Optional.ofNullable(chunks.peek());
        }

        @Override
        public Optional<QuicReceiverStream> stream() {
            return Optional.of(stream);
        }

        @Override
        public boolean connected() {
            return true;
        }

        @Override
        public boolean started() {
            return started;
        }

        @Override
        public void start() {
            started = true;
            scheduler().runOrSchedule();
        }
    }

    private static final class BlockingTestReader extends QuicStreamReader {
        private final QuicReceiverStream stream;
        private final ArrayDeque<BufferData> chunks;
        private final Executor executor;
        private final CountDownLatch pollStarted;
        private final CountDownLatch continuePoll;
        private boolean started;

        private BlockingTestReader(QuicReceiverStream stream,
                                   SequentialScheduler scheduler,
                                   ArrayDeque<BufferData> chunks,
                                   Executor executor,
                                   CountDownLatch pollStarted,
                                   CountDownLatch continuePoll) {
            super(scheduler);
            this.stream = stream;
            this.chunks = chunks;
            this.executor = executor;
            this.pollStarted = pollStarted;
            this.continuePoll = continuePoll;
        }

        @Override
        public QuicReceiverStream.ReceivingStreamState receivingState() {
            return QuicReceiverStream.ReceivingStreamState.RECV;
        }

        @Override
        public Optional<BufferData> poll() {
            pollStarted.countDown();
            try {
                if (!continuePoll.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to complete the test read");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test reader interrupted", e);
            }
            return Optional.ofNullable(chunks.poll());
        }

        @Override
        public Optional<BufferData> peek() {
            return Optional.ofNullable(chunks.peek());
        }

        @Override
        public Optional<QuicReceiverStream> stream() {
            return Optional.of(stream);
        }

        @Override
        public boolean connected() {
            return true;
        }

        @Override
        public boolean started() {
            return started;
        }

        @Override
        public void start() {
            started = true;
            executor.execute(() -> scheduler().runOrSchedule());
        }
    }
}
