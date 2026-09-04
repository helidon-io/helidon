/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.nio.ByteBuffer;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;

/**
 * Interface for consumer of QUIC transport parameters, in wire-encoded format.
 */
@Api.Internal
public interface QuicTransportParametersConsumer {
    /**
     * Consumes the provided QUIC transport parameters.
     *
     * @param buffer buffer data containing encoded quic transport parameters
     * @throws QuicTransportException if buffer does not represent valid parameters
     */
    default void accept(BufferData buffer) throws QuicTransportException {
        byte[] bytes = new byte[buffer.available()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) buffer.get(i);
        }
        accept(ByteBuffer.wrap(bytes));
    }

    /**
     * Consumes the provided QUIC transport parameters.
     *
     * @param buffer byte buffer containing encoded quic transport parameters
     * @throws QuicTransportException if buffer does not represent valid parameters
     */
    void accept(ByteBuffer buffer) throws QuicTransportException;
}
