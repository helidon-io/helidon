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

import java.io.Serial;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

/**
 * Per-connection QPACK state shared between HTTP/3 request and response streams.
 */
@Api.Internal
public final class Http3QpackContext {
    private static final int ENCODED_FIELD_SECTION_ENVELOPE = 64;
    private final QpackConnectionState delegate;

    private Http3QpackContext(long localMaxTableCapacity,
                             long localBlockedStreams,
                             int maxHeadersSize,
                             Consumer<Throwable> connectionFailureHandler) {
        this.delegate = QpackConnectionState.create(localMaxTableCapacity,
                                                     localBlockedStreams,
                                                     maxHeadersSize,
                                                     connectionFailureHandler);
    }

    /**
     * Create a new QPACK context with a hard local header limit.
     *
     * @param localMaxTableCapacity local decoder dynamic table capacity advertised in SETTINGS
     * @param localBlockedStreams local decoder blocked streams limit advertised in SETTINGS
     * @param maxHeadersSize hard local decoded-header limit
     * @param connectionFailureHandler connection-owner handler for QPACK connection failure
     * @return new QPACK context
     * @throws IllegalArgumentException if a local capacity or limit is negative
     */
    public static Http3QpackContext create(long localMaxTableCapacity,
                                           long localBlockedStreams,
                                           int maxHeadersSize,
                                           Consumer<Throwable> connectionFailureHandler) {
        return new Http3QpackContext(localMaxTableCapacity,
                                     localBlockedStreams,
                                     maxHeadersSize,
                                     Objects.requireNonNull(connectionFailureHandler, "connectionFailureHandler"));
    }

    /**
     * Derive a finite encoded field-section limit from the decoded-header limit.
     * The expansion factor covers the longest HPACK/QPACK Huffman symbols, and the envelope covers field-line framing.
     *
     * @param maxHeadersSize hard local decoded-header limit
     * @return encoded field-section limit
     */
    static int encodedFieldSectionLimit(int maxHeadersSize) {
        if (maxHeadersSize < 0) {
            throw new IllegalArgumentException("maxHeadersSize must not be negative: " + maxHeadersSize);
        }
        return (int) Math.min(Integer.MAX_VALUE,
                              maxHeadersSize * 4L + ENCODED_FIELD_SECTION_ENVELOPE);
    }

    /**
     * Configure the peer decoder's QPACK settings observed on the remote control stream.
     *
     * @param qpackMaxTableCapacity peer QPACK dynamic-table capacity
     * @param qpackBlockedStreams peer QPACK blocked-stream limit
     * @throws Http3ProtocolException if updating the peer settings fails the local QPACK encoder stream
     * @throws IllegalStateException if this context is closed
     */
    public void peerSettings(long qpackMaxTableCapacity, long qpackBlockedStreams) {
        delegate.peerSettings(qpackMaxTableCapacity, qpackBlockedStreams);
    }

    /**
     * Register a sender for bytes written to the local QPACK encoder stream.
     *
     * @param sender encoder stream sender
     * @throws Http3ProtocolException if flushing pending instructions fails the local QPACK encoder stream
     * @throws IllegalStateException if this context is closed or an encoder instruction sender is already registered
     */
    public void encoderInstructionsSender(InstructionSender sender) {
        delegate.encoderInstructionsSender(Objects.requireNonNull(sender, "sender"));
    }

    /**
     * Register a sender for bytes written to the local QPACK decoder stream.
     *
     * @param sender decoder stream sender
     * @throws Http3ProtocolException if flushing pending instructions fails the local QPACK decoder stream
     * @throws IllegalStateException if this context is closed or a decoder instruction sender is already registered
     */
    public void decoderInstructionsSender(InstructionSender sender) {
        delegate.decoderInstructionsSender(Objects.requireNonNull(sender, "sender"));
    }

    /**
     * Encode one HTTP/3 header section.
     *
     * @param streamId request or response stream id
     * @param headers header fields
     * @return encoded QPACK field section
     * @throws Http3ProtocolException if encoding fails the local QPACK encoder stream
     * @throws IllegalStateException if this context is closed
     */
    public byte[] encodeHeaders(long streamId, Iterable<Header> headers) {
        Objects.requireNonNull(headers, "headers");
        return delegate.encodeHeaders(streamId, headers);
    }

    /**
     * Open the QPACK decoder lifecycle owned by one HTTP/3 request or push stream.
     *
     * @param streamId HTTP/3 stream id
     * @return stream-owned QPACK decoder lifecycle
     */
    public Stream openStream(long streamId) {
        return new Stream(delegate.openDecoderStream(streamId));
    }

    /**
     * Process bytes received on the remote QPACK encoder stream.
     *
     * @param bytes stream bytes
     * @throws Http3ProtocolException if the bytes contain an invalid QPACK encoder-stream instruction
     * @throws IllegalStateException if this context is closed
     */
    public void onEncoderStreamData(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        delegate.onEncoderStreamData(bytes);
    }

