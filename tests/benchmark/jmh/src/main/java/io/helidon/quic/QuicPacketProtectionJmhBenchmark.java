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
 * Benchmarks QUIC packet and header protection allocation and contention.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(4)
public class QuicPacketProtectionJmhBenchmark {
    private static final int HEADER_LENGTH = 24;
    private static final long DECRYPT_PACKET_NUMBER = 0x0102_0304L;
    private static final long PACKET_NUMBER_RANGE_SIZE = 1L << 32;
    private static final AtomicLong PACKET_NUMBER_RANGES = new AtomicLong(1L << 40);

    @Benchmark
    public ByteBuffer threadLocalHeaderProtection(HeaderState state) {
        return state.protection.computeHeaderProtectionMask(state.headerProtectionSample);
    }

    @Benchmark
    public ByteBuffer sharedHeaderProtection(HeaderState state, SharedProtectionState sharedState) {
        return sharedState.protection(state.cipherIndex)
                .computeHeaderProtectionMask(state.headerProtectionSample);
    }

    @Benchmark
    public long threadLocalPackedHeaderProtection(HeaderState state) {
        return state.protection.computeHeaderProtectionMaskBits(state.headerProtectionSample);
    }

    @Benchmark
    public long sharedPackedHeaderProtection(HeaderState state, SharedProtectionState sharedState) {
        return sharedState.protection(state.cipherIndex)
                .computeHeaderProtectionMaskBits(state.headerProtectionSample);
    }

    @Benchmark
    public long threadLocalEncrypt(PacketState state) {
        state.protection.encryptPacket(state.encryptionPacketNumber,
                                       state.header,
                                       state.plaintext,
                                       state.encryptionOutput);
        return outputEvidence(state.encryptionOutput);
    }

    @Benchmark
    public long sharedEncrypt(PacketState state, SharedProtectionState sharedState) {
        sharedState.protection(state.cipherIndex)
                .encryptPacket(state.encryptionPacketNumber,
                               state.header,
                               state.plaintext,
                               state.encryptionOutput);
        return outputEvidence(state.encryptionOutput);
    }

    @Benchmark
    public long threadLocalDecrypt(PacketState state) {
        state.protection.decryptPacket(DECRYPT_PACKET_NUMBER,
                                       state.encryptedPacket,
                                       HEADER_LENGTH,
                                       state.decryptionOutput);
        return outputEvidence(state.decryptionOutput);
    }

    @Benchmark
    public long sharedDecrypt(PacketState state, SharedProtectionState sharedState) {
        sharedState.protection(state.cipherIndex)
                .decryptPacket(DECRYPT_PACKET_NUMBER,
                               state.encryptedPacket,
                               HEADER_LENGTH,
                               state.decryptionOutput);
        return outputEvidence(state.decryptionOutput);
    }

    @Benchmark
    public QuicPacketAuthenticationException threadLocalBadTagDecrypt(PacketState state) {
        try {
            state.protection.decryptPacket(DECRYPT_PACKET_NUMBER,
                                           state.badTagPacket,
                                           HEADER_LENGTH,
                                           state.badTagOutput);
        } catch (QuicPacketAuthenticationException e) {
            return e;
        }
        throw new IllegalStateException("Bad-tag QUIC benchmark packet authenticated");
    }

    @Benchmark
    public QuicPacketAuthenticationException sharedBadTagDecrypt(PacketState state,
                                                                 SharedProtectionState sharedState) {
        try {
            sharedState.protection(state.cipherIndex)
                    .decryptPacket(DECRYPT_PACKET_NUMBER,
                                   state.badTagPacket,
                                   HEADER_LENGTH,
                                   state.badTagOutput);
        } catch (QuicPacketAuthenticationException e) {
            return e;
        }
        throw new IllegalStateException("Bad-tag QUIC benchmark packet authenticated");
    }

    /**
     * Per-thread header-protection sample and key state.
     */
    @State(Scope.Thread)
    public static class HeaderState {
        /** TLS cipher suite used to protect packet headers. */
        @Param({"TLS_AES_128_GCM_SHA256", "TLS_CHACHA20_POLY1305_SHA256"})
        public String cipherSuite;

        private int cipherIndex;
        private QuicPacketProtection protection;
        private ByteBuffer headerProtectionSample;

        @Setup(Level.Trial)
        public void setUpTrial() {
            cipherIndex = cipherSuiteIndex(cipherSuite);
            protection = createProtection(cipherSuite);

            byte[] sampleBytes = new byte[QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE];
            fill(sampleBytes, 0x51);
            headerProtectionSample = ByteBuffer.wrap(sampleBytes).asReadOnlyBuffer();
        }

