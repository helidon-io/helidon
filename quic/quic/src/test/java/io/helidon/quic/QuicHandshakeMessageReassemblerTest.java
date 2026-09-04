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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.Test;

import static io.helidon.quic.QuicTLSEngine.HandshakeState.HANDSHAKE_CONFIRMED;
import static io.helidon.quic.QuicTLSEngine.HandshakeState.NEED_RECV_CRYPTO;
import static io.helidon.quic.QuicTLSEngine.HandshakeState.NEED_SEND_CRYPTO;
import static io.helidon.quic.QuicTLSEngine.KeySpace.HANDSHAKE;
import static io.helidon.quic.QuicTLSEngine.KeySpace.INITIAL;
import static io.helidon.quic.QuicTLSEngine.KeySpace.ONE_RTT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicHandshakeMessageReassemblerTest {
    @Test
    void shouldReassemblePartialHeaderAndBody() throws Exception {
        QuicHandshakeMessageReassembler reassembler =
                new QuicHandshakeMessageReassembler(() -> NEED_RECV_CRYPTO,
                                                    QuicConfigSupport.DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE,
                                                    true);
        List<byte[]> messages = new ArrayList<>();

        reassembler.consume(INITIAL, bytes(1, 0), collectInto(messages));
        assertThat(messages, empty());

        reassembler.consume(INITIAL, bytes(0, 2, 0x11, 0x22, 2, 0, 0, 0), collectInto(messages));

        assertThat(messages, hasSize(2));
        assertThat(messages.get(0), is(new byte[] {1, 0, 0, 2, 0x11, 0x22}));
        assertThat(messages.get(1), is(new byte[] {2, 0, 0, 0}));
    }

    @Test
    void shouldRejectUnfinishedMessageInDifferentKeySpace() throws Exception {
        QuicHandshakeMessageReassembler reassembler =
                new QuicHandshakeMessageReassembler(() -> NEED_RECV_CRYPTO,
                                                    QuicConfigSupport.DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE,
                                                    true);

        reassembler.consume(INITIAL, bytes(1, 0), ignore());

        QuicTransportException ex = assertThrows(QuicTransportException.class,
                                                 () -> reassembler.consume(HANDSHAKE, bytes(8, 0, 0, 0), ignore()));

        assertThat(ex.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 10));
        assertThat(ex.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(ex.getCause().getMessage(), containsString("Unfinished message in INITIAL"));
    }

    @Test
    void shouldRejectMessageInWrongKeySpace() {
        QuicHandshakeMessageReassembler reassembler =
                new QuicHandshakeMessageReassembler(() -> NEED_RECV_CRYPTO,
                                                    QuicConfigSupport.DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE,
                                                    true);

        QuicTransportException ex = assertThrows(QuicTransportException.class,
                                                 () -> reassembler.consume(HANDSHAKE, bytes(1, 0, 0, 0), ignore()));

        assertThat(ex.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 10));
        assertThat(ex.getCause().getMessage(), containsString("received in HANDSHAKE but should be INITIAL"));
    }

    @Test
    void shouldRejectOversizedPartialMessage() {
        QuicHandshakeMessageReassembler reassembler =
                new QuicHandshakeMessageReassembler(() -> NEED_RECV_CRYPTO, 4, true);

        QuicTransportException ex = assertThrows(QuicTransportException.class,
                                                 () -> reassembler.consume(INITIAL, bytes(1, 0x00, 0x00, 0x05), ignore()));

        assertThat(ex.errorCode(), is(QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED.code()));
        assertThat(ex.reason(), containsString("maximum allowed size (4)"));
    }

    @Test
    void shouldRejectHandshakeMessagesWhenStateDoesNotExpectReceive() {
        QuicHandshakeMessageReassembler reassembler =
                new QuicHandshakeMessageReassembler(() -> NEED_SEND_CRYPTO,
                                                    QuicConfigSupport.DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE,
                                                    true);

        QuicTransportException ex = assertThrows(QuicTransportException.class,
                                                 () -> reassembler.consume(HANDSHAKE, bytes(8, 0, 0, 0), ignore()));

        assertThat(ex.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 10));
        assertThat(ex.getCause().getMessage(), containsString("state: NEED_SEND_CRYPTO"));
    }

    @Test
    void shouldAllowNewSessionTicketInOneRttAfterHandshake() throws Exception {
        AtomicReference<QuicTLSEngine.HandshakeState> handshakeState = new AtomicReference<>(HANDSHAKE_CONFIRMED);
        QuicHandshakeMessageReassembler reassembler =
                new QuicHandshakeMessageReassembler(handshakeState::get,
                                                    QuicConfigSupport.DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE,
                                                    true);
        List<byte[]> messages = new ArrayList<>();

        reassembler.consume(ONE_RTT, bytes(4, 0, 0, 0), collectInto(messages));

        assertThat(messages, hasSize(1));
        assertThat(messages.get(0), is(new byte[] {4, 0, 0, 0}));
    }

    @Test
    void shouldAllowCertificateRequestDuringClientHandshake() throws Exception {
        QuicHandshakeMessageReassembler reassembler =
                new QuicHandshakeMessageReassembler(() -> NEED_RECV_CRYPTO,
                                                    QuicConfigSupport.DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE,
                                                    true);
        List<byte[]> messages = new ArrayList<>();

        reassembler.consume(HANDSHAKE,
                            bytes(QuicTlsHandshakeMessages.CERTIFICATE_REQUEST, 0, 0, 0),
                            collectInto(messages));

        assertThat(messages, hasSize(1));
        assertThat(messages.get(0),
                   is(new byte[] {(byte) QuicTlsHandshakeMessages.CERTIFICATE_REQUEST, 0, 0, 0}));
    }

    @Test
    void shouldRejectClientPostHandshakeCertificateRequestWithProtocolViolation() {
        QuicHandshakeMessageReassembler reassembler =
                new QuicHandshakeMessageReassembler(() -> HANDSHAKE_CONFIRMED,
                                                    QuicConfigSupport.DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE,
                                                    true);

        QuicTransportException ex = assertThrows(
                QuicTransportException.class,
                () -> reassembler.consume(ONE_RTT,
                                          bytes(QuicTlsHandshakeMessages.CERTIFICATE_REQUEST),
                                          ignore()));

        assertThat(ex.errorCode(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
        assertThat(ex.keySpace().orElseThrow(), is(ONE_RTT));
        assertThat(ex.frameType(), is(0L));
    }

    @Test
    void shouldRetainTlsErrorForCertificateRequestInClientInitialSpace() {
        QuicHandshakeMessageReassembler reassembler =
                new QuicHandshakeMessageReassembler(() -> NEED_RECV_CRYPTO,
                                                    QuicConfigSupport.DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE,
                                                    true);

        QuicTransportException ex = assertThrows(
                QuicTransportException.class,
                () -> reassembler.consume(INITIAL,
                                          bytes(QuicTlsHandshakeMessages.CERTIFICATE_REQUEST, 0, 0, 0),
                                          ignore()));

        assertThat(ex.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 10));
        assertThat(ex.getCause().getMessage(), containsString("received in INITIAL but should be HANDSHAKE"));
    }

    @Test
    void shouldRetainTlsErrorForCertificateRequestReceivedByServerInOneRtt() {
        QuicHandshakeMessageReassembler reassembler =
                new QuicHandshakeMessageReassembler(() -> HANDSHAKE_CONFIRMED,
                                                    QuicConfigSupport.DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE,
                                                    false);

        QuicTransportException ex = assertThrows(
                QuicTransportException.class,
                () -> reassembler.consume(ONE_RTT,
                                          bytes(QuicTlsHandshakeMessages.CERTIFICATE_REQUEST),
                                          ignore()));

        assertThat(ex.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 10));
        assertThat(ex.getCause().getMessage(), containsString("received in ONE_RTT but should be HANDSHAKE"));
    }

    private static ByteBuffer bytes(int... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return ByteBuffer.wrap(data);
    }

    private static byte[] copy(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        byte[] copy = new byte[duplicate.remaining()];
        duplicate.get(copy);
        return copy;
    }

    private QuicHandshakeMessageReassembler.HandshakeMessageConsumer collectInto(List<byte[]> messages) {
        return (keySpace, message) -> messages.add(copy(message));
    }

    private QuicHandshakeMessageReassembler.HandshakeMessageConsumer ignore() {
        return (keySpace, message) -> {
        };
    }
}
