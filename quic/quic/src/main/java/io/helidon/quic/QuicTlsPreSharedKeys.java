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
import java.security.MessageDigest;
import java.security.ProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.crypto.SecretKey;

final class QuicTlsPreSharedKeys {
    private static final String MESSAGE_NAME = "pre_shared_key extension";
    private static final int MIN_BINDER_LENGTH = 32;

    private QuicTlsPreSharedKeys() {
    }

    static ByteBuffer encodeClientHello(OfferedPsks offeredPsks) {
        OfferedPsks normalized = Objects.requireNonNull(offeredPsks, "offeredPsks");
        int identitiesLength = 0;
        for (PskIdentity identity : normalized.identities()) {
            identitiesLength += QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT16_LENGTH,
                                                                        identity.identity.length,
                                                                        "identity")
                    + Integer.BYTES;
        }

        int bindersLength = 0;
        for (byte[] binder : normalized.binders) {
            bindersLength += QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT8_LENGTH,
                                                                     binder.length,
                                                                     "binder");
        }

        ByteBuffer encoded = ByteBuffer.allocate(
                QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT16_LENGTH,
                                                        identitiesLength,
                                                        "identities")
                        + QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT16_LENGTH,
                                                                  bindersLength,
                                                                  "binders"));
        QuicTlsCodecSupport.putUnsigned(encoded, QuicTlsCodecSupport.UINT16_LENGTH, identitiesLength);
        for (PskIdentity identity : normalized.identities()) {
            QuicTlsCodecSupport.putVector(encoded,
                                          QuicTlsCodecSupport.UINT16_LENGTH,
                                          identity.identity,
                                          "identity");
            encoded.putInt((int) identity.obfuscatedTicketAge);
        }
        QuicTlsCodecSupport.putUnsigned(encoded, QuicTlsCodecSupport.UINT16_LENGTH, bindersLength);
        for (byte[] binder : normalized.binders) {
            QuicTlsCodecSupport.putVector(encoded, QuicTlsCodecSupport.UINT8_LENGTH, binder, "binder");
        }
        return encoded.flip();
    }

    static OfferedPsks decodeClientHello(ByteBuffer buffer) throws QuicTransportException {
        ByteBuffer data = Objects.requireNonNull(buffer, "buffer").asReadOnlyBuffer();
        ByteBuffer identitiesBuffer = QuicTlsCodecSupport.readVector(data,
                                                                     QuicTlsCodecSupport.UINT16_LENGTH,
                                                                     "identities",
                                                                     MESSAGE_NAME);
        if (!identitiesBuffer.hasRemaining()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed " + MESSAGE_NAME + ": identities");
        }

        List<PskIdentity> identities = new ArrayList<>();
        while (identitiesBuffer.hasRemaining()) {
            ByteBuffer identity = QuicTlsCodecSupport.readVector(identitiesBuffer,
                                                                 QuicTlsCodecSupport.UINT16_LENGTH,
                                                                 "identity",
                                                                 MESSAGE_NAME);
            if (!identity.hasRemaining()) {
                throw QuicTlsHandshakeMessages.decodeError("Malformed " + MESSAGE_NAME + ": empty identity");
            }
            identities.add(new PskIdentity(QuicTlsCodecSupport.copy(identity),
                                           readUint32(identitiesBuffer, "obfuscated_ticket_age")));
        }

        ByteBuffer bindersBuffer = QuicTlsCodecSupport.readVector(data,
                                                                  QuicTlsCodecSupport.UINT16_LENGTH,
                                                                  "binders",
                                                                  MESSAGE_NAME);
        if (!bindersBuffer.hasRemaining()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed " + MESSAGE_NAME + ": binders");
        }

        List<byte[]> binders = new ArrayList<>();
        while (bindersBuffer.hasRemaining()) {
            byte[] binder = QuicTlsCodecSupport.copy(QuicTlsCodecSupport.readVector(bindersBuffer,
                                                                                    QuicTlsCodecSupport.UINT8_LENGTH,
                                                                                    "binder",
                                                                                    MESSAGE_NAME));
            if (binder.length < MIN_BINDER_LENGTH) {
                throw QuicTlsHandshakeMessages.decodeError("Malformed " + MESSAGE_NAME + ": short binder");
            }
            binders.add(requireBinder(binder));
        }

        QuicTlsCodecSupport.ensureConsumed(data, MESSAGE_NAME);
        if (identities.size() != binders.size()) {
            throw QuicTlsHandshakeMessages.decodeError(
                    "Malformed " + MESSAGE_NAME + ": identities and binders have different sizes");
        }
        return new OfferedPsks(identities, binders);
    }

    static ByteBuffer encodeServerHello(int selectedIdentity) {
        ByteBuffer encoded = ByteBuffer.allocate(QuicTlsCodecSupport.UINT16_LENGTH);
        QuicTlsCodecSupport.putUnsigned(encoded, QuicTlsCodecSupport.UINT16_LENGTH, selectedIdentity);
        return encoded.flip();
    }

    static int decodeServerHello(ByteBuffer buffer) throws QuicTransportException {
        ByteBuffer data = Objects.requireNonNull(buffer, "buffer").asReadOnlyBuffer();
        int selectedIdentity = QuicTlsCodecSupport.readUnsigned(data,
                                                                QuicTlsCodecSupport.UINT16_LENGTH,
                                                                "selected_identity",
                                                                MESSAGE_NAME);
        QuicTlsCodecSupport.ensureConsumed(data, MESSAGE_NAME);
        return selectedIdentity;
    }

    static byte[] computeBinder(QuicTlsResumptionTicket resumptionTicket,
                                byte[] previousClientHello,
                                byte[] helloRetryRequest,
                                byte[] encodedClientHello)
            throws QuicTransportException {
        QuicTlsResumptionTicket ticket = Objects.requireNonNull(resumptionTicket, "resumptionTicket");
        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(ticket.cipherSuite());
        SecretKey earlySecret = secretSchedule.extractEarlySecret(ticket.resumptionPsk());
        SecretKey binderKey = secretSchedule.deriveResumptionBinderKey(earlySecret);
        SecretKey finishedKey = secretSchedule.deriveFinishedKey(binderKey);
        return secretSchedule.computeVerifyData(finishedKey,
                                                binderTranscriptHash(ticket.cipherSuite(),
                                                                     previousClientHello,
                                                                     helloRetryRequest,
                                                                     encodedClientHello));
    }

    static byte[] truncateClientHello(byte[] clientHelloBytes) throws QuicTransportException {
        byte[] encodedClientHello = Objects.requireNonNull(clientHelloBytes, "clientHelloBytes").clone();
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(encodedClientHello));
        if (clientHello.extension(QuicTlsExtensions.PRE_SHARED_KEY).isEmpty()) {
            return encodedClientHello;
        }
        if (clientHello.extensions().getLast().type() != QuicTlsExtensions.PRE_SHARED_KEY) {
            throw QuicTlsHandshakeMessages.decodeError(
                    "Malformed ClientHello message: pre_shared_key must be the last extension");
        }

        OfferedPsks offeredPsks = clientHello.offeredPreSharedKeys()
                .orElseThrow(() -> QuicTlsHandshakeMessages.decodeError(
                        "Malformed ClientHello message: missing pre_shared_key extension"));
        return Arrays.copyOf(encodedClientHello, encodedClientHello.length - offeredPsks.bindersListEncodedLength());
    }

    private static byte[] requireIdentity(byte[] identity) {
        byte[] copy = Objects.requireNonNull(identity, "identity").clone();
        if (copy.length == 0 || copy.length > 0xFFFF) {
            throw new IllegalArgumentException("PSK identity length is out of range");
        }
        return copy;
    }

    private static byte[] requireBinder(byte[] binder) {
        byte[] copy = Objects.requireNonNull(binder, "binder").clone();
        if (copy.length < MIN_BINDER_LENGTH || copy.length > 0xFF) {
            throw new IllegalArgumentException("PSK binder length is out of range");
        }
        return copy;
    }

    private static byte[] binderTranscriptHash(QuicTls13CipherSuite cipherSuite,
                                               byte[] previousClientHello,
                                               byte[] helloRetryRequest,
                                               byte[] encodedClientHello) throws QuicTransportException {
        byte[] truncatedClientHello = truncateClientHello(encodedClientHello);
        if (previousClientHello == null || helloRetryRequest == null) {
            return cipherSuite.digest(truncatedClientHello);
        }

        MessageDigest digest = cipherSuite.newDigest();
        try {
            digest.update(syntheticMessageHash(cipherSuite.digest(previousClientHello)));
            digest.update(helloRetryRequest);
            digest.update(truncatedClientHello);
            return digest.digest();
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to hash the TLS resumption binder transcript", e);
        }
    }

    private static long readUint32(ByteBuffer buffer, String fieldName) throws QuicTransportException {
        if (buffer.remaining() < Integer.BYTES) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed " + MESSAGE_NAME + ": " + fieldName);
        }
        return buffer.getInt() & 0xFFFF_FFFFL;
    }

    private static byte[] syntheticMessageHash(byte[] messageHash) {
        byte[] result = new byte[QuicTlsHandshakeMessages.HEADER_LENGTH + messageHash.length];
        QuicTlsCodecSupport.putHandshakeHeader(ByteBuffer.wrap(result), 0xFE, messageHash.length);
        System.arraycopy(messageHash, 0, result, QuicTlsHandshakeMessages.HEADER_LENGTH, messageHash.length);
        return result;
    }

    record PskIdentity(byte[] identity, long obfuscatedTicketAge) {
        PskIdentity {
            identity = requireIdentity(identity);
            if (obfuscatedTicketAge < 0 || obfuscatedTicketAge > 0xFFFF_FFFFL) {
                throw new IllegalArgumentException("obfuscatedTicketAge does not fit a 32-bit unsigned integer");
            }
        }

        @Override
        public byte[] identity() {
            return identity.clone();
        }
    }

    record OfferedPsks(List<PskIdentity> identities, List<byte[]> binders) {
        OfferedPsks {
            Objects.requireNonNull(identities, "identities");
            Objects.requireNonNull(binders, "binders");
            if (identities.isEmpty()) {
                throw new IllegalArgumentException("At least one PSK identity is required");
            }
            if (binders.isEmpty()) {
                throw new IllegalArgumentException("At least one PSK binder is required");
            }
            if (identities.size() != binders.size()) {
                throw new IllegalArgumentException("PSK identities and binders must have the same size");
            }

            List<PskIdentity> identityCopy = new ArrayList<>(identities.size());
            for (PskIdentity identity : identities) {
                identityCopy.add(Objects.requireNonNull(identity, "identity"));
            }

            List<byte[]> binderCopy = new ArrayList<>(binders.size());
            for (byte[] binder : binders) {
                binderCopy.add(requireBinder(binder));
            }

            identities = List.copyOf(identityCopy);
            binders = List.copyOf(binderCopy);
        }

        @Override
        public List<byte[]> binders() {
            List<byte[]> copy = new ArrayList<>(binders.size());
            for (byte[] binder : binders) {
                copy.add(binder.clone());
            }
            return List.copyOf(copy);
        }

        int bindersListEncodedLength() {
            int bindersLength = 0;
            for (byte[] binder : binders) {
                bindersLength += QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT8_LENGTH,
                                                                         binder.length,
                                                                         "binder");
            }
            return QuicTlsCodecSupport.encodedVectorLength(QuicTlsCodecSupport.UINT16_LENGTH,
                                                           bindersLength,
                                                           "binders");
        }
    }
}
