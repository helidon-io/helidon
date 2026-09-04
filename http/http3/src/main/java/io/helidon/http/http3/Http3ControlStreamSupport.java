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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicStreamReader;

import static io.helidon.common.buffers.BufferData.EMPTY_BYTES;

/**
 * Helpers for observing HTTP/3 remote unidirectional streams such as the control stream and QPACK streams.
 */
@Api.Internal
public final class Http3ControlStreamSupport {
    private static final int MAX_RAW_DATA_CHUNK_SIZE = 8 * 1024;
    private static final int MAX_SETTINGS_PAYLOAD_SIZE = 16 * 1024;
    private static final Http3FrameListener NO_OP_FRAME_LISTENER = Http3FrameListener.create(List.of());

    private Http3ControlStreamSupport() {
    }

    /**
     * Observe a remote HTTP/3 control stream without QPACK stream processing.
     *
     * @param stream remote unidirectional stream
     * @param peerCriticalStreams connection-owned peer critical-stream registry
     * @param context socket context
     * @param listener connection-owned control-stream listener
     * @return owned stream observation; the caller must close it when observation is no longer needed
     */
    public static Http3StreamObservation observe(QuicReceiverStream stream,
                                                 Http3PeerCriticalStreams peerCriticalStreams,
                                                 SocketContext context,
                                                 Http3ControlStreamListener listener) {
        return observe(stream,
                       Optional::empty,
                       peerCriticalStreams,
                       Objects.requireNonNull(context, "context"),
                       NO_OP_FRAME_LISTENER,
                       listener);
    }

    /**
     * Observe a remote HTTP/3 unidirectional stream, dispatching control-stream frames and QPACK bytes.
     *
     * @param stream remote unidirectional stream
     * @param qpackContext per-connection QPACK context
     * @param peerCriticalStreams connection-owned peer critical-stream registry
     * @param context socket context
     * @param listener connection-owned control-stream listener
     * @return owned stream observation; the caller must close it when observation is no longer needed
     */
    public static Http3StreamObservation observe(QuicReceiverStream stream,
                                                 Http3QpackContext qpackContext,
                                                 Http3PeerCriticalStreams peerCriticalStreams,
                                                 SocketContext context,
                                                 Http3ControlStreamListener listener) {
        return observe(stream, qpackContext, peerCriticalStreams, context, NO_OP_FRAME_LISTENER, listener);
    }

    /**
     * Observe a remote HTTP/3 unidirectional stream, dispatching control-stream frames and QPACK bytes.
     *
     * @param stream remote unidirectional stream
     * @param qpackContext per-connection QPACK context
     * @param peerCriticalStreams connection-owned peer critical-stream registry
     * @param context socket context
     * @param frameListener frame listener
     * @param listener connection-owned control-stream listener
     * @return owned stream observation; the caller must close it when observation is no longer needed
     */
    public static Http3StreamObservation observe(QuicReceiverStream stream,
                                                 Http3QpackContext qpackContext,
                                                 Http3PeerCriticalStreams peerCriticalStreams,
                                                 SocketContext context,
                                                 Http3FrameListener frameListener,
                                                 Http3ControlStreamListener listener) {
        Objects.requireNonNull(qpackContext, "qpackContext");
        return observe(stream,
                       () -> Optional.of(qpackContext),
                       peerCriticalStreams,
                       Objects.requireNonNull(context, "context"),
                       frameListener,
                       listener);
    }

