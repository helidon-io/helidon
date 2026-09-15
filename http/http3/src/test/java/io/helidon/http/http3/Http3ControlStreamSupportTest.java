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

package io.helidon.http.http3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState;
import io.helidon.quic.stream.QuicStream;
import io.helidon.quic.stream.QuicStreamReader;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3ControlStreamSupportTest {
    private static final Http3ControlStreamListener NO_OP_CONTROL_LISTENER = new Http3ControlStreamListener() {
        @Override
        public void onSettings(Http3Settings settings) {
        }

        @Override
        public void onGoAway(Http3GoAway goAway) {
        }
    };

    @Test
    void shouldCloseObservationBeforeStreamType() {
        FakeControlStream stream = FakeControlStream.pending(List.of());
        Http3StreamObservation observation = Http3ControlStreamSupport.observe(stream,
                                                                                Http3PeerCriticalStreams.create(),
                                                                                Http3TestSocketContext.INSTANCE,
                                                                                NO_OP_CONTROL_LISTENER);
        CompletableFuture<Void> completion = observation.completion().toCompletableFuture();

        assertThat(completion.isDone(), is(false));
        assertThat(stream.reader.connected(), is(true));

        observation.close();

        assertThat(completion.isCompletedExceptionally(), is(true));
        assertThat(stream.reader.connected(), is(false));
        assertThat(stream.disconnectCount, is(1));

        observation.close();

        assertThat(stream.disconnectCount, is(1));
    }

    @Test
    void shouldCloseUnknownStreamObservation() {
        long unknownStreamType = 0x21;
        BufferData bytes = BufferData.create(VariableLengthEncoder.encodedSize(unknownStreamType));
        VariableLengthEncoder.encode(bytes, unknownStreamType);
        FakeControlStream stream = FakeControlStream.pending(List.of(BufferData.create(bytes.readBytes())));

        Http3StreamObservation observation = Http3ControlStreamSupport.observe(stream,
                                                                                Http3PeerCriticalStreams.create(),
                                                                                Http3TestSocketContext.INSTANCE,
                                                                                NO_OP_CONTROL_LISTENER);
        CompletableFuture<Void> completion = observation.completion().toCompletableFuture();

        assertThat(stream.reader.index, is(1));
        assertThat(completion.isDone(), is(false));

        observation.close();

        assertThat(completion.isCompletedExceptionally(), is(true));
        assertThat(stream.reader.connected(), is(false));
        assertThat(stream.disconnectCount, is(1));
    }

    @Test
    void shouldTolerateIncompleteStreamTypeAtFin() {
        for (byte[] prefix : incompleteVarIntPrefixes()) {
            observeSuccessfully(FakeControlStream.create(List.of(BufferData.create(prefix))));
        }
    }

    @Test
    void shouldTolerateIncompleteStreamTypeAtReset() {
        for (byte[] prefix : incompleteVarIntPrefixes()) {
            observeSuccessfully(FakeControlStream.resetAfter(List.of(BufferData.create(prefix))));
        }
    }

    @Test
    void shouldTolerateCompleteUnknownStreamAtFinAndReset() {
        byte[] streamType = encodeVarInt(0x21);

        observeSuccessfully(FakeControlStream.create(List.of(BufferData.create(streamType))));
        observeSuccessfully(FakeControlStream.resetAfter(List.of(BufferData.create(streamType))));
    }

    @Test
    void shouldNotExposeMutableObservationFuture() {
        FakeControlStream stream = FakeControlStream.pending(List.of());
        Http3StreamObservation observation = Http3ControlStreamSupport.observe(stream,
                                                                                Http3PeerCriticalStreams.create(),
                                                                                Http3TestSocketContext.INSTANCE,
                                                                                NO_OP_CONTROL_LISTENER);

        observation.completion().toCompletableFuture().complete(null);

        assertThat(observation.completion().toCompletableFuture().isDone(), is(false));

        observation.close();
    }

    @Test
    void shouldNotFailQpackStateWhenObservationClosedByOwner() {
        FakeControlStream stream = FakeControlStream.pending(List.of(BufferData.create(
                Http3Protocol.qpackUniStreamPreamble(Http3StreamType.QPACK_ENCODER))));
        Http3QpackContext qpackContext = Http3QpackContext.create(128, 1, 16_384, _ -> {
        });
        Http3StreamObservation observation = Http3ControlStreamSupport.observe(stream,
                                                                                qpackContext,
                                                                                Http3PeerCriticalStreams.create(),
                                                                                Http3TestSocketContext.INSTANCE,
                                                                                NO_OP_CONTROL_LISTENER);

        observation.close();

        assertThat(observation.completion().toCompletableFuture().isCompletedExceptionally(), is(true));
        assertThat(stream.reader.connected(), is(false));
        assertThat(stream.disconnectCount, is(1));
        qpackContext.encodeHeaders(0, List.of());
    }

    @Test
    void shouldDisconnectReaderWhenReaderStartFails() {
        IllegalStateException startFailure = new IllegalStateException("test start failure");
        FakeControlStream stream = FakeControlStream.startFailure(startFailure);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> Http3ControlStreamSupport.observe(stream,
                                                        Http3PeerCriticalStreams.create(),
                                                        Http3TestSocketContext.INSTANCE,
                                                        NO_OP_CONTROL_LISTENER));

        assertThat(thrown, is(startFailure));
        assertThat(stream.reader.connected(), is(false));
        assertThat(stream.disconnectCount, is(1));
    }

    @Test
    void shouldDisconnectReaderWhenQpackFailureNotificationFails() {
        FakeControlStream stream = FakeControlStream.resetAfter(List.of(BufferData.create(
                Http3Protocol.qpackUniStreamPreamble(Http3StreamType.QPACK_ENCODER))));
        Http3QpackContext qpackContext = Http3QpackContext.create(128, 1, 16_384, _ -> {
            throw new IllegalStateException("test QPACK failure handler");
        });

        Http3StreamObservation observation = Http3ControlStreamSupport.observe(stream,
                                                                                qpackContext,
                                                                                Http3PeerCriticalStreams.create(),
                                                                                Http3TestSocketContext.INSTANCE,
                                                                                NO_OP_CONTROL_LISTENER);

        assertThat(observation.completion().toCompletableFuture().isCompletedExceptionally(), is(true));
        assertThat(stream.reader.connected(), is(false));
        assertThat(stream.disconnectCount, is(1));
    }

    @Test
    void shouldReportGoAwayIdsBeforeCriticalStreamClose() {
        byte[] preamble = Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0));
        byte[] goAwayA = Http3Protocol.goAwayFrame(Http3GoAway.requestStream(64));
        byte[] goAwayB = Http3Protocol.goAwayFrame(Http3GoAway.requestStream(8));

        FakeControlStream stream = FakeControlStream.create(List.of(
                BufferData.create(preamble),
                BufferData.create(goAwayA),
                BufferData.create(goAwayB)));

        List<Long> seenGoAwayIds = new ArrayList<>();
        Http3ProtocolException exception = observeFailure(stream, seenGoAwayIds);

        assertThat(seenGoAwayIds, contains(64L, 8L));
        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.CLOSED_CRITICAL_STREAM));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectControlStreamWithoutInitialSettings() {
        byte[] goAway = Http3Protocol.goAwayFrame(Http3GoAway.requestStream(4));
        BufferData bytes = BufferData.create(VariableLengthEncoder.encodedSize(Http3StreamType.CONTROL.code())
                                                     + goAway.length);
        VariableLengthEncoder.encode(bytes, Http3StreamType.CONTROL.code());
        bytes.write(goAway);

        FakeControlStream stream = FakeControlStream.create(List.of(BufferData.create(bytes.readBytes())));

        Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.MISSING_SETTINGS));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldSignalControlBatchBeforeMalformedFrame() {
        byte[] goAway = Http3Protocol.goAwayFrame(Http3GoAway.requestStream(4));
        BufferData bytes = BufferData.create(VariableLengthEncoder.encodedSize(Http3StreamType.CONTROL.code())
                                                     + goAway.length);
        VariableLengthEncoder.encode(bytes, Http3StreamType.CONTROL.code());
        bytes.write(goAway);
        FakeControlStream stream = FakeControlStream.create(List.of(BufferData.create(bytes.readBytes())));
        List<String> events = new ArrayList<>();

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> Http3ControlStreamSupport.observe(stream,
                                                        Http3PeerCriticalStreams.create(),
                                                        Http3TestSocketContext.INSTANCE,
                                                        new Http3ControlStreamListener() {
                                                            @Override
                                                            public void onControlDataProcessing() {
                                                                events.add("batch-start");
                                                            }

                                                            @Override
                                                            public void onSettings(Http3Settings settings) {
                                                                events.add("settings");
                                                            }

                                                            @Override
                                                            public void onGoAway(Http3GoAway goAway) {
                                                                events.add("goaway");
                                                            }
                                                        })
                        .completion()
                        .toCompletableFuture()
                        .join());

        assertThat(events, contains("batch-start"));
        assertThat(Http3ProtocolException.find(failure).orElseThrow().errorCode(),
                   equalTo(Http3ErrorCode.MISSING_SETTINGS));
    }

    @Test
    void shouldRejectWrongServerGoAwayIdentifierShape() {
        FakeControlStream stream = FakeControlStream.create(List.of(
                BufferData.create(Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0))),
                BufferData.create(Http3Protocol.goAwayFrame(Http3GoAway.pushId(1)))));

        Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.ID_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldReportMissingSettingsForRequestStreamFrameBeforeSettings() {
        for (long frameType : List.of(Http3Protocol.FRAME_DATA, Http3Protocol.FRAME_HEADERS)) {
            BufferData bytes = BufferData.growing(4);
            VariableLengthEncoder.encode(bytes, Http3StreamType.CONTROL.code());
            bytes.write(emptyFrame(frameType));

            Http3ProtocolException exception = observeFailure(
                    FakeControlStream.create(List.of(BufferData.create(bytes.readBytes()))),
                    new ArrayList<>());

            assertThat("frame type " + frameType,
                       exception.errorCode(),
                       equalTo(Http3ErrorCode.MISSING_SETTINGS));
            assertThat("frame type " + frameType,
                       exception.scope(),
                       is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldRejectRequestStreamFrameOnControlStreamAfterSettings() {
        for (long frameType : List.of(Http3Protocol.FRAME_DATA, Http3Protocol.FRAME_HEADERS)) {
            FakeControlStream stream = FakeControlStream.create(List.of(
                    BufferData.create(Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0))),
                    BufferData.create(emptyFrame(frameType))));

            Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

            assertThat("frame type " + frameType,
                       exception.errorCode(),
                       equalTo(Http3ErrorCode.FRAME_UNEXPECTED));
            assertThat("frame type " + frameType,
                       exception.scope(),
                       is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldRejectSecondSettingsFrameOnControlStream() {
        FakeControlStream stream = FakeControlStream.create(List.of(BufferData.create(Http3Protocol.controlStreamPreamble(
                                                                            Http3Settings.create(0, 0))),
                                                                    BufferData.create(emptyFrame(
                                                                            Http3Protocol.FRAME_SETTINGS))));

        Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.FRAME_UNEXPECTED));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectReservedHttp2FrameTypesAfterSettings() {
        for (long frameType : List.of(0x02L, 0x06L, 0x08L, 0x09L)) {
            FakeControlStream afterSettings = FakeControlStream.create(List.of(
                    BufferData.create(Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0))),
                    BufferData.create(emptyFrame(frameType))));

            Http3ProtocolException frameFailure = observeFailure(afterSettings, new ArrayList<>());

            assertThat("frame type " + frameType,
                       frameFailure.errorCode(),
                       equalTo(Http3ErrorCode.FRAME_UNEXPECTED));
            assertThat("frame type " + frameType,
                       frameFailure.scope(),
                       is(Http3ProtocolException.Scope.CONNECTION));

            BufferData beforeSettingsBytes = BufferData.growing(4);
            VariableLengthEncoder.encode(beforeSettingsBytes, Http3StreamType.CONTROL.code());
            beforeSettingsBytes.write(emptyFrame(frameType));
            Http3ProtocolException settingsFailure =
                    observeFailure(FakeControlStream.create(
                                           List.of(BufferData.create(beforeSettingsBytes.readBytes()))),
                                   new ArrayList<>());

            assertThat("frame type " + frameType,
                       settingsFailure.errorCode(),
                       equalTo(Http3ErrorCode.MISSING_SETTINGS));
        }
    }

    @Test
    void shouldRejectClientCreatedPushStream() {
        FakeControlStream stream = FakeControlStream.clientInitiated(
                List.of(BufferData.create(streamPreamble(Http3StreamType.PUSH))));

        Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.STREAM_CREATION_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectFragmentedServerPushIdWhenPushIsDisabled() {
        byte[] pushId = encodeVarInt(64);
        FakeControlStream stream = FakeControlStream.create(
                List.of(BufferData.create(streamPreamble(Http3StreamType.PUSH)),
                        BufferData.createReadOnly(pushId, 0, 1),
                        BufferData.createReadOnly(pushId, 1, pushId.length - 1)));

        Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.ID_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectIncompleteServerPushIdAtFin() {
        for (byte[] prefix : incompleteVarIntPrefixes()) {
            FakeControlStream stream = FakeControlStream.create(
                    List.of(BufferData.create(streamPreamble(Http3StreamType.PUSH)),
                            BufferData.create(prefix)));

            Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

            assertThat("prefix " + Arrays.toString(prefix),
                       exception.errorCode(),
                       equalTo(Http3ErrorCode.ID_ERROR));
            assertThat("prefix " + Arrays.toString(prefix),
                       exception.scope(),
                       is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldRejectIncompleteServerPushIdAtReset() {
        for (byte[] prefix : incompleteVarIntPrefixes()) {
            FakeControlStream stream = FakeControlStream.resetAfter(
                    List.of(BufferData.create(streamPreamble(Http3StreamType.PUSH)),
                            BufferData.create(prefix)));

            Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

            assertThat("prefix " + Arrays.toString(prefix),
                       exception.errorCode(),
                       equalTo(Http3ErrorCode.ID_ERROR));
            assertThat("prefix " + Arrays.toString(prefix),
                       exception.scope(),
                       is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldRejectServerMaxPushId() {
        FakeControlStream stream = FakeControlStream.create(
                List.of(BufferData.create(Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0))),
                        BufferData.create(identifierFrame(Http3Protocol.FRAME_MAX_PUSH_ID, 0))));

        Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.FRAME_UNEXPECTED));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldAcceptNondecreasingClientMaxPushId() {
        FakeControlStream stream = FakeControlStream.clientInitiated(
                List.of(BufferData.create(Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0))),
                        BufferData.create(identifierFrame(Http3Protocol.FRAME_MAX_PUSH_ID, 1)),
                        BufferData.create(identifierFrame(Http3Protocol.FRAME_MAX_PUSH_ID, 1)),
                        BufferData.create(identifierFrame(Http3Protocol.FRAME_MAX_PUSH_ID, 64))));

        Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.CLOSED_CRITICAL_STREAM));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectDecreasingClientMaxPushId() {
        FakeControlStream stream = FakeControlStream.clientInitiated(
                List.of(BufferData.create(Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0))),
                        BufferData.create(identifierFrame(Http3Protocol.FRAME_MAX_PUSH_ID, 64)),
                        BufferData.create(identifierFrame(Http3Protocol.FRAME_MAX_PUSH_ID, 63))));

        Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.ID_ERROR));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectCancelPushWhenPushIsDisabled() {
        byte[] preamble = Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0));
        byte[] cancelPush = identifierFrame(Http3Protocol.FRAME_CANCEL_PUSH, 0);
        List<FakeControlStream> streams = List.of(
                FakeControlStream.create(List.of(BufferData.create(preamble), BufferData.create(cancelPush))),
                FakeControlStream.clientInitiated(
                        List.of(BufferData.create(preamble), BufferData.create(cancelPush))));

        for (FakeControlStream stream : streams) {
            Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

            assertThat("server initiated: " + stream.isServerInitiated(),
                       exception.errorCode(),
                       equalTo(Http3ErrorCode.ID_ERROR));
            assertThat("server initiated: " + stream.isServerInitiated(),
                       exception.scope(),
                       is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldRejectMalformedPushIdentifierFrames() {
        List<byte[]> malformedPayloads = List.of(BufferData.EMPTY_BYTES,
                                                 new byte[]{(byte) 0x40},
                                                 new byte[]{0, 0},
                                                 new byte[Long.BYTES + 1]);
        for (long frameType : List.of(Http3Protocol.FRAME_CANCEL_PUSH, Http3Protocol.FRAME_MAX_PUSH_ID)) {
            for (byte[] payload : malformedPayloads) {
                FakeControlStream stream = FakeControlStream.clientInitiated(
                        List.of(BufferData.create(Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0))),
                                BufferData.create(frame(frameType, payload))));

                Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

                assertThat("frame " + frameType + ", payload length " + payload.length,
                           exception.errorCode(),
                           equalTo(Http3ErrorCode.FRAME_ERROR));
                assertThat("frame " + frameType + ", payload length " + payload.length,
                           exception.scope(),
                           is(Http3ProtocolException.Scope.CONNECTION));
            }
        }
    }

    @Test
    void shouldRejectOversizedSettingsFromFrameHeader() {
        BufferData data = BufferData.growing(16);
        VariableLengthEncoder.encode(data, Http3StreamType.CONTROL.code());
        VariableLengthEncoder.encode(data, Http3Protocol.FRAME_SETTINGS);
        VariableLengthEncoder.encode(data, 16_385);
        FakeControlStream stream = FakeControlStream.create(List.of(BufferData.create(data.readBytes())));

        Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.EXCESSIVE_LOAD));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldDiscardFragmentedUnknownFrameBeforeGoAway() {
        int unknownPayloadSize = 20 * 1024;
        BufferData unknownHeader = BufferData.growing(16);
        VariableLengthEncoder.encode(unknownHeader, 0x21);
        VariableLengthEncoder.encode(unknownHeader, unknownPayloadSize);
        FakeControlStream stream = FakeControlStream.create(List.of(
                BufferData.create(Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0))),
                BufferData.create(unknownHeader.readBytes()),
                BufferData.create(new byte[8 * 1024]),
                BufferData.create(new byte[8 * 1024]),
                BufferData.create(new byte[4 * 1024]),
                BufferData.create(Http3Protocol.goAwayFrame(Http3GoAway.requestStream(8)))));
        List<Long> seenGoAwayIds = new ArrayList<>();
        List<String> frameDataEvents = new ArrayList<>();
        AtomicReference<Long> frameType = new AtomicReference<>();
        Http3FrameListener frameListener = new Http3FrameListener() {
            @Override
            public boolean rawDataEnabled() {
                return true;
            }

            @Override
            public void frameHeader(SocketContext context,
                                    long streamId,
                                    long type,
                                    long frameLength,
                                    int encodedLength) {
                frameType.set(type);
            }

            @Override
            public void frameData(SocketContext context,
                                  long streamId,
                                  int byteCount,
                                  boolean last) {
                if (frameType.get() == 0x21) {
                    frameDataEvents.add("metadata:" + byteCount + ":" + last);
                }
            }

            @Override
            public void rawFrameData(SocketContext context,
                                     long streamId,
                                     byte[] data,
                                     boolean last) {
                if (frameType.get() == 0x21) {
                    frameDataEvents.add("raw:" + data.length + ":" + last);
                }
            }
        };

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> Http3ControlStreamSupport.observe(stream,
                                                        Optional::empty,
                                                        Http3PeerCriticalStreams.create(),
                                                        Http3TestSocketContext.INSTANCE,
                                                        frameListener,
                                                        new Http3ControlStreamListener() {
                                                            @Override
                                                            public void onSettings(Http3Settings settings) {
                                                            }

                                                            @Override
                                                            public void onGoAway(Http3GoAway goAway) {
                                                                seenGoAwayIds.add(goAway.identifier());
                                                            }
                                                        })
                        .completion()
                        .toCompletableFuture()
                        .join());
        Http3ProtocolException exception = Http3ProtocolException.find(failure).orElseThrow();

        assertThat(frameDataEvents,
                   contains("metadata:8192:false",
                            "raw:8192:false",
                            "metadata:8192:false",
                            "raw:8192:false",
                            "metadata:4096:true",
                            "raw:4096:true"));
        assertThat(seenGoAwayIds, contains(8L));
        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.CLOSED_CRITICAL_STREAM));
    }

    @Test
    void shouldReportInitialPeerSettings() {
        FakeControlStream stream = FakeControlStream.create(List.of(BufferData.create(Http3Protocol.controlStreamPreamble(
                Http3Settings.create(32_768, 4_096, 16)))));
        AtomicReference<Http3Settings> seenSettings = new AtomicReference<>();

        Http3ProtocolException exception = observeFailure(stream, seenSettings);

        assertThat(seenSettings.get(), equalTo(Http3Settings.create(32_768, 4_096, 16)));
        assertThat(exception.errorCode(), equalTo(Http3ErrorCode.CLOSED_CRITICAL_STREAM));
        assertThat(exception.scope(), is(Http3ProtocolException.Scope.CONNECTION));
    }

    @Test
    void shouldRejectDuplicateCriticalStreamBeforeProcessingSettings() {
        FakeControlStream firstStream = FakeControlStream.create(List.of(BufferData.create(
                Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0)))));
        FakeControlStream duplicateStream = FakeControlStream.create(List.of(BufferData.create(
                Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0)))));
        Http3PeerCriticalStreams peerCriticalStreams = Http3PeerCriticalStreams.create();
        List<String> events = new ArrayList<>();

        assertThrows(CompletionException.class,
                     () -> Http3ControlStreamSupport.observe(firstStream,
                                                             peerCriticalStreams,
                                                             Http3TestSocketContext.INSTANCE,
                                                             new Http3ControlStreamListener() {
                                                                 @Override
                                                                 public void onControlDataProcessing() {
                                                                     events.add("first batch");
                                                                 }

                                                                 @Override
                                                                 public void onSettings(Http3Settings settings) {
                                                                     events.add("settings");
                                                                 }

                                                                 @Override
                                                                 public void onGoAway(Http3GoAway goAway) {
                                                                 }
                                                             })
                             .completion()
                             .toCompletableFuture()
                             .join());
        assertThrows(CompletionException.class,
                     () -> Http3ControlStreamSupport.observe(duplicateStream,
                                                             peerCriticalStreams,
                                                             Http3TestSocketContext.INSTANCE,
                                                             new Http3ControlStreamListener() {
                                                                 @Override
                                                                 public void onControlDataProcessing() {
                                                                     events.add("duplicate batch");
                                                                 }

                                                                 @Override
                                                                 public void onSettings(Http3Settings settings) {
                                                                     events.add("duplicate settings");
                                                                 }

                                                                 @Override
                                                                 public void onGoAway(Http3GoAway goAway) {
                                                                 }
                                                             })
                             .completion()
                             .toCompletableFuture()
                             .join());

        assertThat(events, contains("first batch", "settings", "duplicate batch"));
    }

    @Test
    void shouldNotProcessCriticalStreamAfterRegistryClosed() {
        FakeControlStream stream = FakeControlStream.create(List.of(BufferData.create(
                Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0)))));
        Http3PeerCriticalStreams peerCriticalStreams = Http3PeerCriticalStreams.create();
        peerCriticalStreams.close(new IllegalStateException("connection closed"));
        List<String> events = new ArrayList<>();

        assertThrows(CompletionException.class,
                     () -> Http3ControlStreamSupport.observe(stream,
                                                             peerCriticalStreams,
                                                             Http3TestSocketContext.INSTANCE,
                                                             new Http3ControlStreamListener() {
                                                                 @Override
                                                                 public void onSettings(Http3Settings settings) {
                                                                     events.add("settings");
                                                                 }

                                                                 @Override
                                                                 public void onGoAway(Http3GoAway goAway) {
                                                                 }
                                                             })
                             .completion()
                             .toCompletableFuture()
                             .join());

        assertThat(events.isEmpty(), is(true));
    }

    @Test
    void shouldMapCriticalStreamResetsToClosedCriticalStream() {
        List<byte[]> preambles = List.of(
                Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0)),
                Http3Protocol.qpackUniStreamPreamble(Http3StreamType.QPACK_ENCODER),
                Http3Protocol.qpackUniStreamPreamble(Http3StreamType.QPACK_DECODER));
        for (byte[] preamble : preambles) {
            FakeControlStream stream = FakeControlStream.resetAfter(List.of(BufferData.create(preamble)));

            Http3ProtocolException exception = observeFailure(stream, new ArrayList<>());

            assertThat("stream type " + preamble[0],
                       exception.errorCode(),
                       equalTo(Http3ErrorCode.CLOSED_CRITICAL_STREAM));
            assertThat("stream type " + preamble[0],
                       exception.scope(),
                       is(Http3ProtocolException.Scope.CONNECTION));
        }
    }

    @Test
    void shouldFailConnectionOwnedQpackStateWhenInstructionStreamResets() {
        FakeControlStream stream = FakeControlStream.resetAfter(List.of(BufferData.create(
                Http3Protocol.qpackUniStreamPreamble(Http3StreamType.QPACK_ENCODER))));
        Http3QpackContext qpackContext = Http3QpackContext.create(128, 1, 16_384, _ -> {
        });

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> Http3ControlStreamSupport.observe(stream,
                                                        qpackContext,
                                                        Http3PeerCriticalStreams.create(),
                                                        Http3TestSocketContext.INSTANCE,
                                                        new Http3ControlStreamListener() {
                                                            @Override
                                                            public void onSettings(Http3Settings settings) {
                                                            }

                                                            @Override
                                                            public void onGoAway(Http3GoAway goAway) {
                                                            }
                                                        })
                        .completion()
                        .toCompletableFuture()
                        .join());

        Http3ProtocolException protocolFailure = Http3ProtocolException.find(failure).orElseThrow();
        assertThat(protocolFailure.errorCode(), equalTo(Http3ErrorCode.CLOSED_CRITICAL_STREAM));
        assertThrows(IllegalStateException.class, () -> qpackContext.encodeHeaders(0, List.of()));
    }

    @Test
    void shouldNotMapConnectionTerminationToClosedCriticalStream() {
        FakeControlStream stream = FakeControlStream.connectionClosedAfter(List.of(BufferData.create(
                Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0)))));

        CompletionException exception = assertThrows(CompletionException.class,
                                                      () -> Http3ControlStreamSupport.observe(
                                                                      stream,
                                                                      Http3PeerCriticalStreams.create(),
                                                                      Http3TestSocketContext.INSTANCE,
                                                                      new Http3ControlStreamListener() {
                                                                          @Override
                                                                          public void onSettings(Http3Settings settings) {
                                                                          }

                                                                          @Override
                                                                          public void onGoAway(Http3GoAway goAway) {
                                                                          }
                                                                      })
                                                              .completion()
                                                              .toCompletableFuture()
                                                              .join());

        assertThat(Http3ProtocolException.find(exception).isEmpty(), is(true));
    }

    @Test
    void shouldPreserveNonResetFailureBeforeStreamTypeComplete() {
        List<byte[]> prefixes = incompleteVarIntPrefixes();
        FakeControlStream stream = FakeControlStream.connectionClosedAfter(
                List.of(BufferData.create(prefixes.get(prefixes.size() - 1))));

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> Http3ControlStreamSupport.observe(stream,
                                                        Http3PeerCriticalStreams.create(),
                                                        Http3TestSocketContext.INSTANCE,
                                                        NO_OP_CONTROL_LISTENER)
                        .completion()
                        .toCompletableFuture()
                        .join());

        assertThat(Http3ProtocolException.find(exception).isEmpty(), is(true));
        assertThat(exception.getCause().getMessage(), is("test connection close"));
    }

    private static void observeSuccessfully(FakeControlStream stream) {
        Http3ControlStreamSupport.observe(stream,
                                          Http3PeerCriticalStreams.create(),
                                          Http3TestSocketContext.INSTANCE,
                                          NO_OP_CONTROL_LISTENER)
                .completion()
                .toCompletableFuture()
                .join();
    }

    private static Http3ProtocolException observeFailure(FakeControlStream stream, List<Long> seenGoAwayIds) {
        CompletionException exception = assertThrows(CompletionException.class,
                                                    () -> Http3ControlStreamSupport.observe(stream,
                                                                                            Http3PeerCriticalStreams.create(),
                                                                                            Http3TestSocketContext.INSTANCE,
                                                                                            new Http3ControlStreamListener() {
                                                                                                @Override
                                                                                                public void onSettings(
                                                                                                        Http3Settings settings) {
                                                                                                }

                                                                                                @Override
                                                                                                public void onGoAway(
                                                                                                        Http3GoAway goAway) {
                                                                                                    seenGoAwayIds.add(
                                                                                                            goAway.identifier());
                                                                                                }
                                                                                            })
                                                            .completion()
                                                            .toCompletableFuture()
                                                            .join());
        Optional<Http3ProtocolException> protocolException = Http3ProtocolException.find(exception);
        if (protocolException.isEmpty()) {
            throw exception;
        }
        return protocolException.orElseThrow();
    }

    private static Http3ProtocolException observeFailure(FakeControlStream stream,
                                                         AtomicReference<Http3Settings> seenSettings) {
        CompletionException exception = assertThrows(CompletionException.class,
                                                    () -> Http3ControlStreamSupport.observe(stream,
                                                                                            Optional::empty,
                                                                                            Http3PeerCriticalStreams.create(),
                                                                                            Http3TestSocketContext.INSTANCE,
                                                                                            Http3FrameListener.create(List.of()),
                                                                                            new Http3ControlStreamListener() {
                                                                                                @Override
                                                                                                public void onSettings(
                                                                                                        Http3Settings settings) {
                                                                                                    seenSettings.set(settings);
                                                                                                }

                                                                                                @Override
                                                                                                public void onGoAway(
                                                                                                        Http3GoAway goAway) {
                                                                                                }
                                                                                            })
                                                            .completion()
                                                            .toCompletableFuture()
                                                            .join());
        Optional<Http3ProtocolException> protocolException = Http3ProtocolException.find(exception);
        if (protocolException.isEmpty()) {
            throw exception;
        }
        return protocolException.orElseThrow();
    }

    private static byte[] emptyFrame(long frameType) {
        return frame(frameType, BufferData.EMPTY_BYTES);
    }

    private static byte[] identifierFrame(long frameType, long identifier) {
        return frame(frameType, encodeVarInt(identifier));
    }

    private static byte[] frame(long frameType, byte[] payload) {
        BufferData output = BufferData.create(VariableLengthEncoder.encodedSize(frameType)
                                                     + VariableLengthEncoder.encodedSize(payload.length)
                                                     + payload.length);
        VariableLengthEncoder.encode(output, frameType);
        VariableLengthEncoder.encode(output, payload.length);
        output.write(payload);
        return output.readBytes();
    }

    private static byte[] streamPreamble(Http3StreamType streamType) {
        return encodeVarInt(streamType.code());
    }

    private static byte[] encodeVarInt(long value) {
        BufferData output = BufferData.create(VariableLengthEncoder.encodedSize(value));
        VariableLengthEncoder.encode(output, value);
        return output.readBytes();
    }

    private static List<byte[]> incompleteVarIntPrefixes() {
        List<byte[]> prefixes = new ArrayList<>();
        prefixes.add(BufferData.EMPTY_BYTES);
        for (byte[] encoded : List.of(new byte[]{0x40, 0},
                                      new byte[]{(byte) 0x80, 0, 0, 0},
                                      new byte[]{(byte) 0xc0, 0, 0, 0, 0, 0, 0, 0})) {
            for (int length = 1; length < encoded.length; length++) {
                prefixes.add(Arrays.copyOf(encoded, length));
            }
        }
        return prefixes;
    }

    private static final class FakeControlStream implements QuicReceiverStream {
        private final List<byte[]> buffers;
        private final long dataReceived;
        private final RuntimeException readFailure;
        private final long receiveErrorCode;
        private final boolean resetFailure;
        private final boolean serverInitiated;
        private final RuntimeException startFailure;
        private FakeStreamReader reader;
        private boolean disconnected;
        private int disconnectCount;

        private FakeControlStream(List<byte[]> buffers,
                                  RuntimeException readFailure,
                                  long receiveErrorCode,
                                  boolean resetFailure,
                                  boolean serverInitiated,
                                  boolean endOfStream,
                                  RuntimeException startFailure) {
            this.buffers = new ArrayList<>(buffers);
            this.readFailure = readFailure;
            this.receiveErrorCode = receiveErrorCode;
            this.resetFailure = resetFailure;
            this.serverInitiated = serverInitiated;
            this.startFailure = startFailure;
            if (endOfStream) {
                this.buffers.add(null);
            }
            this.dataReceived = this.buffers.stream()
                    .filter(buffer -> buffer != null)
                    .mapToLong(buffer -> buffer.length)
                    .sum();
        }

        @Override
        public ReceivingStreamState receivingState() {
            if (disconnected) {
                return ReceivingStreamState.DATA_READ;
            }
            if (resetFailure && reader != null && reader.failed) {
                return ReceivingStreamState.RESET_RECVD;
            }
            return ReceivingStreamState.RECV;
        }

        @Override
        public QuicStreamReader connectReader(SequentialScheduler scheduler) {
            if (reader != null && reader.connected()) {
                throw new IllegalStateException("Reader already connected.");
            }
            reader = new FakeStreamReader(this, scheduler, buffers);
            return reader;
        }

        @Override
        public boolean isLocalInitiated() {
            return false;
        }

        @Override
        public boolean isRemoteInitiated() {
            return true;
        }

        @Override
        public int type() {
            return serverInitiated ? 0x03 : 0x02;
        }

        @Override
        public long streamId() {
            return serverInitiated ? 3 : 2;
        }

        @Override
        public StreamMode mode() {
            return StreamMode.READ_ONLY;
        }

        @Override
        public QuicStream.StreamState state() {
            return receivingState();
        }

        @Override
        public boolean isBidirectional() {
            return false;
        }

        @Override
        public boolean isServerInitiated() {
            return serverInitiated;
        }

        @Override
        public boolean isClientInitiated() {
            return !serverInitiated;
        }

        @Override
        public void disconnectReader(QuicStreamReader reader) {
            if (this.reader != reader || this.reader == null || !this.reader.connected()) {
                throw new IllegalStateException("Reader is not connected.");
            }
            markDisconnected();
            this.reader.disconnect();
        }

        @Override
        public void requestStopSending(long errorCode) {
            // no-op for test
        }

        @Override
        public long dataReceived() {
            return dataReceived;
        }

        @Override
        public long maxStreamData() {
            return dataReceived;
        }

        @Override
        public long rcvErrorCode() {
            return receiveErrorCode;
        }

        private static FakeControlStream create(List<BufferData> data) {
            List<byte[]> input = new ArrayList<>(data.size());
            for (BufferData buffer : data) {
                input.add(buffer.readBytes());
            }
            return new FakeControlStream(input, null, -1, false, true, true, null);
        }

        private static FakeControlStream clientInitiated(List<BufferData> data) {
            List<byte[]> input = new ArrayList<>(data.size());
            for (BufferData buffer : data) {
                input.add(buffer.readBytes());
            }
            return new FakeControlStream(input, null, -1, false, false, true, null);
        }

        private static FakeControlStream pending(List<BufferData> data) {
            List<byte[]> input = new ArrayList<>(data.size());
            for (BufferData buffer : data) {
                input.add(buffer.readBytes());
            }
            return new FakeControlStream(input, null, -1, false, true, false, null);
        }

        private static FakeControlStream resetAfter(List<BufferData> data) {
            List<byte[]> input = new ArrayList<>(data.size());
            for (BufferData buffer : data) {
                input.add(buffer.readBytes());
            }
            return new FakeControlStream(input, new IllegalStateException("test reset"), 0x10c, true, true, false, null);
        }

        private static FakeControlStream connectionClosedAfter(List<BufferData> data) {
            List<byte[]> input = new ArrayList<>(data.size());
            for (BufferData buffer : data) {
                input.add(buffer.readBytes());
            }
            return new FakeControlStream(input,
                                         new IllegalStateException("test connection close"),
                                         0,
                                         false,
                                         true,
                                         false,
                                         null);
        }

        private static FakeControlStream startFailure(RuntimeException failure) {
            return new FakeControlStream(List.of(), null, -1, false, true, false, failure);
        }

        private void markDisconnected() {
            disconnected = true;
            disconnectCount++;
        }
    }

    private static final class FakeStreamReader extends QuicStreamReader {
        private final FakeControlStream stream;
        private final List<byte[]> buffers;
        private final SequentialScheduler scheduler;
        private int index;
        private boolean connected;
        private boolean started;
        private boolean failed;

        private FakeStreamReader(FakeControlStream stream,
                                 SequentialScheduler scheduler,
                                 List<byte[]> buffers) {
            super(scheduler);
            this.stream = stream;
            this.buffers = buffers;
            this.scheduler = scheduler;
            this.connected = true;
        }

        @Override
        public ReceivingStreamState receivingState() {
            return stream.receivingState();
        }

        @Override
        public Optional<BufferData> poll() {
            ensureConnected();
            if (!started || index >= buffers.size()) {
                if (started && !failed && stream.readFailure != null) {
                    failed = true;
                    throw stream.readFailure;
                }
                return Optional.empty();
            }
            byte[] next = buffers.get(index++);
            if (next == null) {
                return Optional.of(EOF);
            }
            return Optional.of(BufferData.createReadOnly(next, 0, next.length));
        }

        @Override
        public Optional<BufferData> peek() {
            ensureConnected();
            if (!started || index >= buffers.size()) {
                return Optional.empty();
            }
            byte[] next = buffers.get(index);
            if (next == null) {
                return Optional.of(EOF);
            }
            return Optional.of(BufferData.createReadOnly(next, 0, next.length));
        }

        @Override
        public Optional<QuicReceiverStream> stream() {
            return connected ? Optional.of(stream) : Optional.empty();
        }

        @Override
        public boolean connected() {
            return connected;
        }

        @Override
        public boolean started() {
            return started;
        }

        @Override
        public void start() {
            if (stream.startFailure != null) {
                throw stream.startFailure;
            }
            started = true;
            scheduler.runOrSchedule();
        }

        private void disconnect() {
            connected = false;
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("Reader not connected.");
            }
        }
    }
}
