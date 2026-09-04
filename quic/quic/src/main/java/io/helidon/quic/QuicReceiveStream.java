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

import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;

/**
 * Readable QUIC stream.
 */
@Api.Incubating
public interface QuicReceiveStream extends QuicStream {

    /**
     * Reads the next complete transport buffer, blocking until data, FIN, reset, or session termination.
     *
     * <p>An empty result signals FIN. Read operations are serialized for a stream and are intended for virtual threads.
     *
     * @return next read-only buffer, or empty at FIN
     * @throws QuicStreamTerminationException if the stream closes locally or the peer resets it
     */
    Optional<BufferData> read();

    /**
     * Stops reading and asks the peer to stop sending.
     *
     * <p>The application error code must be between {@code 0} and 2<sup>62</sup> - 1, inclusive.
     * If the receiving side has already reached a terminal condition, this method has no effect.
     *
     * @param applicationErrorCode application protocol error code
     * @throws IllegalArgumentException if the application error code is outside the QUIC application error-code range
     */
    void stopReading(long applicationErrorCode);
}
