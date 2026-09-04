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

package io.helidon.quic.spi;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.quic.QuicKeyUnavailableException;
import io.helidon.quic.QuicPacketAuthenticationException;
import io.helidon.quic.QuicTLSEngine;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.QuicVersion;

/**
 * Internal ByteBuffer-based QUIC TLS packet-protection SPI.
 * <p>
 * Future JDK migration point: once {@code QuicTLSCallbacks} is public, replace this direct key access
 * with callback-derived packet-protection material from methods such as
 * {@code deriveHandshakeReadSecrets}, {@code deriveHandshakeWriteSecrets},
 * {@code deriveApplicationReadSecrets}, {@code deriveApplicationWriteSecret}, and
 * {@code cryptoError(alertCode, exception)}.
 */
@Api.Internal
public interface QuicPacketTLSEngine extends QuicTLSEngine {
    /**
     * Cast a public QUIC TLS engine to the internal packet-protection SPI.
     *
     * @param engine public engine instance
     * @return packet-protection SPI
     */
    static QuicPacketTLSEngine internal(QuicTLSEngine engine) {
        if (engine instanceof QuicPacketTLSEngine packetEngine) {
            return packetEngine;
        }
        throw new IllegalStateException("Engine does not expose internal packet-protection access: "
                                                + engine.getClass().getName());
    }

    /**
     * Provide quic_transport_parameters for inclusion in the handshake message.
     *
     * @param params encoded quic_transport_parameters
     */
    void localQuicTransportParametersBuffer(ByteBuffer params);

    /**
     * Derive initial keys for the given QUIC version and connection ID.
     *
     * @param quicVersion  QUIC protocol version
     * @param connectionId initial destination connection ID
     * @throws IllegalArgumentException if the QUIC version is not supported by this engine
     * @throws IllegalStateException    if key derivation fails
     */
    void deriveInitialKeysBuffer(QuicVersion quicVersion, ByteBuffer connectionId);