    /**
     * Observe a remote HTTP/3 unidirectional stream, dispatching control-stream frames and QPACK bytes.
     *
     * @param stream remote unidirectional stream
     * @param qpackContextSupplier supplier of the current per-connection QPACK context when QPACK stream bytes should
     *                             be processed
     * @param peerCriticalStreams connection-owned peer critical-stream registry
     * @param context socket context
     * @param frameListener frame listener
     * @param listener connection-owned control-stream listener
     * @return owned stream observation; the caller must close it when observation is no longer needed
     */
    public static Http3StreamObservation observe(QuicReceiverStream stream,
                                                 Supplier<Optional<Http3QpackContext>> qpackContextSupplier,
                                                 Http3PeerCriticalStreams peerCriticalStreams,
                                                 SocketContext context,
                                                 Http3FrameListener frameListener,
                                                 Http3ControlStreamListener listener) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(qpackContextSupplier, "qpackContextSupplier");
        Objects.requireNonNull(peerCriticalStreams, "peerCriticalStreams");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(frameListener, "frameListener");
        Objects.requireNonNull(listener, "listener");

        CompletableFuture<Void> result = new CompletableFuture<>();
        RemoteUniStreamObserver observer = new RemoteUniStreamObserver(stream,
                                                                       qpackContextSupplier,
                                                                       peerCriticalStreams,
                                                                       context,
                                                                       frameListener,
                                                                       stream.isServerInitiated()
                                                                               ? Http3GoAway.Type.REQUEST_STREAM_ID
                                                                               : Http3GoAway.Type.PUSH_ID,
                                                                       listener,
                                                                       result);
        QuicStreamReader[] holder = new QuicStreamReader[1];
        SequentialScheduler scheduler = SequentialScheduler.lockingScheduler(() -> {
            try {
                observer.controlDataProcessing();
                QuicStreamReader reader = holder[0];
                for (;;) {
                    Optional<BufferData> next;
                    try {
                        next = reader.poll();
                    } catch (Throwable t) {
                        observer.readFailure(t)
                                .ifPresentOrElse(result::completeExceptionally, () -> result.complete(null));
                        return;
                    }
                    if (next.isEmpty()) {
                        observer.controlDataProcessed();
                        return;
                    }
                    BufferData buffer = next.orElseThrow();
                    if (buffer == QuicStreamReader.EOF) {
                        observer.endOfStream();
                        result.complete(null);
                        return;
                    }
                    observer.onData(buffer);
                }
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        holder[0] = stream.connectReader(scheduler);
        result.whenComplete((_, throwable) -> {
            try {
                if (throwable != null
                        && !result.isCancelled()
                        && (observer.streamType == Http3StreamType.QPACK_ENCODER
                        || observer.streamType == Http3StreamType.QPACK_DECODER)) {
                    observer.qpackContext().ifPresent(qpackContext -> qpackContext.instructionStreamFailed(
                            observer.streamType,
                            throwable));
                }
            } finally {
                try {
                    stream.disconnectReader(holder[0]);
                } catch (IllegalStateException _) {
                }
            }
        });
        try {
            holder[0].start();
        } catch (RuntimeException | Error failure) {
            result.completeExceptionally(failure);
            throw failure;
        }
        return new Http3StreamObservation(result);
    }

    private static byte[] copy(byte[] source, int offset, int length) {
        byte[] bytes = new byte[length];
        System.arraycopy(source, offset, bytes, 0, length);
        return bytes;
    }

    private static final class RemoteUniStreamObserver {
        private final QuicReceiverStream stream;
        private final long streamId;
        private final Supplier<Optional<Http3QpackContext>> qpackContextSupplier;
        private final Http3PeerCriticalStreams peerCriticalStreams;
        private final SocketContext context;
        private final Http3FrameListener frameListener;
        private final Http3GoAway.Type goAwayType;
        private final Http3ControlStreamListener listener;
        private final CompletableFuture<Void> observation;

        private final EncodedVarInt encodedStreamType = new EncodedVarInt();
        private final EncodedVarInt encodedPushId = new EncodedVarInt();
        private final EncodedVarInt encodedControlFrameType = new EncodedVarInt();
        private final EncodedVarInt encodedControlFrameLength = new EncodedVarInt();
        private Http3StreamType streamType;
        private long controlFrameType = -1;
        private long controlFrameRemaining;
        private long highestPeerMaxPushId = -1;
        private byte[] controlFramePayload;
        private int controlFramePayloadOffset;
        private boolean controlFrameDataSeen;
        private boolean streamTypeParsed;
        private boolean settingsReceived;
        private boolean controlBatchStarted;

        private RemoteUniStreamObserver(QuicReceiverStream stream,
                                        Supplier<Optional<Http3QpackContext>> qpackContextSupplier,
                                        Http3PeerCriticalStreams peerCriticalStreams,
                                        SocketContext context,
                                        Http3FrameListener frameListener,
                                        Http3GoAway.Type goAwayType,
                                        Http3ControlStreamListener listener,
                                        CompletableFuture<Void> observation) {
            this.stream = stream;
            this.streamId = stream.streamId();
            this.qpackContextSupplier = qpackContextSupplier;
            this.peerCriticalStreams = peerCriticalStreams;
            this.context = context;
            this.frameListener = frameListener;
            this.goAwayType = goAwayType;
            this.listener = listener;
            this.observation = observation;
        }

        private Optional<Http3QpackContext> qpackContext() {
            return qpackContextSupplier.get();
        }

        private void onData(BufferData data) {
            if (!streamTypeParsed) {
                if (!encodedStreamType.read(data)) {
                    return;
                }
                long streamTypeCode = encodedStreamType.value();
                streamTypeParsed = true;
                streamType = Http3StreamType.of(streamTypeCode).orElse(null);
                if (streamType == null) {
                    String label = "unknown unidirectional stream type " + streamTypeCode;
                    frameListener.streamData(context, streamId, label, encodedStreamType.length());
                    notifyRawStreamTypeData(label);
                } else {
                    frameListener.streamType(context, streamId, streamType, encodedStreamType.length());
                    notifyRawStreamTypeData("stream type data");
                    if (streamType == Http3StreamType.CONTROL) {
                        controlDataProcessing();
                    }
                    if (streamType != Http3StreamType.PUSH
                            && !peerCriticalStreams.claim(streamType, stream, observation)) {
                        return;
                    }
                }
            }
            if (streamType == Http3StreamType.CONTROL) {
                controlDataProcessing();
                while (data.available() > 0 || (controlFrameType >= 0 && controlFrameRemaining == 0)) {
                    if (controlFrameType < 0) {
                        if (!encodedControlFrameType.read(data)) {
                            return;
                        }
                        if (!encodedControlFrameLength.read(data)) {
                            return;
                        }
                        controlFrameType = encodedControlFrameType.value();
                        controlFrameRemaining = encodedControlFrameLength.value();
                        int frameTypeLength = encodedControlFrameType.length();
                        int encodedControlFrameHeaderLength = frameTypeLength + encodedControlFrameLength.length();
                        frameListener.frameHeader(context, streamId, controlFrameType, controlFrameRemaining,
                                                  encodedControlFrameHeaderLength);
                        if (frameListener.rawDataEnabled()) {
                            byte[] encodedHeader = new byte[encodedControlFrameHeaderLength];
                            System.arraycopy(encodedControlFrameType.encoded, 0, encodedHeader, 0, frameTypeLength);
                            System.arraycopy(encodedControlFrameLength.encoded, 0, encodedHeader, frameTypeLength,
                                             encodedControlFrameLength.length());
                            frameListener.rawFrameHeader(context, streamId, encodedHeader);
                        }
                        if (!settingsReceived) {
                            if (controlFrameType != Http3Protocol.FRAME_SETTINGS) {
                                throw Http3ProtocolException.connectionError(Http3ErrorCode.MISSING_SETTINGS,
                                        "HTTP/3 control stream must start with SETTINGS");
                            }
                        } else if (controlFrameType == Http3Protocol.FRAME_SETTINGS
                                || controlFrameType == Http3Protocol.FRAME_DATA
                                || controlFrameType == Http3Protocol.FRAME_HEADERS
                                || controlFrameType == Http3Protocol.FRAME_PUSH_PROMISE
                                || Http3Protocol.isReservedHttp2FrameType(controlFrameType)) {
                            throw Http3ProtocolException.connectionError(Http3ErrorCode.FRAME_UNEXPECTED,
                                    "Unexpected HTTP/3 frame on control stream: " + controlFrameType);
                        }
                        if (controlFrameType == Http3Protocol.FRAME_SETTINGS) {
                            controlFramePayload = allocateControlFramePayload(MAX_SETTINGS_PAYLOAD_SIZE,
                                                                              Http3ErrorCode.EXCESSIVE_LOAD,
                                                                              "SETTINGS");
                        } else if (controlFrameType == Http3Protocol.FRAME_GOAWAY
                                || controlFrameType == Http3Protocol.FRAME_CANCEL_PUSH
                                || controlFrameType == Http3Protocol.FRAME_MAX_PUSH_ID) {
                            controlFramePayload = allocateControlFramePayload(Long.BYTES,
                                                                              Http3ErrorCode.FRAME_ERROR,
                                                                              "identifier");
                        } else {
                            controlFramePayload = null;
                        }
                        controlFramePayloadOffset = 0;
                        controlFrameDataSeen = false;
                    }
                    if (controlFrameRemaining == 0) {
                        completeControlFrame();
                        continue;
                    }
                    int chunkSize = (int) Math.min(Math.min(controlFrameRemaining, data.available()),
                                                   MAX_RAW_DATA_CHUNK_SIZE);
                    if (chunkSize == 0) {
                        return;
                    }
                    boolean last = controlFrameRemaining == chunkSize;
                    byte[] rawChunk = null;
                    if (controlFramePayload == null) {
                        if (frameListener.rawDataEnabled()) {
                            rawChunk = new byte[chunkSize];
                            data.read(rawChunk);
                        } else {
                            data.skip(chunkSize);
                        }
                    } else {
                        data.read(controlFramePayload, controlFramePayloadOffset, chunkSize);
                        if (frameListener.rawDataEnabled()) {
                            rawChunk = copy(controlFramePayload, controlFramePayloadOffset, chunkSize);
                        }
                        controlFramePayloadOffset += chunkSize;
                    }
                    frameListener.frameData(context, streamId, chunkSize, last);
                    if (rawChunk != null) {
                        frameListener.rawFrameData(context, streamId, rawChunk, last);
                    }
                    controlFrameDataSeen = true;
                    controlFrameRemaining -= chunkSize;
                    if (controlFrameRemaining == 0) {
                        completeControlFrame();
                    }
                }
                return;
            }
            if (streamType == Http3StreamType.PUSH) {
                if (!stream.isServerInitiated()) {
                    throw Http3ProtocolException.connectionError(Http3ErrorCode.STREAM_CREATION_ERROR,
                                                                 "Client-created HTTP/3 push stream");
                }
                if (!encodedPushId.read(data)) {
                    return;
                }
                throw Http3ProtocolException.connectionError(Http3ErrorCode.ID_ERROR,
                                                             "HTTP/3 push exceeds unset MAX_PUSH_ID: "
                                                                     + encodedPushId.value());
            }
            if (streamType == Http3StreamType.QPACK_ENCODER || streamType == Http3StreamType.QPACK_DECODER) {
                processQpackData(data, streamType);
                return;
            }
            int byteCount = data.available();
            if (byteCount == 0) {
                return;
            }
            String label = streamType == null ? "unknown unidirectional stream data" : streamType + " stream data";
            if (frameListener.rawDataEnabled()) {
                while (data.available() > 0) {
                    byte[] chunk = new byte[Math.min(data.available(), MAX_RAW_DATA_CHUNK_SIZE)];
                    data.read(chunk);
                    notifyStreamData(label, chunk);
                }
            } else {
                frameListener.streamData(context, streamId, label, byteCount);
                data.skip(byteCount);
            }
        }

        private Optional<Throwable> readFailure(Throwable throwable) {
            if (!stream.receivingState().isReset()
                    || Http3ProtocolException.find(throwable).isPresent()) {
                return Optional.of(throwable);
            }
            if (!streamTypeParsed || streamType == null) {
                return Optional.empty();
            }
            if (streamType == Http3StreamType.PUSH) {
                return Optional.of(Http3ProtocolException.connectionError(
                        Http3ErrorCode.ID_ERROR,
                        "HTTP/3 push stream reset before Push ID",
                        throwable));
            }
            return Optional.of(Http3ProtocolException.connectionError(
                    Http3ErrorCode.CLOSED_CRITICAL_STREAM,
                    "HTTP/3 critical stream reset: " + streamType,
                    throwable));
        }

        private void controlDataProcessed() {
            if (controlBatchStarted) {
                controlBatchStarted = false;
                listener.onControlDataProcessed();
            }
        }

        private void controlDataProcessing() {
            if (streamType == Http3StreamType.CONTROL && !controlBatchStarted) {
                controlBatchStarted = true;
                listener.onControlDataProcessing();
            }
        }

        private void processQpackData(BufferData data, Http3StreamType type) {
            Optional<Http3QpackContext> qpackContext = qpackContext();
            String label = type + " stream data";
            if (qpackContext.isEmpty() && !frameListener.rawDataEnabled()) {
                int byteCount = data.available();
                frameListener.streamData(context, streamId, label, byteCount);
                data.skip(byteCount);
                return;
            }

            while (data.available() > 0) {
                byte[] bytes = new byte[Math.min(data.available(), MAX_RAW_DATA_CHUNK_SIZE)];
                data.read(bytes);
                notifyStreamData(label, bytes);
                if (qpackContext.isEmpty()) {
                    continue;
                }
                if (type == Http3StreamType.QPACK_ENCODER) {
                    qpackContext.orElseThrow().onEncoderStreamData(bytes);
                } else {
                    qpackContext.orElseThrow().onDecoderStreamData(bytes);
                }
            }
        }

        private void endOfStream() {
            if (streamType == Http3StreamType.PUSH) {
                throw Http3ProtocolException.connectionError(Http3ErrorCode.ID_ERROR,
                                                             "HTTP/3 push stream ended before Push ID");
            }
            if (streamType == Http3StreamType.CONTROL) {
                if (!settingsReceived) {
                    throw Http3ProtocolException.connectionError(Http3ErrorCode.MISSING_SETTINGS,
                                                                 "HTTP/3 control stream ended before SETTINGS");
                }
                if (encodedControlFrameType.length() > 0
                        || encodedControlFrameLength.length() > 0
                        || controlFrameType >= 0) {
                    throw Http3ProtocolException.connectionError(Http3ErrorCode.FRAME_ERROR,
                                                                 "Truncated HTTP/3 control stream frame");
                }
            }
            if (streamType == Http3StreamType.CONTROL
                    || streamType == Http3StreamType.QPACK_ENCODER
                    || streamType == Http3StreamType.QPACK_DECODER) {
                throw Http3ProtocolException.connectionError(Http3ErrorCode.CLOSED_CRITICAL_STREAM,
                                                             "HTTP/3 critical stream closed: " + streamType);
            }
        }

        private void completeControlFrame() {
            byte[] payload = controlFramePayload == null ? EMPTY_BYTES : controlFramePayload;
            if (!controlFrameDataSeen) {
                frameListener.frameData(context, streamId, 0, true);
                if (frameListener.rawDataEnabled()) {
                    frameListener.rawFrameData(context, streamId, EMPTY_BYTES, true);
                }
            }
            if (controlFrameType == Http3Protocol.FRAME_SETTINGS) {
                settingsReceived = true;
                listener.onSettings(Http3Protocol.decodeSettingsPayload(payload));
            } else if (controlFrameType == Http3Protocol.FRAME_GOAWAY) {
                listener.onGoAway(Http3Protocol.decodeGoAway(payload, goAwayType));
            } else if (controlFrameType == Http3Protocol.FRAME_CANCEL_PUSH
                    || controlFrameType == Http3Protocol.FRAME_MAX_PUSH_ID) {
                BufferData data = BufferData.create(payload);
                long pushId = Http3Protocol.tryReadVarInt(data);
                if (pushId < 0 || data.available() > 0) {
                    throw Http3ProtocolException.connectionError(Http3ErrorCode.FRAME_ERROR,
                                                                 "Malformed HTTP/3 push identifier frame");
                }
                if (controlFrameType == Http3Protocol.FRAME_CANCEL_PUSH) {
                    throw Http3ProtocolException.connectionError(Http3ErrorCode.ID_ERROR,
                                                                 "HTTP/3 CANCEL_PUSH refers to an unavailable push: "
                                                                         + pushId);
                }
                if (stream.isServerInitiated()) {
                    throw Http3ProtocolException.connectionError(Http3ErrorCode.FRAME_UNEXPECTED,
                                                                 "Server sent HTTP/3 MAX_PUSH_ID");
                }
                if (highestPeerMaxPushId > pushId) {
                    throw Http3ProtocolException.connectionError(Http3ErrorCode.ID_ERROR,
                                                                 "HTTP/3 MAX_PUSH_ID decreased from "
                                                                         + highestPeerMaxPushId + " to " + pushId);
                }
                highestPeerMaxPushId = pushId;
            }
            encodedControlFrameType.reset();
            encodedControlFrameLength.reset();
            controlFrameType = -1;
            controlFrameRemaining = 0;
            controlFramePayload = null;
            controlFramePayloadOffset = 0;
            controlFrameDataSeen = false;
        }

        private byte[] allocateControlFramePayload(int maxSize, Http3ErrorCode errorCode, String label) {
            if (controlFrameRemaining > maxSize) {
                throw Http3ProtocolException.connectionError(
                        errorCode,
                        "HTTP/3 " + label + " payload exceeds the local limit: " + controlFrameRemaining + " > " + maxSize);
            }
            return new byte[(int) controlFrameRemaining];
        }

        private void notifyRawStreamTypeData(String label) {
            if (frameListener.rawDataEnabled()) {
                frameListener.rawStreamData(context,
                                            streamId,
                                            label,
                                            copy(encodedStreamType.encoded, 0, encodedStreamType.length()));
            }
        }

        private void notifyStreamData(String label, byte[] data) {
            frameListener.streamData(context, streamId, label, data.length);
            if (!frameListener.rawDataEnabled()) {
                return;
            }
            if (data.length == 0) {
                frameListener.rawStreamData(context, streamId, label, data);
                return;
            }
            for (int offset = 0; offset < data.length; offset += MAX_RAW_DATA_CHUNK_SIZE) {
                int length = Math.min(data.length - offset, MAX_RAW_DATA_CHUNK_SIZE);
                byte[] chunk = length == data.length ? data : copy(data, offset, length);
                frameListener.rawStreamData(context, streamId, label, chunk);
            }
        }
    }

    private static final class EncodedVarInt {
        private final byte[] encoded = new byte[Long.BYTES];
        private int length;
        private int expectedLength;

        private boolean read(BufferData data) {
            if (length == 0) {
                if (data.available() == 0) {
                    return false;
                }
                int first = data.read() & 0xff;
                encoded[length++] = (byte) first;
                expectedLength = 1 << (first >>> 6);
            }
            int additional = Math.min(data.available(), expectedLength - length);
            if (additional > 0) {
                data.read(encoded, length, additional);
                length += additional;
            }
            return length == expectedLength;
        }

        private long value() {
            return Http3Protocol.tryReadVarInt(BufferData.createReadOnly(encoded, 0, length));
        }

        private int length() {
            return length;
        }

        private void reset() {
            length = 0;
            expectedLength = 0;
        }
    }

}
