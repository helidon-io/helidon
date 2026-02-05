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

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.CRC32C;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;

import io.helidon.http.DirectHandler;
import io.helidon.http.RequestException;
import io.helidon.webserver.ProxyProtocolData.Family;
import io.helidon.webserver.ProxyProtocolData.Protocol;

class ProxyProtocolHandler implements Supplier<ProxyProtocolData> {
    private static final System.Logger LOGGER = System.getLogger(ProxyProtocolHandler.class.getName());

    private static final int MAX_V1_FIELD_LENGTH = 40;

    private static final InetSocketAddress UNSPECIFIED_ADDRESS;
    private static final byte[] CHECKSUM_REPLACEMENT_BYTES = {0, 0, 0, 0};

    static {
        try {
            UNSPECIFIED_ADDRESS = new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), 0);
        } catch (UnknownHostException e) {
            // UnknownHostException thrown if address is invalid length, but we pass 4 bytes for IPv4.
            throw new RuntimeException(e);
        }
    }

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

    private static RequestException badProtocolException(String messageSuffix) {
        return RequestException.builder()
            .type(DirectHandler.EventType.OTHER)
            .message("Unable to parse proxy protocol header - " + messageSuffix)
            .build();
    }

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
            return handleAnyProtocol(socket.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ProxyProtocolData handleAnyProtocol(InputStream socketInputStream) throws IOException {
        byte[] prefix = new byte[V1_PREFIX.length];
        int n = socketInputStream.read(prefix);
        if (n < V1_PREFIX.length) {
            throw BAD_PROTOCOL_EXCEPTION;
        }
        if (arrayEquals(prefix, V1_PREFIX, V1_PREFIX.length)) {
            return handleV1Protocol(socketInputStream);
        } else if (arrayEquals(prefix, V2_PREFIX_1, V2_PREFIX_1.length)) {
            return handleV2Protocol(socketInputStream);
        } else {
            throw BAD_PROTOCOL_EXCEPTION;
        }
    }

    static ProxyProtocolData handleV1Protocol(InputStream socketInputStream) throws IOException {
        final var inputStream = new PushbackInputStream(socketInputStream);
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
                if (family == Family.UNKNOWN) {
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

    static ProxyProtocolData handleV2Protocol(InputStream socketInputStream) throws IOException {
        final Checksum checksum = new DebuggingChecksum("computed", new CRC32C());
        checksum.update(V2_PREFIX_1);
        final CheckedInputStream inputStream = new CheckedInputStream(socketInputStream, checksum);

        // match rest of prefix
        matchV2RemainingPrefix(inputStream, V2_PREFIX_2);

        // only accept version 2
        int b = readNext(inputStream);
        if (b >>> 4 != 0x02) {
            throw badProtocolException(String.format("invalid proxy version bits %#04x", (b >> 4)));
        }
        var command = switch (b & 0x0F) {
            case 0x00 -> ProxyProtocolV2Data.Command.LOCAL;
            case 0x01 -> ProxyProtocolV2Data.Command.PROXY;
            default -> throw badProtocolException(String.format("unexpected V2 command bits %#04x", (b & 0x0F)));
        };

        // protocol and family
        b = readNext(inputStream);
        var family = switch (b >>> 4) {
            case 0x0 -> Family.UNKNOWN;
            case 0x1 -> Family.IPv4;
            case 0x2 -> Family.IPv6;
            case 0x3 -> Family.UNIX;
            default -> throw badProtocolException(String.format("invalid V2 family bits %#04x", (b >>> 4)));
        };
        var protocol = switch (b & 0x0F) {
            case 0x0 -> Protocol.UNKNOWN;
            case 0x1 -> Protocol.TCP;
            case 0x2 -> Protocol.UDP;
            default -> throw badProtocolException(String.format("invalid V2 transport protocol bits %#04x", (b >>> 4)));
        };

        // length
        b = readNext(inputStream);
        int headerLength = ((b << 8) & 0xFF00) | (readNext(inputStream) & 0xFF);

        // Read address bytes.
        int addressBytesLength = switch (family) {
            case UNKNOWN -> 0;
            case IPv4 -> 12;
            case IPv6 -> 36;
            case UNIX -> 216;
        };
        final byte[] addressBytes = new byte[addressBytesLength];
        int addressBytesRead = inputStream.readNBytes(addressBytes, 0, addressBytesLength);
        if (addressBytesRead != addressBytesLength) {
            throw badProtocolException("insufficient proxy address bytes present in data stream");
        }

        // decode addresses and ports
        final SocketAddress sourceSocketAddress;
        final SocketAddress destinationSocketAddress;
        int _exhaustive = switch (family) {
            case UNKNOWN -> {
                sourceSocketAddress = null;
                destinationSocketAddress = null;
                yield 0;
            }
            case IPv4 -> {
                sourceSocketAddress = new InetSocketAddress(
                    InetAddress.getByAddress(Arrays.copyOfRange(addressBytes, 0, 4)),
                    ((addressBytes[8] & 0xFF) << 8) | (addressBytes[9] & 0xFF)
                );
                destinationSocketAddress = new InetSocketAddress(
                    InetAddress.getByAddress(Arrays.copyOfRange(addressBytes, 4, 8)),
                    ((addressBytes[10] & 0xFF) << 8) | (addressBytes[11] & 0xFF)
                );
                yield 0;
            }
            case IPv6 -> {
                sourceSocketAddress = new InetSocketAddress(
                    InetAddress.getByAddress(Arrays.copyOfRange(addressBytes, 0, 16)),
                    ((addressBytes[32] & 0xFF) << 8) | (addressBytes[33] & 0xFF)
                );
                destinationSocketAddress = new InetSocketAddress(
                    InetAddress.getByAddress(Arrays.copyOfRange(addressBytes, 16, 32)),
                    ((addressBytes[34] & 0xFF) << 8) | (addressBytes[35] & 0xFF)
                );
                yield 0;
            }
            case UNIX -> {
                int sourceAddressLength = 0;
                for (int i = 0; i < 108; i++) {
                    sourceAddressLength = i;
                    if (addressBytes[i] == 0) {
                        break;
                    }
                }
                int destinationAddressLength = 0;
                for (int i = 0; i < 108; i++) {
                    destinationAddressLength = i;
                    if (addressBytes[108 + i] == 0) {
                        break;
                    }
                }
                sourceSocketAddress = UnixDomainSocketAddress.of(
                    new String(addressBytes, 0, sourceAddressLength, StandardCharsets.US_ASCII));
                destinationSocketAddress = UnixDomainSocketAddress.of(
                    new String(addressBytes, 108, destinationAddressLength, StandardCharsets.US_ASCII));
                yield 0;
            }
        };

        // Account for the consumed address bytes.
        headerLength -= addressBytesLength;

        // If the family was unspecified, then we have no way of distinguishing address bytes from TLV bytes,
        // so we cannot parse any TLVs that may be present. All we can do is skip over the rest of the proxy header.
        if (family == Family.UNKNOWN) {
            try {
                inputStream.skipNBytes(headerLength);
            } catch (EOFException e) {
                throw badProtocolException("end of data stream reached before proxy protocol header was complete");
            }
            return new ProxyProtocolV2DataImpl(family, protocol, command, null, null, List.of());
        }

        // Read the TLV records.
        final List<ProxyProtocolV2Data.TLV> tlvs;
        ProxyProtocolV2Data.TLV.Crc32c checksumTlv = null;
        if (headerLength == 0) {
            tlvs = List.of();
        } else {
            final var tlvsBuilder = new ArrayList<ProxyProtocolV2Data.TLV>();
            while (headerLength > 0) {
                final var parsedTlv = readTlv(socketInputStream, inputStream, checksum);

                if (parsedTlv.tlv instanceof ProxyProtocolV2Data.TLV.Crc32c crc) {
                    if (checksumTlv == null) {
                        checksumTlv = crc;
                    } else if (checksumTlv.checksum() != crc.checksum()) {
                        throw badProtocolException("duplicate CRC32c checksum TLVs present with non-matching checksums");
                    }
                }

                headerLength -= parsedTlv.length;
                tlvsBuilder.add(parsedTlv.tlv);
            }
            tlvs = Collections.unmodifiableList(tlvsBuilder);
        }

        // Validate checksum if requested.
        if (checksumTlv != null) {
            if (checksumTlv.checksum() != (int) checksum.getValue()) {
                throw badProtocolException("proxy header checksum mismatch");
            }
        }

        return new ProxyProtocolV2DataImpl(family, protocol, command, sourceSocketAddress, destinationSocketAddress, tlvs);
    }

    record ParsedTLV(int length, ProxyProtocolV2Data.TLV tlv) {}

    private static ParsedTLV readTlv(InputStream socketInputStream, InputStream checksumStream, Checksum checksum) throws IOException {
        int type = checksumStream.read();
        if (type == -1) {
            throw badProtocolException("end of data stream reached before TLV type could be read");
        }

        int lengthHi = checksumStream.read();
        if (lengthHi == -1) {
            throw badProtocolException("end of data stream reached before TLV length could be read");
        }

        int lengthLo = checksumStream.read();
        if (lengthLo == -1) {
            throw badProtocolException("end of data stream reached before TLV length could be read");
        }

        int length = ((lengthHi & 0xFF) << 8) | (lengthLo & 0xFF);

        // Enforce length expectations for some types.
        switch (type) {
            case ProxyProtocolV2Data.TLV.PP2_TYPE_CRC32C -> {
                if (length != 4) {
                    throw badProtocolException("PP2_TYPE_CRC32C checksum TLV length is incorrect");
                }
            }
            case ProxyProtocolV2Data.TLV.PP2_TYPE_UNIQUE_ID -> {
                if (length > 128) {
                    throw badProtocolException("PP2_TYPE_UNIQUE_ID value is too large");
                }
            }
        }

        byte[] value = new byte[length];
        // If the type is CRC32C, then we need to read directly from the underlying socket input stream
        // because the CRC32C value needs to not be included in the checksum.
        if (type == ProxyProtocolV2Data.TLV.PP2_TYPE_CRC32C) {
            if (length != socketInputStream.readNBytes(value, 0, length)) {
                throw badProtocolException("end of data stream reached before TLV value could be read");
            }
            // Substitute 0s for the checksum bytes.
            checksum.update(CHECKSUM_REPLACEMENT_BYTES);
        } else if (length != checksumStream.readNBytes(value, 0, length)) {
            throw badProtocolException("end of data stream reached before TLV value could be read");
        }

        return new ParsedTLV(3 + length, switch (type) {
            case ProxyProtocolV2Data.TLV.PP2_TYPE_ALPN -> new ProxyProtocolV2Data.TLV.Alpn(value);
            case ProxyProtocolV2Data.TLV.PP2_TYPE_AUTHORITY -> new ProxyProtocolV2Data.TLV.Authority(new String(value, StandardCharsets.UTF_8));
            case ProxyProtocolV2Data.TLV.PP2_TYPE_CRC32C -> new ProxyProtocolV2Data.TLV.Crc32c(bitsToInt32BigEndian(value, 0));
            case ProxyProtocolV2Data.TLV.PP2_TYPE_NOOP -> new ProxyProtocolV2Data.TLV.Noop(value);
            case ProxyProtocolV2Data.TLV.PP2_TYPE_UNIQUE_ID -> new ProxyProtocolV2Data.TLV.UniqueId(value);
            case ProxyProtocolV2Data.TLV.PP2_TYPE_SSL -> {
                int client = value[0];
                int verify = bitsToInt32BigEndian(value, 1);

                int remainingBytes = length - 5;
                var remainingStream = new ByteArrayInputStream(value, 5, remainingBytes);
                var subTlvs = new ArrayList<ProxyProtocolV2Data.TLV>();
                while (remainingBytes > 0) {
                    var parsedTlv = readTlv(remainingStream, remainingStream, checksum);
                    remainingBytes -= parsedTlv.length;
                    subTlvs.add(parsedTlv.tlv);
                }

                yield new ProxyProtocolV2Data.TLV.Ssl(client, verify, subTlvs);
            }
            case ProxyProtocolV2Data.TLV.PP2_SUBTYPE_SSL_VERSION -> new ProxyProtocolV2Data.TLV.SslVersion(new String(value, StandardCharsets.US_ASCII));
            case ProxyProtocolV2Data.TLV.PP2_SUBTYPE_SSL_CN -> new ProxyProtocolV2Data.TLV.SslCn(new String(value, StandardCharsets.UTF_8));
            case ProxyProtocolV2Data.TLV.PP2_SUBTYPE_SSL_CIPHER -> new ProxyProtocolV2Data.TLV.SslCipher(new String(value, StandardCharsets.US_ASCII));
            case ProxyProtocolV2Data.TLV.PP2_SUBTYPE_SSL_SIG_ALG -> new ProxyProtocolV2Data.TLV.SslSigAlg(new String(value, StandardCharsets.US_ASCII));
            case ProxyProtocolV2Data.TLV.PP2_SUBTYPE_SSL_KEY_ALG -> new ProxyProtocolV2Data.TLV.SslKeyAlg(new String(value, StandardCharsets.US_ASCII));
            case ProxyProtocolV2Data.TLV.PP2_TYPE_NETNS -> new ProxyProtocolV2Data.TLV.Netns(new String(value, StandardCharsets.US_ASCII));
            default -> new ProxyProtocolV2Data.TLV.Unregistered(type, value);
        });
    }

    static int bitsToInt32BigEndian(byte[] bits, int offset) {
        return (bits[offset] & 0xFF) << 24 | (bits[offset + 1] & 0xFF) << 16 | (bits[offset + 2] & 0xFF) << 8 | (bits[offset + 3] & 0xFF);
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

    private static void matchV2RemainingPrefix(InputStream inputStream, byte... bs) throws IOException {
        for (byte b : bs) {
            int c = inputStream.read();
            if (((byte) c) != b) {
                throw badProtocolException("invalid V2 protocol header");
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

    record ProxyProtocolV2DataImpl(Family family,
                                   Protocol protocol,
                                   Command command,
                                   SocketAddress sourceSocketAddress,
                                   SocketAddress destSocketAddress,
                                   List<TLV> tlvs) implements ProxyProtocolV2Data {

        @Override
        public SocketAddress sourceSocketAddress() {
            return sourceSocketAddress == null ? UNSPECIFIED_ADDRESS : sourceSocketAddress;
        }

        @Override
        public SocketAddress destSocketAddress() {
            return destSocketAddress == null ? UNSPECIFIED_ADDRESS : destSocketAddress;
        }

        @Override
        public String sourceAddress() {
            return switch (sourceSocketAddress) {
                case InetSocketAddress socket -> socket.getHostString();
                case UnixDomainSocketAddress unix -> unix.toString();
                case null -> "";
                // This should never happen because only the ProxyProtocolHandler code ever
                // constructs a ProxyProtocolV2DataImpl instance.
                default -> throw new IllegalStateException("Unexpected SocketAddress type: " + sourceSocketAddress.getClass().getName());
            };
        }

        @Override
        public String destAddress() {
            return switch (destSocketAddress) {
                case InetSocketAddress socket -> socket.getHostString();
                case UnixDomainSocketAddress unix -> unix.toString();
                case null -> "";
                // This should never happen because only the ProxyProtocolHandler code ever
                // constructs a ProxyProtocolV2DataImpl instance.
                default -> throw new IllegalStateException("Unexpected SocketAddress type: " + sourceSocketAddress.getClass().getName());
            };
        }

        @Override
        public int sourcePort() {
            if (sourceSocketAddress instanceof InetSocketAddress inet) {
                return inet.getPort();
            }
            return -1;
        }

        @Override
        public int destPort() {
            if (destSocketAddress instanceof InetSocketAddress inet) {
                return inet.getPort();
            }
            return -1;
        }
    }
}
