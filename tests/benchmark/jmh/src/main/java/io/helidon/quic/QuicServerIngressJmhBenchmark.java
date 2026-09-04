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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks stateless QUIC server ingress before connection allocation.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QuicServerIngressJmhBenchmark {
    @Benchmark
    public Object supportedInitial(ServerIngressState state) {
        return state.ingress.inspect(state.peerAddress, state.connectionIdFactory, state.supportedInitial);
    }

    @Benchmark
    public Object malformedInitial(ServerIngressState state) {
        return state.ingress.inspect(state.peerAddress, state.connectionIdFactory, state.malformedInitial);
    }

    @Benchmark
    public Object versionNegotiation(ServerIngressState state) {
        return state.ingress.inspect(state.peerAddress, state.connectionIdFactory, state.unsupportedVersion);
    }

    @Benchmark
    public Object retryInitial(ServerIngressState state) {
        return state.retryIngress.inspect(state.peerAddress, state.connectionIdFactory, state.supportedInitial);
    }

    @Benchmark
    public Object newTokenInitial(ServerIngressState state) {
        return state.retryIngress.inspect(state.peerAddress, state.connectionIdFactory, state.newTokenInitial);
    }

    @Benchmark
    public Object validRetryInitial(ServerIngressState state) {
        return state.retryIngress.inspect(state.peerAddress, state.connectionIdFactory, state.validRetryInitial);
    }

    /** Stateless server-ingress packet shapes. */
    @State(Scope.Thread)
    public static class ServerIngressState {
        private QuicServerIngress ingress;
        private QuicServerIngress retryIngress;
        private QuicAddressTokenService tokenService;
        private QuicConnectionIdFactory connectionIdFactory;
        private InetSocketAddress peerAddress;
        private ByteBuffer supportedInitial;
        private ByteBuffer malformedInitial;
        private ByteBuffer unsupportedVersion;
        private ByteBuffer newTokenInitial;
        private ByteBuffer validRetryInitial;

        @Setup(Level.Trial)
        public void setUp() {
            tokenService = QuicAddressTokenService.create();
            connectionIdFactory = QuicConnectionIdFactory.server();
            peerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 443);
            ingress = new QuicServerIngress(List.of(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1), false, null);
            retryIngress = new QuicServerIngress(List.of(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1), true, tokenService);
            byte[] destinationId = new byte[8];
            byte[] sourceId = new byte[8];

            supportedInitial = supportedInitial(0);

            ByteBuffer malformed = ByteBuffer.allocate(QuicConfigSupport.MINIMUM_DATAGRAM_SIZE);
            malformed.put((byte) 0xc0);
            malformed.putInt(QuicVersion.QUIC_V1.versionNumber());
            malformed.put((byte) 7);
            malformed.put(new byte[7]);
            malformed.put((byte) sourceId.length);
            malformed.put(sourceId);
            VariableLengthEncoder.encode(malformed, 0);
            VariableLengthEncoder.encode(malformed, 20);
            malformed.position(malformed.limit());
            malformedInitial = malformed.flip().asReadOnlyBuffer();

            ByteBuffer unsupported = ByteBuffer.allocate(QuicConfigSupport.MINIMUM_DATAGRAM_SIZE);
            unsupported.put((byte) 0x80);
            unsupported.putInt(0x0a0a0a0a);
            unsupported.put((byte) destinationId.length);
            unsupported.put(destinationId);
            unsupported.put((byte) sourceId.length);
            unsupported.put(sourceId);
            unsupported.position(unsupported.limit());
            unsupportedVersion = unsupported.flip().asReadOnlyBuffer();

            byte[] token = tokenService.newToken(peerAddress, QuicVersion.QUIC_V1).orElseThrow();
            ByteBuffer tokenInitial = ByteBuffer.allocate(QuicConfigSupport.MINIMUM_DATAGRAM_SIZE);
            tokenInitial.put((byte) 0xc0);
            tokenInitial.putInt(QuicVersion.QUIC_V1.versionNumber());
            tokenInitial.put((byte) destinationId.length);
            tokenInitial.put(destinationId);
            tokenInitial.put((byte) sourceId.length);
            tokenInitial.put(sourceId);
            VariableLengthEncoder.encode(tokenInitial, token.length);
            tokenInitial.put(token);
            VariableLengthEncoder.encode(tokenInitial, tokenInitial.capacity() - tokenInitial.position() - 2);
            tokenInitial.position(tokenInitial.limit());
            newTokenInitial = tokenInitial.flip().asReadOnlyBuffer();

            QuicConnectionId originalDestinationId = PeerConnectionId.create(destinationId);
            QuicConnectionId retrySourceId = connectionIdFactory.newConnectionId();
            QuicConnectionId clientSourceId = PeerConnectionId.create(sourceId);
            byte[] retryToken = tokenService.retryToken(peerAddress,
                                                        QuicVersion.QUIC_V1,
                                                        originalDestinationId,
                                                        retrySourceId,
                                                        clientSourceId)
                    .orElseThrow();
            ByteBuffer retryTokenPacket = ByteBuffer.allocate(QuicConfigSupport.MINIMUM_DATAGRAM_SIZE);
            retryTokenPacket.put((byte) 0xc0);
            retryTokenPacket.putInt(QuicVersion.QUIC_V1.versionNumber());
            retryTokenPacket.put((byte) retrySourceId.length());
            retryTokenPacket.put(retrySourceId.asReadOnlyBuffer());
            retryTokenPacket.put((byte) sourceId.length);
            retryTokenPacket.put(sourceId);
            VariableLengthEncoder.encode(retryTokenPacket, retryToken.length);
            retryTokenPacket.put(retryToken);
            VariableLengthEncoder.encode(retryTokenPacket, retryTokenPacket.capacity() - retryTokenPacket.position() - 2);
            retryTokenPacket.position(retryTokenPacket.limit());
            validRetryInitial = retryTokenPacket.flip().asReadOnlyBuffer();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            tokenService.close();
        }
    }

    static ByteBuffer supportedInitial(long destination) {
        ByteBuffer initial = ByteBuffer.allocate(QuicConfigSupport.MINIMUM_DATAGRAM_SIZE);
        initial.put((byte) 0xc0);
        initial.putInt(QuicVersion.QUIC_V1.versionNumber());
        initial.put((byte) Long.BYTES);
        initial.putLong(destination);
        initial.put((byte) Long.BYTES);
        initial.putLong(0);
        VariableLengthEncoder.encode(initial, 0);
        VariableLengthEncoder.encode(initial, initial.capacity() - initial.position() - 2);
        initial.position(initial.limit());
        return initial.flip().asReadOnlyBuffer();
    }
}
