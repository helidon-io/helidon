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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.quic.packet.LongHeader;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacketDecoder;

/**
 * Stateless server processing that runs before connection admission and allocation.
 */
final class QuicServerIngress {
    static final int MIN_INITIAL_DATAGRAM_SIZE = QuicConfigSupport.MINIMUM_DATAGRAM_SIZE;

    private static final int LONG_HEADER_BIT = 0x80;
    private static final int FIXED_BIT = 0x40;
    private static final int INVARIANT_HEADER_SIZE = 7;
    private static final int MIN_PROTECTED_PACKET_LENGTH = 20;
    private static final int RETRY_INTEGRITY_TAG_SIZE = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final List<QuicVersion> availableVersions;
    private final int[] versionNumbers;
    private final boolean retryEnabled;
    private final QuicAddressTokenService tokenService;

    QuicServerIngress(List<QuicVersion> availableVersions,
                      boolean retryEnabled,
                      QuicAddressTokenService tokenService) {
        this.availableVersions = List.copyOf(availableVersions);
        this.versionNumbers = this.availableVersions.stream()
                .mapToInt(QuicVersion::versionNumber)
                .toArray();
        this.retryEnabled = retryEnabled;
        this.tokenService = retryEnabled ? Objects.requireNonNull(tokenService, "tokenService") : null;
    }

    Action inspect(InetSocketAddress peerAddress,
                   QuicConnectionIdFactory connectionIdFactory,
                   ByteBuffer datagram) {
        Objects.requireNonNull(peerAddress, "peerAddress");
        Objects.requireNonNull(connectionIdFactory, "connectionIdFactory");
        Objects.requireNonNull(datagram, "datagram");
        ByteBuffer packet = datagram.asReadOnlyBuffer();
        int start = packet.position();
        int limit = packet.limit();
        if (limit - start < INVARIANT_HEADER_SIZE || (packet.get(start) & LONG_HEADER_BIT) == 0) {
            return Drop.INSTANCE;
        }

        int versionNumber = packet.getInt(start + 1);
        if (versionNumber == 0) {
            return Drop.INSTANCE;
        }
        int destinationLength = Byte.toUnsignedInt(packet.get(start + 5));
        int destinationStart = start + 6;
        if (destinationLength >= limit - destinationStart) {
            return Drop.INSTANCE;
        }
        int sourceLengthOffset = destinationStart + destinationLength;
        int sourceLength = Byte.toUnsignedInt(packet.get(sourceLengthOffset));
        int sourceStart = sourceLengthOffset + 1;
        if (sourceLength > limit - sourceStart) {
            return Drop.INSTANCE;
        }
        int invariantEnd = sourceStart + sourceLength;

        QuicVersion version = null;
        for (int i = 0; i < availableVersions.size(); i++) {
            QuicVersion candidate = availableVersions.get(i);
            if (candidate.versionNumber() == versionNumber) {
                version = candidate;
                break;
            }
        }
        if (version == null) {
            if (packet.remaining() < MIN_INITIAL_DATAGRAM_SIZE) {
                return Drop.INSTANCE;
            }
            int responseSize = Math.addExact(INVARIANT_HEADER_SIZE, destinationLength);
            responseSize = Math.addExact(responseSize, sourceLength);
            responseSize = Math.addExact(responseSize, Math.multiplyExact(versionNumbers.length, Integer.BYTES));
            ByteBuffer response = ByteBuffer.allocate(responseSize);
            response.put((byte) (LONG_HEADER_BIT | FIXED_BIT));
            response.putInt(0);
            response.put((byte) sourceLength);
            response.put(packet.slice(sourceStart, sourceLength));
            response.put((byte) destinationLength);
            response.put(packet.slice(destinationStart, destinationLength));
            for (int supportedVersion : versionNumbers) {
                response.putInt(supportedVersion);
            }
            return new Respond(response.flip());
        }

        if (packet.remaining() < MIN_INITIAL_DATAGRAM_SIZE
                || QuicPacketDecoder.of(version).peekPacketType(packet) != QuicPacket.PacketType.INITIAL
                || destinationLength < 8
                || destinationLength > QuicConnectionId.MAX_CONNECTION_ID_LENGTH
                || sourceLength > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) {
            return Drop.INSTANCE;
        }

        int tokenLengthSize = VariableLengthEncoder.peekEncodedValueSize(packet, invariantEnd);
        long tokenLength = VariableLengthEncoder.peekEncodedValue(packet, invariantEnd);
        if (tokenLengthSize < 0 || tokenLength < 0 || tokenLength > limit - invariantEnd - tokenLengthSize) {
            return Drop.INSTANCE;
        }
        long packetLengthOffset = invariantEnd + tokenLengthSize + tokenLength;
        if (packetLengthOffset > Integer.MAX_VALUE) {
            return Drop.INSTANCE;
        }
        int lengthOffset = (int) packetLengthOffset;
        int packetLengthSize = VariableLengthEncoder.peekEncodedValueSize(packet, lengthOffset);
        long packetLength = VariableLengthEncoder.peekEncodedValue(packet, lengthOffset);
        if (packetLengthSize < 0
                || packetLength < MIN_PROTECTED_PACKET_LENGTH
                || packetLength > limit - lengthOffset - packetLengthSize) {
            return Drop.INSTANCE;
        }

        var longHeaderResult = QuicPacketDecoder.peekLongHeader(packet);
        if (longHeaderResult.isEmpty()) {
            return Drop.INSTANCE;
        }
        LongHeader longHeader = longHeaderResult.orElseThrow();
        ByteBuffer encodedToken = packet.slice(invariantEnd + tokenLengthSize, (int) tokenLength).asReadOnlyBuffer();
        QuicConnectionId originalDestinationId = longHeader.destinationId();
        QuicConnectionId retrySourceId = null;
        boolean addressValidated = false;
        QuicAddressTokenService.TokenKind tokenKind = QuicAddressTokenService.kind(encodedToken);
        if (tokenLength > QuicAddressTokenService.MAX_TOKEN_SIZE) {
            if (tokenKind == QuicAddressTokenService.TokenKind.RETRY || !retryEnabled) {
                return Drop.INSTANCE;
            }
            return retry(peerAddress, connectionIdFactory, version, longHeader, packet.remaining());
        }
        byte[] presentedToken = new byte[encodedToken.remaining()];
        encodedToken.get(presentedToken);
        if (presentedToken.length == 0) {
            if (retryEnabled) {
                return retry(peerAddress, connectionIdFactory, version, longHeader, packet.remaining());
            }
        } else if (!retryEnabled) {
            if (tokenKind == QuicAddressTokenService.TokenKind.RETRY) {
                return Drop.INSTANCE;
            }
        } else {
            Optional<QuicAddressTokenService.ValidatedToken> validated =
                    tokenService.validate(peerAddress,
                                          version,
                                          longHeader.destinationId(),
                                          longHeader.sourceId(),
                                          presentedToken);
            if (validated.isPresent()) {
                QuicAddressTokenService.ValidatedToken validatedToken = validated.orElseThrow();
                addressValidated = true;
                if (validatedToken.kind() == QuicAddressTokenService.TokenKind.RETRY) {
                    originalDestinationId = validatedToken.originalDestinationId();
                    retrySourceId = validatedToken.retrySourceId();
                }
            } else if (tokenKind == QuicAddressTokenService.TokenKind.RETRY) {
                return Drop.INSTANCE;
            } else {
                return retry(peerAddress, connectionIdFactory, version, longHeader, packet.remaining());
            }
        }
        return new Admit(version,
                         longHeader,
                         originalDestinationId,
                         retrySourceId,
                         presentedToken,
                         addressValidated);
    }