        @Setup(Level.Invocation)
        public void setUpInvocation() {
            headerProtectionSample.clear();
        }
    }

    /**
     * Per-thread packet inputs, outputs, and packet-protection key state.
     */
    @State(Scope.Thread)
    public static class PacketState {
        /** TLS cipher suite used to protect packets. */
        @Param({"TLS_AES_128_GCM_SHA256", "TLS_CHACHA20_POLY1305_SHA256"})
        public String cipherSuite;

        /** Complete protected datagram size, including header and authentication tag. */
        @Param({"64", "1200", "1452", "65527"})
        public int datagramSize;

        private int cipherIndex;
        private int payloadSize;
        private long nextEncryptionPacketNumber;
        private long encryptionPacketNumber;
        private QuicPacketProtection protection;
        private ByteBuffer header;
        private ByteBuffer plaintext;
        private ByteBuffer encryptionOutput;
        private ByteBuffer encryptedPacket;
        private ByteBuffer decryptionOutput;
        private ByteBuffer badTagPacket;
        private ByteBuffer badTagOutput;

        @Setup(Level.Trial)
        public void setUpTrial() {
            payloadSize = datagramSize - HEADER_LENGTH - QuicPacketProtection.AUTH_TAG_SIZE;
            if (payloadSize <= 0) {
                throw new IllegalArgumentException("QUIC benchmark datagram is too small");
            }

            cipherIndex = cipherSuiteIndex(cipherSuite);
            protection = createProtection(cipherSuite);
            nextEncryptionPacketNumber = PACKET_NUMBER_RANGES.getAndAdd(PACKET_NUMBER_RANGE_SIZE);

            byte[] headerBytes = new byte[HEADER_LENGTH];
            byte[] plaintextBytes = new byte[payloadSize];
            fill(headerBytes, 0x31);
            fill(plaintextBytes, 0x71);

            header = ByteBuffer.wrap(headerBytes).asReadOnlyBuffer();
            plaintext = ByteBuffer.wrap(plaintextBytes).asReadOnlyBuffer();
            encryptionOutput = ByteBuffer.allocate(datagramSize);
            encryptionOutput.put(headerBytes);

            ByteBuffer packet = ByteBuffer.allocate(datagramSize);
            packet.put(headerBytes);
            protection.encryptPacket(DECRYPT_PACKET_NUMBER,
                                     header.duplicate(),
                                     plaintext.duplicate(),
                                     packet);
            if (packet.position() != datagramSize) {
                throw new IllegalStateException("QUIC benchmark ciphertext has an unexpected size");
            }
            packet.flip();
            encryptedPacket = packet.asReadOnlyBuffer();
            byte[] badTagBytes = packet.array().clone();
            badTagBytes[badTagBytes.length - 1] ^= 1;
            badTagPacket = ByteBuffer.wrap(badTagBytes).asReadOnlyBuffer();
            decryptionOutput = ByteBuffer.allocate(payloadSize);
            badTagOutput = ByteBuffer.allocate(payloadSize);
        }

        @Setup(Level.Invocation)
        public void setUpInvocation() {
            encryptionPacketNumber = nextEncryptionPacketNumber++;
            header.clear();
            plaintext.clear();
            encryptionOutput.clear().position(HEADER_LENGTH);
            encryptedPacket.clear();
            decryptionOutput.clear();
            badTagPacket.clear();
            badTagOutput.clear();
        }
    }

    /**
     * Packet-protection key state intentionally shared by all benchmark threads.
     */
    @State(Scope.Benchmark)
    public static class SharedProtectionState {
        private QuicPacketProtection[] protections;

        @Setup(Level.Trial)
        public void setUp() {
            protections = new QuicPacketProtection[] {
                    createProtection("TLS_AES_128_GCM_SHA256"),
                    createProtection("TLS_CHACHA20_POLY1305_SHA256")
            };
        }

        private QuicPacketProtection protection(int cipherIndex) {
            return protections[cipherIndex];
        }
    }

    private static int cipherSuiteIndex(String cipherSuite) {
        return switch (cipherSuite) {
            case "TLS_AES_128_GCM_SHA256" -> 0;
            case "TLS_CHACHA20_POLY1305_SHA256" -> 1;
            default -> throw new IllegalArgumentException(
                    "Unsupported QUIC benchmark cipher suite: " + cipherSuite);
        };
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

    private static void fill(byte[] bytes, int seed) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (seed + i * 31);
        }
    }

    private static long outputEvidence(ByteBuffer output) {
        int position = output.position();
        return ((long) position << 32)
                | ((long) output.get(0) & 0xff) << 8
                | ((long) output.get(position - 1) & 0xff);
    }
}
