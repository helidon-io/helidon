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

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLParameters;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.QuicTransportParameters.ParameterId;

import com.sun.management.ThreadMXBean;
import org.openjdk.jmh.annotations.AuxCounters;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures real TLS server-flight creation with bounded supported_groups vectors.
 * Setup and verification are excluded from timing. Process-wide GC profilers include that fixture work;
 * use the separately bracketed allocation method for bytes per ClientHello.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QuicTlsGroupsJmhBenchmark {
    private static final QuicVersion VERSION = QuicVersion.QUIC_V1;
    private static final String CIPHER_SUITE = "TLS_AES_128_GCM_SHA256";
    private static final String APPLICATION_PROTOCOL = "h3";
    private static final int MAX_UNKNOWN_GROUPS = 1024;
    private static final int FIRST_UNKNOWN_GROUP = 0x8000;
    private static final byte[] INITIAL_CONNECTION_ID = {1, 2, 3, 4, 5, 6, 7, 8};
    private static final byte[] CLIENT_CONNECTION_ID = {9, 10, 11, 12, 13, 14, 15, 16};
    private static final byte[] SERVER_CONNECTION_ID = {17, 18, 19, 20, 21, 22, 23, 24};

    /**
     * Processes the prepared ClientHello on a fresh server handshake.
     *
     * @param state prepared input and fresh handshake
     * @return real server-flight result
     */
    @Benchmark
    public Object serverFlight(HandshakeState state) {
        state.result = state.handshake.consumeClientHello(state.input);
        return state.result;
    }

    /**
     * Counts allocation on the calling JMH worker only inside ClientHello processing.
     * Allocation-counter overhead makes this method unsuitable for timing comparisons.
     *
     * @param state prepared input and fresh handshake
     * @param counters worker allocation and operation totals
     * @return real server-flight result
     */
    @Benchmark
    public Object serverFlightAllocation(HandshakeState state, AllocationCounters counters) {
        long before = counters.allocationBean.getThreadAllocatedBytes(counters.threadId);
        var result = state.handshake.consumeClientHello(state.input);
        long allocated = counters.allocationBean.getThreadAllocatedBytes(counters.threadId) - before;
        if (before < 0 || allocated < 0) {
            throw new IllegalStateException("Worker-thread allocation counter is unavailable or moved backwards");
        }
        counters.allocatedBytes += allocated;
        counters.handshakeOperations++;
        state.result = result;
        return result;
    }

    private static ByteBuffer supportedGroups(int unknownGroupCount) {
        if (unknownGroupCount < 0 || unknownGroupCount > MAX_UNKNOWN_GROUPS) {
            throw new IllegalArgumentException("unknownGroupCount must be between 0 and " + MAX_UNKNOWN_GROUPS);
        }
        int vectorLength = Short.BYTES * (unknownGroupCount + 1);
        ByteBuffer groups = ByteBuffer.allocate(Short.BYTES + vectorLength);
        groups.putShort((short) vectorLength);
        for (int i = 0; i < unknownGroupCount; i++) {
            groups.putShort((short) (FIRST_UNKNOWN_GROUP + i));
        }
        groups.putShort((short) QuicTlsNamedGroup.X25519.codePoint());
        return groups.flip();
    }

    private static byte[] transportParameters(boolean server) {
        QuicTransportParameters parameters = QuicTransportParameters.create();
        parameters.parameter(ParameterId.initial_source_connection_id,
                             server ? SERVER_CONNECTION_ID : CLIENT_CONNECTION_ID);
        if (server) {
            parameters.parameter(ParameterId.original_destination_connection_id, INITIAL_CONNECTION_ID);
        }
        ByteBuffer encoded = ByteBuffer.allocate(parameters.size());
        parameters.encode(encoded);
        return encoded.array();
    }

    /**
     * Trial-stable ClientHello, client key share, identity, and TLS configuration.
     * Each invocation uses a fresh server handshake.
     * No socket, network task, or timer is created.
     */
    @State(Scope.Thread)
    public static class HandshakeState {
        /**
         * Number of distinct unsupported groups preceding the advertised, usable X25519 group.
         */
        @Param({"0", "32", "1024"})
        public int unknownGroupCount;

        private QuicTls13ServerHandshake.StartParameters serverParameters;
        private QuicTlsServerSessionCache sessionCache;
        private ByteBuffer input;
        private int baseClientHelloSize;
        private QuicTls13ServerHandshake handshake;
        private QuicTls13ServerHandshake.Result result;

        /**
         * Loads the checked-in benchmark identity and prepares one valid ClientHello with its real key share.
         */
        @Setup(Level.Trial)
        public void setUpTrial() {
            ByteBuffer groups = supportedGroups(unknownGroupCount);
            Keys keys = Keys.builder()
                    .keystore(store -> store.keystore(Resource.create("io/helidon/quic/benchmark/server-keystore.p12"))
                            .passphrase("changeit")
                            .keyAlias("server"))
                    .build();
            var parameters = new SSLParameters(new String[] {CIPHER_SUITE}, new String[] {"TLSv1.3"});
            parameters.setApplicationProtocols(new String[] {APPLICATION_PROTOCOL});
            parameters.setNamedGroups(new String[] {"x25519"});
            Tls tls = Tls.builder().privateKey(keys).privateKeyCertChain(keys)
                    .sslParameters(parameters).sessionCacheSize(0).build();
            QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(QuicConfig.create());
            QuicTlsConfigSnapshot config = QuicTlsConfigSnapshot.create(tls, runtimeConfig);
            var client = new HelidonClientQuicTLSEngine(config);
            client.deriveInitialKeysBuffer(VERSION, ByteBuffer.wrap(INITIAL_CONNECTION_ID));
            client.localQuicTransportParametersBuffer(ByteBuffer.wrap(transportParameters(false)));
            QuicTlsClientHelloMessage template = QuicTlsClientHelloMessage.decode(
                    client.handshakeBytesBuffer(KeySpace.INITIAL).orElseThrow());
            if (!template.supportedGroups().equals(List.of(QuicTlsNamedGroup.X25519))) {
                throw new IllegalStateException("Expected a one-group X25519 ClientHello template");
            }
            baseClientHelloSize = template.encode().remaining();
            List<QuicTlsExtension> extensions = template.extensions().stream()
                    .map(extension -> extension.type() == QuicTlsExtensions.SUPPORTED_GROUPS
                            ? QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_GROUPS, groups)
                            : extension)
                    .toList();
            input = QuicTlsClientHelloMessage.create(template.legacyVersion(),
                                                     template.random(),
                                                     template.legacySessionId(),
                                                     template.cipherSuites(),
                                                     template.legacyCompressionMethods(),
                                                     extensions)
                    .encode().asReadOnlyBuffer();
            sessionCache = new QuicTlsServerSessionCache(0, Duration.ZERO);
            serverParameters = new QuicTls13ServerHandshake.StartParameters(config.sslContext(),
                                                                             config.sslParameters(),
                                                                             config.keyManager().orElseThrow(),
                                                                             null,
                                                                             transportParameters(true),
                                                                             config.secureRandom(),
                                                                             null,
                                                                             -1,
                                                                             sessionCache,
                                                                             runtimeConfig.confidentialityLimits());
        }

        /**
         * Creates only the server handshake and restores the prepared input position outside the measured call.
         */
        @Setup(Level.Invocation)
        public void setUpInvocation() {
            input.rewind();
            handshake = QuicTls13ServerHandshake.start(VERSION, serverParameters);
            result = null;
        }

        /**
         * Validates the actual server flight and releases per-invocation handshake state outside the measurement.
         */
        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            try {
                if (!(result instanceof QuicTls13ServerHandshake.ServerFlight flight)) {
                    throw new IllegalStateException("ClientHello did not produce a server flight");
                }
                QuicTlsServerHelloMessage hello =
                        QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(flight.serverHello()));
                if (hello.helloRetryRequest()
                        || hello.cipherSuite() != QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256.codePoint()
                        || hello.keyShare().orElseThrow().namedGroup() != QuicTlsNamedGroup.X25519
                        || !APPLICATION_PROTOCOL.equals(flight.applicationProtocol())
                        || flight.certificate() == null || flight.certificateVerify() == null
                        || flight.finished().length == 0) {
                    throw new IllegalStateException("Server flight did not retain the expected full-handshake shape");
                }
            } finally {
                handshake = null;
                result = null;
            }
        }

        /**
         * Closes the disabled session cache and releases the prepared trial state.
         */
        @TearDown(Level.Trial)
        public void tearDownTrial() {
            handshake = null;
            result = null;
            serverParameters = null;
            input = null;
            if (sessionCache != null) {
                sessionCache.close();
                sessionCache = null;
            }
        }

        ByteBuffer clientHello() {
            return input.asReadOnlyBuffer().rewind();
        }

        int baseClientHelloSize() {
            return baseClientHelloSize;
        }
    }

    /**
     * Unnormalized worker-allocation and operation totals; divide bytes by operations for bytes per ClientHello.
     */
    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class AllocationCounters {
        /**
         * Bytes allocated on the worker inside measured ClientHello-processing calls.
         */
        public long allocatedBytes;
        /**
         * Number of ClientHello-processing calls bracketed by the counter.
         */
        public long handshakeOperations;

        private ThreadMXBean allocationBean;
        private long threadId;

        /**
         * Requires a supported, enabled allocation counter for the actual benchmark worker.
         */
        @Setup(Level.Trial)
        public void setUpTrial() {
            var bean = ManagementFactory.getThreadMXBean();
            if (!(bean instanceof ThreadMXBean extendedBean) || !extendedBean.isThreadAllocatedMemorySupported()) {
                throw new IllegalStateException("This JVM does not support worker-thread allocation counters");
            }
            allocationBean = extendedBean;
            if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
                allocationBean.setThreadAllocatedMemoryEnabled(true);
            }
            threadId = Thread.currentThread().threadId();
            if (allocationBean.getThreadAllocatedBytes(threadId) < 0) {
                throw new IllegalStateException("Worker-thread allocation counter is unavailable");
            }
        }

        /**
         * Starts each iteration with independent event totals.
         */
        @Setup(Level.Iteration)
        public void reset() {
            allocatedBytes = 0;
            handshakeOperations = 0;
        }
    }
}
