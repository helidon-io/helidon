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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTlsApplicationProtocolsTest {
    @Test
    void shouldRoundTripAsciiProtocols() throws Exception {
        ByteBuffer clientHello = QuicTlsApplicationProtocols.encodeClientHello(new String[] {"h3", "http/1.1"});

        assertThat(QuicTlsApplicationProtocols.decodeClientHello(clientHello), equalTo(List.of("h3", "http/1.1")));
        assertThat(QuicTlsApplicationProtocols.decodeServerSelection(
                QuicTlsApplicationProtocols.encodeServerSelection("h3")), is("h3"));
    }

    @Test
    void shouldPreserveOpaqueProtocolBytesAndSelectKnownProtocol() throws Exception {
        String opaqueProtocol = "\u0000\u00ff";
        ByteBuffer clientHello = ByteBuffer.wrap(new byte[] {0, 6, 2, 0, (byte) 0xFF, 2, 'h', '3'});

        List<String> decoded = QuicTlsApplicationProtocols.decodeClientHello(clientHello);

        assertThat(decoded, contains(opaqueProtocol, "h3"));
        assertThat(QuicTlsApplicationProtocols.selectServerProtocol(decoded, new String[] {"h3"}), is("h3"));
        assertThat(QuicTlsApplicationProtocols.selectServerProtocol(decoded, new String[] {opaqueProtocol}),
                   is(opaqueProtocol));
        assertThat(QuicTlsApplicationProtocols.decodeClientHello(
                QuicTlsApplicationProtocols.encodeClientHello(new String[] {opaqueProtocol})),
                   contains(opaqueProtocol));
        assertThat(QuicTlsApplicationProtocols.decodeServerSelection(
                QuicTlsApplicationProtocols.encodeServerSelection(opaqueProtocol)), is(opaqueProtocol));
    }

    @Test
    void shouldReportNoApplicationProtocolForOpaqueUnknownOffer() throws Exception {
        List<String> decoded = QuicTlsApplicationProtocols.decodeClientHello(
                ByteBuffer.wrap(new byte[] {0, 2, 1, (byte) 0xFF}));

        QuicTransportException thrown =
                assertThrows(QuicTransportException.class,
                             () -> QuicTlsApplicationProtocols.selectServerProtocol(decoded, new String[] {"h3"}));

        assertThat(thrown.errorCode(), is(0x178L));
    }

    @Test
    void shouldRejectCharactersOutsideByteRangeOnEncode() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                                                       () -> QuicTlsApplicationProtocols.encodeClientHello(
                                                               new String[] {"h3-\u20ac"}));

        assertThat(thrown.getMessage(), containsString("outside the byte range"));
    }

    @Test
    void shouldRejectMalformedEmptyAndTruncatedClientLists() {
        for (byte[] malformed : List.of(new byte[] {0, 0},
                                        new byte[] {0, 1, 0},
                                        new byte[] {0, 2, 2, 'h'})) {
            QuicTransportException thrown =
                    assertThrows(QuicTransportException.class,
                                 () -> QuicTlsApplicationProtocols.decodeClientHello(ByteBuffer.wrap(malformed)));
            assertThat(thrown.reason(), containsString("Malformed"));
        }
    }
}
