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

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.net.ssl.SSLParameters;

import io.helidon.common.tls.Tls;
import io.helidon.quic.QuicTLSEngine.HandshakeState;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.frame.StreamFrame;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacketDecoder;
import io.helidon.quic.packet.QuicPacketDecoder.IncomingQuicPacket;
import io.helidon.quic.packet.QuicPacketEncoder;
import io.helidon.quic.spi.QuicPacketTLSEngine;

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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures 1-RTT codec diagnostics with the production TLS engines and AES-128-GCM packet protection.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class QuicCodecDiagnosticsJmhBenchmark {
    static final long FIRST_PACKET_NUMBER = 0x0102_0304L;
    static final long STREAM_ID = 256;
    static final long STREAM_OFFSET = 4096;
    static final String LOG_TAG = "codec-socket codec-connection";

    private static final String KEYSTORE = "/io/helidon/quic/benchmark/server-keystore.p12";
    private static final String PASSWORD = "changeit";
    private static final String CIPHER_SUITE = "TLS_AES_128_GCM_SHA256";
    private static final QuicConnectionId CONNECTION_ID =
            PeerConnectionId.create(ByteBuffer.wrap(new byte[] {3, 5, 7, 11, 13, 17, 19, 23}));

    /**
     * Creates and encrypts one packet, including header protection, into a reusable heap buffer.
     *
     * @param state prepared codec inputs
     * @return protected packet bytes, with position after the packet
     */
    @Benchmark
    public ByteBuffer encodeOneRtt(CodecState state) {
        var packet = state.encoder.newOneRttPacket(CONNECTION_ID,
                                                  state.packetNumber,
                                                  state.packetNumber - 256,
                                                  state.frames,
                                                  state.sending,
                                                  LOG_TAG);
        state.encoder.encode(packet, state.output, state.sending, LOG_TAG);
        return state.output;
    }

    /**
     * Removes protection, authenticates, decrypts, and parses one transport-owned packet.
     *
     * @param state restored protected input
     * @return decoded packet and its STREAM frame
     */
    @Benchmark
    public IncomingQuicPacket decodeOneRtt(CodecState state) {
        return state.decoder.decodeOwned(state.input, state.receiving, LOG_TAG).orElseThrow();
    }

    /**
     * Measures only the worker allocation for packet creation and encoding.
     *
     * @param state prepared codec inputs
     * @param counters worker allocation counters
     * @return protected packet bytes
     */
    @Benchmark
    public ByteBuffer encodeOneRttAllocation(CodecState state, AllocationCounters counters) {
        long before = counters.before();
        ByteBuffer result = encodeOneRtt(state);
        counters.record(before);
        return result;
    }

    /**
     * Measures only the worker allocation for packet decoding.
     *
     * @param state restored protected input
     * @param counters worker allocation counters
     * @return decoded packet
     */
    @Benchmark
    public IncomingQuicPacket decodeOneRttAllocation(CodecState state, AllocationCounters counters) {
        long before = counters.before();
        IncomingQuicPacket result = decodeOneRtt(state);
        counters.record(before);
        return result;
    }

    private static KeyStore keyStore() throws Exception {
        var store = KeyStore.getInstance("PKCS12");
        try (InputStream input = QuicCodecDiagnosticsJmhBenchmark.class.getResourceAsStream(KEYSTORE)) {
            if (input == null) {
                throw new IllegalStateException("Missing codec benchmark key store: " + KEYSTORE);
            }
            store.load(input, PASSWORD.toCharArray());
        }
        return store;
    }

    private static void transfer(QuicPacketTLSEngine from, QuicPacketTLSEngine to, KeySpace keySpace) {
        int messages = 0;
        while (from.handshakeState() == HandshakeState.NEED_SEND_CRYPTO && from.currentSendKeySpace() == keySpace) {
            var bytes = from.handshakeBytesBuffer(keySpace).orElseThrow();
            if (++messages > 16) {
                throw new IllegalStateException("Codec fixture handshake exceeded its message bound");
            }
            to.consumeHandshakeBytesBuffer(keySpace, bytes);
        }
        if (messages == 0) {
            throw new IllegalStateException("Codec fixture has no handshake message in " + keySpace);
        }
    }

    /**
     * Codec diagnostic level; raw protocol data is always disabled.
     */
    public enum LogLevel {
        /**
         * Disable codec diagnostics.
         */
        OFF,
        /**
         * Enable DEBUG diagnostics with a bounded in-memory handler.
         */
        DEBUG
    }

    /**
     * Fork-wide diagnostic configuration, restored when the trial ends.
     */
    @State(Scope.Benchmark)
    public static class LoggingState {
        private final CaptureHandler handler = new CaptureHandler();

        /**
         * Level used by both codec loggers.
         */
        @Param({"OFF", "DEBUG"})
        public LogLevel logLevel;

        private List<LoggerSetting> settings;

        /**
         * Installs codec handlers retaining only message counts and the latest message.
         */
        @Setup(Level.Trial)
        public void setUp() {
            settings = List.of(new LoggerSetting("io.helidon.quic"),
                               new LoggerSetting(QuicPacketEncoder.class.getName()),
                               new LoggerSetting(QuicPacketDecoder.class.getName()));
            settings.getFirst().install(LogLevel.OFF, handler);
            for (int i = 1; i < settings.size(); i++) {
                LoggerSetting setting = settings.get(i);
                setting.install(logLevel, handler);
                System.Logger logger = System.getLogger(setting.logger.getName());
                if (logger.isLoggable(System.Logger.Level.DEBUG) != (logLevel == LogLevel.DEBUG)
                        || logger.isLoggable(System.Logger.Level.TRACE)) {
                    tearDown();
                    throw new IllegalStateException("Codec System.Logger is not using the configured JUL level");
                }
            }
        }

        /**
         * Restores all modified logger settings.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            for (LoggerSetting setting : settings) {
                setting.restore(handler);
            }
        }

        long messages() {
            return handler.messages.get();
        }

        String lastMessage() {
            return handler.lastMessage;
        }
    }

    /**
     * Actual codec and TLS state, with all input preparation outside each measured operation.
     */
    @State(Scope.Thread)
    public static class CodecState {
        private final QuicPacketEncoder encoder = QuicPacketEncoder.of(QuicVersion.QUIC_V1);
        private final QuicPacketDecoder decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);

        /**
         * STREAM data size; packet and frame headers plus the authentication tag are additional bytes.
         */
        @Param({"64", "1200"})
        public int payloadSize;

        private QuicTlsConfigSnapshot clientConfig;
        private QuicTlsConfigSnapshot serverConfig;
        private CodecContext sending;
        private CodecContext receiving;
        private List<StreamFrame> frames;
        private byte[] payload;
        private byte[] ciphertext;
        private ByteBuffer input;
        private ByteBuffer output;
        private long nextPacketNumber;
        private long packetNumber;

        /**
         * Loads the existing benchmark certificate and prepares immutable STREAM data and heap buffers.
         *
         * @param logging active fork logging state
         * @throws Exception if TLS material cannot be loaded
         */
        @Setup(Level.Trial)
        public void setUp(LoggingState logging) throws Exception {
            if (payloadSize != 64 && payloadSize != 1200) {
                throw new IllegalArgumentException("payloadSize must be 64 or 1200");
            }
            var store = keyStore();
            var privateKey = (PrivateKey) store.getKey("server", PASSWORD.toCharArray());
            List<X509Certificate> chain = Arrays.stream(store.getCertificateChain("server"))
                    .map(X509Certificate.class::cast)
                    .toList();
            var parameters = new SSLParameters(new String[] {CIPHER_SUITE}, new String[] {"TLSv1.3"});
            parameters.setApplicationProtocols(new String[] {"h3"});
            parameters.setNamedGroups(new String[] {"x25519"});
            var serverTls = Tls.builder()
                    .privateKey(privateKey)
                    .privateKeyCertChain(chain)
                    .sslParameters(parameters)
                    .sessionCacheSize(0)
                    .build();
            var clientTls = Tls.builder()
                    .trust(chain)
                    .sslParameters(parameters)
                    .sessionCacheSize(0)
                    .build();
            var runtimeConfig = QuicRuntimeConfig.create(QuicConfig.create());
            clientConfig = QuicTlsConfigSnapshot.create(clientTls, runtimeConfig);
            serverConfig = QuicTlsConfigSnapshot.create(serverTls, runtimeConfig);
            payload = new byte[payloadSize];
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (37 + 31 * i);
            }
            frames = List.of(StreamFrame.create(STREAM_ID,
                                               STREAM_OFFSET,
                                               payloadSize,
                                               false,
                                               ByteBuffer.wrap(payload).asReadOnlyBuffer()));
            output = ByteBuffer.allocate(payloadSize + 64);
        }

        /**
         * Completes an in-memory TLS handshake and prepares one protected decode input using fresh traffic keys.
         */
        @Setup(Level.Iteration)
        public void setUpIteration() {
            var client = new HelidonClientQuicTLSEngine(clientConfig);
            var server = new HelidonServerQuicTLSEngine(serverConfig);
            client.deriveInitialKeysBuffer(QuicVersion.QUIC_V1, CONNECTION_ID.asReadOnlyBuffer());
            server.deriveInitialKeysBuffer(QuicVersion.QUIC_V1, CONNECTION_ID.asReadOnlyBuffer());
            client.localQuicTransportParametersBuffer(ByteBuffer.allocate(0));
            server.localQuicTransportParametersBuffer(ByteBuffer.allocate(0));
            server.versionNegotiated(QuicVersion.QUIC_V1);
            transfer(client, server, KeySpace.INITIAL);
            client.versionNegotiated(QuicVersion.QUIC_V1);
            transfer(server, client, KeySpace.INITIAL);
            transfer(server, client, KeySpace.HANDSHAKE);
            transfer(client, server, KeySpace.HANDSHAKE);
            if (!client.isTLSHandshakeComplete() || !server.isTLSHandshakeComplete()
                    || !client.tryReceiveHandshakeDone() || !server.tryMarkHandshakeDone()
                    || !CIPHER_SUITE.equals(client.session().getCipherSuite())
                    || !CIPHER_SUITE.equals(server.session().getCipherSuite())) {
                throw new IllegalStateException("Codec fixture did not establish the expected AES-128-GCM session");
            }
            sending = new CodecContext(client);
            receiving = new CodecContext(server);
            sending.packetNumber = FIRST_PACKET_NUMBER;
            receiving.packetNumber = FIRST_PACKET_NUMBER;
            client.oneRttContext(sending);
            server.oneRttContext(receiving);
            var packet = encoder.newOneRttPacket(CONNECTION_ID,
                                                 FIRST_PACKET_NUMBER,
                                                 FIRST_PACKET_NUMBER - 256,
                                                 frames,
                                                 sending,
                                                 LOG_TAG);
            output.clear();
            encoder.encode(packet, output, sending, LOG_TAG);
            ciphertext = Arrays.copyOf(output.array(), output.position());
            input = ByteBuffer.allocate(ciphertext.length);
            nextPacketNumber = FIRST_PACKET_NUMBER + 1;
        }

        /**
         * Restores mutable decode storage and advances the encryption nonce without allocating input buffers.
         */
        @Setup(Level.Invocation)
        public void prepareInvocation() {
            if (nextPacketNumber - FIRST_PACKET_NUMBER >= QuicAeadLimits.DEFAULT_AES_GCM_CONFIDENTIALITY_LIMIT) {
                throw new IllegalStateException("Shorten the codec iteration to remain below the AES-GCM key limit");
            }
            packetNumber = nextPacketNumber++;
            sending.packetNumber = packetNumber;
            receiving.packetNumber = FIRST_PACKET_NUMBER;
            input.clear().put(ciphertext).flip();
            output.clear();
        }

        long packetNumber() {
            return packetNumber;
        }

        byte[] expectedPayload() {
            return payload.clone();
        }

        int inputRemaining() {
            return input.remaining();
        }

        IncomingQuicPacket decodeEncoded() {
            byte[] encoded = Arrays.copyOf(output.array(), output.position());
            receiving.packetNumber = packetNumber;
            return decoder.decodeOwned(ByteBuffer.wrap(encoded), receiving, LOG_TAG).orElseThrow();
        }
    }

    /**
     * Worker allocation event totals; divide allocated bytes by operations to obtain bytes per packet.
     */
    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class AllocationCounters {
        /**
         * Bytes allocated during measured codec operations.
         */
        public long allocatedBytes;

        /**
         * Number of measured packets.
         */
        public long operations;

        private ThreadMXBean bean;
        private long threadId;

        /**
         * Enables allocation accounting on the current worker thread.
         */
        @Setup(Level.Trial)
        public void setUp() {
            if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean extended)
                    || !extended.isThreadAllocatedMemorySupported()) {
                throw new IllegalStateException("Worker-thread allocation measurement is unavailable");
            }
            bean = extended;
            if (!bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            threadId = Thread.currentThread().threadId();
            if (before() < 0) {
                throw new IllegalStateException("Worker-thread allocation counter is unavailable");
            }
        }

        private long before() {
            return bean.getThreadAllocatedBytes(threadId);
        }

        private void record(long before) {
            long allocated = bean.getThreadAllocatedBytes(threadId) - before;
            if (before < 0 || allocated < 0) {
                throw new IllegalStateException("Worker-thread allocation counter moved backwards");
            }
            allocatedBytes += allocated;
            operations++;
        }
    }

    private static final class CodecContext implements CodingContext, QuicOneRttContext {
        private final QuicTLSEngine engine;
        private long packetNumber;

        private CodecContext(QuicTLSEngine engine) {
            this.engine = engine;
        }

        @Override
        public long largestProcessedPN(PacketNumberSpace packetSpace) {
            return packetNumber - 1;
        }

        @Override
        public long largestAckedPN(PacketNumberSpace packetSpace) {
            return packetNumber - 256;
        }

        @Override
        public int connectionIdLength() {
            return CONNECTION_ID.length();
        }

        @Override
        public int writePacket(QuicPacket packet, ByteBuffer buffer) {
            throw new UnsupportedOperationException("The benchmark calls the encoder directly");
        }

        @Override
        public Optional<QuicPacket> parsePacket(ByteBuffer src) {
            throw new UnsupportedOperationException("The benchmark calls the decoder directly");
        }

        @Override
        public QuicConnectionId originalServerConnId() {
            return CONNECTION_ID;
        }

        @Override
        public QuicTLSEngine tlsEngine() {
            return engine;
        }

        @Override
        public String logTag() {
            return LOG_TAG;
        }

        @Override
        public boolean verifyToken(QuicConnectionId destinationID, byte[] token) {
            throw new UnsupportedOperationException("The benchmark uses only 1-RTT packets");
        }

        @Override
        public long largestPeerAcknowledgedPacketNumber() {
            return packetNumber - 256;
        }
    }

    private static final class CaptureHandler extends Handler {
        private final AtomicLong messages = new AtomicLong();
        private volatile String lastMessage;

        @Override
        public void publish(LogRecord record) {
            messages.incrementAndGet();
            lastMessage = record.getMessage();
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static final class LoggerSetting {
        private final Logger logger;
        private final java.util.logging.Level level;
        private final Handler[] handlers;
        private final boolean parentHandlers;

        private LoggerSetting(String name) {
            logger = Logger.getLogger(name);
            level = logger.getLevel();
            handlers = logger.getHandlers();
            parentHandlers = logger.getUseParentHandlers();
        }

        private void install(LogLevel level, Handler handler) {
            for (Handler existing : handlers) {
                logger.removeHandler(existing);
            }
            logger.setUseParentHandlers(false);
            logger.addHandler(handler);
            logger.setLevel(level == LogLevel.OFF ? java.util.logging.Level.OFF : java.util.logging.Level.FINE);
        }

        private void restore(Handler handler) {
            logger.removeHandler(handler);
            for (Handler existing : handlers) {
                logger.addHandler(existing);
            }
            logger.setUseParentHandlers(parentHandlers);
            logger.setLevel(level);
        }
    }
}
