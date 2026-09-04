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

package io.helidon.quic.stream;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import io.helidon.common.buffers.BufferData;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnectionImpl;
import io.helidon.quic.QuicConnectionImpl.ReassemblyBudget;
import io.helidon.quic.QuicInstance;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.QuicTerminationTestSupport;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.QuicTransportParameters;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.frame.MaxStreamsFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.frame.StreamFrame;
import io.helidon.quic.frame.StreamsBlockedFrame;

import org.junit.jupiter.api.Test;

import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_max_stream_data_uni;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_max_streams_bidi;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_max_streams_uni;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuicConnectionStreamsTest {
    private static final int STREAM_BUFFER_SIZE = 1 << 16;

    @Test
    void dispatchesDistinctServerStreamsInReadyOrder() {
        QuicConnectionStreams.ReadyStreamCollection ready = QuicConnectionStreams.serverReadyStreams();
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicSenderStreamImpl firstReady = new QuicSenderStreamImpl(connection, 8, STREAM_BUFFER_SIZE);
        QuicSenderStreamImpl secondReady = new QuicSenderStreamImpl(connection, 0, STREAM_BUFFER_SIZE);

        ready.add(firstReady);
        ready.add(secondReady);
        ready.add(firstReady);

        assertThat(ready.size(), equalTo(2));
        assertThat(ready.poll(), sameInstance(firstReady));
        assertThat(ready.poll(), sameInstance(secondReady));
        assertThat(ready.poll(), nullValue());
        assertThat(ready.isEmpty(), equalTo(true));

        ready.add(firstReady);

        assertThat(ready.poll(), sameInstance(firstReady));
    }

    @Test
    void coalescesConcurrentServerReadyNotifications() throws Exception {
        QuicConnectionStreams.ReadyStreamCollection ready = QuicConnectionStreams.serverReadyStreams();
        QuicSenderStreamImpl sender = new QuicSenderStreamImpl(mock(QuicConnectionImpl.class),
                                                               0,
                                                               STREAM_BUFFER_SIZE);
        int producerCount = 16;
        CountDownLatch producersReady = new CountDownLatch(producerCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> producerFailure = new AtomicReference<>();
        List<Thread> producers = new ArrayList<>(producerCount);
        for (int i = 0; i < producerCount; i++) {
            Thread producer = Thread.ofPlatform().unstarted(() -> {
                producersReady.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to publish ready streams");
                    }
                    ready.add(sender);
                } catch (Throwable t) {
                    if (t instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    producerFailure.compareAndSet(null, t);
                }
            });
            producers.add(producer);
            producer.start();
        }
        assertThat(producersReady.await(10, TimeUnit.SECONDS), equalTo(true));
        start.countDown();
        for (Thread producer : producers) {
            producer.join(10_000);
            assertThat(producer.isAlive(), equalTo(false));
        }
        assertThat(producerFailure.get(), nullValue());

        assertThat(ready.size(), equalTo(1));
        assertThat(ready.poll(), sameInstance(sender));
        assertThat(ready.poll(), nullValue());

        ready.add(sender);

        assertThat(ready.poll(), sameInstance(sender));
    }

    @Test
    void directlyPublishesReadySenderAndRejectsForeignSender() throws Exception {
        QuicTransportParameters peerParameters = QuicTransportParameters.create();
        peerParameters.intParameter(initial_max_streams_uni, 1);
        peerParameters.intParameter(initial_max_stream_data_uni, 1);
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(false);
        when(connection.peerTransportParameters()).thenReturn(Optional.of(peerParameters));
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        QuicConnectionStreams streams = createStreams(connection);
        streams.newPeerTransportParameters(peerParameters);
        QuicSenderStreamImpl sender = (QuicSenderStreamImpl) streams.createNewLocalUniStream(Duration.ZERO).join();
        QuicStreamWriter writer = sender.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));

        writer.scheduleForWriting(BufferData.create(new byte[] {1}), true);

        verify(connection).streamDataAvailableForSending(sender);
        QuicConnectionImpl otherConnection = mock(QuicConnectionImpl.class);
        assertThrows(IllegalArgumentException.class,
                     () -> streams.enqueueForSending(new QuicSenderStreamImpl(otherConnection, 3, STREAM_BUFFER_SIZE)));

        streams.enqueueForSending(sender);
        streams.enqueueForSending(sender);
        assertThat(streams.hasAvailableData(), equalTo(true));
        List<QuicFrame> frames = new ArrayList<>();
        streams.produceFramesToSend(null, 1200, 1, frames);
        assertThat(frames.stream().filter(StreamFrame.class::isInstance).count(), equalTo(1L));
        assertThat(streams.hasAvailableData(), equalTo(false));
        streams.streamFramesDispatched(frames);
        assertThat(sender.sendingState(), equalTo(QuicSenderStream.SendingStreamState.DATA_SENT));

        streams.enqueueForSending(sender);

        assertThat(streams.hasAvailableData(), equalTo(false));
    }

    @Test
    void recoversRemoteCreditWhenHigherStreamRetiresFirst() throws Exception {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = remoteUniStreams(connection, 4);

        finishRemoteUniStream(streams, 7);

        assertThat(streams.nextMaxStreamsLimit(false), equalTo(0L));

        finishRemoteUniStream(streams, 3);

        assertThat(streams.nextMaxStreamsLimit(false), equalTo(6L));
    }

    @Test
    void derivesRemoteStreamCreditFromRetiredStreams() {
        QuicConnectionStreams.RemoteStreamCredit credit = new QuicConnectionStreams.RemoteStreamCredit();

        assertThat(credit.nextLimit(false), equalTo(0L));
        assertThat(credit.nextLimit(true), equalTo(0L));

        credit.initialize(4);
        assertThat(credit.currentLimit(), equalTo(4L));
        assertThat(credit.nextLimit(false), equalTo(0L));
        assertThat(credit.nextLimit(true), equalTo(0L));

        credit.streamRetired();
        assertThat(credit.nextLimit(false), equalTo(0L));
        assertThat(credit.nextLimit(true), equalTo(5L));

        credit.streamRetired();
        assertThat(credit.nextLimit(false), equalTo(6L));
        assertThat(credit.tryAdvanceLimit(6), equalTo(true));
        assertThat(credit.nextLimit(false), equalTo(0L));
    }

    @Test
    void countsConcurrentRemoteStreamRetirements() throws Exception {
        QuicConnectionStreams.RemoteStreamCredit credit = new QuicConnectionStreams.RemoteStreamCredit();
        credit.initialize(64);
        int retirementCount = 17;
        CountDownLatch ready = new CountDownLatch(retirementCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>(retirementCount);
        for (int i = 0; i < retirementCount; i++) {
            Thread thread = Thread.ofPlatform().unstarted(() -> {
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to retire a stream");
                    }
                    credit.streamRetired();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            });
            threads.add(thread);
            thread.start();
        }
        assertThat(ready.await(10, TimeUnit.SECONDS), equalTo(true));
        start.countDown();
        for (Thread thread : threads) {
            thread.join(10_000);
            assertThat(thread.isAlive(), equalTo(false));
        }

        assertThat(credit.nextLimit(false), equalTo(81L));
    }

    @Test
    void replenishesRemoteUniCreditAcrossMultipleWindows() throws Exception {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = remoteUniStreams(connection, 4);
        List<Long> advertisedLimits = new ArrayList<>();

        for (int ordinal = 0; ordinal < 8; ordinal++) {
            finishRemoteUniStream(streams, 3 + 4L * ordinal);
            if ((ordinal & 1) == 0) {
                assertThat(streams.hasControlFrames(), equalTo(false));
            } else {
                assertThat(streams.nextMaxStreamsLimit(false), equalTo(6L + ordinal - 1));
                assertThat(streams.hasControlFrames(), equalTo(true));
                List<QuicFrame> frames = new ArrayList<>();
                streams.produceFramesToSend(null, 1200, 0, frames);
                MaxStreamsFrame frame = frames.stream()
                        .filter(MaxStreamsFrame.class::isInstance)
                        .map(MaxStreamsFrame.class::cast)
                        .filter(candidate -> !candidate.isBidi())
                        .findFirst()
                        .orElseThrow();
                advertisedLimits.add(frame.maxStreams());
                assertThat(streams.hasControlFrames(), equalTo(false));
            }
        }

        assertThat(advertisedLimits, equalTo(List.of(6L, 8L, 10L, 12L)));
    }

    @Test
    void blockedPeerWaitsForAndThenReceivesRetiredStreamCredit() throws Exception {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = remoteUniStreams(connection, 4);
        streams.peerStreamsBlocked(StreamsBlockedFrame.create(false, 4));
        List<QuicFrame> frames = new ArrayList<>();

        assertThat(streams.hasControlFrames(), equalTo(false));
        streams.produceFramesToSend(null, 1200, 0, frames);
        assertThat(frames.stream().anyMatch(MaxStreamsFrame.class::isInstance), equalTo(false));

        finishRemoteUniStream(streams, 3);
        assertThat(streams.nextMaxStreamsLimit(false, true), equalTo(5L));
        assertThat(streams.hasControlFrames(), equalTo(true));
        streams.produceFramesToSend(null, 1200, 0, frames);
        MaxStreamsFrame frame = frames.stream()
                .filter(MaxStreamsFrame.class::isInstance)
                .map(MaxStreamsFrame.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(frame.isBidi(), equalTo(false));
        assertThat(frame.maxStreams(), equalTo(5L));
        assertThat(streams.hasControlFrames(), equalTo(false));
    }

    @Test
    void ignoresStreamsBlockedForAnUnadvertisedLimit() throws Exception {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = remoteUniStreams(connection, 4);

        streams.peerStreamsBlocked(StreamsBlockedFrame.create(false, 5));
        finishRemoteUniStream(streams, 3);

        assertThat(streams.hasControlFrames(), equalTo(false));

        finishRemoteUniStream(streams, 7);
        assertThat(streams.hasControlFrames(), equalTo(true));
        List<QuicFrame> frames = new ArrayList<>();
        streams.produceFramesToSend(null, 1200, 0, frames);
        MaxStreamsFrame frame = frames.stream()
                .filter(MaxStreamsFrame.class::isInstance)
                .map(MaxStreamsFrame.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(frame.maxStreams(), equalTo(6L));
    }

    @Test
    void preservesConcurrentStreamsBlockedUpdateWhileAdvertisingCredit() throws Exception {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = remoteUniStreams(connection, 4);
        streams.peerStreamsBlocked(StreamsBlockedFrame.create(false, 4));
        finishRemoteUniStream(streams, 3);
        CountDownLatch frameSelected = new CountDownLatch(1);
        CountDownLatch continueSelection = new CountDownLatch(1);
        List<QuicFrame> frames = new ArrayList<>() {
            @Override
            public boolean add(QuicFrame frame) {
                if (frame instanceof MaxStreamsFrame) {
                    frameSelected.countDown();
                    try {
                        if (!continueSelection.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to finish MAX_STREAMS selection");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
                return super.add(frame);
            }
        };
        AtomicReference<Throwable> producerFailure = new AtomicReference<>();
        Thread producer = Thread.ofPlatform().unstarted(() -> {
            try {
                streams.produceFramesToSend(null, 1200, 0, frames);
            } catch (Throwable t) {
                producerFailure.set(t);
            }
        });
        producer.start();
        assertThat(frameSelected.await(10, TimeUnit.SECONDS), equalTo(true));

        streams.peerStreamsBlocked(StreamsBlockedFrame.create(false, 5));
        continueSelection.countDown();
        producer.join(10_000);

        assertThat(producer.isAlive(), equalTo(false));
        assertThat(producerFailure.get(), nullValue());
        assertThat(streams.hasControlFrames(), equalTo(false));

        finishRemoteUniStream(streams, 7);
        assertThat(streams.hasControlFrames(), equalTo(true));
        List<QuicFrame> nextFrames = new ArrayList<>();
        streams.produceFramesToSend(null, 1200, 0, nextFrames);
        MaxStreamsFrame frame = nextFrames.stream()
                .filter(MaxStreamsFrame.class::isInstance)
                .map(MaxStreamsFrame.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(frame.maxStreams(), equalTo(6L));
    }

    @Test
    void rejectsNullWriterInputsWithoutChangingStreamState() {
        QuicTransportParameters peerParameters = QuicTransportParameters.create();
        peerParameters.intParameter(initial_max_streams_uni, 1);
        peerParameters.intParameter(initial_max_stream_data_uni, 4);
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(false);
        when(connection.isOpen()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.of(peerParameters));
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        QuicConnectionStreams streams = createStreams(connection);
        streams.newPeerTransportParameters(peerParameters);
        QuicSenderStream stream = streams.createNewLocalUniStream(Duration.ZERO).join();

        assertThrows(NullPointerException.class, () -> stream.connectWriter(null));

        QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        assertThrows(NullPointerException.class, () -> stream.disconnectWriter(null));
        assertThrows(NullPointerException.class, () -> writer.scheduleForWriting(null, false));
        assertThrows(NullPointerException.class,
                     () -> writer.scheduleForWritingAndGetDispatchCompletion(null, false));
        assertThrows(NullPointerException.class, () -> writer.queueForWriting(null));

        assertThat(writer.connected(), equalTo(true));
        assertThat(writer.sendingState(), equalTo(QuicSenderStream.SendingStreamState.READY));
        stream.disconnectWriter(writer);
        assertThat(writer.connected(), equalTo(false));
    }

    @Test
    void rejectsNullLocalStreamTimeoutsBeforeCreatingStreams() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        QuicConnectionStreams streams = createStreams(connection);

        assertThrows(NullPointerException.class, () -> streams.createNewLocalUniStream(null));
        assertThrows(NullPointerException.class, () -> streams.createNewLocalBidiStream(null));

        assertThat(streams.peekNextStreamId(2), equalTo(2L));
        assertThat(streams.peekNextStreamId(0), equalTo(0L));
    }

    @Test
    void cancellationRemovesPendingBidiReservationWaiter() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = localBidiStreams(connection, 0);
        CompletableFuture<QuicBidiStreamReservation> canceled = streams.reserveNewLocalBidiStream();

        assertThat(canceled.isDone(), equalTo(false));
        assertThat(canceled.cancel(false), equalTo(true));
        assertThat(streams.tryIncreaseStreamLimit(MaxStreamsFrame.create(true, 1)), equalTo(true));

        QuicBidiStreamReservation reservation = streams.reserveNewLocalBidiStream().join();
        assertThat(streams.peekNextStreamId(0), equalTo(0L));
        assertThat(reservation.open().streamId(), equalTo(0L));
    }

    @Test
    void closingUnusedBidiReservationReturnsCredit() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = localBidiStreams(connection, 1);
        QuicBidiStreamReservation unused = streams.reserveNewLocalBidiStream().join();

        assertThat(streams.peekNextStreamId(0), equalTo(0L));
        unused.close();
        unused.close();

        QuicBidiStreamReservation replacement = streams.reserveNewLocalBidiStream().join();
        assertThat(replacement.open().streamId(), equalTo(0L));
    }

    @Test
    void opensBidiReservationExactlyOnce() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = localBidiStreams(connection, 1);
        QuicBidiStreamReservation reservation = streams.reserveNewLocalBidiStream().join();

        assertThat(reservation.open().streamId(), equalTo(0L));
        assertThrows(IllegalStateException.class, reservation::open);
        reservation.close();

        CompletableFuture<QuicBidiStreamReservation> blocked = streams.reserveNewLocalBidiStream();
        assertThat(blocked.isDone(), equalTo(false));
        assertThat(blocked.cancel(false), equalTo(true));
    }

    @Test
    void failedBidiStreamCreationRollsBackReservationAndStreamId() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = localBidiStreams(connection, 1);
        QuicBidiStreamReservation failedReservation = streams.reserveNewLocalBidiStream().join();
        IllegalStateException creationFailure = new IllegalStateException("test stream creation failure");
        when(connection.peerTransportParameters()).thenThrow(creationFailure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, failedReservation::open);

        assertThat(thrown, sameInstance(creationFailure));
        assertThat(streams.peekNextStreamId(0), equalTo(0L));
        assertThat(streams.findStream(0), equalTo(Optional.empty()));
        failedReservation.close();

        doReturn(Optional.empty()).when(connection).peerTransportParameters();
        QuicBidiStreamReservation replacement = streams.reserveNewLocalBidiStream().join();
        assertThat(replacement.open().streamId(), equalTo(0L));
    }

    @Test
    void terminationBeforeOpeningBidiReservationRollsBackCreditAndStreamId() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = localBidiStreams(connection, 1);
        QuicBidiStreamReservation reservation = streams.reserveNewLocalBidiStream().join();
        QuicTermination termination = QuicTerminationTestSupport.local(
                QuicCloseCommand.application(0, "test reservation termination"));

        streams.terminate(termination);

        RuntimeException thrown = assertThrows(RuntimeException.class, reservation::open);
        assertThat(thrown, sameInstance(termination.closeCause()));
        assertThat(streams.peekNextStreamId(0), equalTo(0L));
        assertThat(streams.findStream(0), equalTo(Optional.empty()));
    }

    @Test
    void failedUniStreamCreationRollsBackPermitAndStreamId() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = localUniStreams(connection, 1);
        IllegalStateException creationFailure = new IllegalStateException("test stream creation failure");
        when(connection.peerTransportParameters()).thenThrow(creationFailure);

        CompletionException thrown = assertThrows(
                CompletionException.class,
                () -> streams.createNewLocalUniStream(Duration.ZERO).join());

        assertThat(thrown.getCause(), sameInstance(creationFailure));
        assertThat(streams.peekNextStreamId(2), equalTo(2L));
        assertThat(streams.findStream(2), equalTo(Optional.empty()));

        doReturn(Optional.empty()).when(connection).peerTransportParameters();
        assertThat(streams.createNewLocalUniStream(Duration.ZERO).join().streamId(), equalTo(2L));
    }

    @Test
    void terminationFailsPendingBidiReservation() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicConnectionStreams streams = localBidiStreams(connection, 0);
        CompletableFuture<QuicBidiStreamReservation> pending = streams.reserveNewLocalBidiStream();
        QuicTermination termination = QuicTerminationTestSupport.local(
                QuicCloseCommand.application(0, "test reservation termination"));

        streams.terminate(termination);

        CompletionException thrown = assertThrows(CompletionException.class, pending::join);
        assertThat(thrown.getCause(), sameInstance(termination.closeCause()));
        assertThat(streams.peekNextStreamId(0), equalTo(0L));
    }

    @Test
    void blockedStreamReminderAllowsConcurrentTracking() throws Exception {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        AtomicReference<Set<Long>> reminderStreamIds = new AtomicReference<>();
        doAnswer(invocation -> {
            reminderStreamIds.set(invocation.getArgument(0));
            return null;
        }).when(connection).streamDataAvailableForSending(anySet());
        QuicConnectionStreams streams = createStreams(connection);
        streams.trackBlockedStream(0);
        streams.trackBlockedStream(4);
        streams.enqueueStreamDataBlocked();
        Set<Long> streamIds = reminderStreamIds.get();
        CountDownLatch iteratorReady = new CountDownLatch(1);
        CountDownLatch mutationComplete = new CountDownLatch(1);
        AtomicReference<Throwable> iterationFailure = new AtomicReference<>();
        Thread iterationThread = Thread.ofPlatform().unstarted(() -> {
            try {
                Iterator<Long> iterator = streamIds.iterator();
                iterator.next();
                iteratorReady.countDown();
                if (!mutationComplete.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting for blocked-stream mutation");
                }
                iterator.forEachRemaining(_ -> {
                });
            } catch (Throwable t) {
                iterationFailure.set(t);
            } finally {
                iteratorReady.countDown();
            }
        });
        iterationThread.start();
        assertThat(iteratorReady.await(10, TimeUnit.SECONDS), equalTo(true));

        streams.trackBlockedStream(8);
        mutationComplete.countDown();
        iterationThread.join(10_000);

        assertThat(iterationThread.isAlive(), equalTo(false));
        assertThat(iterationFailure.get(), nullValue());
        assertThat(streamIds.size(), equalTo(3));
    }

    @Test
    void terminationFailsAndSealsPendingLocalStreamCreation() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.executor()).thenReturn(Runnable::run);
        when(connection.quicInstance()).thenReturn(instance);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.empty());
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        QuicConnectionStreams streams = createStreams(connection);
        QuicTermination termination = QuicTerminationTestSupport.local(
                QuicCloseCommand.application(0, "test termination"));
        CompletableFuture<QuicSenderStream> pending = streams.createNewLocalUniStream(Duration.ofDays(1));

        streams.terminate(termination);

        CompletionException thrown = assertThrows(CompletionException.class, pending::join);
        assertThat(thrown.getCause(), sameInstance(termination.closeCause()));
        CompletionException lateThrown = assertThrows(
                CompletionException.class,
                () -> streams.createNewLocalUniStream(Duration.ZERO).join());
        assertThat(lateThrown.getCause(), sameInstance(termination.closeCause()));
        assertThat(streams.peekNextStreamId(2), equalTo(2L));
    }

    @Test
    void terminationClearsAndSealsRemoteStreamDelivery() throws Exception {
        QuicTransportParameters localParameters = QuicTransportParameters.create();
        localParameters.intParameter(initial_max_streams_uni, 2);
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        when(connection.isOpen()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.empty());
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        when(connection.newReassemblyBudget()).thenReturn(mock(ReassemblyBudget.class));
        QuicConnectionStreams streams = createStreams(connection);
        streams.newLocalTransportParameters(localParameters);
        AtomicInteger retainedListenerCalls = new AtomicInteger();
        Predicate<QuicReceiverStream> retainedListener = stream -> {
            retainedListenerCalls.incrementAndGet();
            return false;
        };
        assertThat(streams.addRemoteStreamListener(retainedListener), equalTo(true));
        streams.ensureRemoteStream(3, QuicFrame.STREAM);
        assertThat(retainedListenerCalls.get(), equalTo(1));

        QuicTermination termination = QuicTerminationTestSupport.local(
                QuicCloseCommand.application(0, "test termination"));
        streams.terminate(termination);

        assertThat(streams.removeRemoteStreamListener(retainedListener), equalTo(false));
        AtomicInteger lateListenerCalls = new AtomicInteger();
        assertThat(streams.addRemoteStreamListener(stream -> {
            lateListenerCalls.incrementAndGet();
            return true;
        }), equalTo(false));
        assertThat(streams.ensureRemoteStream(7, QuicFrame.STREAM), equalTo(Optional.empty()));
        assertThat(retainedListenerCalls.get(), equalTo(1));
        assertThat(lateListenerCalls.get(), equalTo(0));
    }

    @Test
    void dispatchesLaterZeroLengthFinWithoutConnectionCredit() throws Exception {
        QuicTransportParameters peerParameters = QuicTransportParameters.create();
        peerParameters.intParameter(initial_max_streams_uni, 2);
        peerParameters.intParameter(initial_max_stream_data_uni, 1);
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(false);
        when(connection.isOpen()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.of(peerParameters));
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        QuicConnectionStreams streams = createStreams(connection);
        streams.newPeerTransportParameters(peerParameters);
        QuicSenderStream dataStream = streams.createNewLocalUniStream(Duration.ZERO).join();
        QuicStreamWriter dataWriter = dataStream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        CompletableFuture<Void> dataDispatch = dataWriter.scheduleForWritingAndGetDispatchCompletion(
                BufferData.create(new byte[] {1}), false);
        QuicSenderStream finalStream = streams.createNewLocalUniStream(Duration.ZERO).join();
        QuicStreamWriter finalWriter = finalStream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        CompletableFuture<Void> finalDispatch = finalWriter.scheduleForWritingAndGetDispatchCompletion(
                BufferData.create(0), true);
        streams.enqueueForSending(dataStream.streamId());
        streams.enqueueForSending(finalStream.streamId());
        streams.enqueueForSending(finalStream.streamId());
        List<QuicFrame> frames = new ArrayList<>();

        long produced = streams.produceFramesToSend(null, 1200, 0, frames);
        List<StreamFrame> streamFrames = frames.stream()
                .filter(StreamFrame.class::isInstance)
                .map(StreamFrame.class::cast)
                .toList();

        assertThat(produced, equalTo(0L));
        assertThat(streamFrames.size(), equalTo(1));
        assertThat(streamFrames.getFirst().streamId(), equalTo(finalStream.streamId()));
        assertThat(streamFrames.getFirst().isLast(), equalTo(true));
        assertThat(dataDispatch.isDone(), equalTo(false));
        assertThat(finalDispatch.isDone(), equalTo(false));

        streams.streamFramesDispatched(frames);

        assertThat(dataDispatch.isDone(), equalTo(false));
        assertThat(finalDispatch.isDone(), equalTo(true));

        frames.clear();
        produced = streams.produceFramesToSend(null, 1200, 1, frames);
        streamFrames = frames.stream()
                .filter(StreamFrame.class::isInstance)
                .map(StreamFrame.class::cast)
                .toList();

        assertThat(produced, equalTo(1L));
        assertThat(streamFrames.size(), equalTo(1));
        assertThat(streamFrames.getFirst().streamId(), equalTo(dataStream.streamId()));

        streams.streamFramesDispatched(frames);

        assertThat(dataDispatch.isDone(), equalTo(true));
    }

    @Test
    void writerCopiesSubmittedCompositeBytesBeforeReturning() throws Exception {
        QuicTransportParameters peerParameters = QuicTransportParameters.create();
        peerParameters.intParameter(initial_max_streams_uni, 1);
        peerParameters.intParameter(initial_max_stream_data_uni, 4);
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(false);
        when(connection.isOpen()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.of(peerParameters));
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        QuicConnectionStreams streams = createStreams(connection);
        streams.newPeerTransportParameters(peerParameters);
        QuicSenderStream stream = streams.createNewLocalUniStream(Duration.ZERO).join();
        QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        byte[] prefix = {0};
        byte[] payload = {1, 2, 3};
        BufferData submission = BufferData.create(BufferData.createReadOnly(prefix, 0, prefix.length),
                                                  BufferData.createReadOnly(payload, 0, payload.length));

        CompletableFuture<Void> dispatch = writer.scheduleForWritingAndGetDispatchCompletion(submission, true);
        assertThat(submission.consumed(), equalTo(true));
        prefix[0] = 9;
        payload[0] = 9;
        payload[1] = 9;
        payload[2] = 9;
        streams.enqueueForSending(stream.streamId());
        List<QuicFrame> frames = new ArrayList<>();

        streams.produceFramesToSend(null, 1200, 4, frames);
        StreamFrame streamFrame = frames.stream()
                .filter(StreamFrame.class::isInstance)
                .map(StreamFrame.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(streamFrame.withOwnedPayload(), sameInstance(streamFrame));
        byte[] emitted = new byte[streamFrame.dataLength()];
        streamFrame.payload().get(emitted);

        assertThat(emitted, equalTo(new byte[] {0, 1, 2, 3}));
        assertThat(streamFrame.isLast(), equalTo(true));
        assertThat(dispatch.isDone(), equalTo(false));

        streams.streamFramesDispatched(frames);

        assertThat(dispatch.isDone(), equalTo(true));
    }

    @Test
    void preservesTransportFailureWhilePackagingStreamData() throws Exception {
        QuicTransportParameters peerParameters = QuicTransportParameters.create();
        peerParameters.intParameter(initial_max_streams_uni, 1);
        peerParameters.intParameter(initial_max_stream_data_uni, 1);
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(false);
        when(connection.isOpen()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.of(peerParameters));
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        QuicConnectionStreams streams = createStreams(connection);
        streams.newPeerTransportParameters(peerParameters);
        QuicSenderStream stream = streams.createNewLocalUniStream(Duration.ZERO).join();
        QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        writer.scheduleForWriting(BufferData.create(new byte[] {1}), false);
        streams.enqueueForSending(stream.streamId());
        @SuppressWarnings("unchecked")
        List<QuicFrame> frames = mock(List.class);
        QuicTransportException failure = new QuicTransportException(
                "test failure",
                KeySpace.ONE_RTT,
                QuicFrame.STREAM,
                QuicTransportErrors.FINAL_SIZE_ERROR,
                stream.streamId());
        doThrow(failure).when(frames).add(any());

        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> streams.produceFramesToSend(null, 1200, 1, frames));

        assertThat(thrown, sameInstance(failure));
        assertThat(thrown.sourceStreamId().orElseThrow(), equalTo(stream.streamId()));
        assertThat(thrown.errorCode(), equalTo(QuicTransportErrors.FINAL_SIZE_ERROR.code()));
        assertThat(thrown.frameType(), equalTo((long) QuicFrame.STREAM));
    }

    @Test
    void publishesStopSendingAfterFinalDataWasDispatched() throws Exception {
        QuicTransportParameters peerParameters = QuicTransportParameters.create();
        peerParameters.intParameter(initial_max_streams_uni, 1);
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        when(connection.isOpen()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.of(peerParameters));
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        QuicConnectionStreams streams = createStreams(connection);
        streams.newPeerTransportParameters(peerParameters);
        QuicSenderStream stream = streams.createNewLocalUniStream(Duration.ZERO).join();
        QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        CompletableFuture<Void> dispatch = writer.scheduleForWritingAndGetDispatchCompletion(
                BufferData.create(0), true);
        streams.enqueueForSending(stream.streamId());
        List<QuicFrame> frames = new ArrayList<>();

        streams.produceFramesToSend(null, 1200, 0, frames);
        streams.streamFramesDispatched(frames);

        assertThat(dispatch.isDone(), equalTo(true));
        assertThat(stream.sendingState(), equalTo(QuicSenderStream.SendingStreamState.DATA_SENT));

        streams.stopSendingReceived(stream, 0x110);

        assertThat(stream.whenStopSendingReceived().toCompletableFuture().join(), equalTo(0x110L));
        assertThat(stream.stopSendingReceived(), equalTo(true));
        assertThat(stream.sndErrorCode(), equalTo(0x110L));
    }

    @Test
    void resetPublishesFrameRequestBeforeFailingPendingDispatch() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        when(connection.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(connection).runWithStreamDispatchLock(any(Runnable.class));
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger resetPublication = new AtomicInteger();
        doAnswer(_ -> {
            resetPublication.set(sequence.incrementAndGet());
            return null;
        }).when(connection).requestResetStream(anyLong(), anyLong());
        QuicSenderStreamImpl stream = new QuicSenderStreamImpl(connection, 2, STREAM_BUFFER_SIZE);
        QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        CompletableFuture<Void> dispatch = writer.scheduleForWritingAndGetDispatchCompletion(
                BufferData.create(new byte[] {1}), false);
        AtomicInteger dispatchFailure = new AtomicInteger();
        dispatch.whenComplete((_, _) -> dispatchFailure.set(sequence.incrementAndGet()));

        stream.reset(0x110);

        assertThat(resetPublication.get(), equalTo(1));
        assertThat(dispatchFailure.get(), equalTo(2));
        assertThat(dispatch.isCompletedExceptionally(), equalTo(true));
    }

    @Test
    void stopSendingObservationFollowsResetAndWriterWakeup() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        when(connection.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(connection).runWithStreamDispatchLock(any(Runnable.class));
        QuicInstance quicInstance = mock(QuicInstance.class);
        when(quicInstance.executor()).thenReturn(Runnable::run);
        when(connection.quicInstance()).thenReturn(quicInstance);
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger resetPublication = new AtomicInteger();
        doAnswer(_ -> {
            resetPublication.set(sequence.incrementAndGet());
            return null;
        }).when(connection).requestResetStream(anyLong(), anyLong());
        AtomicInteger writerWakeup = new AtomicInteger();
        QuicSenderStreamImpl stream = new QuicSenderStreamImpl(connection, 2, STREAM_BUFFER_SIZE);
        QuicStreamWriter writer = stream.connectWriter(
                SequentialScheduler.lockingScheduler(() -> writerWakeup.set(sequence.incrementAndGet())));
        CompletableFuture<Void> dispatch = writer.scheduleForWritingAndGetDispatchCompletion(
                BufferData.create(new byte[] {1}), false);
        AtomicInteger dispatchFailure = new AtomicInteger();
        dispatch.whenComplete((_, _) -> dispatchFailure.set(sequence.incrementAndGet()));
        AtomicInteger stopObservation = new AtomicInteger();
        stream.whenStopSendingReceived()
                .whenComplete((_, _) -> stopObservation.set(sequence.incrementAndGet()));

        stream.stopSendingReceived(0x110);

        assertThat(resetPublication.get(), equalTo(1));
        assertThat(dispatchFailure.get(), equalTo(2));
        assertThat(writerWakeup.get(), equalTo(3));
        assertThat(stopObservation.get(), equalTo(4));
    }

    @Test
    void terminalResetDoesNotChangeStreamError() throws Exception {
        QuicTransportParameters peerParameters = QuicTransportParameters.create();
        peerParameters.intParameter(initial_max_streams_uni, 1);
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        when(connection.isOpen()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.of(peerParameters));
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(connection).runWithStreamDispatchLock(any(Runnable.class));
        QuicConnectionStreams streams = createStreams(connection);
        streams.newPeerTransportParameters(peerParameters);
        QuicSenderStream stream = streams.createNewLocalUniStream(Duration.ZERO).join();
        QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        writer.scheduleForWriting(BufferData.create(0), true);
        streams.enqueueForSending(stream.streamId());
        List<QuicFrame> frames = new ArrayList<>();
        streams.produceFramesToSend(null, 1200, 0, frames);
        streams.streamFramesDispatched(frames);
        ((QuicSenderStreamImpl) stream).dataAcknowledged(0);
        assertThat(stream.sendingState(), equalTo(QuicSenderStream.SendingStreamState.DATA_RECVD));
        assertThat(stream.sndErrorCode(), equalTo(-1L));

        stream.reset(0x110);

        assertThat(stream.sendingState(), equalTo(QuicSenderStream.SendingStreamState.DATA_RECVD));
        assertThat(stream.sndErrorCode(), equalTo(-1L));
    }

    @Test
    void resetPublishesErrorBeforeWriterFailure() throws Exception {
        QuicTransportParameters peerParameters = QuicTransportParameters.create();
        peerParameters.intParameter(initial_max_streams_uni, 1);
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        when(connection.isOpen()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.of(peerParameters));
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(connection).runWithStreamDispatchLock(any(Runnable.class));
        CountDownLatch resetSelected = new CountDownLatch(1);
        CountDownLatch releaseReset = new CountDownLatch(1);
        doAnswer(invocation -> {
            resetSelected.countDown();
            if (!releaseReset.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to release reset publication");
            }
            return null;
        }).when(connection).requestResetStream(anyLong(), anyLong());
        QuicConnectionStreams streams = createStreams(connection);
        streams.newPeerTransportParameters(peerParameters);
        QuicSenderStreamImpl stream = (QuicSenderStreamImpl) streams.createNewLocalUniStream(Duration.ZERO).join();
        QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        writer.scheduleForWriting(BufferData.create(0), true);
        streams.enqueueForSending(stream.streamId());
        List<QuicFrame> frames = new ArrayList<>();
        streams.produceFramesToSend(null, 1200, 0, frames);
        streams.streamFramesDispatched(frames);
        assertThat(stream.sendingState(), equalTo(QuicSenderStream.SendingStreamState.DATA_SENT));
        AtomicReference<Throwable> resetFailure = new AtomicReference<>();
        Thread resetThread = Thread.ofPlatform().unstarted(() -> {
            try {
                stream.reset(0x110);
            } catch (Throwable t) {
                resetFailure.set(t);
            }
        });
        resetThread.start();
        assertThat(resetSelected.await(10, TimeUnit.SECONDS), equalTo(true));

        CountDownLatch writerStarted = new CountDownLatch(1);
        AtomicReference<Throwable> writerFailure = new AtomicReference<>();
        Thread writerThread = Thread.ofPlatform().unstarted(() -> {
            writerStarted.countDown();
            try {
                writer.scheduleForWriting(BufferData.create(0), false);
            } catch (Throwable t) {
                writerFailure.set(t);
            }
        });
        writerThread.start();
        assertThat(writerStarted.await(10, TimeUnit.SECONDS), equalTo(true));
        long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (writerThread.getState() != Thread.State.WAITING
                && writerFailure.get() == null
                && System.nanoTime() < waitDeadline) {
            Thread.onSpinWait();
        }
        boolean writerWaitedForPublication = writerThread.getState() == Thread.State.WAITING;
        try {
            if (writerWaitedForPublication) {
                stream.dataAcknowledged(0);
            }
        } finally {
            releaseReset.countDown();
        }
        resetThread.join(10_000);
        writerThread.join(10_000);

        assertThat(writerWaitedForPublication, equalTo(true));
        assertThat(resetThread.isAlive(), equalTo(false));
        assertThat(writerThread.isAlive(), equalTo(false));
        assertThat(resetFailure.get(), nullValue());
        Throwable writerException = writerFailure.get();
        assertThat(writerException, instanceOf(QuicStreamException.class));
        QuicStreamException failure = (QuicStreamException) writerException;
        assertThat(failure.kind(), equalTo(QuicStreamException.Kind.RESET_LOCALLY));
        assertThat(failure.errorCode().orElseThrow(), equalTo(0x110L));
        assertThat(stream.sendingState(), equalTo(QuicSenderStream.SendingStreamState.DATA_RECVD));
        assertThat(stream.sndErrorCode(), equalTo(0x110L));
    }

    @Test
    void terminationFailsStopSendingObservation() {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        QuicSenderStreamImpl stream = new QuicSenderStreamImpl(connection, 2, STREAM_BUFFER_SIZE);
        QuicTermination termination = QuicTerminationTestSupport.local(
                QuicCloseCommand.application(0, "test termination"));

        stream.terminate(termination);

        CompletionException thrown = assertThrows(
                CompletionException.class,
                () -> stream.whenStopSendingReceived().toCompletableFuture().join());
        assertThat(thrown.getCause(), sameInstance(termination.closeCause()));
    }

    @Test
    void acknowledgedFinalFrameCompletesDispatchAfterStreamRemoval() throws Exception {
        QuicTransportParameters peerParameters = QuicTransportParameters.create();
        peerParameters.intParameter(initial_max_streams_uni, 1);
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        when(connection.isOpen()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.of(peerParameters));
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        QuicConnectionStreams streams = createStreams(connection);
        streams.newPeerTransportParameters(peerParameters);
        doAnswer(invocation -> {
            streams.notifyTerminalState(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(connection).notifyTerminalState(anyLong(), any());
        QuicSenderStream stream = streams.createNewLocalUniStream(Duration.ZERO).join();
        QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        CompletableFuture<Void> dispatch = writer.scheduleForWritingAndGetDispatchCompletion(BufferData.create(0), true);
        streams.enqueueForSending(stream.streamId());
        List<QuicFrame> frames = new ArrayList<>();

        streams.produceFramesToSend(null, 1200, 0, frames);
        StreamFrame streamFrame = frames.stream()
                .filter(StreamFrame.class::isInstance)
                .map(StreamFrame.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(dispatch.isDone(), equalTo(false));

        streams.streamDataSentAcknowledged(streamFrame);

        assertThat(dispatch.isDone(), equalTo(true));

        streams.streamFramesDispatched(frames);

        assertThat(dispatch.isDone(), equalTo(true));
    }

    private static QuicConnectionStreams localBidiStreams(QuicConnectionImpl connection, long initialLimit) {
        QuicTransportParameters peerParameters = QuicTransportParameters.create();
        peerParameters.intParameter(initial_max_streams_bidi, initialLimit);
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.executor()).thenReturn(Runnable::run);
        when(connection.quicInstance()).thenReturn(instance);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.of(peerParameters));
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        when(connection.newReassemblyBudget()).thenReturn(mock(ReassemblyBudget.class));
        QuicConnectionStreams streams = createStreams(connection);
        streams.newPeerTransportParameters(peerParameters);
        return streams;
    }

    private static QuicConnectionStreams localUniStreams(QuicConnectionImpl connection, long initialLimit) {
        QuicTransportParameters peerParameters = QuicTransportParameters.create();
        peerParameters.intParameter(initial_max_streams_uni, initialLimit);
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.executor()).thenReturn(Runnable::run);
        when(connection.quicInstance()).thenReturn(instance);
        when(connection.quicConfig()).thenReturn(QuicConfig.create());
        when(connection.isClientConnection()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.of(peerParameters));
        when(connection.localTransportParameters()).thenReturn(Optional.empty());
        when(connection.newReassemblyBudget()).thenReturn(mock(ReassemblyBudget.class));
        QuicConnectionStreams streams = createStreams(connection);
        streams.newPeerTransportParameters(peerParameters);
        return streams;
    }

    private static QuicConnectionStreams remoteUniStreams(QuicConnectionImpl connection, long initialLimit) {
        QuicTransportParameters localParameters = QuicTransportParameters.create();
        localParameters.intParameter(initial_max_streams_uni, initialLimit);
        when(connection.quicConfig()).thenReturn(QuicConfig.builder()
                                                        .maxUniStreams(initialLimit)
                                                        .buildPrototype());
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.executor()).thenReturn(Runnable::run);
        when(connection.quicInstance()).thenReturn(instance);
        when(connection.isClientConnection()).thenReturn(true);
        when(connection.isOpen()).thenReturn(true);
        when(connection.peerTransportParameters()).thenReturn(Optional.empty());
        when(connection.localTransportParameters()).thenReturn(Optional.of(localParameters));
        when(connection.newReassemblyBudget()).thenReturn(mock(ReassemblyBudget.class));
        QuicConnectionStreams streams = createStreams(connection);
        when(connection.nextMaxStreamsLimit(false, false))
                .thenAnswer(_ -> streams.nextMaxStreamsLimit(false, false));
        when(connection.nextMaxStreamsLimit(false, true))
                .thenAnswer(_ -> streams.nextMaxStreamsLimit(false, true));
        doAnswer(invocation -> {
            streams.notifyTerminalState(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(connection).notifyTerminalState(anyLong(), any());
        streams.newLocalTransportParameters(localParameters);
        return streams;
    }

    private static void finishRemoteUniStream(QuicConnectionStreams streams, long streamId)
            throws QuicTransportException {
        QuicStream stream = streams.ensureRemoteStream(streamId, QuicFrame.STREAM).orElseThrow();
        streams.processIncomingFrame(stream,
                                     StreamFrame.create(streamId, 0, 0, true, ByteBuffer.allocate(0)));
        QuicReceiverStream receiver = (QuicReceiverStream) stream;
        QuicStreamReader reader = receiver.connectReader(SequentialScheduler.lockingScheduler(() -> {
        }));
        reader.start();
        assertThat(reader.poll().orElseThrow(), sameInstance(QuicStreamReader.EOF));
        assertThat(receiver.receivingState(), equalTo(QuicReceiverStream.ReceivingStreamState.DATA_READ));
        assertThat(streams.findStream(streamId), equalTo(Optional.empty()));
    }

    private static QuicConnectionStreams createStreams(QuicConnectionImpl connection) {
        return QuicConnectionStreams.create(connection);
    }
}
