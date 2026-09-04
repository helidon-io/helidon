/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.Api;
import io.helidon.quic.QuicConnectionImpl;

/**
 * A collection of utilities methods to analyze and work with
 * quic streams.
 */
@Api.Internal
public final class QuicStreams {
    /**
     * Mask selecting the two low bits that encode stream type.
     */
    public static final int TYPE_MASK = 0x03;
    /**
     * Bit mask indicating unidirectional stream type.
     */
    public static final int UNI_MASK = 0x02;
    /**
     * Bit mask indicating server-initiated stream type.
     */
    public static final int SRV_MASK = 0x01;
    /**
     * Utility class; no instances.
     */
    private QuicStreams() {
        throw new InternalError("should not come here");
    }

    /**
     * Returns the encoded stream type for the given stream ID.
     *
     * @param streamId stream identifier
     * @return stream type value in range {@code [0..3]}
     */
    public static int streamType(long streamId) {
        return (int) streamId & TYPE_MASK;
    }

    /**
     * Returns whether the stream ID identifies a bidirectional stream.
     *
     * @param streamId stream identifier
     * @return {@code true} if the stream is bidirectional
     */
    public static boolean isBidirectional(long streamId) {
        return ((int) streamId & UNI_MASK) == 0;
    }

    /**
     * Returns whether the stream ID identifies a unidirectional stream.
     *
     * @param streamId stream identifier
     * @return {@code true} if the stream is unidirectional
     */
    public static boolean isUnidirectional(long streamId) {
        return ((int) streamId & UNI_MASK) == UNI_MASK;
    }

    /**
     * Returns whether the stream type identifies a bidirectional stream.
     *
     * @param streamType encoded stream type value
     * @return {@code true} if the stream type is bidirectional
     */
    public static boolean isBidirectional(int streamType) {
        return (streamType & UNI_MASK) == 0;
    }

    /**
     * Returns whether the stream type identifies a unidirectional stream.
     *
     * @param streamType encoded stream type value
     * @return {@code true} if the stream type is unidirectional
     */
    public static boolean isUnidirectional(int streamType) {
        return (streamType & UNI_MASK) == UNI_MASK;
    }

    /**
     * Returns whether the stream ID identifies a client-initiated stream.
     *
     * @param streamId stream identifier
     * @return {@code true} if the stream is client initiated
     */
    public static boolean isClientInitiated(long streamId) {
        return ((int) streamId & SRV_MASK) == 0;
    }

    /**
     * Returns whether the stream ID identifies a server-initiated stream.
     *
     * @param streamId stream identifier
     * @return {@code true} if the stream is server initiated
     */
    public static boolean isServerInitiated(long streamId) {
        return ((int) streamId & SRV_MASK) == SRV_MASK;
    }

    /**
     * Returns whether the stream type identifies a client-initiated stream.
     *
     * @param streamType encoded stream type value
     * @return {@code true} if the stream type is client initiated
     */
    public static boolean isClientInitiated(int streamType) {
        return (streamType & SRV_MASK) == 0;
    }

    /**
     * Returns whether the stream type identifies a server-initiated stream.
     *
     * @param streamType encoded stream type value
     * @return {@code true} if the stream type is server initiated
     */
    public static boolean isServerInitiated(int streamType) {
        return (streamType & SRV_MASK) == SRV_MASK;
    }

    /**
     * Creates a stream instance for the given stream identifier and connection role.
     *
     * @param connection        connection owning the stream
     * @param streamId          stream identifier
     * @param maxSmallFragments maximum number of small fragments accepted by a receiver stream
     * @param streamBufferSize  sender stream buffer size in bytes
     * @return stream implementation matching the stream type
     */
    static AbstractQuicStream createStream(QuicConnectionImpl connection,
                                           long streamId,
                                           int maxSmallFragments,
                                           int streamBufferSize) {
        int type = streamType(streamId);
        boolean isClient = connection.isClientConnection();
        return switch (type) {
            case 0x00, 0x01 -> new QuicBidiStreamImpl(connection,
                                                      streamId,
                                                      maxSmallFragments,
                                                      streamBufferSize);
            case 0x02 -> isClient ? new QuicSenderStreamImpl(connection, streamId, streamBufferSize)
                    : new QuicReceiverStreamImpl(connection, streamId, maxSmallFragments);
            case 0x03 -> isClient ? new QuicReceiverStreamImpl(connection, streamId, maxSmallFragments)
                    : new QuicSenderStreamImpl(connection, streamId, streamBufferSize);
            default -> throw new IllegalArgumentException("bad stream type %s for stream %s"
                                                                  .formatted(type, streamId));
        };
    }

}
