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

import java.nio.ByteBuffer;
import java.util.Objects;

final class QuicTlsFinishedMessage {
    private final byte[] verifyData;

    private QuicTlsFinishedMessage(byte[] verifyData) {
        this.verifyData = Objects.requireNonNull(verifyData, "verifyData").clone();
    }

    static QuicTlsFinishedMessage create(byte[] verifyData) {
        return new QuicTlsFinishedMessage(verifyData);
    }

    static QuicTlsFinishedMessage decode(ByteBuffer message) throws QuicTransportException {
        ByteBuffer body = QuicTlsCodecSupport.handshakeBody(message, QuicTlsHandshakeMessages.FINISHED, "Finished");
        byte[] verifyData = QuicTlsCodecSupport.copy(body);
        if (verifyData.length == 0) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed Finished message: empty verify_data");
        }
        return new QuicTlsFinishedMessage(verifyData);
    }

    byte[] verifyData() {
        return verifyData.clone();
    }

    ByteBuffer encode() {
        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH + verifyData.length);
        QuicTlsCodecSupport.putHandshakeHeader(encoded, QuicTlsHandshakeMessages.FINISHED, verifyData.length);
        encoded.put(verifyData);
        return encoded.flip();
    }
}
