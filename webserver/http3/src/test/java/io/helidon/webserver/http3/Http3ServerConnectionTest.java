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

package io.helidon.webserver.http3;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3GoAway;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.http.http3.Http3Settings;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicRemoteStreamRegistration;
import io.helidon.quic.QuicStreamLimitException;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;
import io.helidon.webserver.Router;
import io.helidon.webserver.TransportBindingContext;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static io.helidon.quic.stream.QuicSenderStream.SendingStreamState.DATA_RECVD;
import static io.helidon.quic.stream.QuicSenderStream.SendingStreamState.READY;
import static io.helidon.quic.stream.QuicSenderStream.SendingStreamState.RESET_RECVD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class Http3ServerConnectionTest {

    @Test
    void dispatchesRequestsOnConfiguredExecutorAndCancelsQueuedWorkOnClose() {
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        RequestDispatchFixture fixture = requestDispatchFixture(submittedTask::set);

        assertThat(fixture.remoteStreamListener.test(fixture.stream), equalTo(true));
        assertThat(submittedTask.get() != null, equalTo(true));
        assertThat(fixture.completedRequests.get(), equalTo(0));

        fixture.connection.transportTerminated(new IllegalStateException("test close"));

        assertThat(((Future<?>) submittedTask.get()).isCancelled(), equalTo(true));
        submittedTask.get().run();
        assertThat(fixture.completedRequests.get(), equalTo(1));
        verify(fixture.stream, times(1)).disconnectReader(fixture.reader);
    }

    @Test
    void closesConnectionWhenConfiguredExecutorRejectsRequest() {
        RejectedExecutionException rejection = new RejectedExecutionException("test rejection");
        ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
        StreamObservation streamObservation = mock(StreamObservation.class);
        when(connectionObservation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE))
                .thenReturn(streamObservation);
        RequestDispatchFixture fixture = requestDispatchFixture(command -> {
            throw rejection;
        }, Http3Config.create(), connectionObservation);

        assertThat(fixture.remoteStreamListener.test(fixture.stream), equalTo(true));

        verify(connectionObservation, times(1)).streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE);
        verify(streamObservation, times(1)).close(StreamOutcome.REJECTED);
        verify(fixture.quicConnection).terminate(argThat(command -> command.cause().orElseThrow() == rejection));
        assertThat(fixture.completedRequests.get(), equalTo(1));
    }

    @Test
    void observesCompletedRequestStreamExactlyOnce() {
        StreamObservation observation = mock(StreamObservation.class);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                FixedLimit.create(),
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                observation,
                true);

        fixture.submittedTask.run();
        fixture.sendingCompletion.complete(DATA_RECVD);
        fixture.connection.transportTerminated(new IllegalStateException("late close"));

        verify(observation, times(1)).close(StreamOutcome.COMPLETED);
        verifyNoMoreInteractions(observation);
    }

    @Test
    void observesResetRequestStreamExactlyOnce() {
        StreamObservation observation = mock(StreamObservation.class);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                FixedLimit.create(),
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                observation,
                true);

        fixture.submittedTask.run();
        fixture.sendingCompletion.complete(RESET_RECVD);
        fixture.connection.transportTerminated(new IllegalStateException("late close"));

        verify(observation, times(1)).close(StreamOutcome.RESET);
        verifyNoMoreInteractions(observation);
    }

    @Test
    void observesFailedRequestStreamExactlyOnce() {
        StreamObservation observation = mock(StreamObservation.class);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                FixedLimit.create(),
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                observation,
                true);

        fixture.submittedTask.run();
        fixture.sendingCompletion.completeExceptionally(new IllegalStateException("test send failure"));
        fixture.connection.transportTerminated(new IllegalStateException("late close"));

        verify(observation, times(1)).close(StreamOutcome.ERROR);
        verifyNoMoreInteractions(observation);
    }

    @Test
    void finalDispatchReleasesWorkerBeforeCompletingExchange() {
        Limit requestLimit = mock(Limit.class);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        when(requestLimit.tryAcquireOutcome(false))
                .thenReturn(LimitAlgorithm.Outcome.immediateAcceptance("test", "test", token));
        StreamObservation observation = mock(StreamObservation.class);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                requestLimit,
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                observation,
                true);
        CompletableFuture<Void> finalDispatch = new CompletableFuture<>();
        when(fixture.writer.scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                .thenReturn(finalDispatch);

        fixture.submittedTask.run();

        assertThat(((Future<?>) fixture.submittedTask).isDone(), equalTo(true));
        verify(token, never()).ignore();
        verify(token, never()).success();
        verify(token, never()).dropped();
        verify(fixture.transportStream, never()).disconnectReader(fixture.reader);
        verify(observation, never()).close(any(StreamOutcome.class));

        finalDispatch.complete(null);

        verify(token, never()).ignore();
        verify(token, never()).success();
        verify(token, never()).dropped();
        verify(fixture.transportStream, never()).disconnectReader(fixture.reader);
        verify(observation, never()).close(any(StreamOutcome.class));

        fixture.completionTask.get().run();

        verify(token, times(1)).success();
        verify(token, never()).ignore();
        verify(token, never()).dropped();
        verify(fixture.transportStream, times(1)).disconnectReader(fixture.reader);
        verify(observation, never()).close(any(StreamOutcome.class));

        fixture.sendingCompletion.complete(DATA_RECVD);

        verify(observation, times(1)).close(StreamOutcome.COMPLETED);
        verifyNoMoreInteractions(observation);
    }

    @Test
    void failedFinalDispatchDropsRequestAndClosesResetStream() {
        Limit requestLimit = mock(Limit.class);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        when(requestLimit.tryAcquireOutcome(false))
                .thenReturn(LimitAlgorithm.Outcome.immediateAcceptance("test", "test", token));
        StreamObservation observation = mock(StreamObservation.class);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                requestLimit,
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                observation,
                true);
        CompletableFuture<Void> finalDispatch = new CompletableFuture<>();
        when(fixture.writer.scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                .thenReturn(finalDispatch);

        fixture.submittedTask.run();
        when(fixture.transportStream.sendingState()).thenReturn(RESET_RECVD);

        finalDispatch.completeExceptionally(new IllegalStateException("test dispatch failure"));

        verify(token, never()).ignore();
        verify(token, never()).success();
        verify(token, never()).dropped();
        verify(fixture.transportStream, never()).reset(Http3ErrorCode.INTERNAL_ERROR.code());
        verify(observation, never()).close(any(StreamOutcome.class));

        fixture.completionTask.get().run();

        verify(token, times(1)).dropped();
        verify(token, never()).ignore();
        verify(token, never()).success();
        verify(fixture.transportStream).reset(Http3ErrorCode.INTERNAL_ERROR.code());
        verify(observation, times(1)).close(StreamOutcome.RESET);
        verifyNoMoreInteractions(observation);
    }

    @Test
    void finalDispatchFailureRacingConnectionCloseCompletesLifecycleOnce() {
        Limit requestLimit = mock(Limit.class);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        when(requestLimit.tryAcquireOutcome(false))
                .thenReturn(LimitAlgorithm.Outcome.immediateAcceptance("test", "test", token));
        StreamObservation observation = mock(StreamObservation.class);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                requestLimit,
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                observation,
                true);
        CompletableFuture<Void> finalDispatch = new CompletableFuture<>();
        when(fixture.writer.scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                .thenReturn(finalDispatch);
        IllegalStateException connectionFailure = new IllegalStateException("test connection close");

        fixture.submittedTask.run();
        fixture.connection.transportTerminated(connectionFailure);
        finalDispatch.completeExceptionally(connectionFailure);
        fixture.completionTask.get().run();

        verify(token, times(1)).dropped();
        verify(token, never()).ignore();
        verify(token, never()).success();
        verify(observation, times(1)).close(StreamOutcome.ERROR);
        verifyNoMoreInteractions(observation);
        verify(fixture.transportStream, times(1)).disconnectReader(fixture.reader);
    }

    @Test
    void failedCompletedFinalDispatchIsHandledOnce() {
        Limit requestLimit = mock(Limit.class);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        when(requestLimit.tryAcquireOutcome(false))
                .thenReturn(LimitAlgorithm.Outcome.immediateAcceptance("test", "test", token));
        StreamObservation observation = mock(StreamObservation.class);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                requestLimit,
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                observation,
                true);
        when(fixture.writer.scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("test dispatch failure")));
        when(fixture.transportStream.sendingState()).thenReturn(RESET_RECVD);

        fixture.submittedTask.run();

        verify(fixture.transportStream, times(1)).reset(Http3ErrorCode.INTERNAL_ERROR.code());
        verify(token, times(1)).dropped();
        verify(token, never()).ignore();
        verify(token, never()).success();
        verify(observation, times(1)).close(StreamOutcome.RESET);
        assertThat(fixture.completionTask.get(), nullValue());
        verifyNoMoreInteractions(observation);
    }

    @Test
    void rejectedDispatchCompletionFallsBackToReceiptThread() {
        Limit requestLimit = mock(Limit.class);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        when(requestLimit.tryAcquireOutcome(false))
                .thenReturn(LimitAlgorithm.Outcome.immediateAcceptance("test", "test", token));
        StreamObservation observation = mock(StreamObservation.class);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                requestLimit,
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                observation,
                true);
        CompletableFuture<Void> finalDispatch = new CompletableFuture<>();
        when(fixture.writer.scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                .thenReturn(finalDispatch);
        fixture.completionExecutor.set(_ -> {
            throw new RejectedExecutionException("test rejection");
        });

        fixture.submittedTask.run();
        finalDispatch.complete(null);

        verify(token, times(1)).success();
        verify(token, never()).ignore();
        verify(token, never()).dropped();
        verify(fixture.transportStream, times(1)).disconnectReader(fixture.reader);
        assertThat(fixture.completionTask.get(), nullValue());
        verify(observation, never()).close(any(StreamOutcome.class));

        fixture.sendingCompletion.complete(DATA_RECVD);

        verify(observation, times(1)).close(StreamOutcome.COMPLETED);
        verifyNoMoreInteractions(observation);
    }

    @Test
    void acceptsRequestStreamIdLargerThanIntegerRange() {
        long streamId = (long) Integer.MAX_VALUE + 1;
        AtomicInteger requestId = new AtomicInteger();
        AtomicReference<Long> handledStreamId = new AtomicReference<>();
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                FixedLimit.create(),
                stream -> {
                    requestId.set(stream.requestId());
                    handledStreamId.set(stream.streamId());
                    return Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response"));
                },
                StreamObservation.noop(),
                true,
                Duration.ZERO,
                null,
                streamId);

        fixture.submittedTask.run();
        fixture.sendingCompletion.complete(DATA_RECVD);

        assertThat(requestId.get(), equalTo(1));
        assertThat(handledStreamId.get(), equalTo(streamId));
    }

    @Test
    void observesDrainingRequestRejection() {
        ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
        StreamObservation streamObservation = mock(StreamObservation.class);
        when(connectionObservation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE))
                .thenReturn(streamObservation);
        RequestDispatchFixture fixture = requestDispatchFixture(Runnable::run,
                                                                Http3Config.create(),
                                                                connectionObservation);
        fixture.connection.markDraining();

        assertThat(fixture.remoteStreamListener.test(fixture.stream), equalTo(true));

        verify(streamObservation).close(StreamOutcome.REJECTED);
        verifyNoMoreInteractions(streamObservation);
    }

    @Test
    void observesNormalConnectionTerminationAsStreamCancellation() {
        ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
        StreamObservation streamObservation = mock(StreamObservation.class);
        when(connectionObservation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE))
                .thenReturn(streamObservation);
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        RequestDispatchFixture fixture = requestDispatchFixture(submittedTask::set,
                                                                Http3Config.create(),
                                                                connectionObservation);
        assertThat(fixture.remoteStreamListener.test(fixture.stream), equalTo(true));

        fixture.connection.transportTerminated(null, null, StreamOutcome.CANCELLED);
        fixture.connection.transportTerminated(new IllegalStateException("duplicate close"), null, StreamOutcome.ERROR);

        verify(streamObservation).close(StreamOutcome.CANCELLED);
        verifyNoMoreInteractions(streamObservation);
    }

    @Test
    void observesFailedConnectionTerminationAsStreamError() {
        ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
        StreamObservation streamObservation = mock(StreamObservation.class);
        when(connectionObservation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE))
                .thenReturn(streamObservation);
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        RequestDispatchFixture fixture = requestDispatchFixture(submittedTask::set,
                                                                Http3Config.create(),
                                                                connectionObservation);
        assertThat(fixture.remoteStreamListener.test(fixture.stream), equalTo(true));

        fixture.connection.transportTerminated(new IllegalStateException("test failure"), null, StreamOutcome.ERROR);

        verify(streamObservation).close(StreamOutcome.ERROR);
        verifyNoMoreInteractions(streamObservation);
    }

    @Test
    void keepsConnectionFailureDetailLocalByDefault() {
        RequestDispatchFixture fixture = requestDispatchFixture(Runnable::run);
        Http3ProtocolException failure = Http3ProtocolException.connectionError(Http3ErrorCode.ID_ERROR,
                                                                                 "invalid\nidentifier");

        fixture.connection.fail(7, failure);

        verify(fixture.quicConnection).terminate(argThat(command -> command.layer()
                == QuicCloseCommand.Layer.APPLICATION
                && command.errorCode().orElseThrow() == Http3ErrorCode.ID_ERROR.code()
                && command.streamId().orElseThrow() == 7
                && command.cause().orElseThrow() == failure
                && command.peerDetail().isEmpty()));
    }

    @Test
    void exposesSanitizedConnectionFailureDetailWhenConfigured() {
        RequestDispatchFixture fixture = requestDispatchFixture(
                Runnable::run,
                Http3Config.builder().sendErrorDetails(true).buildPrototype());
        Http3ProtocolException failure = Http3ProtocolException.connectionError(
                Http3ErrorCode.ID_ERROR,
                "invalid\n" + "identifier".repeat(40));

        fixture.connection.fail(11, failure);

        verify(fixture.quicConnection).terminate(argThat(command -> {
            String detail = command.peerDetail().orElseThrow();
            return command.streamId().orElseThrow() == 11
                    && !detail.contains("\n")
                    && detail.getBytes(StandardCharsets.UTF_8).length <= 256;
        }));
    }

    @Test
    void rejectsStreamFailureAtConnectionOwner() {
        RequestDispatchFixture fixture = requestDispatchFixture(Runnable::run);
        Http3ProtocolException failure = Http3ProtocolException.streamError(Http3ErrorCode.MESSAGE_ERROR,
                                                                             "invalid message");

        assertThrows(IllegalArgumentException.class, () -> fixture.connection.fail(7, failure));
        verify(fixture.quicConnection, never()).terminate(any());
    }

    @Test
    void completesTerminalStageExceptionallyWhenRequestReaderCleanupFails() {
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
        StreamObservation streamObservation = mock(StreamObservation.class);
        when(connectionObservation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE))
                .thenReturn(streamObservation);
        RequestDispatchFixture fixture = requestDispatchFixture(submittedTask::set,
                                                                Http3Config.create(),
                                                                connectionObservation);
        IllegalArgumentException cleanupFailure = new IllegalArgumentException("test reader cleanup failure");
        doThrow(cleanupFailure).when(fixture.stream).disconnectReader(fixture.reader);
        assertThat(fixture.remoteStreamListener.test(fixture.stream), equalTo(true));

        fixture.connection.transportTerminated(new IllegalStateException("test connection close"));

        CompletionException thrown = assertThrows(
                CompletionException.class,
                () -> fixture.connection.whenTerminated().toCompletableFuture().join());
        assertThat(thrown.getCause(), sameInstance(cleanupFailure));
        assertThat(fixture.completedRequests.get(), equalTo(1));
        verify(streamObservation).close(StreamOutcome.ERROR);
        verifyNoMoreInteractions(streamObservation);
    }

    @Test
    void startsAndSendsSettingsExactlyOnce() throws Exception {
        ControlConnectionFixture fixture = controlConnectionFixture();

        fixture.connection.start();
        fixture.connection.start();
        fixture.controlOpen.complete(fixture.controlStream);
        fixture.connection.initialized().get(5, TimeUnit.SECONDS);

        verify(fixture.quicConnection, times(1)).addRemoteStreamListener(any());
        verify(fixture.quicConnection, times(3)).openNewLocalUniStream(any());
        verify(fixture.writer, times(1)).scheduleForWriting(any(BufferData.class), anyBoolean());
        verify(fixture.qpackWriter, times(2)).scheduleForWriting(any(BufferData.class), anyBoolean());
    }

    @Test
    void doesNotResetCriticalStreamBeforeSettingsErrorClose() throws Exception {
        ControlConnectionFixture fixture = controlConnectionFixture();
        fixture.connection.start();
        fixture.controlOpen.complete(fixture.controlStream);
        fixture.connection.initialized().get(5, TimeUnit.SECONDS);
        Http3ProtocolException failure = Http3ProtocolException.connectionError(Http3ErrorCode.SETTINGS_ERROR,
                                                                                 "invalid settings");

        fixture.connection.fail(failure);

        verify(fixture.quicConnection).terminate(argThat(command -> command.layer() == QuicCloseCommand.Layer.APPLICATION
                && command.errorCode().orElseThrow() == Http3ErrorCode.SETTINGS_ERROR.code()));
        verify(fixture.controlStream, never()).reset(anyLong());
    }

    @Test
    void waitsForAllLocalCriticalStreamsBeforeInitialization() throws Exception {
        Http3Settings settings = Http3Settings.create(0, 0);
        Http3FrameListener frameListener = Http3FrameListener.create(List.of());
        QuicConnection quicConnection = mock(QuicConnection.class);
        QuicSenderStream controlStream = mock(QuicSenderStream.class);
        QuicSenderStream encoderStream = mock(QuicSenderStream.class);
        QuicSenderStream decoderStream = mock(QuicSenderStream.class);
        QuicStreamWriter writer = mock(QuicStreamWriter.class);
        CompletableFuture<QuicSenderStream> controlOpen = new CompletableFuture<>();
        CompletableFuture<QuicSenderStream> encoderOpen = new CompletableFuture<>();
        CompletableFuture<QuicSenderStream> decoderOpen = new CompletableFuture<>();
        AtomicInteger streamOpens = new AtomicInteger();
        when(quicConnection.addRemoteStreamListener(any())).thenReturn(mock(QuicRemoteStreamRegistration.class));
        when(quicConnection.openNewLocalUniStream(any()))
                .thenAnswer(invocation -> switch (streamOpens.getAndIncrement()) {
                    case 0 -> controlOpen;
                    case 1 -> encoderOpen;
                    case 2 -> decoderOpen;
                    default -> throw new IllegalStateException("Unexpected local stream open");
                });
        for (QuicSenderStream stream : List.of(controlStream, encoderStream, decoderStream)) {
            when(stream.connectWriter(any(SequentialScheduler.class))).thenReturn(writer);
            when(stream.futureSendingCompletion()).thenReturn(new CompletableFuture<>());
        }
        Http3ServerConnection connection = serverConnection(quicConnection,
                                                            ConnectionObservation.noop(),
                                                            settings,
                                                            mock(Http3Handler.class),
                                                            frameListener,
                                                            Http3Config.create(),
                                                            mock(Http3ServerConnection.StreamLifecycle.class),
                                                            Runnable::run,
                                                            FixedLimit.create());

        connection.start();
        controlOpen.complete(controlStream);
        assertThat(connection.initialized().isDone(), equalTo(false));
        encoderOpen.complete(encoderStream);
        assertThat(connection.initialized().isDone(), equalTo(false));
        decoderOpen.complete(decoderStream);

        connection.initialized().get(5, TimeUnit.SECONDS);
    }

    @Test
    void mapsInsufficientCriticalStreamLimitToHttp3Error() {
        Http3Settings settings = Http3Settings.create(0, 0);
        Http3FrameListener frameListener = Http3FrameListener.create(List.of());
        QuicConnection quicConnection = mock(QuicConnection.class);
        QuicSenderStream controlStream = mock(QuicSenderStream.class);
        QuicStreamWriter writer = mock(QuicStreamWriter.class);
        when(quicConnection.isOpen()).thenReturn(true);
        when(quicConnection.addRemoteStreamListener(any())).thenReturn(mock(QuicRemoteStreamRegistration.class));
        when(quicConnection.openNewLocalUniStream(any()))
                .thenReturn(CompletableFuture.completedFuture(controlStream))
                .thenReturn(CompletableFuture.failedFuture(new QuicStreamLimitException("test stream limit")));
        when(controlStream.connectWriter(any(SequentialScheduler.class))).thenReturn(writer);
        when(controlStream.futureSendingCompletion()).thenReturn(new CompletableFuture<>());
        Http3ServerConnection connection = serverConnection(quicConnection,
                                                            ConnectionObservation.noop(),
                                                            settings,
                                                            mock(Http3Handler.class),
                                                            frameListener,
                                                            Http3Config.create(),
                                                            mock(Http3ServerConnection.StreamLifecycle.class),
                                                            Runnable::run,
                                                            FixedLimit.create());

        connection.start();

        CompletionException failure = assertThrows(CompletionException.class, connection.initialized()::join);
        Http3ProtocolException protocolFailure = Http3ProtocolException.find(failure).orElseThrow();
        assertThat(protocolFailure.errorCode(), equalTo(Http3ErrorCode.STREAM_CREATION_ERROR));
    }

    @Test
    void doesNotResetControlStreamWhenInitialWriteFails() {
        Http3Settings settings = Http3Settings.create(0, 0);
        Http3FrameListener frameListener = Http3FrameListener.create(List.of());
        QuicConnection quicConnection = mock(QuicConnection.class);
        QuicSenderStream controlStream = mock(QuicSenderStream.class);
        QuicStreamWriter writer = mock(QuicStreamWriter.class);
        IllegalStateException writeFailure = new IllegalStateException("test write failure");
        when(quicConnection.addRemoteStreamListener(any())).thenReturn(mock(QuicRemoteStreamRegistration.class));
        when(quicConnection.openNewLocalUniStream(any())).thenReturn(CompletableFuture.completedFuture(controlStream));
        when(controlStream.connectWriter(any(SequentialScheduler.class))).thenReturn(writer);
        doThrow(writeFailure).when(writer).scheduleForWriting(any(BufferData.class), anyBoolean());
        Http3ServerConnection connection = serverConnection(quicConnection,
                                                            ConnectionObservation.noop(),
                                                            settings,
                                                            mock(Http3Handler.class),
                                                            frameListener,
                                                            Http3Config.create(),
                                                            mock(Http3ServerConnection.StreamLifecycle.class),
                                                            Runnable::run,
                                                            FixedLimit.create());

        connection.start();

        assertThrows(CompletionException.class, connection.initialized()::join);
        verify(quicConnection).terminate(argThat(command -> command.layer() == QuicCloseCommand.Layer.APPLICATION
                && command.errorCode().orElseThrow() == Http3ErrorCode.INTERNAL_ERROR.code()));
        verify(controlStream, never()).reset(anyLong());
    }

    @Test
    void doesNotResetInstructionStreamWhenWriterConnectionFails() {
        Http3Settings settings = Http3Settings.create(0, 0);
        Http3FrameListener frameListener = Http3FrameListener.create(List.of());
        QuicConnection quicConnection = mock(QuicConnection.class);
        QuicSenderStream controlStream = mock(QuicSenderStream.class);
        QuicSenderStream encoderStream = mock(QuicSenderStream.class);
        QuicStreamWriter controlWriter = mock(QuicStreamWriter.class);
        when(quicConnection.addRemoteStreamListener(any())).thenReturn(mock(QuicRemoteStreamRegistration.class));
        when(quicConnection.openNewLocalUniStream(any()))
                .thenReturn(CompletableFuture.completedFuture(controlStream))
                .thenReturn(CompletableFuture.completedFuture(encoderStream));
        when(controlStream.connectWriter(any(SequentialScheduler.class))).thenReturn(controlWriter);
        when(controlStream.futureSendingCompletion()).thenReturn(new CompletableFuture<>());
        when(encoderStream.connectWriter(any(SequentialScheduler.class)))
                .thenThrow(new IllegalStateException("test writer connection failure"));
        Http3ServerConnection connection = serverConnection(quicConnection,
                                                            ConnectionObservation.noop(),
                                                            settings,
                                                            mock(Http3Handler.class),
                                                            frameListener,
                                                            Http3Config.create(),
                                                            mock(Http3ServerConnection.StreamLifecycle.class),
                                                            Runnable::run,
                                                            FixedLimit.create());

        connection.start();

        assertThrows(CompletionException.class, connection.initialized()::join);
        verify(quicConnection).terminate(argThat(command -> command.layer() == QuicCloseCommand.Layer.APPLICATION
                && command.errorCode().orElseThrow() == Http3ErrorCode.INTERNAL_ERROR.code()));
        verify(encoderStream, never()).reset(anyLong());
    }

    @Test
    void doesNotResetCriticalStreamOpenedDuringConnectionClose() {
        ControlConnectionFixture fixture = controlConnectionFixture();
        fixture.connection.start();
        fixture.connection.fail(Http3ProtocolException.connectionError(Http3ErrorCode.SETTINGS_ERROR,
                                                                        "invalid settings"));

        fixture.controlOpen.complete(fixture.controlStream);

        verify(fixture.controlStream, never()).reset(anyLong());
    }

    @Test
    void serializesGoAwayAndReusesEqualDispatchReceipt() throws Exception {
        ControlConnectionFixture fixture = controlConnectionFixture();
        List<byte[]> frames = new ArrayList<>();
        CompletableFuture<Void> firstDispatch = new CompletableFuture<>();
        CompletableFuture<Void> secondDispatch = new CompletableFuture<>();
        when(fixture.writer.scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                .thenAnswer(invocation -> {
                    frames.add(invocation.<BufferData>getArgument(0).readBytes());
                    return frames.size() == 1 ? firstDispatch : secondDispatch;
                });

        CompletableFuture<Void> first = fixture.connection.sendGoAway(
                Http3GoAway.requestStream(Http3Protocol.MAX_CLIENT_BIDIRECTIONAL_STREAM_ID),
                "test-first");
        CompletableFuture<Void> equal = fixture.connection.sendGoAway(
                Http3GoAway.requestStream(Http3Protocol.MAX_CLIENT_BIDIRECTIONAL_STREAM_ID),
                "test-equal");
        CompletableFuture<Void> second = fixture.connection.sendGoAway(Http3GoAway.requestStream(8), "test-second");

        assertThat(equal, sameInstance(first));
        assertThrows(IllegalStateException.class,
                     () -> fixture.connection.sendGoAway(Http3GoAway.requestStream(12), "test-increase"));
        fixture.connection.start();
        fixture.controlOpen.complete(fixture.controlStream);
        fixture.connection.initialized().get(5, TimeUnit.SECONDS);

        verify(fixture.writer, times(1)).scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean());
        assertThat(frames.getFirst(),
                   equalTo(Http3Protocol.goAwayFrame(
                           Http3GoAway.requestStream(Http3Protocol.MAX_CLIENT_BIDIRECTIONAL_STREAM_ID))));
        assertThat(second.isDone(), equalTo(false));

        firstDispatch.complete(null);

        verify(fixture.writer, times(2)).scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean());
        assertThat(frames.get(1), equalTo(Http3Protocol.goAwayFrame(Http3GoAway.requestStream(8))));
        secondDispatch.complete(null);
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);

        InOrder order = inOrder(fixture.writer);
        order.verify(fixture.writer).scheduleForWriting(any(BufferData.class), anyBoolean());
        order.verify(fixture.writer, times(2))
                .scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean());
        verify(fixture.controlStream, never()).dataSent();
    }

    @Test
    void treatsLocalCriticalStreamTerminationAsConnectionError() throws Exception {
        ControlConnectionFixture fixture = controlConnectionFixture();
        fixture.connection.start();
        fixture.controlOpen.complete(fixture.controlStream);
        fixture.connection.initialized().get(5, TimeUnit.SECONDS);

        fixture.controlCompletion.complete(DATA_RECVD);

        verify(fixture.quicConnection).terminate(argThat(command -> command.layer() == QuicCloseCommand.Layer.APPLICATION
                && command.errorCode().orElseThrow() == Http3ErrorCode.CLOSED_CRITICAL_STREAM.code()));
    }

    @Test
    void finalGoAwayRemainsAboveHighestAcceptedStream() throws Exception {
        ControlConnectionFixture fixture = controlConnectionFixture();
        List<byte[]> frames = new ArrayList<>();
        when(fixture.writer.scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                .thenAnswer(invocation -> {
                    frames.add(invocation.<BufferData>getArgument(0).readBytes());
                    return CompletableFuture.completedFuture(null);
                });
        fixture.connection.start();
        fixture.controlOpen.complete(fixture.controlStream);
        fixture.connection.initialized().get(5, TimeUnit.SECONDS);

        RequestTransportFixture accepted = requestTransportFixture(8, BufferData.EMPTY_BYTES, false);
        assertThat(fixture.remoteStreamListener.test(accepted.stream), equalTo(true));
        assertThat(fixture.submittedTask.get() != null, equalTo(true));
        fixture.connection.markDraining();
        RequestTransportFixture rejected = requestTransportFixture(4, BufferData.EMPTY_BYTES, false);
        assertThat(fixture.remoteStreamListener.test(rejected.stream), equalTo(true));
        verify(rejected.stream).requestStopSending(Http3ErrorCode.REQUEST_REJECTED.code());
        verify(rejected.stream).reset(Http3ErrorCode.REQUEST_REJECTED.code());

        fixture.connection.sendFinalGoAway().get(5, TimeUnit.SECONDS);

        assertThat(frames.size(), equalTo(1));
        assertThat(frames.getFirst(), equalTo(Http3Protocol.goAwayFrame(Http3GoAway.requestStream(12))));
    }

    @Test
    void finalGoAwayDoesNotIncreaseAnExistingBoundary() throws Exception {
        ControlConnectionFixture fixture = controlConnectionFixture();
        List<byte[]> frames = new ArrayList<>();
        when(fixture.writer.scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                .thenAnswer(invocation -> {
                    frames.add(invocation.<BufferData>getArgument(0).readBytes());
                    return CompletableFuture.completedFuture(null);
                });
        fixture.connection.start();
        fixture.controlOpen.complete(fixture.controlStream);
        fixture.connection.initialized().get(5, TimeUnit.SECONDS);

        RequestTransportFixture accepted = requestTransportFixture(8, BufferData.EMPTY_BYTES, false);
        assertThat(fixture.remoteStreamListener.test(accepted.stream), equalTo(true));
        assertThat(fixture.submittedTask.get() != null, equalTo(true));
        fixture.connection.sendGoAway(Http3GoAway.requestStream(4), "manual").get(5, TimeUnit.SECONDS);

        fixture.connection.sendFinalGoAway().get(5, TimeUnit.SECONDS);

        assertThat(frames.size(), equalTo(1));
        assertThat(frames.getFirst(), equalTo(Http3Protocol.goAwayFrame(Http3GoAway.requestStream(4))));
    }

    @Test
    void closesRemoteStreamRegistrationOnce() {
        Http3Settings settings = Http3Settings.create(0, 0);
        Http3FrameListener frameListener = Http3FrameListener.create(List.of());
        QuicConnection quicConnection = mock(QuicConnection.class);
        QuicRemoteStreamRegistration registration = mock(QuicRemoteStreamRegistration.class);
        when(quicConnection.addRemoteStreamListener(any())).thenReturn(registration);
        when(quicConnection.openNewLocalUniStream(any())).thenReturn(new CompletableFuture<QuicSenderStream>());
        Http3ServerConnection connection = serverConnection(quicConnection,
                                                            ConnectionObservation.noop(),
                                                            settings,
                                                            mock(Http3Handler.class),
                                                            frameListener,
                                                            Http3Config.create(),
                                                            mock(Http3ServerConnection.StreamLifecycle.class),
                                                            Runnable::run,
                                                            FixedLimit.create());

        connection.start();
        connection.transportTerminated(new IllegalStateException("test close"));
        connection.transportTerminated(new IllegalStateException("duplicate close"));

        verify(registration, times(1)).close();
    }

    @Test
    void closesConnectionOwnedQpackStateBeforeStreamCleanup() {
        ControlConnectionFixture fixture = controlConnectionFixture();
        IllegalStateException closeCause = new IllegalStateException("test close");

        fixture.connection.transportTerminated(closeCause);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> fixture.connection.qpackContext().encodeHeaders(0, List.of()));
        assertThat(failure.getCause(), sameInstance(closeCause));
    }

    @Test
    void closeWaitsForAtomicRequestRegistration() throws Exception {
        CountDownLatch admissionStarted = new CountDownLatch(1);
        CountDownLatch releaseAdmission = new CountDownLatch(1);
        CountDownLatch closeStarted = new CountDownLatch(1);
        AtomicInteger completedRequests = new AtomicInteger();
        Http3ServerConnection.StreamLifecycle lifecycle = new Http3ServerConnection.StreamLifecycle() {
            @Override
            public boolean requestStarted(Http3ServerConnection connection, Http3ServerStream stream) {
                admissionStarted.countDown();
                try {
                    return releaseAdmission.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            @Override
            public void requestCompleted(Http3ServerConnection connection, Http3ServerStream stream) {
                completedRequests.incrementAndGet();
            }
        };

        Http3Settings settings = Http3Settings.create(0, 0);
        Http3FrameListener frameListener = Http3FrameListener.create(List.of());
        QuicConnection quicConnection = mock(QuicConnection.class);
        Http3Handler handler = mock(Http3Handler.class);
        AtomicReference<Predicate<? super QuicReceiverStream>> remoteStreamListener = new AtomicReference<>();
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        when(quicConnection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            remoteStreamListener.set(invocation.getArgument(0));
            return mock(QuicRemoteStreamRegistration.class);
        });
        when(quicConnection.openNewLocalUniStream(any())).thenReturn(new CompletableFuture<>());
        Http3ServerConnection connection = serverConnection(quicConnection,
                                                            ConnectionObservation.noop(),
                                                            settings,
                                                            handler,
                                                            frameListener,
                                                            Http3Config.create(),
                                                            lifecycle,
                                                            submittedTask::set,
                                                            FixedLimit.create());
        RequestTransportFixture requestStream = requestTransportFixture(0, BufferData.EMPTY_BYTES, false);
        connection.start();

        CompletableFuture<Boolean> registration = CompletableFuture.supplyAsync(
                () -> remoteStreamListener.get().test(requestStream.stream));
        CompletableFuture<Void> close = null;
        try {
            assertThat(admissionStarted.await(5, TimeUnit.SECONDS), equalTo(true));
            close = CompletableFuture.runAsync(() -> {
                closeStarted.countDown();
                connection.transportTerminated(new IllegalStateException("test close"));
            });
            assertThat(closeStarted.await(5, TimeUnit.SECONDS), equalTo(true));
            CompletableFuture<Void> currentClose = close;
            assertThrows(TimeoutException.class, () -> currentClose.get(100, TimeUnit.MILLISECONDS));

            releaseAdmission.countDown();
            assertThat(registration.get(5, TimeUnit.SECONDS), equalTo(true));
            close.get(5, TimeUnit.SECONDS);
            assertThat(completedRequests.get(), equalTo(1));
            verify(requestStream.stream, times(1)).disconnectReader(requestStream.reader);

            connection.transportTerminated(new IllegalStateException("duplicate close"));
            assertThat(completedRequests.get(), equalTo(1));
        } finally {
            releaseAdmission.countDown();
            if (close != null) {
                close.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void ignoresPermitAcceptedAfterConnectionCloses() throws Exception {
        CountDownLatch acquisitionStarted = new CountDownLatch(1);
        CountDownLatch releaseAcquisition = new CountDownLatch(1);
        Limit requestLimit = mock(Limit.class);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        LimitAlgorithm.Outcome outcome = LimitAlgorithm.Outcome.immediateAcceptance("test", "test", token);
        when(requestLimit.tryAcquireOutcome(false)).thenAnswer(invocation -> {
            acquisitionStarted.countDown();
            boolean interrupted = false;
            for (;;) {
                try {
                    if (releaseAcquisition.await(5, TimeUnit.SECONDS)) {
                        break;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return outcome;
        });
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        RequestDispatchFixture fixture = requestDispatchFixture(submittedTask::set,
                                                                Http3Config.create(),
                                                                ConnectionObservation.noop(),
                                                                requestLimit);
        CompletableFuture<Boolean> admission = CompletableFuture.supplyAsync(
                () -> fixture.remoteStreamListener.test(fixture.stream));
        try {
            assertThat(acquisitionStarted.await(5, TimeUnit.SECONDS), equalTo(true));
            fixture.connection.transportTerminated(new IllegalStateException("test close"));
            releaseAcquisition.countDown();

            assertThat(admission.get(5, TimeUnit.SECONDS), equalTo(true));
            assertThat(submittedTask.get(), nullValue());
            assertThat(fixture.completedRequests.get(), equalTo(1));
            verify(token, times(1)).ignore();
            verify(token, never()).success();
            verify(token, never()).dropped();
        } finally {
            releaseAcquisition.countDown();
        }
    }

    @Test
    void rejectsBeforeRequestDecodeAndTaskSubmission() {
        Limit requestLimit = mock(Limit.class);
        when(requestLimit.tryAcquireOutcome(false))
                .thenReturn(LimitAlgorithm.Outcome.immediateRejection("test", "test"));
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        RequestDispatchFixture fixture = requestDispatchFixture(submittedTask::set,
                                                                Http3Config.create(),
                                                                ConnectionObservation.noop(),
                                                                requestLimit);

        assertThat(fixture.remoteStreamListener.test(fixture.stream), equalTo(true));

        assertThat(submittedTask.get(), nullValue());
        assertThat(fixture.completedRequests.get(), equalTo(1));
        verify(fixture.reader, never()).poll();
        verify(requestLimit).tryAcquireOutcome(false);
        verify(fixture.stream).requestStopSending(Http3ErrorCode.REQUEST_REJECTED.code());
        verify(fixture.stream).reset(Http3ErrorCode.REQUEST_REJECTED.code());
        verify(fixture.stream).disconnectReader(fixture.reader);
    }

    @Test
    void requestHeadTimeoutResetsStreamAndReleasesPermit() {
        Limit requestLimit = mock(Limit.class);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        when(requestLimit.tryAcquireOutcome(false))
                .thenReturn(LimitAlgorithm.Outcome.immediateAcceptance("test", "test", token));
        AtomicInteger handled = new AtomicInteger();
        StreamObservation observation = mock(StreamObservation.class);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                requestLimit,
                _ -> {
                    handled.incrementAndGet();
                    return Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "unexpected"));
                },
                observation,
                false,
                Duration.ofMillis(50),
                BufferData.EMPTY_BYTES);

        fixture.submittedTask.run();

        assertThat(handled.get(), equalTo(0));
        verify(fixture.transportStream).requestStopSending(Http3ErrorCode.REQUEST_CANCELLED.code());
        verify(fixture.transportStream).reset(Http3ErrorCode.REQUEST_CANCELLED.code());
        verify(token).dropped();
        verify(token, never()).ignore();
        verify(token, never()).success();

        fixture.sendingCompletion.complete(RESET_RECVD);
        verify(observation).close(StreamOutcome.RESET);
    }

    @Test
    void holdsRequestPermitWhileDrainingResponseIsStillSending() {
        Limit requestLimit = mock(Limit.class);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        LimitAlgorithm.Outcome outcome = LimitAlgorithm.Outcome.immediateAcceptance("test", "test", token);
        when(requestLimit.tryAcquireOutcome(false)).thenReturn(outcome);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                requestLimit,
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                StreamObservation.noop(),
                true);
        fixture.connection.markDraining();

        fixture.submittedTask.run();

        verify(token, never()).ignore();
        verify(token, never()).success();
        verify(token, never()).dropped();

        fixture.sendingCompletion.complete(DATA_RECVD);

        verify(token, times(1)).success();
        verify(token, never()).ignore();
        verify(token, never()).dropped();
    }

    @Test
    void dropsRequestPermitWhenDrainingResponseSendFails() {
        Limit requestLimit = mock(Limit.class);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        LimitAlgorithm.Outcome outcome = LimitAlgorithm.Outcome.immediateAcceptance("test", "test", token);
        when(requestLimit.tryAcquireOutcome(false)).thenReturn(outcome);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                requestLimit,
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                StreamObservation.noop(),
                true);
        fixture.connection.markDraining();

        fixture.submittedTask.run();

        fixture.sendingCompletion.completeExceptionally(new IllegalStateException("test send failure"));

        verify(token, times(1)).dropped();
        verify(token, never()).ignore();
        verify(token, never()).success();
    }

    @Test
    void holdsRequestPermitUntilWorkerStopsAfterConnectionClose() throws Exception {
        Limit requestLimit = mock(Limit.class);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        LimitAlgorithm.Outcome outcome = LimitAlgorithm.Outcome.immediateAcceptance("test", "test", token);
        when(requestLimit.tryAcquireOutcome(false)).thenReturn(outcome);
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        RequestLifecycleFixture fixture = requestLifecycleFixture(requestLimit,
                                                                  _ -> {
                                                                      handlerStarted.countDown();
                                                                      boolean interrupted = false;
                                                                      for (;;) {
                                                                          try {
                                                                              if (releaseHandler.await(5,
                                                                                                       TimeUnit.SECONDS)) {
                                                                                  break;
                                                                              }
                                                                          } catch (InterruptedException e) {
                                                                              interrupted = true;
                                                                          }
                                                                      }
                                                                      if (interrupted) {
                                                                          Thread.currentThread().interrupt();
                                                                      }
                                                                      return Optional.of(Http3Handler.BufferedResponse.text(
                                                                              Status.OK_200.code(),
                                                                              "test response"));
                                                                  },
                                                                  StreamObservation.noop(),
                                                                  true);
        CompletableFuture<Void> worker = CompletableFuture.runAsync(fixture.submittedTask);
        try {
            assertThat(handlerStarted.await(5, TimeUnit.SECONDS), equalTo(true));
            fixture.connection.transportTerminated(new IllegalStateException("test close"));

            verify(token, never()).ignore();
            verify(token, never()).success();
            verify(token, never()).dropped();

            releaseHandler.countDown();
            worker.get(5, TimeUnit.SECONDS);

            verify(token, times(1)).dropped();
            verify(token, never()).ignore();
            verify(token, never()).success();
        } finally {
            releaseHandler.countDown();
        }
    }

    @Test
    void isolatesRequestPermitCallbackFailureFromConnectionClose() {
        Limit requestLimit = mock(Limit.class);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        doThrow(new IllegalStateException("test callback failure")).when(token).dropped();
        LimitAlgorithm.Outcome outcome = LimitAlgorithm.Outcome.immediateAcceptance("test", "test", token);
        when(requestLimit.tryAcquireOutcome(false)).thenReturn(outcome);
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                requestLimit,
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                StreamObservation.noop(),
                true);
        fixture.connection.markDraining();

        fixture.submittedTask.run();

        fixture.connection.transportTerminated(new IllegalStateException("test close"));
        verify(token, times(1)).dropped();
    }

    @Test
    void cancelsRequestInputExactlyOnce() {
        RequestLifecycleFixture fixture = requestLifecycleFixture(
                FixedLimit.create(),
                _ -> Optional.of(Http3Handler.BufferedResponse.text(Status.OK_200.code(), "test response")),
                StreamObservation.noop(),
                false);

        fixture.submittedTask.run();
        fixture.connection.transportTerminated(new IllegalStateException("late close"));
        fixture.connection.transportTerminated(new IllegalStateException("duplicate close"));

        verify(fixture.transportStream, times(1)).requestStopSending(Http3ErrorCode.REQUEST_CANCELLED.code());
        verify(fixture.transportStream, times(1)).disconnectReader(fixture.reader);
        verify(fixture.transportStream, never()).reset(anyLong());
    }

    private static ControlConnectionFixture controlConnectionFixture() {
        Http3Settings settings = Http3Settings.create(0, 0);
        Http3FrameListener frameListener = Http3FrameListener.create(List.of());
        QuicConnection quicConnection = mock(QuicConnection.class);
        QuicRemoteStreamRegistration registration = mock(QuicRemoteStreamRegistration.class);
        QuicSenderStream controlStream = mock(QuicSenderStream.class);
        QuicSenderStream encoderStream = mock(QuicSenderStream.class);
        QuicSenderStream decoderStream = mock(QuicSenderStream.class);
        QuicStreamWriter writer = mock(QuicStreamWriter.class);
        QuicStreamWriter qpackWriter = mock(QuicStreamWriter.class);
        CompletableFuture<QuicSenderStream> controlOpen = new CompletableFuture<>();
        CompletableFuture<QuicSenderStream.SendingStreamState> controlCompletion = new CompletableFuture<>();
        AtomicInteger streamOpens = new AtomicInteger();
        AtomicReference<Predicate<? super QuicReceiverStream>> remoteStreamListener = new AtomicReference<>();
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        Http3ServerConnection.StreamLifecycle lifecycle = mock(Http3ServerConnection.StreamLifecycle.class);
        when(lifecycle.requestStarted(any(), any())).thenReturn(true);
        when(quicConnection.isOpen()).thenReturn(true);
        when(quicConnection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            remoteStreamListener.set(invocation.getArgument(0));
            return registration;
        });
        when(quicConnection.openNewLocalUniStream(any()))
                .thenAnswer(invocation -> switch (streamOpens.getAndIncrement()) {
                    case 0 -> controlOpen;
                    case 1 -> CompletableFuture.completedFuture(encoderStream);
                    case 2 -> CompletableFuture.completedFuture(decoderStream);
                    default -> throw new IllegalStateException("Unexpected local stream open");
                });
        when(controlStream.connectWriter(any(SequentialScheduler.class))).thenReturn(writer);
        when(controlStream.futureSendingCompletion()).thenReturn(controlCompletion);
        when(encoderStream.connectWriter(any(SequentialScheduler.class))).thenReturn(qpackWriter);
        when(encoderStream.futureSendingCompletion()).thenReturn(new CompletableFuture<>());
        when(decoderStream.connectWriter(any(SequentialScheduler.class))).thenReturn(qpackWriter);
        when(decoderStream.futureSendingCompletion()).thenReturn(new CompletableFuture<>());
        Http3ServerConnection connection = serverConnection(quicConnection,
                                                            ConnectionObservation.noop(),
                                                            settings,
                                                            mock(Http3Handler.class),
                                                            frameListener,
                                                            Http3Config.create(),
                                                            lifecycle,
                                                            submittedTask::set,
                                                            FixedLimit.create());
        return new ControlConnectionFixture(connection,
                                            quicConnection,
                                            controlStream,
                                            writer,
                                            qpackWriter,
                                            controlOpen,
                                            controlCompletion,
                                            stream -> remoteStreamListener.get().test(stream),
                                            submittedTask);
    }

    private static RequestDispatchFixture requestDispatchFixture(Executor requestExecutor) {
        return requestDispatchFixture(requestExecutor, Http3Config.create());
    }

    private static RequestDispatchFixture requestDispatchFixture(Executor requestExecutor, Http3Config config) {
        return requestDispatchFixture(requestExecutor, config, ConnectionObservation.noop());
    }

    private static RequestDispatchFixture requestDispatchFixture(Executor requestExecutor,
                                                                 Http3Config config,
                                                                 ConnectionObservation transportObservation) {
        return requestDispatchFixture(requestExecutor,
                                      config,
                                      transportObservation,
                                      FixedLimit.create());
    }

    private static RequestDispatchFixture requestDispatchFixture(Executor requestExecutor,
                                                                 Http3Config config,
                                                                 ConnectionObservation transportObservation,
                                                                 Limit requestLimit) {
        Http3Settings settings = Http3Settings.create(0, 0);
        Http3FrameListener frameListener = Http3FrameListener.create(List.of());
        QuicConnection quicConnection = mock(QuicConnection.class);
        QuicRemoteStreamRegistration registration = mock(QuicRemoteStreamRegistration.class);
        AtomicReference<Predicate<? super QuicReceiverStream>> remoteStreamListener = new AtomicReference<>();
        when(quicConnection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            remoteStreamListener.set(invocation.getArgument(0));
            return registration;
        });
        when(quicConnection.openNewLocalUniStream(any())).thenReturn(new CompletableFuture<>());
        AtomicInteger completedRequests = new AtomicInteger();
        Http3ServerConnection.StreamLifecycle lifecycle = new Http3ServerConnection.StreamLifecycle() {
            @Override
            public boolean requestStarted(Http3ServerConnection connection, Http3ServerStream stream) {
                return true;
            }

            @Override
            public void requestCompleted(Http3ServerConnection connection, Http3ServerStream stream) {
                completedRequests.incrementAndGet();
            }
        };
        Http3ServerConnection connection = serverConnection(quicConnection,
                                                            transportObservation,
                                                            settings,
                                                            mock(Http3Handler.class),
                                                            frameListener,
                                                            config,
                                                            lifecycle,
                                                            requestExecutor,
                                                            requestLimit);
        QuicBidiStream stream = mock(QuicBidiStream.class);
        QuicStreamReader reader = mock(QuicStreamReader.class);
        when(stream.streamId()).thenReturn(0L);
        when(stream.connectReader(any(SequentialScheduler.class))).thenReturn(reader);
        connection.start();
        return new RequestDispatchFixture(connection,
                                          quicConnection,
                                          remoteStreamListener.get(),
                                          stream,
                                          reader,
                                          completedRequests);
    }

    private static RequestLifecycleFixture requestLifecycleFixture(Limit requestLimit,
                                                                   Http3Handler handler,
                                                                   StreamObservation transportObservation,
                                                                   boolean endOfStream) {
        return requestLifecycleFixture(requestLimit,
                                       handler,
                                       transportObservation,
                                       endOfStream,
                                       Duration.ZERO,
                                       null,
                                       0);
    }

    private static RequestLifecycleFixture requestLifecycleFixture(Limit requestLimit,
                                                                   Http3Handler handler,
                                                                   StreamObservation transportObservation,
                                                                   boolean endOfStream,
                                                                   Duration requestReadTimeout,
                                                                   byte[] requestBytesOverride) {
        return requestLifecycleFixture(requestLimit,
                                       handler,
                                       transportObservation,
                                       endOfStream,
                                       requestReadTimeout,
                                       requestBytesOverride,
                                       0);
    }

    private static RequestLifecycleFixture requestLifecycleFixture(Limit requestLimit,
                                                                   Http3Handler handler,
                                                                   StreamObservation transportObservation,
                                                                   boolean endOfStream,
                                                                   Duration requestReadTimeout,
                                                                   byte[] requestBytesOverride,
                                                                   long streamId) {
        Http3Settings settings = Http3Settings.create(0, 0);
        Http3FrameListener frameListener = Http3FrameListener.create(List.of());
        QuicConnection quicConnection = mock(QuicConnection.class);
        QuicRemoteStreamRegistration registration = mock(QuicRemoteStreamRegistration.class);
        QuicSenderStream controlStream = mock(QuicSenderStream.class);
        QuicSenderStream encoderStream = mock(QuicSenderStream.class);
        QuicSenderStream decoderStream = mock(QuicSenderStream.class);
        QuicStreamWriter criticalStreamWriter = mock(QuicStreamWriter.class);
        AtomicInteger streamOpens = new AtomicInteger();
        AtomicReference<Predicate<? super QuicReceiverStream>> remoteStreamListener = new AtomicReference<>();
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        AtomicReference<Runnable> completionTask = new AtomicReference<>();
        AtomicReference<Executor> completionExecutor = new AtomicReference<>(task -> {
            if (!completionTask.compareAndSet(null, task)) {
                throw new IllegalStateException("Unexpected additional completion task");
            }
        });
        Executor requestExecutor = task -> {
            if (!submittedTask.compareAndSet(null, task)) {
                completionExecutor.get().execute(task);
            }
        };
        ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
        when(connectionObservation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE))
                .thenReturn(transportObservation);
        when(quicConnection.isOpen()).thenReturn(true);
        when(quicConnection.addRemoteStreamListener(any())).thenAnswer(invocation -> {
            remoteStreamListener.set(invocation.getArgument(0));
            return registration;
        });
        when(quicConnection.openNewLocalUniStream(any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(switch (streamOpens.getAndIncrement()) {
                    case 0 -> controlStream;
                    case 1 -> encoderStream;
                    case 2 -> decoderStream;
                    default -> throw new IllegalStateException("Unexpected local stream open");
                }));
        for (QuicSenderStream stream : List.of(controlStream, encoderStream, decoderStream)) {
            when(stream.connectWriter(any(SequentialScheduler.class))).thenReturn(criticalStreamWriter);
            when(stream.futureSendingCompletion()).thenReturn(new CompletableFuture<>());
        }
        Http3ServerConnection.StreamLifecycle lifecycle = mock(Http3ServerConnection.StreamLifecycle.class);
        when(lifecycle.requestStarted(any(), any())).thenReturn(true);
        Http3Config config = Http3Config.create();
        Http3ServerConnection connection = serverConnection(quicConnection,
                                                            connectionObservation,
                                                            settings,
                                                            handler,
                                                            frameListener,
                                                            config,
                                                            lifecycle,
                                                            requestExecutor,
                                                            requestLimit,
                                                            requestReadTimeout);
        connection.start();
        connection.initialized().join();

        Http3QpackContext requestQpack = Http3QpackContext.create(0,
                                                                 0,
                                                                 config.maxHeadersSize(),
                                                                 failure -> {
                                                                     throw new AssertionError(failure);
                                                                 });
        requestQpack.peerSettings(0, 0);
        byte[] requestBytes = requestBytesOverride == null
                ? Http3Protocol.encodeRequestHeaders(requestQpack,
                                                     0,
                                                     URI.create("https://localhost/test"),
                                                     "GET",
                                                     WritableHeaders.create())
                : requestBytesOverride;
        RequestTransportFixture requestStream = requestTransportFixture(streamId, requestBytes, endOfStream);
        assertThat(remoteStreamListener.get().test(requestStream.stream), equalTo(true));
        assertThat(submittedTask.get() != null, equalTo(true));
        return new RequestLifecycleFixture(connection,
                                           requestStream.stream,
                                           requestStream.reader,
                                           requestStream.writer,
                                           requestStream.sendingCompletion,
                                           submittedTask.get(),
                                           completionTask,
                                           completionExecutor);
    }

    private static RequestTransportFixture requestTransportFixture(long streamId,
                                                                   byte[] requestBytes,
                                                                   boolean endOfStream) {
        QuicBidiStream transportStream = mock(QuicBidiStream.class);
        QuicStreamReader reader = mock(QuicStreamReader.class);
        QuicStreamWriter writer = mock(QuicStreamWriter.class);
        CompletableFuture<QuicSenderStream.SendingStreamState> sendingCompletion = new CompletableFuture<>();
        when(transportStream.streamId()).thenReturn(streamId);
        when(transportStream.connectReader(any(SequentialScheduler.class))).thenReturn(reader);
        when(transportStream.connectWriter(any(SequentialScheduler.class))).thenReturn(writer);
        when(transportStream.futureSendingCompletion()).thenReturn(sendingCompletion);
        when(writer.scheduleForWritingAndGetDispatchCompletion(any(BufferData.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(transportStream.sendingState()).thenReturn(READY);
        when(transportStream.sndErrorCode()).thenReturn(-1L);
        when(transportStream.rcvErrorCode()).thenReturn(-1L);
        AtomicInteger polls = new AtomicInteger();
        when(reader.poll()).thenAnswer(_ -> polls.getAndIncrement() == 0
                ? Optional.of(BufferData.create(requestBytes))
                : endOfStream ? Optional.of(QuicStreamReader.EOF) : Optional.empty());
        when(reader.peek()).thenReturn(endOfStream ? Optional.of(QuicStreamReader.EOF) : Optional.empty());
        return new RequestTransportFixture(transportStream, reader, writer, sendingCompletion);
    }

    private static Http3ServerConnection serverConnection(QuicConnection quicConnection,
                                                          ConnectionObservation transportObservation,
                                                          Http3Settings settings,
                                                          Http3Handler handler,
                                                          Http3FrameListener frameListener,
                                                          Http3Config config,
                                                          Http3ServerConnection.StreamLifecycle lifecycle,
                                                          Executor requestExecutor,
                                                          Limit requestLimit) {
        return serverConnection(quicConnection,
                                transportObservation,
                                settings,
                                handler,
                                frameListener,
                                config,
                                lifecycle,
                                requestExecutor,
                                requestLimit,
                                Duration.ZERO);
    }

    private static Http3ServerConnection serverConnection(QuicConnection quicConnection,
                                                          ConnectionObservation transportObservation,
                                                          Http3Settings settings,
                                                          Http3Handler handler,
                                                          Http3FrameListener frameListener,
                                                          Http3Config config,
                                                          Http3ServerConnection.StreamLifecycle lifecycle,
                                                          Executor requestExecutor,
                                                          Limit requestLimit,
                                                          Duration requestReadTimeout) {
        TransportBindingContext bindingContext = mock(TransportBindingContext.class);
        PeerInfo remotePeer = mock(PeerInfo.class);
        when(bindingContext.router()).thenReturn(mock(Router.class));
        when(bindingContext.requestLimit()).thenReturn(requestLimit);
        when(quicConnection.remotePeer()).thenReturn(remotePeer);
        when(remotePeer.tlsCertificates()).thenReturn(Optional.empty());
        return new Http3ServerConnection(bindingContext,
                                         quicConnection,
                                         transportObservation,
                                         Optional.empty(),
                                         settings,
                                         handler,
                                         frameListener,
                                         frameListener,
                                         Duration.ZERO,
                                         requestReadTimeout,
                                         config,
                                         lifecycle,
                                         requestExecutor);
    }

    private record RequestLifecycleFixture(Http3ServerConnection connection,
                                           QuicBidiStream transportStream,
                                           QuicStreamReader reader,
                                           QuicStreamWriter writer,
                                           CompletableFuture<QuicSenderStream.SendingStreamState> sendingCompletion,
                                           Runnable submittedTask,
                                           AtomicReference<Runnable> completionTask,
                                           AtomicReference<Executor> completionExecutor) {
    }

    private record RequestTransportFixture(QuicBidiStream stream,
                                           QuicStreamReader reader,
                                           QuicStreamWriter writer,
                                           CompletableFuture<QuicSenderStream.SendingStreamState> sendingCompletion) {
    }

    private record RequestDispatchFixture(Http3ServerConnection connection,
                                          QuicConnection quicConnection,
                                          Predicate<? super QuicReceiverStream> remoteStreamListener,
                                          QuicBidiStream stream,
                                          QuicStreamReader reader,
                                          AtomicInteger completedRequests) {
    }

    private record ControlConnectionFixture(Http3ServerConnection connection,
                                            QuicConnection quicConnection,
                                            QuicSenderStream controlStream,
                                            QuicStreamWriter writer,
                                            QuicStreamWriter qpackWriter,
                                            CompletableFuture<QuicSenderStream> controlOpen,
                                            CompletableFuture<QuicSenderStream.SendingStreamState> controlCompletion,
                                            Predicate<? super QuicReceiverStream> remoteStreamListener,
                                            AtomicReference<Runnable> submittedTask) {
    }
}