    /**
     * Process bytes received on the remote QPACK decoder stream.
     *
     * @param bytes stream bytes
     * @throws Http3ProtocolException if the bytes contain an invalid QPACK decoder-stream instruction
     * @throws IllegalStateException if this context is closed
     */
    public void onDecoderStreamData(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        delegate.onDecoderStreamData(bytes);
    }

    /**
     * Fail QPACK because a local or peer instruction stream failed.
     *
     * @param streamType QPACK instruction stream type
     * @param cause instruction-stream failure
     */
    public void instructionStreamFailed(Http3StreamType streamType, Throwable cause) {
        Objects.requireNonNull(streamType, "streamType");
        Objects.requireNonNull(cause, "cause");
        if (streamType != Http3StreamType.QPACK_ENCODER && streamType != Http3StreamType.QPACK_DECODER) {
            throw new IllegalArgumentException("Not a QPACK instruction stream: " + streamType);
        }
        delegate.instructionStreamFailed(cause);
    }

    /**
     * Close all QPACK state owned by the HTTP/3 connection.
     *
     * @param cause connection close cause
     */
    public void close(Throwable cause) {
        delegate.close(Objects.requireNonNull(cause, "cause"));
    }

    /**
     * Sender of QPACK instruction bytes on a unidirectional stream.
     */
    @FunctionalInterface
    public interface InstructionSender {
        /**
         * Send bytes on the configured instruction stream.
         *
         * @param bytes bytes to send
         */
        void send(byte[] bytes);
    }

    /**
     * QPACK decoder lifecycle owned by one HTTP/3 request or push stream.
     */
    @Api.Internal
    public static final class Stream {
        private final QpackConnectionState.DecoderStream delegate;

        private Stream(QpackConnectionState.DecoderStream delegate) {
            this.delegate = delegate;
        }

        /**
         * Decode one field section as headers.
         *
         * @param buffer encoded field section
         * @param maxFieldSectionSize caller-specific decoded size limit, or a negative value to use the connection's
         *                            common header limit; non-negative values are clamped to the common limit
         * @return decoded headers
         * @throws Http3ProtocolException if the field section is malformed or violates QPACK limits
         * @throws IllegalStateException if this decoder or its context is closed, or waiting for encoder progress is interrupted
         */
        public Headers decodeHeaders(BufferData buffer, long maxFieldSectionSize) {
            WritableHeaders<?> headers = WritableHeaders.create();
            decodeHeaderLines(buffer, maxFieldSectionSize)
                    .forEach(header -> QpackCodec.addDecodedHeader(headers, header));
            return headers;
        }

        /**
         * Decode one field section while preserving field-line order.
         *
         * @param buffer encoded field section
         * @param maxFieldSectionSize caller-specific decoded size limit, or a negative value to use the connection's
         *                            common header limit; non-negative values are clamped to the common limit
         * @return decoded field lines in wire order
         * @throws Http3ProtocolException if the field section is malformed or violates QPACK limits
         * @throws IllegalStateException if this decoder or its context is closed, or waiting for encoder progress is interrupted
         */
        public List<Header> decodeHeaderLines(BufferData buffer, long maxFieldSectionSize) {
            try {
                return delegate.decodeHeaderLines(Objects.requireNonNull(buffer, "buffer"), maxFieldSectionSize);
            } catch (IllegalStateException e) {
                if (e.getCause() instanceof InterruptedException interruptedException) {
                    throw new DecodingInterruptedException(e.getMessage(), interruptedException);
                }
                throw e;
            }
        }

        List<Header> decodeHeaderLines(BufferData buffer,
                                       long maxFieldSectionSize,
                                       Duration readTimeout,
                                       CompletableFuture<Void> readTimeoutActivation) {
            try {
                return delegate.decodeHeaderLines(Objects.requireNonNull(buffer, "buffer"),
                                                  maxFieldSectionSize,
                                                  Objects.requireNonNull(readTimeout, "readTimeout"),
                                                  Objects.requireNonNull(readTimeoutActivation,
                                                                         "readTimeoutActivation"));
            } catch (IllegalStateException e) {
                if (e.getCause() instanceof InterruptedException interruptedException) {
                    throw new DecodingInterruptedException(e.getMessage(), interruptedException);
                }
                throw e;
            }
        }

        /**
         * Complete processing of peer field sections on the HTTP/3 stream.
         */
        public void complete() {
            delegate.complete();
        }

        /**
         * Abandon processing of peer field sections on the HTTP/3 stream.
         */
        public void cancel() {
            delegate.cancel();
        }
    }

    static final class DecodingInterruptedException extends IllegalStateException {
        @Serial
        private static final long serialVersionUID = 1L;

        private DecodingInterruptedException(String message, InterruptedException cause) {
            super(message, cause);
        }
    }
}
