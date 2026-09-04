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

/**
 * An interface to model a QuicStream.
 * A quic stream can be either unidirectional
 * or bidirectional. A unidirectional stream can
 * be opened for reading or for writing.
 * Concrete subclasses of {@code QuicStream} should
 * implement {@link QuicSenderStream} (unidirectional {@link
 * StreamMode#WRITE_ONLY} stream), or {@link QuicReceiverStream}
 * (unidirectional {@link StreamMode#READ_ONLY} stream), or
 * {@link QuicBidiStream} (bidirectional {@link StreamMode#READ_WRITE} stream).
 */
@Api.Internal
public sealed interface QuicStream
        permits QuicSenderStream, QuicReceiverStream, QuicBidiStream, AbstractQuicStream {

    /**
     * Returns the stream ID of this stream.
     *
     * @return the stream ID of this stream
     */
    long streamId();

    /**
     * Returns this stream operation mode.
     * One of {@link StreamMode#READ_ONLY}, {@link StreamMode#WRITE_ONLY},
     * or {@link StreamMode#READ_WRITE}.
     *
     * @return this stream operation mode
     */
    StreamMode mode();

    /**
     * Returns whether this stream is client initiated.
     *
     * @return whether this stream is client initiated
     */
    boolean isClientInitiated();

    /**
     * Returns whether this stream is server initiated.
     *
     * @return whether this stream is server initiated
     */
    boolean isServerInitiated();

    /**
     * Returns whether this stream is bidirectional.
     *
     * @return whether this stream is bidirectional
     */
    boolean isBidirectional();

    /**
     * Returns whether this stream is locally initiated.
     *
     * @return true if this stream is locally initiated
     */
    boolean isLocalInitiated();

    /**
     * Returns whether this stream is remotely initiated.
     *
     * @return true if this stream is remotely initiated
     */
    boolean isRemoteInitiated();

    /**
     * The type of this stream, as an int. This is a number between
     * 0 and 3 inclusive, and corresponds to the last two lowest bits
     * of the stream ID.
     * <ul>
     *    <li> 0x00: bidirectional, client initiated</li>
     *    <li> 0x01: bidirectional, server initiated</li>
     *    <li> 0x02: unidirectional, client initiated</li>
     *    <li> 0x03: unidirectional, server initiated</li>
     * </ul>
     *
     * @return the type of this stream, as an int
     */
    int type();

    /**
     * Returns the combined stream state.
     * This is mostly used for logging purposes, to log the
     * combined state of a stream.
     *
     * @return the combined stream state
     */
    StreamState state();

    /**
     * Returns whether the stream has errors.
     * For a {@linkplain QuicBidiStream bidirectional} stream,
     * this method returns true if either its sending part or
     * its receiving part was closed with a non-zero error code.
     *
     * @return true if the stream has errors
     */
    boolean hasError();

    /**
     * The stream operation mode.
     * One of {@link #READ_ONLY}, {@link #WRITE_ONLY}, or {@link #READ_WRITE}.
     */
    enum StreamMode {
        /**
         * Read-only stream mode.
         */
        READ_ONLY,
        /**
         * Write-only stream mode.
         */
        WRITE_ONLY,
        /**
         * Bidirectional read-write stream mode.
         */
        READ_WRITE;

        /**
         * Returns whether this operation mode allows reading data from the stream.
         *
         * @return true if this operation mode allows reading data from the stream
         */
        public boolean isReadable() {
            return this != WRITE_ONLY;
        }

        /**
         * Returns whether this operation mode allows writing data to the stream.
         *
         * @return true if this operation mode allows writing data to the stream
         */
        public boolean isWritable() {
            return this != READ_ONLY;
        }
    }

    /**
     * An interface that unifies the three different stream states.
     * This is mostly used for logging purposes, to log the
     * combined state of a stream.
     */
    sealed interface StreamState permits
                                 QuicReceiverStream.ReceivingStreamState,
                                 QuicSenderStream.SendingStreamState,
                                 QuicBidiStream.BidiStreamState {
        /**
         * Returns the canonical enum-style state name.
         *
         * @return the canonical enum-style state name
         */
        String name();

        /**
         * Returns the canonical stream-state text.
         *
         * @return the canonical stream-state text
         */
        default String text() {
            return name();
        }

        /**
         * Returns whether this is a terminal state.
         *
         * @return true if this is a terminal state
         */
        boolean isTerminal();
    }

}
