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
import java.util.List;
import java.util.Optional;

final class QuicTlsTransportParametersMessage {
    private QuicTlsTransportParametersMessage() {
    }

    static Optional<ByteBuffer> extractRemoteTransportParameters(boolean localClientMode, ByteBuffer handshakeMessage)
            throws QuicTransportException {
        ByteBuffer message = handshakeMessage.asReadOnlyBuffer();
        return localClientMode
                ? extractEncryptedExtensions(message)
                : extractClientHello(message);
    }

    private static Optional<ByteBuffer> extractClientHello(ByteBuffer message) throws QuicTransportException {
        if (QuicTlsHandshakeMessages.messageType(message) != QuicTlsHandshakeMessages.CLIENT_HELLO) {
            return Optional.empty();
        }

        return QuicTlsClientHelloMessage.decode(message)
                .extension(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS)
                .map(QuicTlsExtension::dataBuffer)
                .map(ByteBuffer::asReadOnlyBuffer);
    }

    private static Optional<ByteBuffer> extractEncryptedExtensions(ByteBuffer message) throws QuicTransportException {
        if (QuicTlsHandshakeMessages.messageType(message) != QuicTlsHandshakeMessages.ENCRYPTED_EXTENSIONS) {
            return Optional.empty();
        }

        ByteBuffer body = QuicTlsCodecSupport.handshakeBody(message, "EncryptedExtensions");
        List<QuicTlsExtension> extensions = QuicTlsExtensions.decode(body, "EncryptedExtensions");
        QuicTlsCodecSupport.ensureConsumed(body, "EncryptedExtensions");
        return QuicTlsExtensions.find(extensions, QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS)
                .map(QuicTlsExtension::dataBuffer)
                .map(ByteBuffer::asReadOnlyBuffer);
    }
}
