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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;

import javax.crypto.SecretKey;

final class QuicOneRttTrafficKeys {
    private static final long NO_PACKET_NUMBER = -1;
    // The JDK's QUIC engine begins a proactive update at roughly 80% of the confidentiality budget, which gives both
    // endpoints time to exchange an updated key phase before the hard AEAD limit is reached.
    private static final double CONFIDENTIALITY_UPDATE_THRESHOLD = 0.8d;

    private final QuicVersion version;
    private final QuicTls13CipherSuite cipherSuite;
    private final QuicTls13SecretSchedule secretSchedule;
    private final long aesGcmConfidentialityLimit;
    private final long chacha20Poly1305ConfidentialityLimit;
    private final Lock keySeriesLock = new ReentrantLock();
    // RFC 9001 Section 6.6 applies the integrity budget across all failed decryptions for the connection, not per key
    // phase, so the counter lives on the manager instead of on individual read-key objects.
    private final AtomicLong invalidPackets = new AtomicLong();

    private volatile KeySeries keySeries;
    private volatile boolean keysDiscarded;
    private volatile QuicOneRttContext oneRttContext;

    private QuicOneRttTrafficKeys(QuicVersion version,
                                  QuicTls13CipherSuite cipherSuite,
                                  QuicTls13SecretSchedule secretSchedule,
                                  KeySeries keySeries,
                                  long aesGcmConfidentialityLimit,
                                  long chacha20Poly1305ConfidentialityLimit) {
        this.version = Objects.requireNonNull(version, "version");
        this.cipherSuite = Objects.requireNonNull(cipherSuite, "cipherSuite");
        this.secretSchedule = Objects.requireNonNull(secretSchedule, "secretSchedule");
        this.keySeries = Objects.requireNonNull(keySeries, "keySeries");
        this.aesGcmConfidentialityLimit = aesGcmConfidentialityLimit;
        this.chacha20Poly1305ConfidentialityLimit = chacha20Poly1305ConfidentialityLimit;
    }

    static QuicOneRttTrafficKeys create(QuicVersion version,
                                        QuicTls13CipherSuite cipherSuite,
                                        SecretKey clientTrafficSecret,
                                        SecretKey serverTrafficSecret,
                                        boolean clientMode) {
        return create(version,
                      cipherSuite,
                      clientTrafficSecret,
                      serverTrafficSecret,
                      clientMode,
                      QuicAeadLimits.DEFAULT_AES_GCM_CONFIDENTIALITY_LIMIT,
                      QuicAeadLimits.DEFAULT_CHACHA20_POLY1305_CONFIDENTIALITY_LIMIT);
    }

    static QuicOneRttTrafficKeys create(QuicVersion version,
                                        QuicTls13CipherSuite cipherSuite,
                                        SecretKey clientTrafficSecret,
                                        SecretKey serverTrafficSecret,
                                        boolean clientMode,
                                        long aesGcmConfidentialityLimit,
                                        long chacha20Poly1305ConfidentialityLimit) {
        Objects.requireNonNull(clientTrafficSecret, "clientTrafficSecret");
        Objects.requireNonNull(serverTrafficSecret, "serverTrafficSecret");

        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(cipherSuite);
        ReadKeys readKeys = ReadKeys.create(version,
                                            cipherSuite,
                                            clientMode ? serverTrafficSecret : clientTrafficSecret,
                                            0,
                                            aesGcmConfidentialityLimit,
                                            chacha20Poly1305ConfidentialityLimit);
        WriteKeys writeKeys = WriteKeys.create(version,
                                               cipherSuite,
                                               clientMode ? clientTrafficSecret : serverTrafficSecret,
                                               0,
                                               aesGcmConfidentialityLimit,
                                               chacha20Poly1305ConfidentialityLimit);
        TrafficKeyPair current = new TrafficKeyPair(readKeys, writeKeys);
        TrafficKeyPair next = new TrafficKeyPair(readKeys.next(version,
                                                               cipherSuite,
                                                               secretSchedule,
                                                               aesGcmConfidentialityLimit,
                                                               chacha20Poly1305ConfidentialityLimit),
                                                 writeKeys.next(version,
                                                                cipherSuite,
                                                                secretSchedule,
                                                                aesGcmConfidentialityLimit,
                                                                chacha20Poly1305ConfidentialityLimit));
        return new QuicOneRttTrafficKeys(version,
                                         cipherSuite,
                                         secretSchedule,
                                         new KeySeries(null, current, next),
                                         aesGcmConfidentialityLimit,
                                         chacha20Poly1305ConfidentialityLimit);
    }

