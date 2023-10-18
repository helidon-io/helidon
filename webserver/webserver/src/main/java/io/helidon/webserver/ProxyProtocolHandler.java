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
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.Socket;
import java.util.Arrays;
import java.util.function.Supplier;

import io.helidon.http.DirectHandler;
import io.helidon.http.RequestException;

class ProxyProtocolHandler implements Supplier<ProxyProtocolData> {
    private static final System.Logger LOGGER = System.getLogger(ProxyProtocolHandler.class.getName());

    private static final int MAX_V1_FIELD_LENGTH = 40;

    static final byte[] V1_PREFIX = {
            (byte) 'P',
            (byte) 'R',
            (byte) 'O',
            (byte) 'X',
            (byte) 'Y',
    };

    static final byte[] V2_PREFIX = {
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x00,
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
            } else if (arrayEquals(prefix, V2_PREFIX, V1_PREFIX.length)) {
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
            var family = ProxyProtocolData.ProtocolFamily.valueOf(new String(buffer, 0, n));
            byte b = (byte) inputStream.read();
            if (b == (byte) '\r') {
                // special case for just UNKNOWN family
                if (family == ProxyProtocolData.ProtocolFamily.UNKNOWN) {
                    return new ProxyProtocolDataImpl(ProxyProtocolData.ProtocolFamily.UNKNOWN,
                            null, null, -1, -1);
                }
            }

            match(b, (byte) ' ');

            // source address
            n = readUntil(inputStream, buffer, (byte) ' ');
            var sourceAddress = new String(buffer, 0, n);
            match(inputStream, (byte) ' ');

            // destination address
            n = readUntil(inputStream, buffer, (byte) ' ');
            var destAddress = new String(buffer, 0, n);
            match(inputStream, (byte) ' ');

            // source port
            n = readUntil(inputStream, buffer, (byte) ' ');
            int sourcePort = Integer.parseInt(new String(buffer, 0, n));
            match(inputStream, (byte) ' ');

            // destination port
            n = readUntil(inputStream, buffer, (byte) '\r');
            int destPort = Integer.parseInt(new String(buffer, 0, n));
            match(inputStream, (byte) '\r');
            match(inputStream, (byte) '\n');

            return new ProxyProtocolDataImpl(family, sourceAddress, destAddress, sourcePort, destPort);
        } catch (IllegalArgumentException e) {
            throw BAD_PROTOCOL_EXCEPTION;
        }
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

    private static int readUntil(PushbackInputStream inputStream, byte[] buffer, byte... delims) throws IOException {
        int n = 0;
        do {
            int b = inputStream.read();
            if (b < 0) {
                throw BAD_PROTOCOL_EXCEPTION;
            }
            if (arrayContains(delims, (byte) b)) {
                inputStream.unread(b);
                return n;
            }
            buffer[n++] = (byte) b;
            if (n >= buffer.length) {
                throw BAD_PROTOCOL_EXCEPTION;
            }
        } while (true);
    }

    static ProxyProtocolData handleV2Protocol(PushbackInputStream inputStream) throws IOException {
        return null;
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

    record ProxyProtocolDataImpl(ProtocolFamily protocolFamily,
                                 String sourceAddress,
                                 String destAddress,
                                 int sourcePort,
                                 int destPort) implements ProxyProtocolData {
    }
}
