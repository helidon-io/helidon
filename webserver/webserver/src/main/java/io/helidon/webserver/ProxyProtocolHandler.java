/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.Inet6Address;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Supplier;

import io.helidon.http.DirectHandler;
import io.helidon.http.RequestException;
import io.helidon.webserver.ProxyProtocolData.Family;
import io.helidon.webserver.ProxyProtocolData.Protocol;

class ProxyProtocolHandler implements Supplier<ProxyProtocolData> {
    private static final System.Logger LOGGER = System.getLogger(ProxyProtocolHandler.class.getName());

    private static final int MAX_V1_FIELD_LENGTH = 40;
    private static final int MAX_TLV_BYTES_TO_SKIP = 128 * 4;        // 128 entries

    static final byte[] V1_PREFIX = {
            (byte) 'P',
            (byte) 'R',
            (byte) 'O',
            (byte) 'X',
            (byte) 'Y',
    };

    static final byte[] V2_PREFIX_1 = {
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x00,
    };

    static final byte[] V2_PREFIX_2 = {
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x51,
            (byte) 0x55,
            (byte) 0x49,
            (byte) 0x54,
            (byte) 0x0A
    };

    static final RequestException BAD_PROTOCOL_EXCEPTION = RequestException.builder()
            .type(DirectHandler.EventType.OTHER)
            .message("Unable to parse proxy protocol header")
            .build();

    private final Socket socket;
    private final String channelId;

    ProxyProtocolHandler(Socket socket, String channelId) {
        this.socket = socket;
        this.channelId = channelId;
    }

