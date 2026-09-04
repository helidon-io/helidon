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

package io.helidon.quic.packet;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.quic.CodingContext;
import io.helidon.quic.QuicConnectionId;
import io.helidon.quic.QuicKeyUnavailableException;
import io.helidon.quic.QuicTLSEngine;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacket.PacketType;

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
 * Benchmarks normal and policy-limited QUIC ACK frame decoding.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class QuicAckDecodeJmhBenchmark {
    private static final int ACK_RANGE_LIMIT = 1024;

    @Benchmark
    public Object configuredLimit(DecodeState state) throws QuicTransportException {
        return state.decode(state.configuredContext);
    }

    @Benchmark
    public Object unboundedControl(DecodeState state) throws QuicTransportException {
        return state.decode(state.unboundedContext);
    }

    /**
     * Encoded frame payload and decoder context for one benchmark thread.
     */
    @State(Scope.Thread)
    public static class DecodeState {
        /** Frame payload shape. */
        @Param
        public Scenario scenario;

        private ByteBuffer readerBuffer;
        private ByteBuffer payload;
        private QuicPacketDecoder decoder;
        private CodingContext configuredContext;
        private CodingContext unboundedContext;

        @Setup(Level.Trial)
        public void setUpTrial() {
            ByteBuffer encoded = ByteBuffer.allocate(16 * 1024);
            switch (scenario) {
                case ONE_RANGE -> encodeAckFrame(encoded, 1, false);
                case FRAGMENTED_32 -> encodeAckFrame(encoded, 32, false);
                case BOUNDARY_1024 -> encodeAckFrame(encoded, ACK_RANGE_LIMIT, false);
                case SINGLE_EXCESS_1025 -> encodeAckFrame(encoded, ACK_RANGE_LIMIT + 1, false);
                case REPEATED_EXCESS_1025 -> {
                    for (int i = 0; i < 4; i++) {
                        encodeAckFrame(encoded, ACK_RANGE_LIMIT + 1, (i & 1) != 0);
                    }
                }
                case NON_ACK_32 -> {
                    for (int i = 0; i < 32; i++) {
                        VariableLengthEncoder.encode(encoded, QuicFrame.PING);
                    }
                }
            }
            payload = encoded.flip().asReadOnlyBuffer();
            readerBuffer = ByteBuffer.allocate(0);
            decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);
            configuredContext = new BenchmarkCodingContext(ACK_RANGE_LIMIT);
            unboundedContext = new BenchmarkCodingContext(Integer.MAX_VALUE);
        }

        @Setup(Level.Invocation)
        public void setUpInvocation() {
            payload.position(0);
        }

        private Object decode(CodingContext context) throws QuicTransportException {
            QuicPacketDecoder.PacketReader reader = decoder.new PacketReader(readerBuffer,
                                                                              context,
                                                                              PacketType.ONERTT,
                                                                              "",
                                                                              true);
            try {
                return reader.parsePayloadSlice(payload);
            } catch (QuicPacketDecodeException e) {
                return e;
            }
        }

        private static void encodeAckFrame(ByteBuffer buffer, int totalRanges, boolean ecn) {
            VariableLengthEncoder.encode(buffer, ecn ? QuicFrame.ACK + 1 : QuicFrame.ACK);
            VariableLengthEncoder.encode(buffer, 3L * totalRanges);
            VariableLengthEncoder.encode(buffer, 0);
            VariableLengthEncoder.encode(buffer, totalRanges - 1L);
            VariableLengthEncoder.encode(buffer, 1);
            for (int i = 1; i < totalRanges; i++) {
                VariableLengthEncoder.encode(buffer, 0);
                VariableLengthEncoder.encode(buffer, 1);
            }
            if (ecn) {
                VariableLengthEncoder.encode(buffer, 0);
                VariableLengthEncoder.encode(buffer, 0);
                VariableLengthEncoder.encode(buffer, 0);
            }
        }
    }

    /**
     * Payload shapes covered by the ACK decoder benchmark.
     */
    public enum Scenario {
        /** One ordinary ACK range. */
        ONE_RANGE,
        /** One ACK frame containing 32 discontiguous ranges. */
        FRAGMENTED_32,
        /** One ACK frame exactly at the configured limit. */
        BOUNDARY_1024,
        /** One ACK frame containing one range more than the configured limit. */
        SINGLE_EXCESS_1025,
        /** Four over-limit ACK and ACK_ECN frames in one packet payload. */
        REPEATED_EXCESS_1025,
        /** A control payload containing only PING frames. */
        NON_ACK_32
    }

    private static final class BenchmarkCodingContext implements CodingContext {
        private final int maxAckRangesPerFrame;

        private BenchmarkCodingContext(int maxAckRangesPerFrame) {
            this.maxAckRangesPerFrame = maxAckRangesPerFrame;
        }

        @Override
        public long largestProcessedPN(PacketNumberSpace packetSpace) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long largestAckedPN(PacketNumberSpace packetSpace) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int connectionIdLength() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int maxAckRangesPerFrame() {
            return maxAckRangesPerFrame;
        }

        @Override
        public int writePacket(QuicPacket packet, ByteBuffer buffer)
                throws QuicKeyUnavailableException, QuicTransportException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<QuicPacket> parsePacket(ByteBuffer src)
                throws QuicKeyUnavailableException, QuicTransportException {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuicConnectionId originalServerConnId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuicTLSEngine tlsEngine() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean verifyToken(QuicConnectionId destinationID, byte[] token) {
            throw new UnsupportedOperationException();
        }
    }
}