    /**
     * Compute the header protection mask for the supplied packet sample.
     *
     * @param keySpace packet key space
     * @param incoming whether the packet is incoming
     * @param sample   sampled packet bytes
     * @return header protection mask bytes
     * @throws QuicKeyUnavailableException if keys for the given key space are unavailable
     * @throws QuicTransportException      if header-protection processing exceeds cryptographic limits
     */
    ByteBuffer computeHeaderProtectionMaskBuffer(KeySpace keySpace,
                                                 boolean incoming,
                                                 ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * Compute the five-byte header protection mask packed into the low 40 bits of a {@code long}.
     * <p>
     * This compatibility implementation packs the independently owned buffer returned by
     * {@link #computeHeaderProtectionMaskBuffer(KeySpace, boolean, ByteBuffer)}. Built-in engines override this method
     * so their packet hot path does not allocate the intermediate buffer.
     *
     * @param keySpace packet key space
     * @param incoming whether the packet is incoming
     * @param sample sampled packet bytes
     * @return packed mask, with the first mask byte in bits 39 through 32
     * @throws QuicKeyUnavailableException if keys for the given key space are unavailable
     * @throws QuicTransportException if header-protection processing exceeds cryptographic limits
     */
    default long computeHeaderProtectionMaskBits(KeySpace keySpace,
                                                 boolean incoming,
                                                 ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTransportException {
        ByteBuffer mask = computeHeaderProtectionMaskBuffer(keySpace, incoming, sample);
        return ((long) mask.get() & 0xff) << 32
                | ((long) mask.get() & 0xff) << 24
                | ((long) mask.get() & 0xff) << 16
                | ((long) mask.get() & 0xff) << 8
                | (long) mask.get() & 0xff;
    }

    /**
     * Encrypt packet payload bytes into the supplied output buffer.
     *
     * @param keySpace        packet key space
     * @param packetNumber    full packet number
     * @param headerGenerator function that creates the packet header for the selected key phase
     * @param packetPayload   unencrypted packet payload
     * @param output          destination for the encrypted payload and authentication tag
     * @throws QuicKeyUnavailableException if keys are not available
     * @throws QuicTransportException      if encrypting the packet would exceed cryptographic limits
     * @throws BufferOverflowException     if the output buffer is too small
     */
    void encryptPacketBuffer(KeySpace keySpace,
                             long packetNumber,
                             IntFunction<ByteBuffer> headerGenerator,
                             ByteBuffer packetPayload,
                             ByteBuffer output)
            throws QuicKeyUnavailableException, QuicTransportException, BufferOverflowException;

    /**
     * Decrypt packet bytes into the supplied output buffer.
     *
     * @param keySpace     packet key space
     * @param packetNumber full packet number
     * @param keyPhase     key phase bit, or {@code -1} if not applicable
     * @param packet       packet header and encrypted payload
     * @param headerLength packet header length
     * @param output       destination for decrypted packet payload bytes
     * @throws IllegalArgumentException    if the key phase is invalid
     * @throws QuicKeyUnavailableException if keys are not available
     * @throws QuicPacketAuthenticationException if the packet authentication tag is invalid
     * @throws BufferOverflowException     if the output buffer is too small
     * @throws QuicTransportException      if decrypting the packet would exceed cryptographic limits
     */
    void decryptPacketBuffer(KeySpace keySpace,
                             long packetNumber,
                             int keyPhase,
                             ByteBuffer packet,
                             int headerLength,
                             ByteBuffer output)
            throws QuicKeyUnavailableException, QuicPacketAuthenticationException, QuicTransportException;

    /**
     * Sign a retry packet.
     *
     * @param version              QUIC version
     * @param originalConnectionId original destination connection ID
     * @param packet               retry packet bytes without the integrity tag
     * @param output               destination for the integrity tag
     * @throws BufferOverflowException if the output buffer is too small
     * @throws QuicTransportException if retry-packet signing fails
     */
    void signRetryPacketBuffer(QuicVersion version,
                               ByteBuffer originalConnectionId,
                               ByteBuffer packet,
                               ByteBuffer output)
            throws BufferOverflowException, QuicTransportException;

    /**
     * Verify a retry packet.
     *
     * @param version              QUIC version
     * @param originalConnectionId original destination connection ID
     * @param packet               retry packet bytes with the integrity tag
     * @throws QuicPacketAuthenticationException if the integrity tag is invalid
     * @throws QuicTransportException if retry-packet verification fails
     */
    void verifyRetryPacketBuffer(QuicVersion version,
                                 ByteBuffer originalConnectionId,
                                 ByteBuffer packet)
            throws QuicPacketAuthenticationException, QuicTransportException;

    /**
     * Produce handshake bytes for the supplied key space.
     *
     * @param keySpace key space in which the handshake bytes will be sent
     * @return handshake bytes, or {@link Optional#empty()} when no bytes are available
     * @throws IllegalStateException if handshake bytes cannot be produced
     */
    Optional<ByteBuffer> handshakeBytesBuffer(KeySpace keySpace);

    /**
     * Consume handshake bytes for the supplied key space.
     *
     * @param keySpace key space in which the handshake bytes were received
     * @param payload  handshake CRYPTO payload
     * @throws IllegalArgumentException if the key space or payload is invalid
     * @throws QuicTransportException   if the handshake fails
     */
    void consumeHandshakeBytesBuffer(KeySpace keySpace, ByteBuffer payload) throws QuicTransportException;

    @Override
    default void localQuicTransportParameters(BufferData params) {
        localQuicTransportParametersBuffer(snapshot(Objects.requireNonNull(params, "params")));
    }

    @Override
    default void deriveInitialKeys(QuicVersion quicVersion, BufferData connectionId) {
        deriveInitialKeysBuffer(quicVersion, snapshot(Objects.requireNonNull(connectionId, "connectionId")));
    }

    @Override
    default BufferData computeHeaderProtectionMask(KeySpace keySpace,
                                                   boolean incoming,
                                                   BufferData sample)
            throws QuicKeyUnavailableException, QuicTransportException {
        return bufferData(computeHeaderProtectionMaskBuffer(keySpace,
                                                            incoming,
                                                            snapshot(Objects.requireNonNull(sample, "sample"))));
    }

    @Override
    default void encryptPacket(KeySpace keySpace,
                               long packetNumber,
                               IntFunction<BufferData> headerGenerator,
                               BufferData packetPayload,
                               BufferData output)
            throws QuicKeyUnavailableException, QuicTransportException, BufferOverflowException {
        BufferData payload = Objects.requireNonNull(packetPayload, "packetPayload");
        ByteBuffer source = consume(payload);
        ByteBuffer encrypted = ByteBuffer.allocate(source.remaining() + authTagSize());
        try {
            encryptPacketBuffer(keySpace,
                                packetNumber,
                                keyPhase -> consume(Objects.requireNonNull(headerGenerator.apply(keyPhase), "header")),
                                source,
                                encrypted);
        } catch (BufferOverflowException e) {
            throw internalBufferOverflow("Encrypted packet exceeds the internal output buffer", e);
        }
        encrypted.flip();
        copyToOutput(Objects.requireNonNull(output, "output"), encrypted);
    }

    @Override
    default void decryptPacket(KeySpace keySpace,
                               long packetNumber,
                               int keyPhase,
                               BufferData packet,
                               int headerLength,
                               BufferData output)
            throws QuicKeyUnavailableException, QuicPacketAuthenticationException, QuicTransportException {
        BufferData encryptedPacket = Objects.requireNonNull(packet, "packet");
        ByteBuffer source = consume(encryptedPacket);
        int decryptedLength = Math.max(0, source.remaining() - headerLength - authTagSize());
        ByteBuffer decrypted = ByteBuffer.allocate(decryptedLength);
        try {
            decryptPacketBuffer(keySpace, packetNumber, keyPhase, source, headerLength, decrypted);
        } catch (BufferOverflowException e) {
            throw internalBufferOverflow("Decrypted packet exceeds the internal output buffer", e);
        }
        decrypted.flip();
        copyToOutput(Objects.requireNonNull(output, "output"), decrypted);
    }

    @Override
    default void signRetryPacket(QuicVersion version,
                                 BufferData originalConnectionId,
                                 BufferData packet,
                                 BufferData output)
            throws BufferOverflowException, QuicTransportException {
        ByteBuffer tag = ByteBuffer.allocate(authTagSize());
        try {
            signRetryPacketBuffer(version,
                                  snapshot(Objects.requireNonNull(originalConnectionId, "originalConnectionId")),
                                  snapshot(Objects.requireNonNull(packet, "packet")),
                                  tag);
        } catch (BufferOverflowException e) {
            throw internalBufferOverflow("Retry integrity tag exceeds the internal output buffer", e);
        }
        tag.flip();
        copyToOutput(Objects.requireNonNull(output, "output"), tag);
    }

    @Override
    default void verifyRetryPacket(QuicVersion version,
                                   BufferData originalConnectionId,
                                   BufferData packet)
            throws QuicPacketAuthenticationException, QuicTransportException {
        verifyRetryPacketBuffer(version,
                                snapshot(Objects.requireNonNull(originalConnectionId, "originalConnectionId")),
                                snapshot(Objects.requireNonNull(packet, "packet")));
    }

    @Override
    default Optional<BufferData> handshakeBytes(KeySpace keySpace) {
        return handshakeBytesBuffer(keySpace).map(QuicPacketTLSEngine::bufferData);
    }

    @Override
    default void consumeHandshakeBytes(KeySpace keySpace, BufferData payload) throws QuicTransportException {
        consumeHandshakeBytesBuffer(keySpace, consume(Objects.requireNonNull(payload, "payload")));
    }

    private static ByteBuffer snapshot(BufferData data) {
        int length = data.available();
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) data.get(i);
        }
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    private static ByteBuffer consume(BufferData data) {
        return ByteBuffer.wrap(data.readBytes()).asReadOnlyBuffer();
    }

    private static BufferData bufferData(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return BufferData.create(bytes);
    }

    private static void copyToOutput(BufferData output, ByteBuffer source) throws BufferOverflowException {
        while (source.hasRemaining()) {
            int before = source.remaining();
            int copied = output.readFrom(source);
            if (copied <= 0 || source.remaining() == before) {
                throw new BufferOverflowException();
            }
        }
    }

    private static QuicTransportException internalBufferOverflow(String message, BufferOverflowException cause) {
        return new QuicTransportException(message, 0, QuicTransportErrors.INTERNAL_ERROR.code(), cause);
    }
}
