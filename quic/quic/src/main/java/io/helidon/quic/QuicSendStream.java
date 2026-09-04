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

import java.util.concurrent.CompletionStage;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;

/**
 * Writable QUIC stream.
 */
@Api.Incubating
public interface QuicSendStream extends QuicStream {

    /**
     * Writes one buffer.
     *
     * <p>This method synchronously consumes and copies all unread bytes, then blocks until they are handed to the packet
     * path. It does not wait for peer acknowledgment. Writes are serialized and are intended for virtual threads.
     *
     * @param data data to write
     * @throws QuicStreamTerminationException if the sending side has reached a terminal stream condition
     */
    void write(BufferData data);

    /**
     * Writes one final buffer and finishes the stream.
     *
     * <p>This method has the same ownership and dispatch-completion contract as {@link #write(BufferData)}.
     *
     * @param data final data to write
     * @throws QuicStreamTerminationException if the sending side has reached a terminal stream condition
     */
    void writeFinal(BufferData data);

    /**
     * Finishes the stream without additional data.
     *
     * @throws QuicStreamTerminationException if the sending side has reached a terminal stream condition
     */
    void finish();

    /**
     * Abruptly resets this stream's sending side.
     *
     * <p>The application error code must be between {@code 0} and 2<sup>62</sup> - 1, inclusive.
     * If the sending side has already reached a terminal condition, this method has no effect.
     *
     * @param applicationErrorCode application protocol error code
     * @throws IllegalArgumentException if the application error code is outside the QUIC application error-code range
     */
    void reset(long applicationErrorCode);

    /**
     * Whether the peer requested that this endpoint stop sending.
     *
     * <p>This is a non-throwing, non-blocking current-state accessor.
     *
     * @return {@code true} when STOP_SENDING was received
     */
    boolean stopSendingReceived();

    /**
     * Stage completed with the application error code when STOP_SENDING is received.
     *
     * <p>If the connection terminates before STOP_SENDING is received, the stage completes exceptionally with
     * {@link QuicException}.
     *
     * @return peer stop-sending observation
     */
    CompletionStage<Long> whenStopSendingReceived();
}
