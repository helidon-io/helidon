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
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTlsNewSessionTicketTest {
    @Test
    void shouldDecodeTicket() throws Exception {
        byte[] ticketNonce = bytes("010203");
        byte[] ticket = bytes("0a0b0c0d");

        QuicTlsNewSessionTicket decoded = QuicTlsNewSessionTicket.decode(QuicTlsNewSessionTicket.create(7200L,
                                                                                                        0x01020304L,
                                                                                                        ticketNonce,
                                                                                                        ticket)
                                                                                 .encode());

        assertThat(decoded.ticketLifetimeSeconds(), is(7200L));
        assertThat(decoded.ticketAgeAdd(), is(0x01020304L));
        assertThat(decoded.ticketNonce(), equalTo(ticketNonce));
        assertThat(decoded.ticket(), equalTo(ticket));
    }

    @Test
    void shouldAcceptQuicEarlyDataSentinelAndRetainTicket() throws Exception {
        byte[] ticketNonce = bytes("0102");
        byte[] ticket = bytes("0a0b0c");

        QuicTlsNewSessionTicket decoded = QuicTlsNewSessionTicket.decode(newSessionTicket(
                7200L,
                0x01020304L,
                ticketNonce,
                ticket,
                List.of(QuicTlsExtension.create(QuicTlsExtensions.EARLY_DATA, uint32(0xFFFF_FFFFL)))));

        assertThat(decoded.ticketNonce(), equalTo(ticketNonce));
        assertThat(decoded.ticket(), equalTo(ticket));
    }

    @Test
    void shouldRejectInvalidEarlyDataValue() {
        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> QuicTlsNewSessionTicket.decode(newSessionTicket(
                                                             7200L,
                                                             0x01020304L,
                                                             bytes("0102"),
                                                             bytes("0a0b0c"),
                                                             List.of(QuicTlsExtension.create(
                                                                     QuicTlsExtensions.EARLY_DATA,
                                                                     uint32(16L))))));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
        assertThat(thrown.keySpace().orElseThrow(), is(QuicTLSEngine.KeySpace.ONE_RTT));
    }

    @Test
    void shouldRejectDuplicateEarlyDataExtensions() {
        ByteBuffer encodedExtensions = ByteBuffer.allocate(2 + 2 * (2 + 2 + 4));
        encodedExtensions.putShort((short) (2 * (2 + 2 + 4)));
        encodedExtensions.putShort((short) QuicTlsExtensions.EARLY_DATA);
        encodedExtensions.putShort((short) 4);
        encodedExtensions.putInt(-1);
        encodedExtensions.putShort((short) QuicTlsExtensions.EARLY_DATA);
        encodedExtensions.putShort((short) 4);
        encodedExtensions.putInt(-1);
        encodedExtensions.flip();

        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsNewSessionTicket.decode(newSessionTicket(
                        7200L,
                        0x01020304L,
                        bytes("0102"),
                        bytes("0a0b0c"),
                        encodedExtensions)));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
    }

    @Test
    void shouldRejectMalformedEarlyDataExtension() {
        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> QuicTlsNewSessionTicket.decode(newSessionTicket(
                        7200L,
                        0x01020304L,
                        bytes("0102"),
                        bytes("0a0b0c"),
                        List.of(QuicTlsExtension.create(QuicTlsExtensions.EARLY_DATA, bytes("ffffff"))))));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
    }

    @Test
    void shouldRejectTruncatedTicketMessage() {
        ByteBuffer message = newSessionTicket(7200L,
                                              0x01020304L,
                                              bytes("0102"),
                                              bytes("0a0b0c"),
                                              List.of());
        byte[] truncated = new byte[message.remaining() - 1];
        message.get(truncated);

        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> QuicTlsNewSessionTicket.decode(ByteBuffer.wrap(truncated)));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
    }

    private static ByteBuffer newSessionTicket(long ticketLifetimeSeconds,
                                               long ticketAgeAdd,
                                               byte[] ticketNonce,
                                               byte[] ticket,
                                               List<QuicTlsExtension> extensions) {
        return newSessionTicket(ticketLifetimeSeconds,
                                ticketAgeAdd,
                                ticketNonce,
                                ticket,
                                QuicTlsExtensions.encode(extensions));
    }

    private static ByteBuffer newSessionTicket(long ticketLifetimeSeconds,
                                               long ticketAgeAdd,
                                               byte[] ticketNonce,
                                               byte[] ticket,
                                               ByteBuffer encodedExtensions) {
        int bodyLength = 4 + 4 + 1 + ticketNonce.length + 2 + ticket.length + encodedExtensions.remaining();
        ByteBuffer body = ByteBuffer.allocate(bodyLength);
        body.putInt((int) ticketLifetimeSeconds);
        body.putInt((int) ticketAgeAdd);
        body.put((byte) ticketNonce.length);
        body.put(ticketNonce);
        body.putShort((short) ticket.length);
        body.put(ticket);
        body.put(encodedExtensions);
        return handshakeMessage(QuicTlsHandshakeMessages.NEW_SESSION_TICKET, body.flip());
    }

    private static ByteBuffer handshakeMessage(int type, ByteBuffer body) {
        ByteBuffer payload = body.asReadOnlyBuffer();
        ByteBuffer result = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH + payload.remaining());
        result.put((byte) type);
        result.put((byte) ((payload.remaining() >>> 16) & 0xFF));
        result.put((byte) ((payload.remaining() >>> 8) & 0xFF));
        result.put((byte) (payload.remaining() & 0xFF));
        result.put(payload);
        return result.flip();
    }

    private static byte[] uint32(long value) {
        ByteBuffer encoded = ByteBuffer.allocate(4);
        encoded.putInt((int) value);
        return encoded.array();
    }

    private static byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
