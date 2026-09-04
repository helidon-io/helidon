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
import java.util.Objects;

final class QuicTlsNewSessionTicket {
    private static final String MESSAGE_NAME = "NewSessionTicket";
    private static final long QUIC_EARLY_DATA_SENTINEL = 0xFFFF_FFFFL;
    private final long ticketLifetimeSeconds;
    private final long ticketAgeAdd;
    private final byte[] ticketNonce;
    private final byte[] ticket;

    private QuicTlsNewSessionTicket(long ticketLifetimeSeconds,
                                    long ticketAgeAdd,
                                    byte[] ticketNonce,
                                    byte[] ticket) {
        this.ticketLifetimeSeconds = ticketLifetimeSeconds;
        this.ticketAgeAdd = ticketAgeAdd;
        this.ticketNonce = Objects.requireNonNull(ticketNonce, "ticketNonce").clone();
        this.ticket = Objects.requireNonNull(ticket, "ticket").clone();
    }

    static QuicTlsNewSessionTicket create(long ticketLifetimeSeconds,
                                          long ticketAgeAdd,
                                          byte[] ticketNonce,
                                          byte[] ticket) {
        if (ticketLifetimeSeconds < 0 || ticketLifetimeSeconds > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("ticketLifetimeSeconds does not fit a 32-bit unsigned integer");
        }
        if (ticketAgeAdd < 0 || ticketAgeAdd > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("ticketAgeAdd does not fit a 32-bit unsigned integer");
        }
        if (Objects.requireNonNull(ticket, "ticket").length == 0) {
            throw new IllegalArgumentException("ticket must not be empty");
        }
        return new QuicTlsNewSessionTicket(ticketLifetimeSeconds, ticketAgeAdd, ticketNonce, ticket);
    }

    static QuicTlsNewSessionTicket decode(ByteBuffer handshakeMessage) throws QuicTransportException {
        ByteBuffer body = QuicTlsCodecSupport.handshakeBody(handshakeMessage,
                                                            QuicTlsHandshakeMessages.NEW_SESSION_TICKET,
                                                            MESSAGE_NAME);
        long ticketLifetimeSeconds = readUint32(body, "ticket_lifetime");
        long ticketAgeAdd = readUint32(body, "ticket_age_add");
        byte[] ticketNonce = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(body,
                                                                                     QuicTlsCodecSupport.UINT8_LENGTH,
                                                                                     "ticket_nonce",
                                                                                     MESSAGE_NAME));
        byte[] ticket = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(body,
                                                                                QuicTlsCodecSupport.UINT16_LENGTH,
                                                                                "ticket",
                                                                                MESSAGE_NAME));
        if (ticket.length == 0) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed NewSessionTicket message: ticket");
        }

        List<QuicTlsExtension> extensions = QuicTlsExtensions.decode(body, MESSAGE_NAME);
        QuicTlsCodecSupport.ensureConsumed(body, MESSAGE_NAME);
        validateEarlyData(extensions);
        return new QuicTlsNewSessionTicket(ticketLifetimeSeconds, ticketAgeAdd, ticketNonce, ticket);
    }

    static void validate(ByteBuffer handshakeMessage) throws QuicTransportException {
        decode(handshakeMessage);
    }

    long ticketLifetimeSeconds() {
        return ticketLifetimeSeconds;
    }

    long ticketAgeAdd() {
        return ticketAgeAdd;
    }

    byte[] ticketNonce() {
        return ticketNonce.clone();
    }

    byte[] ticket() {
        return ticket.clone();
    }

    ByteBuffer encode() {
        ByteBuffer encodedExtensions = QuicTlsExtensions.encode(List.of());
        ByteBuffer body = ByteBuffer.allocate(Integer.BYTES
                                                      + Integer.BYTES
                                                      + QuicTlsCodecSupport.encodedVectorLength(
                QuicTlsCodecSupport.UINT8_LENGTH,
                ticketNonce.length,
                "ticket_nonce")
                                                      + QuicTlsCodecSupport.encodedVectorLength(
                QuicTlsCodecSupport.UINT16_LENGTH,
                ticket.length,
                "ticket")
                                                      + encodedExtensions.remaining());
        body.putInt((int) ticketLifetimeSeconds);
        body.putInt((int) ticketAgeAdd);
        QuicTlsCodecSupport.putVector(body,
                                      QuicTlsCodecSupport.UINT8_LENGTH,
                                      ticketNonce,
                                      "ticket_nonce");
        QuicTlsCodecSupport.putVector(body,
                                      QuicTlsCodecSupport.UINT16_LENGTH,
                                      ticket,
                                      "ticket");
        body.put(encodedExtensions.asReadOnlyBuffer());
        return handshakeMessage(QuicTlsHandshakeMessages.NEW_SESSION_TICKET, body.flip());
    }

    private static void validateEarlyData(List<QuicTlsExtension> extensions) throws QuicTransportException {
        QuicTlsExtension earlyData = QuicTlsExtensions.find(extensions, QuicTlsExtensions.EARLY_DATA).orElse(null);
        if (earlyData == null) {
            return;
        }
        ByteBuffer extensionData = earlyData.dataBuffer();
        if (extensionData.remaining() != Integer.BYTES) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed NewSessionTicket message: early_data");
        }
        long maxEarlyDataSize = extensionData.getInt() & 0xFFFF_FFFFL;
        if (maxEarlyDataSize != QUIC_EARLY_DATA_SENTINEL) {
            throw new QuicTransportException("QUIC NewSessionTicket early_data is not 0xffffffff",
                                             QuicTLSEngine.KeySpace.ONE_RTT,
                                             0,
                                             QuicTransportErrors.PROTOCOL_VIOLATION);
        }
        // Helidon does not send 0-RTT yet. Ignore only the valid capability while retaining the ticket for 1-RTT resumption.
    }

    private static long readUint32(ByteBuffer buffer, String fieldName) throws QuicTransportException {
        if (buffer.remaining() < Integer.BYTES) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed " + MESSAGE_NAME + " message: " + fieldName);
        }
        return buffer.getInt() & 0xFFFF_FFFFL;
    }

    private static ByteBuffer handshakeMessage(int type, ByteBuffer body) {
        ByteBuffer payload = body.asReadOnlyBuffer();
        ByteBuffer result = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH + payload.remaining());
        QuicTlsCodecSupport.putHandshakeHeader(result, type, payload.remaining());
        result.put(payload);
        return result.flip();
    }

}
