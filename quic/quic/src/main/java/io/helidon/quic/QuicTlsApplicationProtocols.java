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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class QuicTlsApplicationProtocols {
    // RFC 7301 protocol_name values are opaque byte strings. ISO-8859-1 preserves the one-to-one byte mapping used by
    // JSSE's String-based ALPN API without interpreting or normalizing protocol identifiers.
    private static final Charset ALPN_CHARSET = StandardCharsets.ISO_8859_1;

    private QuicTlsApplicationProtocols() {
    }

    static ByteBuffer encodeClientHello(String[] applicationProtocols) {
        Objects.requireNonNull(applicationProtocols, "applicationProtocols");
        int listLength = 0;
        byte[][] encodedProtocols = new byte[applicationProtocols.length][];
        for (int i = 0; i < applicationProtocols.length; i++) {
            String protocol = Objects.requireNonNull(applicationProtocols[i], "applicationProtocol");
            byte[] encodedProtocol = protocolBytes(protocol);
            if (encodedProtocol.length == 0 || encodedProtocol.length > 0xFF) {
                throw new IllegalArgumentException("Invalid ALPN protocol length: " + protocol);
            }
            encodedProtocols[i] = encodedProtocol;
            listLength += 1 + encodedProtocol.length;
        }
        if (listLength == 0 || listLength > 0xFFFF) {
            throw new IllegalArgumentException("Invalid ALPN protocol list length: " + listLength);
        }

        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsCodecSupport.UINT16_LENGTH + listLength);
        encoded.putShort((short) listLength);
        for (byte[] encodedProtocol : encodedProtocols) {
            encoded.put((byte) encodedProtocol.length);
            encoded.put(encodedProtocol);
        }
        return encoded.flip();
    }

    static List<String> decodeClientHello(ByteBuffer extensionData) throws QuicTransportException {
        ByteBuffer buffer = Objects.requireNonNull(extensionData, "extensionData").slice();
        if (buffer.remaining() < 3) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed application_layer_protocol_negotiation extension");
        }

        int listLength = buffer.getShort() & 0xFFFF;
        if (listLength < 2 || listLength != buffer.remaining()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed application_layer_protocol_negotiation extension");
        }

        List<String> protocols = new ArrayList<>();
        while (buffer.hasRemaining()) {
            int protocolLength = buffer.get() & 0xFF;
            if (protocolLength == 0 || protocolLength > buffer.remaining()) {
                throw QuicTlsHandshakeMessages.decodeError("Malformed application_layer_protocol_negotiation extension");
            }
            byte[] encoded = new byte[protocolLength];
            buffer.get(encoded);
            protocols.add(protocolString(encoded));
        }
        if (protocols.isEmpty()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed application_layer_protocol_negotiation extension");
        }
        return List.copyOf(protocols);
    }

    static ByteBuffer encodeServerSelection(String applicationProtocol) {
        String protocol = Objects.requireNonNull(applicationProtocol, "applicationProtocol");
        byte[] protocolBytes = protocolBytes(protocol);
        if (protocolBytes.length == 0 || protocolBytes.length > 0xFF) {
            throw new IllegalArgumentException("Invalid ALPN protocol length: " + protocol);
        }

        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsCodecSupport.UINT16_LENGTH + 1 + protocolBytes.length);
        encoded.putShort((short) (1 + protocolBytes.length));
        encoded.put((byte) protocolBytes.length);
        encoded.put(protocolBytes);
        return encoded.flip();
    }

    static String decodeServerSelection(ByteBuffer extensionData) throws QuicTransportException {
        ByteBuffer buffer = Objects.requireNonNull(extensionData, "extensionData").slice();
        if (buffer.remaining() < 3) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed application_layer_protocol_negotiation extension");
        }

        int listLength = buffer.getShort() & 0xFFFF;
        if (listLength < 2 || listLength != buffer.remaining()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed application_layer_protocol_negotiation extension");
        }

        int protocolLength = buffer.get() & 0xFF;
        if (protocolLength == 0 || protocolLength != buffer.remaining()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed application_layer_protocol_negotiation extension");
        }

        byte[] encoded = new byte[protocolLength];
        buffer.get(encoded);
        return protocolString(encoded);
    }

    static String selectServerProtocol(List<String> clientProtocols, String[] serverProtocols) throws QuicTransportException {
        Objects.requireNonNull(clientProtocols, "clientProtocols");
        Objects.requireNonNull(serverProtocols, "serverProtocols");
        if (clientProtocols.isEmpty() || serverProtocols.length == 0) {
            throw QuicTlsHandshakeMessages.noApplicationProtocol("No matching application layer protocol values");
        }

        for (String serverProtocol : serverProtocols) {
            if (clientProtocols.contains(serverProtocol)) {
                return serverProtocol;
            }
        }
        throw QuicTlsHandshakeMessages.noApplicationProtocol("No matching application layer protocol values");
    }

    private static byte[] protocolBytes(String protocol) {
        for (int i = 0; i < protocol.length(); i++) {
            if (protocol.charAt(i) > 0xFF) {
                throw new IllegalArgumentException("ALPN protocol contains a character outside the byte range: "
                                                           + protocol);
            }
        }
        return protocol.getBytes(ALPN_CHARSET);
    }

    private static String protocolString(byte[] encoded) {
        return new String(encoded, ALPN_CHARSET);
    }
}
