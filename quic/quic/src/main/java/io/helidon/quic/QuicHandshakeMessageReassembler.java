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
import java.util.function.Supplier;

import static io.helidon.quic.QuicTLSEngine.HandshakeState.NEED_RECV_CRYPTO;
import static io.helidon.quic.QuicTLSEngine.KeySpace.ONE_RTT;
import static io.helidon.quic.QuicTLSEngine.KeySpace.ZERO_RTT;

/**
 * Reassembles TLS handshake messages carried in QUIC CRYPTO data.
 */
final class QuicHandshakeMessageReassembler {
    private final Supplier<QuicTLSEngine.HandshakeState> handshakeStateSupplier;
    private final int maxHandshakeMessageSize;
    private final boolean clientMode;

    private ByteBuffer incomingCryptoBuffer;
    private QuicTLSEngine.KeySpace incomingCryptoSpace;

    QuicHandshakeMessageReassembler(Supplier<QuicTLSEngine.HandshakeState> handshakeStateSupplier,
                                    int maxHandshakeMessageSize,
                                    boolean clientMode) {
        this.handshakeStateSupplier = Objects.requireNonNull(handshakeStateSupplier, "handshakeStateSupplier");
        if (maxHandshakeMessageSize <= 0) {
            throw new IllegalArgumentException("maxHandshakeMessageSize must be greater than 0: "
                                                       + maxHandshakeMessageSize);
        }
        this.maxHandshakeMessageSize = maxHandshakeMessageSize;
        this.clientMode = clientMode;
    }

    void reset() {
        incomingCryptoBuffer = null;
        incomingCryptoSpace = null;
    }

    void consume(QuicTLSEngine.KeySpace keySpace, ByteBuffer payload, HandshakeMessageConsumer consumer)
            throws QuicTransportException {
        Objects.requireNonNull(keySpace, "keySpace");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(consumer, "consumer");

        if (!payload.hasRemaining()) {
            throw new IllegalArgumentException("Empty crypto buffer");
        }
        if (keySpace == ZERO_RTT) {
            throw new IllegalArgumentException("Crypto in zero-rtt");
        }
        if (incomingCryptoSpace != null && incomingCryptoSpace != keySpace) {
            throw unexpectedMessage("Unfinished message in " + incomingCryptoSpace);
        }

        while (payload.hasRemaining()) {
            validateHandshakeState(keySpace);

            if (incomingCryptoBuffer == null) {
                if (payload.remaining() < QuicTlsHandshakeMessages.HEADER_LENGTH) {
                    validateMessageType(keySpace, payload.get(payload.position()));
                    incomingCryptoBuffer = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH);
                    incomingCryptoBuffer.put(payload);
                    incomingCryptoSpace = keySpace;
                    return;
                }

                int payloadPos = payload.position();
                validateMessageType(keySpace, payload.get(payloadPos));
                int messageSize = handshakeMessageSize(payload, payloadPos);
                if (payload.remaining() < messageSize + QuicTlsHandshakeMessages.HEADER_LENGTH) {
                    validateMessageSize(messageSize);
                    incomingCryptoBuffer = ByteBuffer.allocate(messageSize + QuicTlsHandshakeMessages.HEADER_LENGTH);
                    incomingCryptoBuffer.put(payload);
                    incomingCryptoSpace = keySpace;
                    return;
                }

                ByteBuffer message = payload.slice(payloadPos, messageSize + QuicTlsHandshakeMessages.HEADER_LENGTH);
                payload.position(payloadPos + messageSize + QuicTlsHandshakeMessages.HEADER_LENGTH);
                consumer.accept(keySpace, message);
                continue;
            }

            int copySize = Math.min(payload.remaining(), incomingCryptoBuffer.remaining());
            ByteBuffer chunk = payload.slice(payload.position(), copySize);
            incomingCryptoBuffer.put(chunk);
            payload.position(payload.position() + copySize);

            if (incomingCryptoBuffer.capacity() == QuicTlsHandshakeMessages.HEADER_LENGTH
                    && incomingCryptoBuffer.position() == QuicTlsHandshakeMessages.HEADER_LENGTH) {
                int messageSize = handshakeMessageSize(incomingCryptoBuffer, 0);
                if (messageSize != 0) {
                    validateMessageSize(messageSize);
                    ByteBuffer expanded = ByteBuffer.allocate(messageSize + QuicTlsHandshakeMessages.HEADER_LENGTH);
                    incomingCryptoBuffer.flip();
                    expanded.put(incomingCryptoBuffer);
                    incomingCryptoBuffer = expanded;
                }
            }

            if (incomingCryptoBuffer.hasRemaining()) {
                if (!payload.hasRemaining()) {
                    return;
                }
                continue;
            }

            ByteBuffer message = incomingCryptoBuffer.flip();
            incomingCryptoBuffer = null;
            incomingCryptoSpace = null;
            consumer.accept(keySpace, message);
        }
    }

    private void validateMessageType(QuicTLSEngine.KeySpace actual, byte messageType) throws QuicTransportException {
        if (clientMode
                && actual == ONE_RTT
                && (messageType & 0xFF) == QuicTlsHandshakeMessages.CERTIFICATE_REQUEST) {
            throw new QuicTransportException("Client received a post-handshake CertificateRequest",
                                             ONE_RTT,
                                             0,
                                             QuicTransportErrors.PROTOCOL_VIOLATION);
        }
        QuicTLSEngine.KeySpace expected = expectedKeySpace(messageType);
        if (expected != actual) {
            throw QuicTlsHandshakeMessages.unexpectedMessage(
                    "Message " + messageType + " received in " + actual + " but should be " + expected);
        }
    }

    private static QuicTLSEngine.KeySpace expectedKeySpace(byte messageType) {
        return QuicTlsHandshakeMessages.expectedKeySpace(messageType & 0xFF);
    }

    private static int handshakeMessageSize(ByteBuffer buffer, int offset) {
        return ((buffer.get(offset + 1) & 0xFF) << 16)
                | ((buffer.get(offset + 2) & 0xFF) << 8)
                | (buffer.get(offset + 3) & 0xFF);
    }

    private static QuicTransportException unexpectedMessage(String detail) {
        return QuicTlsHandshakeMessages.unexpectedMessage(detail);
    }

    private void validateHandshakeState(QuicTLSEngine.KeySpace keySpace) throws QuicTransportException {
        QuicTLSEngine.HandshakeState handshakeState = handshakeStateSupplier.get();
        if (keySpace != ONE_RTT && handshakeState != NEED_RECV_CRYPTO) {
            throw unexpectedMessage("Not expecting a handshake message, state: " + handshakeState);
        }
    }

    private void validateMessageSize(int messageSize) throws QuicTransportException {
        if (messageSize > maxHandshakeMessageSize) {
            throw new QuicTransportException("The size of the handshake message ("
                                                     + messageSize
                                                     + ") exceeds the maximum allowed size ("
                                                     + maxHandshakeMessageSize
                                                     + ")",
                                             0,
                                             QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED);
        }
    }

    @FunctionalInterface
    interface HandshakeMessageConsumer {
        void accept(QuicTLSEngine.KeySpace keySpace, ByteBuffer message) throws QuicTransportException;
    }
}