    @Override
    public ProxyProtocolData get() {
        LOGGER.log(Level.DEBUG, "Reading proxy protocol data for channel %s", channelId);

        try {
            byte[] prefix = new byte[V1_PREFIX.length];
            PushbackInputStream inputStream = new PushbackInputStream(socket.getInputStream(), 1);
            int n = inputStream.read(prefix);
            if (n < V1_PREFIX.length) {
                throw BAD_PROTOCOL_EXCEPTION;
            }
            if (arrayEquals(prefix, V1_PREFIX, V1_PREFIX.length)) {
                return handleV1Protocol(inputStream);
            } else if (arrayEquals(prefix, V2_PREFIX_1, V2_PREFIX_1.length)) {
                return handleV2Protocol(inputStream);
            } else {
                throw BAD_PROTOCOL_EXCEPTION;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ProxyProtocolData handleV1Protocol(PushbackInputStream inputStream) throws IOException {
        try {
            int n;
            byte[] buffer = new byte[MAX_V1_FIELD_LENGTH];

            match(inputStream, (byte) ' ');

            // protocol and family
            n = readUntil(inputStream, buffer, (byte) ' ', (byte) '\r');
            String familyProtocol = new String(buffer, 0, n, StandardCharsets.US_ASCII);
            var family = Family.fromString(familyProtocol);
            var protocol = Protocol.fromString(familyProtocol);
            byte b = readNext(inputStream);
            if (b == (byte) '\r') {
                // special case for just UNKNOWN family
                if (family == ProxyProtocolData.Family.UNKNOWN) {
                    return new ProxyProtocolDataImpl(Family.UNKNOWN, Protocol.UNKNOWN,
                            "", "", -1, -1);
                }
            }

            match(b, (byte) ' ');

            // source address
            n = readUntil(inputStream, buffer, (byte) ' ');
            var sourceAddress = new String(buffer, 0, n, StandardCharsets.US_ASCII);
            match(inputStream, (byte) ' ');

            // destination address
            n = readUntil(inputStream, buffer, (byte) ' ');
            var destAddress = new String(buffer, 0, n, StandardCharsets.US_ASCII);
            match(inputStream, (byte) ' ');

            // source port
            n = readUntil(inputStream, buffer, (byte) ' ');
            int sourcePort = Integer.parseInt(new String(buffer, 0, n, StandardCharsets.US_ASCII));
            match(inputStream, (byte) ' ');

            // destination port
            n = readUntil(inputStream, buffer, (byte) '\r');
            int destPort = Integer.parseInt(new String(buffer, 0, n, StandardCharsets.US_ASCII));
            match(inputStream, (byte) '\r');
            match(inputStream, (byte) '\n');

            return new ProxyProtocolDataImpl(family, protocol, sourceAddress, destAddress, sourcePort, destPort);
        } catch (IllegalArgumentException e) {
            throw BAD_PROTOCOL_EXCEPTION;
        }
    }

    static ProxyProtocolData handleV2Protocol(PushbackInputStream inputStream) throws IOException {
        // match rest of prefix
        match(inputStream, V2_PREFIX_2);

        // only accept version 2, ignore LOCAL/PROXY
        int b = readNext(inputStream);
        if (b >>> 4 != 0x02) {
            throw BAD_PROTOCOL_EXCEPTION;
        }

        // protocol and family
        b = readNext(inputStream);
        var family = switch (b >>> 4) {
            case 0x1 -> Family.IPv4;
            case 0x2 -> Family.IPv6;
            case 0x3 -> Family.UNIX;
            default -> Family.UNKNOWN;
        };
        var protocol = switch (b & 0x0F) {
            case 0x1 -> Protocol.TCP;
            case 0x2 -> Protocol.UDP;
            default -> Protocol.UNKNOWN;
        };

        // length
        b = readNext(inputStream);
        int headerLength = ((b << 8) & 0xFF00) | (readNext(inputStream) & 0xFF);

        // decode addresses and ports
        String sourceAddress = "";
        String destAddress = "";
        int sourcePort = -1;
        int destPort = -1;
        switch (family) {
            case IPv4 -> {
                byte[] buffer = new byte[12];
                int n = inputStream.read(buffer, 0, buffer.length);
                if (n < buffer.length) {
                    throw BAD_PROTOCOL_EXCEPTION;
                }
                sourceAddress = (buffer[0] & 0xFF)
                        + "." + (buffer[1] & 0xFF)
                        + "." + (buffer[2] & 0xFF)
                        + "." + (buffer[3] & 0xFF);
                destAddress = (buffer[4] & 0xFF)
                        + "." + (buffer[5] & 0xFF)
                        + "." + (buffer[6] & 0xFF)
                        + "." + (buffer[7] & 0xFF);
                sourcePort = buffer[9] & 0xFF
                        | ((buffer[8] << 8) & 0xFF00);
                destPort = buffer[11] & 0xFF
                        | ((buffer[10] << 8) & 0xFF00);
                headerLength -= buffer.length;
            }
            case IPv6 -> {
                byte[] buffer = new byte[16];
                int n = inputStream.read(buffer, 0, buffer.length);
                if (n < buffer.length) {
                    throw BAD_PROTOCOL_EXCEPTION;
                }
                sourceAddress = Inet6Address.getByAddress(buffer).getHostAddress();
                n = inputStream.read(buffer, 0, buffer.length);
                if (n < buffer.length) {
                    throw BAD_PROTOCOL_EXCEPTION;
                }
                destAddress = Inet6Address.getByAddress(buffer).getHostAddress();
                n = inputStream.read(buffer, 0, 4);
                if (n < 4) {
                    throw BAD_PROTOCOL_EXCEPTION;
                }
                sourcePort = buffer[1] & 0xFF
                        | ((buffer[0] << 8) & 0xFF00);
                destPort = buffer[3] & 0xFF
                        | ((buffer[2] << 8) & 0xFF00);
                headerLength -= 2 * buffer.length + 4;
            }
            case UNIX -> {
                byte[] buffer = new byte[216];
                int n = inputStream.read(buffer, 0, buffer.length);
                if (n < buffer.length) {
                    throw BAD_PROTOCOL_EXCEPTION;
                }
                sourceAddress = new String(buffer, 0, 108, StandardCharsets.US_ASCII);
                destAddress = new String(buffer, 108, buffer.length, StandardCharsets.US_ASCII);
                headerLength -= buffer.length;
            }
            default -> {
                // falls through
            }
        }

        // skip any TLV vectors up to our max for security reasons
        if (headerLength > MAX_TLV_BYTES_TO_SKIP) {
            throw BAD_PROTOCOL_EXCEPTION;
        }
        while (headerLength > 0) {
            headerLength -= (int) inputStream.skip(headerLength);
        }

        return new ProxyProtocolDataImpl(family, protocol, sourceAddress, destAddress,
                sourcePort, destPort);
    }

    private static byte readNext(InputStream inputStream) throws IOException {
        int b = inputStream.read();
        if (b < 0) {
            throw BAD_PROTOCOL_EXCEPTION;
        }
        return (byte) b;
    }

    private static void match(byte a, byte b) {
        if (a != b) {
            throw BAD_PROTOCOL_EXCEPTION;
        }
    }

    private static void match(PushbackInputStream inputStream, byte b) throws IOException {
        if (inputStream.read() != b) {
            throw BAD_PROTOCOL_EXCEPTION;
        }
    }

    private static void match(PushbackInputStream inputStream, byte... bs) throws IOException {
        for (byte b : bs) {
            int c = inputStream.read();
            if (((byte) c) != b) {
                throw BAD_PROTOCOL_EXCEPTION;
            }
        }
    }

    private static int readUntil(PushbackInputStream inputStream, byte[] buffer, byte... delims) throws IOException {
        int n = 0;
        do {
            byte b = readNext(inputStream);
            if (arrayContains(delims, b)) {
                inputStream.unread(b);
                return n;
            }
            buffer[n++] = b;
            if (n >= buffer.length) {
                throw BAD_PROTOCOL_EXCEPTION;
            }
        } while (true);
    }

    private static boolean arrayEquals(byte[] array1, byte[] array2, int prefix) {
        return Arrays.equals(array1, 0, prefix, array2, 0, prefix);
    }

    private static boolean arrayContains(byte[] array, byte b) {
        for (byte a : array) {
            if (a == b) {
                return true;
            }
        }
        return false;
    }

    record ProxyProtocolDataImpl(Family family,
                                 Protocol protocol,
                                 String sourceAddress,
                                 String destAddress,
                                 int sourcePort,
                                 int destPort) implements ProxyProtocolData {
    }
}