    private Action retry(InetSocketAddress peerAddress,
                         QuicConnectionIdFactory connectionIdFactory,
                         QuicVersion version,
                         LongHeader header,
                         int receivedSize) {
        QuicConnectionId retrySourceId = connectionIdFactory.newConnectionId();
        if (retrySourceId.equals(header.destinationId())) {
            retrySourceId = connectionIdFactory.newConnectionId();
            if (retrySourceId.equals(header.destinationId())) {
                return Drop.INSTANCE;
            }
        }
        Optional<byte[]> retryToken = tokenService.retryToken(peerAddress,
                                                               version,
                                                               header.destinationId(),
                                                               retrySourceId,
                                                               header.sourceId());
        if (retryToken.isEmpty()) {
            return Drop.INSTANCE;
        }
        byte[] token = retryToken.orElseThrow();
        int responseSize = 1 + Integer.BYTES + 2
                + header.sourceId().length()
                + retrySourceId.length()
                + token.length
                + RETRY_INTEGRITY_TAG_SIZE;
        if (responseSize > (long) receivedSize * 3) {
            return Drop.INSTANCE;
        }
        try {
            ByteBuffer response = ByteBuffer.allocate(responseSize);
            int type = version == QuicVersion.QUIC_V1 ? 0xf0 : 0xc0;
            response.put((byte) (type | RANDOM.nextInt(0x10)));
            response.putInt(version.versionNumber());
            response.put((byte) header.sourceId().length());
            response.put(header.sourceId().asReadOnlyBuffer());
            response.put((byte) retrySourceId.length());
            response.put(retrySourceId.asReadOnlyBuffer());
            response.put(token);
            ByteBuffer unsignedPacket = response.asReadOnlyBuffer().flip();
            QuicRetryIntegrity.sign(version,
                                    header.destinationId().asReadOnlyBuffer(),
                                    unsignedPacket,
                                    response);
            return new Respond(response.flip());
        } catch (RuntimeException e) {
            return Drop.INSTANCE;
        }
    }

    enum Drop implements Action {
        INSTANCE
    }

    sealed interface Action permits Admit, Respond, Drop {
    }

    record Admit(QuicVersion version,
                 LongHeader header,
                 QuicConnectionId originalDestinationId,
                 QuicConnectionId retrySourceId,
                 byte[] token,
                 boolean addressValidated) implements Action {
        Admit {
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(originalDestinationId, "originalDestinationId");
            token = Objects.requireNonNull(token, "token").clone();
        }

        @Override
        public byte[] token() {
            return token.clone();
        }
    }

    record Respond(ByteBuffer datagram) implements Action {
        Respond {
            datagram = Objects.requireNonNull(datagram, "datagram").asReadOnlyBuffer();
        }
    }
}
