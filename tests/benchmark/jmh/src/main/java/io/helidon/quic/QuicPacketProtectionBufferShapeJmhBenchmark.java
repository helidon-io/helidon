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
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.spec.SecretKeySpec;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks QUIC packet protection across distinct and overlapping heap and direct buffers.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class QuicPacketProtectionBufferShapeJmhBenchmark {
    private static final int HEADER_LENGTH = 24;
    private static final long DECRYPT_PACKET_NUMBER = 0x0102_0304L;
    private static final long PACKET_NUMBER_RANGE_SIZE = 1L << 32;
    private static final AtomicLong PACKET_NUMBER_RANGES = new AtomicLong(1L << 40);

    @Benchmark
    public long encrypt(BufferState state) {
        state.protection.encryptPacket(state.encryptionPacketNumber,
                                       state.encryptionHeader,
                                       state.encryptionInput,
                                       state.encryptionOutput);
        return outputEvidence(state.encryptionOutput);
    }

    @Benchmark
    public long decrypt(BufferState state) {
        state.protection.decryptPacket(DECRYPT_PACKET_NUMBER,
                                       state.decryptionInput,
                                       HEADER_LENGTH,
                                       state.decryptionOutput);
        return outputEvidence(state.decryptionOutput);
    }

    @Benchmark
    public QuicPacketAuthenticationException badTagDecrypt(BufferState state) {
        try {
            state.protection.decryptPacket(DECRYPT_PACKET_NUMBER,
                                           state.badTagInput,
                                           HEADER_LENGTH,
                                           state.badTagOutput);
        } catch (QuicPacketAuthenticationException e) {
            return e;
        }
        throw new IllegalStateException("Bad-tag QUIC buffer-shape benchmark packet authenticated");
    }

    /**
     * Per-thread packet protection and operation-specific mutable buffer views.
     */
    @State(Scope.Thread)
    public static class BufferState {
        /**
         * TLS cipher suite used to protect packets.
         */
        @Param({"TLS_AES_128_GCM_SHA256", "TLS_CHACHA20_POLY1305_SHA256"})
        public String cipherSuite;

        /**
         * Complete protected datagram size, including header and authentication tag.
         * Larger values such as {@code 65527} can be selected through the runner.
         */
        @Param({"64", "1200", "1452"})
        public int datagramSize;

        /**
         * Input/output storage and overlap shape.
         */
        @Param({
                "DISTINCT_MUTABLE_HEAP",
                "EXACT_IN_PLACE_MUTABLE_HEAP",
                "OVERLAPPING_READ_ONLY_HEAP",
                "DISTINCT_MUTABLE_DIRECT",
                "EXACT_IN_PLACE_MUTABLE_DIRECT",
                "OVERLAPPING_READ_ONLY_DIRECT"
        })
        public String bufferShape;

        private boolean direct;
        private boolean overlapping;
        private boolean readOnlyInput;
        private int payloadSize;
        private byte[] plaintextTemplate;
        private byte[] encryptedPacketTemplate;
        private byte[] badTagPacketTemplate;
        private long nextEncryptionPacketNumber;
        private long encryptionPacketNumber;
        private QuicPacketProtection protection;
        private ByteBuffer encryptionBacking;
        private ByteBuffer encryptionHeader;
        private ByteBuffer encryptionInput;
        private ByteBuffer encryptionOutput;
        private ByteBuffer decryptionBacking;
        private ByteBuffer decryptionInput;
        private ByteBuffer decryptionOutput;
        private ByteBuffer badTagBacking;
        private ByteBuffer badTagInput;
        private ByteBuffer badTagOutput;

        @Setup(Level.Trial)
        public void setUpTrial() {
            switch (bufferShape) {
                case "DISTINCT_MUTABLE_HEAP" -> {
                    direct = false;
                    overlapping = false;
                    readOnlyInput = false;
                }
                case "EXACT_IN_PLACE_MUTABLE_HEAP" -> {
                    direct = false;
                    overlapping = true;
                    readOnlyInput = false;
                }
                case "OVERLAPPING_READ_ONLY_HEAP" -> {
                    direct = false;
                    overlapping = true;
                    readOnlyInput = true;
                }
                case "DISTINCT_MUTABLE_DIRECT" -> {
                    direct = true;
                    overlapping = false;
                    readOnlyInput = false;
                }
                case "EXACT_IN_PLACE_MUTABLE_DIRECT" -> {
                    direct = true;
                    overlapping = true;
                    readOnlyInput = false;
                }
                case "OVERLAPPING_READ_ONLY_DIRECT" -> {
                    direct = true;
                    overlapping = true;
                    readOnlyInput = true;
                }
                default -> throw new IllegalArgumentException(
                        "Unsupported QUIC packet buffer shape: " + bufferShape);
            }

            payloadSize = datagramSize - HEADER_LENGTH - QuicPacketProtection.AUTH_TAG_SIZE;
            if (payloadSize <= 0) {
                throw new IllegalArgumentException("QUIC buffer-shape benchmark datagram is too small");
            }

            protection = createProtection(cipherSuite);
            nextEncryptionPacketNumber = PACKET_NUMBER_RANGES.getAndAdd(PACKET_NUMBER_RANGE_SIZE);
            byte[] headerTemplate = new byte[HEADER_LENGTH];
            plaintextTemplate = new byte[payloadSize];
            fill(headerTemplate, 0x31);
            fill(plaintextTemplate, 0x71);

            ByteBuffer encryptedPacket = ByteBuffer.allocate(datagramSize);
            encryptedPacket.put(headerTemplate);
            protection.encryptPacket(DECRYPT_PACKET_NUMBER,
                                     ByteBuffer.wrap(headerTemplate),
                                     ByteBuffer.wrap(plaintextTemplate),
                                     encryptedPacket);
            if (encryptedPacket.position() != datagramSize) {
                throw new IllegalStateException("QUIC buffer-shape ciphertext has an unexpected size");
            }
            encryptedPacketTemplate = encryptedPacket.array().clone();
            badTagPacketTemplate = encryptedPacketTemplate.clone();
            badTagPacketTemplate[badTagPacketTemplate.length - 1] ^= 1;

            if (overlapping) {
                encryptionBacking = allocate(datagramSize);
                encryptionBacking.put(headerTemplate).put(plaintextTemplate).clear();
                encryptionHeader = encryptionBacking.slice(0, HEADER_LENGTH);
                ByteBuffer payloadView = encryptionBacking.slice(HEADER_LENGTH, payloadSize);
                encryptionInput = readOnlyInput ? payloadView.asReadOnlyBuffer() : payloadView;
                encryptionOutput = encryptionBacking.duplicate();

                decryptionBacking = initializedBuffer(encryptedPacketTemplate);
                ByteBuffer packetView = decryptionBacking.duplicate();
                decryptionInput = readOnlyInput ? packetView.asReadOnlyBuffer() : packetView;
                decryptionOutput = decryptionBacking.slice(HEADER_LENGTH, datagramSize - HEADER_LENGTH);

                badTagBacking = initializedBuffer(badTagPacketTemplate);
                ByteBuffer badTagView = badTagBacking.duplicate();
                badTagInput = readOnlyInput ? badTagView.asReadOnlyBuffer() : badTagView;
                badTagOutput = badTagBacking.slice(HEADER_LENGTH, datagramSize - HEADER_LENGTH);
            } else {
                encryptionHeader = initializedBuffer(headerTemplate);
                encryptionInput = initializedBuffer(plaintextTemplate);
                encryptionOutput = allocate(datagramSize);
                encryptionOutput.put(headerTemplate);

                decryptionInput = initializedBuffer(encryptedPacketTemplate);
                decryptionOutput = allocate(datagramSize - HEADER_LENGTH);
                badTagInput = initializedBuffer(badTagPacketTemplate);
                badTagOutput = allocate(datagramSize - HEADER_LENGTH);
            }

            long validationPacketNumber = nextEncryptionPacketNumber++;
            ByteBuffer expectedEncryptedPacket = ByteBuffer.allocate(datagramSize);
            expectedEncryptedPacket.put(headerTemplate);
            createProtection(cipherSuite).encryptPacket(validationPacketNumber,
                                                        ByteBuffer.wrap(headerTemplate),
                                                        ByteBuffer.wrap(plaintextTemplate),
                                                        expectedEncryptedPacket);

            encryptionOutput.put(0, headerTemplate);
            if (overlapping) {
                encryptionBacking.put(HEADER_LENGTH, plaintextTemplate);
            }
            encryptionHeader.clear();
            encryptionInput.clear();
            encryptionOutput.clear().position(HEADER_LENGTH);
            protection.encryptPacket(validationPacketNumber,
                                     encryptionHeader,
                                     encryptionInput,
                                     encryptionOutput);
            ByteBuffer actualEncryptedPacket = encryptionOutput.duplicate().flip();
            byte[] actualEncryptedBytes = new byte[actualEncryptedPacket.remaining()];
            actualEncryptedPacket.get(actualEncryptedBytes);
            if (!Arrays.equals(expectedEncryptedPacket.array(), actualEncryptedBytes)) {
                throw new IllegalStateException("QUIC buffer-shape encryption validation failed");
            }

            if (overlapping) {
                decryptionBacking.put(0, encryptedPacketTemplate);
            }
            decryptionInput.clear();
            decryptionOutput.clear();
            protection.decryptPacket(DECRYPT_PACKET_NUMBER,
                                     decryptionInput,
                                     HEADER_LENGTH,
                                     decryptionOutput);
            ByteBuffer actualPlaintext = decryptionOutput.duplicate().flip();
            byte[] actualPlaintextBytes = new byte[actualPlaintext.remaining()];
            actualPlaintext.get(actualPlaintextBytes);
            if (!Arrays.equals(plaintextTemplate, actualPlaintextBytes)) {
                throw new IllegalStateException("QUIC buffer-shape decryption validation failed");
            }

            if (overlapping) {
                badTagBacking.put(0, badTagPacketTemplate);
            }
            badTagInput.clear();
            badTagOutput.clear();
            try {
                protection.decryptPacket(DECRYPT_PACKET_NUMBER,
                                         badTagInput,
                                         HEADER_LENGTH,
                                         badTagOutput);
                throw new IllegalStateException("Bad-tag QUIC buffer-shape validation packet authenticated");
            } catch (QuicPacketAuthenticationException e) {
                // Expected.
            }
        }

        @Setup(Level.Invocation)
        public void setUpInvocation() {
            if (overlapping) {
                encryptionBacking.put(HEADER_LENGTH, plaintextTemplate);
                decryptionBacking.put(0, encryptedPacketTemplate);
                badTagBacking.put(0, badTagPacketTemplate);
            }
            encryptionPacketNumber = nextEncryptionPacketNumber++;
            encryptionHeader.clear();
            encryptionInput.clear();
            encryptionOutput.clear().position(HEADER_LENGTH);
            decryptionInput.clear();
            decryptionOutput.clear();
            badTagInput.clear();
            badTagOutput.clear();
        }

        private ByteBuffer allocate(int size) {
            return direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
        }

        private ByteBuffer initializedBuffer(byte[] content) {
            return allocate(content.length).put(content).flip();
        }
    }

    private static void fill(byte[] bytes, int seed) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (seed + i * 31);
        }
    }

    private static QuicPacketProtection createProtection(String cipherSuiteName) {
        QuicTls13CipherSuite cipherSuite = QuicTls13CipherSuite.forName(cipherSuiteName);
        byte[] trafficSecret = new byte[cipherSuite.hashLength()];
        fill(trafficSecret, 0x11);
        QuicPacketProtectionKeys keys =
                QuicPacketProtectionKeys.derive(QuicVersion.QUIC_V1,
                                                cipherSuite,
                                                new SecretKeySpec(trafficSecret, "TlsSecret"));
        return QuicPacketProtection.create(cipherSuite, keys);
    }

    private static long outputEvidence(ByteBuffer output) {
        int position = output.position();
        return ((long) position << 32)
                | ((long) output.get(0) & 0xff) << 8
                | ((long) output.get(position - 1) & 0xff);
    }
}