    boolean keysAvailable() {
        return keySeries != null && !keysDiscarded;
    }

    void discard() {
        keysDiscarded = true;
        keySeriesLock.lock();
        try {
            keySeries = null;
        } finally {
            keySeriesLock.unlock();
        }
    }

    int headerProtectionSampleSize() {
        return QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE;
    }

    void oneRttContext(QuicOneRttContext ctx) {
        oneRttContext = Objects.requireNonNull(ctx, "ctx");
    }

    ByteBuffer computeHeaderProtectionMask(boolean incoming, ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTransportException {
        TrafficKeyState state = incoming ? requireKeySeries().current.readKeys : requireKeySeries().current.writeKeys;
        return state.computeHeaderProtectionMask(sample);
    }

    long computeHeaderProtectionMaskBits(boolean incoming, ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTransportException {
        TrafficKeyState state = incoming ? requireKeySeries().current.readKeys : requireKeySeries().current.writeKeys;
        return state.computeHeaderProtectionMaskBits(sample);
    }

    void encryptPacket(long packetNumber,
                       IntFunction<ByteBuffer> headerGenerator,
                       ByteBuffer packetPayload,
                       ByteBuffer output)
            throws QuicKeyUnavailableException, QuicTransportException {
        KeySeries currentSeries = requireKeySeries();
        if (currentSeries.next == null) {
            currentSeries = generateNextKeys(currentSeries);
        }
        maybeInitiateKeyUpdate(currentSeries);

        WriteKeys writeKeys = requireKeySeries().current.writeKeys;
        ByteBuffer header = Objects.requireNonNull(headerGenerator, "headerGenerator").apply(writeKeys.keyPhase());
        writeKeys.encryptPacket(packetNumber, header, packetPayload, output);
    }

    void decryptPacket(long packetNumber,
                       int keyPhase,
                       ByteBuffer packet,
                       int headerLength,
                       ByteBuffer output)
            throws QuicKeyUnavailableException, QuicPacketAuthenticationException, QuicTransportException {
        if (keyPhase != 0 && keyPhase != 1) {
            throw new IllegalArgumentException("Unexpected key phase value: " + keyPhase);
        }

        KeySeries series = requireKeySeries();
        int currentKeyPhase = series.current.writeKeys.keyPhase();
        if (keyPhase == currentKeyPhase) {
            decryptWithCurrentKeys(series.current.readKeys, packetNumber, packet, headerLength, output);
            return;
        }

        if (series.canUseOldDecryptKey(packetNumber)) {
            decryptWithOldKeys(series, packetNumber, packet, headerLength, output);
            return;
        }

        decryptUsingNextKeys(series, packetNumber, packet, headerLength, output);
    }

    private static int nextKeyPhase(int keyPhase) {
        return keyPhase == 0 ? 1 : 0;
    }

    private static void updateLowestPacketNumber(AtomicLong field, long packetNumber) {
        boolean updated;
        do {
            long current = field.get();
            long next = current == NO_PACKET_NUMBER ? packetNumber : Math.min(current, packetNumber);
            updated = field.compareAndSet(current, next);
        } while (!updated);
    }

    private void decryptWithCurrentKeys(ReadKeys currentReadKeys,
                                        long packetNumber,
                                        ByteBuffer packet,
                                        int headerLength,
                                        ByteBuffer output)
            throws QuicPacketAuthenticationException, QuicTransportException {
        try {
            currentReadKeys.decryptPacket(packetNumber, packet, headerLength, output);
        } catch (QuicPacketAuthenticationException e) {
            countInvalidPacket(currentReadKeys.integrityLimit());
            throw e;
        }
    }

    private void decryptWithOldKeys(KeySeries series,
                                    long packetNumber,
                                    ByteBuffer packet,
                                    int headerLength,
                                    ByteBuffer output)
            throws QuicPacketAuthenticationException, QuicTransportException {
        ReadKeys oldReadKeys = series.old;

        try {
            oldReadKeys.decryptPacket(packetNumber, packet, headerLength, output);
        } catch (QuicPacketAuthenticationException e) {
            countInvalidPacket(oldReadKeys.integrityLimit());
            throw e;
        }

        if (!series.current.usedByBothEndpoints()
                && series.current.writeKeys.hasEncryptedAny()
                && oneRttContext().largestPeerAcknowledgedPacketNumber()
                        >= series.current.writeKeys.lowestEncryptedPacketNumber()) {
            throw new QuicTransportException("peer used incorrect key, was expected to use updated key",
                                             QuicTLSEngine.KeySpace.ONE_RTT,
                                             0,
                                             QuicTransportErrors.KEY_UPDATE_ERROR);
        }
    }

    private void decryptUsingNextKeys(KeySeries currentSeries,
                                      long packetNumber,
                                      ByteBuffer packet,
                                      int headerLength,
                                      ByteBuffer output)
            throws QuicKeyUnavailableException, QuicPacketAuthenticationException, QuicTransportException {
        if (currentSeries.next == null) {
            throw new QuicKeyUnavailableException("next keys unavailable to handle key update",
                                                  QuicTLSEngine.KeySpace.ONE_RTT);
        }

        try {
            currentSeries.next.readKeys.decryptPacket(packetNumber, packet, headerLength, output);
        } catch (QuicPacketAuthenticationException e) {
            countInvalidPacket(currentSeries.next.readKeys.integrityLimit());
            throw e;
        }

        rolloverKeys(currentSeries);
    }

    private void maybeInitiateKeyUpdate(KeySeries currentSeries) throws QuicTransportException {
        WriteKeys writeKeys = currentSeries.current.writeKeys;
        long confidentialityLimit = writeKeys.confidentialityLimit();
        if (confidentialityLimit < 0) {
            return;
        }

        long updateThreshold = (long) Math.ceil(confidentialityLimit * CONFIDENTIALITY_UPDATE_THRESHOLD);
        if (writeKeys.numEncrypted() < updateThreshold) {
            return;
        }
        if (!currentSeries.current.usedByBothEndpoints()) {
            return;
        }

        rolloverKeys(currentSeries);
    }

    private KeySeries generateNextKeys(KeySeries currentSeries) {
        keySeriesLock.lock();
        try {
            if (keySeries != currentSeries) {
                if (keySeries == null) {
                    throw new IllegalStateException("1-RTT keys discarded while generating next keys");
                }
                return keySeries;
            }
            TrafficKeyPair next = new TrafficKeyPair(currentSeries.current.readKeys.next(version,
                                                                                         cipherSuite,
                                                                                         secretSchedule,
                                                                                         aesGcmConfidentialityLimit,
                                                                                         chacha20Poly1305ConfidentialityLimit),
                                                     currentSeries.current.writeKeys.next(version,
                                                                                          cipherSuite,
                                                                                          secretSchedule,
                                                                                          aesGcmConfidentialityLimit,
                                                                                          chacha20Poly1305ConfidentialityLimit));
            KeySeries newSeries = new KeySeries(currentSeries.old, currentSeries.current, next);
            keySeries = newSeries;
            return newSeries;
        } finally {
            keySeriesLock.unlock();
        }
    }

    private KeySeries rolloverKeys(KeySeries currentSeries) {
        keySeriesLock.lock();
        try {
            if (keySeries != currentSeries) {
                if (keySeries == null) {
                    throw new IllegalStateException("1-RTT keys discarded while rolling over keys");
                }
                return keySeries;
            }
            if (currentSeries.next == null) {
                throw new IllegalStateException("Current key series is missing next keys");
            }
            KeySeries newSeries = new KeySeries(currentSeries.current.readKeys, currentSeries.next, null);
            keySeries = newSeries;
            return newSeries;
        } finally {
            keySeriesLock.unlock();
        }
    }

    private void countInvalidPacket(long integrityLimit) throws QuicTransportException {
        if (invalidPackets.incrementAndGet() >= integrityLimit) {
            throw new QuicTransportException("Integrity limit reached",
                                             QuicTLSEngine.KeySpace.ONE_RTT,
                                             0,
                                             QuicTransportErrors.AEAD_LIMIT_REACHED);
        }
    }

    private QuicOneRttContext oneRttContext() {
        QuicOneRttContext ctx = oneRttContext;
        if (ctx == null) {
            throw new IllegalStateException("1-RTT context not set");
        }
        return ctx;
    }

    private KeySeries requireKeySeries() throws QuicKeyUnavailableException {
        KeySeries series = keySeries;
        if (series != null) {
            return series;
        }
        throw new QuicKeyUnavailableException(keysDiscarded ? "Keys have been discarded" : "Keys not available",
                                              QuicTLSEngine.KeySpace.ONE_RTT);
    }

    private sealed interface TrafficKeyState permits ReadKeys, WriteKeys {
        ByteBuffer computeHeaderProtectionMask(ByteBuffer sample) throws QuicTransportException;

        long computeHeaderProtectionMaskBits(ByteBuffer sample) throws QuicTransportException;
    }

    private record KeySeries(ReadKeys old, TrafficKeyPair current, TrafficKeyPair next) {
        private KeySeries {
            Objects.requireNonNull(current, "current");
            if (old != null && old.keyPhase() == current.keyPhase()) {
                throw new IllegalArgumentException("Old read keys and current keys must use different key phases");
            }
            if (next != null && next.keyPhase() == current.keyPhase()) {
                throw new IllegalArgumentException("Current keys and next keys must use different key phases");
            }
        }

        private boolean canUseOldDecryptKey(long packetNumber) {
            if (old == null) {
                return false;
            }
            long lowestDecryptedPacketNumber = current.readKeys.lowestDecryptedPacketNumber();
            if (lowestDecryptedPacketNumber == NO_PACKET_NUMBER) {
                return true;
            }
            return packetNumber < lowestDecryptedPacketNumber;
        }
    }

    private record TrafficKeyPair(ReadKeys readKeys, WriteKeys writeKeys) {
        private TrafficKeyPair {
            Objects.requireNonNull(readKeys, "readKeys");
            Objects.requireNonNull(writeKeys, "writeKeys");
            if (readKeys.keyPhase() != writeKeys.keyPhase()) {
                throw new IllegalArgumentException("Read and write keys must use the same key phase");
            }
        }

        private boolean usedByBothEndpoints() {
            return readKeys.hasDecryptedAny() && writeKeys.hasEncryptedAny();
        }

        private int keyPhase() {
            return writeKeys.keyPhase();
        }
    }

    private static final class ReadKeys implements TrafficKeyState {
        private final SecretKey trafficSecret;
        private final QuicPacketProtectionKeys packetProtectionKeys;
        private final QuicPacketProtection protection;
        private final int keyPhase;
        private final AtomicLong lowestDecryptedPacketNumber = new AtomicLong(NO_PACKET_NUMBER);

        private ReadKeys(SecretKey trafficSecret,
                         QuicPacketProtectionKeys packetProtectionKeys,
                         QuicPacketProtection protection,
                         int keyPhase) {
            this.trafficSecret = Objects.requireNonNull(trafficSecret, "trafficSecret");
            this.packetProtectionKeys = Objects.requireNonNull(packetProtectionKeys, "packetProtectionKeys");
            this.protection = Objects.requireNonNull(protection, "protection");
            this.keyPhase = keyPhase;
        }

        @Override
        public ByteBuffer computeHeaderProtectionMask(ByteBuffer sample) throws QuicTransportException {
            return protection.computeHeaderProtectionMask(sample);
        }

        @Override
        public long computeHeaderProtectionMaskBits(ByteBuffer sample) throws QuicTransportException {
            return protection.computeHeaderProtectionMaskBits(sample);
        }

        private static ReadKeys create(QuicVersion version,
                                       QuicTls13CipherSuite cipherSuite,
                                       SecretKey trafficSecret,
                                       int keyPhase,
                                       long aesGcmConfidentialityLimit,
                                       long chacha20Poly1305ConfidentialityLimit) {
            QuicPacketProtectionKeys keys = QuicPacketProtectionKeys.derive(version, cipherSuite, trafficSecret);
            return new ReadKeys(trafficSecret,
                                keys,
                                QuicPacketProtection.create(cipherSuite,
                                                            keys,
                                                            aesGcmConfidentialityLimit,
                                                            chacha20Poly1305ConfidentialityLimit),
                                keyPhase);
        }

        private ReadKeys next(QuicVersion version,
                              QuicTls13CipherSuite cipherSuite,
                              QuicTls13SecretSchedule secretSchedule,
                              long aesGcmConfidentialityLimit,
                              long chacha20Poly1305ConfidentialityLimit) {
            SecretKey nextTrafficSecret = secretSchedule.deriveNextPacketProtectionSecret(version, trafficSecret);
            QuicPacketProtectionKeys keys = QuicPacketProtectionKeys.derive(version,
                                                                            cipherSuite,
                                                                            nextTrafficSecret,
                                                                            packetProtectionKeys.headerProtectionKey());
            return new ReadKeys(nextTrafficSecret,
                                keys,
                                QuicPacketProtection.create(cipherSuite,
                                                            keys,
                                                            aesGcmConfidentialityLimit,
                                                            chacha20Poly1305ConfidentialityLimit),
                                nextKeyPhase(keyPhase));
        }

        private void decryptPacket(long packetNumber,
                                   ByteBuffer packet,
                                   int headerLength,
                                   ByteBuffer output)
                throws QuicPacketAuthenticationException, QuicTransportException {
            protection.decryptPacket(packetNumber, packet, headerLength, output);
            updateLowestPacketNumber(lowestDecryptedPacketNumber, packetNumber);
        }

        private boolean hasDecryptedAny() {
            return lowestDecryptedPacketNumber.get() != NO_PACKET_NUMBER;
        }

        private long lowestDecryptedPacketNumber() {
            return lowestDecryptedPacketNumber.get();
        }

        private long integrityLimit() {
            return protection.integrityLimit();
        }

        private int keyPhase() {
            return keyPhase;
        }
    }

    private static final class WriteKeys implements TrafficKeyState {
        private final SecretKey trafficSecret;
        private final QuicPacketProtectionKeys packetProtectionKeys;
        private final QuicPacketProtection protection;
        private final int keyPhase;
        private final AtomicLong numEncrypted = new AtomicLong();
        private final AtomicLong lowestEncryptedPacketNumber = new AtomicLong(NO_PACKET_NUMBER);

        private WriteKeys(SecretKey trafficSecret,
                          QuicPacketProtectionKeys packetProtectionKeys,
                          QuicPacketProtection protection,
                          int keyPhase) {
            this.trafficSecret = Objects.requireNonNull(trafficSecret, "trafficSecret");
            this.packetProtectionKeys = Objects.requireNonNull(packetProtectionKeys, "packetProtectionKeys");
            this.protection = Objects.requireNonNull(protection, "protection");
            this.keyPhase = keyPhase;
        }

        @Override
        public ByteBuffer computeHeaderProtectionMask(ByteBuffer sample) throws QuicTransportException {
            return protection.computeHeaderProtectionMask(sample);
        }

        @Override
        public long computeHeaderProtectionMaskBits(ByteBuffer sample) throws QuicTransportException {
            return protection.computeHeaderProtectionMaskBits(sample);
        }

        private static WriteKeys create(QuicVersion version,
                                        QuicTls13CipherSuite cipherSuite,
                                        SecretKey trafficSecret,
                                        int keyPhase,
                                        long aesGcmConfidentialityLimit,
                                        long chacha20Poly1305ConfidentialityLimit) {
            QuicPacketProtectionKeys keys = QuicPacketProtectionKeys.derive(version, cipherSuite, trafficSecret);
            return new WriteKeys(trafficSecret,
                                 keys,
                                 QuicPacketProtection.create(cipherSuite,
                                                             keys,
                                                             aesGcmConfidentialityLimit,
                                                             chacha20Poly1305ConfidentialityLimit),
                                 keyPhase);
        }

        private WriteKeys next(QuicVersion version,
                               QuicTls13CipherSuite cipherSuite,
                               QuicTls13SecretSchedule secretSchedule,
                               long aesGcmConfidentialityLimit,
                               long chacha20Poly1305ConfidentialityLimit) {
            SecretKey nextTrafficSecret = secretSchedule.deriveNextPacketProtectionSecret(version, trafficSecret);
            QuicPacketProtectionKeys keys = QuicPacketProtectionKeys.derive(version,
                                                                            cipherSuite,
                                                                            nextTrafficSecret,
                                                                            packetProtectionKeys.headerProtectionKey());
            return new WriteKeys(nextTrafficSecret,
                                 keys,
                                 QuicPacketProtection.create(cipherSuite,
                                                             keys,
                                                             aesGcmConfidentialityLimit,
                                                             chacha20Poly1305ConfidentialityLimit),
                                 nextKeyPhase(keyPhase));
        }

        private void encryptPacket(long packetNumber,
                                   ByteBuffer header,
                                   ByteBuffer packetPayload,
                                   ByteBuffer output) throws QuicTransportException {
            long confidentialityLimit = confidentialityLimit();
            long encrypted;
            do {
                encrypted = numEncrypted.get();
                if (confidentialityLimit > 0 && encrypted >= confidentialityLimit) {
                    throw new QuicTransportException("confidentiality limit reached",
                                                     QuicTLSEngine.KeySpace.ONE_RTT,
                                                     0,
                                                     QuicTransportErrors.AEAD_LIMIT_REACHED);
                }
            } while (!numEncrypted.compareAndSet(encrypted, encrypted + 1));
            protection.encryptPacket(packetNumber, header, packetPayload, output);
            updateLowestPacketNumber(lowestEncryptedPacketNumber, packetNumber);
        }

        private boolean hasEncryptedAny() {
            return lowestEncryptedPacketNumber.get() != NO_PACKET_NUMBER;
        }

        private long lowestEncryptedPacketNumber() {
            return lowestEncryptedPacketNumber.get();
        }

        private long numEncrypted() {
            return numEncrypted.get();
        }

        private long confidentialityLimit() {
            return protection.confidentialityLimit();
        }

        private int keyPhase() {
            return keyPhase;
        }
    }
}
