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
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.quic.QuicCodecDiagnosticsJmhBenchmark.AllocationCounters;
import io.helidon.quic.QuicCodecDiagnosticsJmhBenchmark.CodecState;
import io.helidon.quic.QuicCodecDiagnosticsJmhBenchmark.LogLevel;
import io.helidon.quic.QuicCodecDiagnosticsJmhBenchmark.LoggingState;
import io.helidon.quic.frame.StreamFrame;
import io.helidon.quic.packet.OneRttPacket;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacketDecodeException;
import io.helidon.quic.packet.QuicPacketDecoder;
import io.helidon.quic.packet.QuicPacketEncoder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.options.TimeValue;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Isolated("Changes codec JUL logger settings")
class QuicCodecDiagnosticsJmhRunnerValidationTest {
    private static final String PREFIX = "quic.codec.diagnostics.jmh.";
    private static final List<String> METHODS = List.of("encodeOneRtt", "decodeOneRtt",
                                                       "encodeOneRttAllocation", "decodeOneRttAllocation");

    private final QuicCodecDiagnosticsJmhBenchmark benchmark = new QuicCodecDiagnosticsJmhBenchmark();

    static Stream<Arguments> codecCases() {
        return METHODS.stream()
                .flatMap(method -> Stream.of(64, 1200)
                        .flatMap(size -> Stream.of(LogLevel.values())
                                .map(level -> Arguments.of(method, size, level))));
    }

    @ParameterizedTest
    @MethodSource("codecCases")
    void namedMethodsPreservePacketsAndRestoreLogging(String method, int size, LogLevel level) throws Exception {
        List<LoggerSnapshot> previous = loggerSnapshots();
        var logging = new LoggingState();
        logging.logLevel = level;
        logging.setUp();
        try {
            var state = new CodecState();
            state.payloadSize = size;
            state.setUp(logging);
            var counters = new AllocationCounters();
            counters.setUp();
            boolean encoding = method.startsWith("encode");
            boolean allocation = method.endsWith("Allocation");
            long operations = 0;
            for (int iteration = 0; iteration < 2; iteration++) {
                state.setUpIteration();
                byte[] previousCiphertext = null;
                for (int invocation = 1; invocation <= 3; invocation++) {
                    state.prepareInvocation();
                    assertThat(state.packetNumber(), is(QuicCodecDiagnosticsJmhBenchmark.FIRST_PACKET_NUMBER + invocation));
                    long messages = logging.messages();
                    long allocatedBytes = counters.allocatedBytes;
                    QuicPacket decoded;
                    int packetSize;
                    if (encoding) {
                        ByteBuffer encoded = allocation
                                ? benchmark.encodeOneRttAllocation(state, counters)
                                : benchmark.encodeOneRtt(state);
                        assertLogging(logging, level, messages, 2);
                        packetSize = encoded.position();
                        assertThat(packetSize, greaterThan(size + QuicPacketProtection.AUTH_TAG_SIZE));
                        byte[] ciphertext = new byte[packetSize];
                        encoded.duplicate().flip().get(ciphertext);
                        if (previousCiphertext != null) {
                            assertThat("A new packet number must produce a different protected packet",
                                       Arrays.equals(ciphertext, previousCiphertext), is(false));
                        }
                        previousCiphertext = ciphertext;
                        decoded = state.decodeEncoded();
                    } else {
                        packetSize = state.inputRemaining();
                        decoded = allocation
                                ? benchmark.decodeOneRttAllocation(state, counters)
                                : benchmark.decodeOneRtt(state);
                        assertLogging(logging, level, messages, 6);
                        assertThat("Decoder consumed the prepared packet", state.inputRemaining(), is(0));
                    }
                    assertPacket(decoded,
                                 encoding ? state.packetNumber() : QuicCodecDiagnosticsJmhBenchmark.FIRST_PACKET_NUMBER,
                                 packetSize,
                                 state.expectedPayload());
                    if (allocation) {
                        assertThat(counters.operations, is(++operations));
                        assertThat(counters.allocatedBytes, greaterThan(allocatedBytes));
                    } else {
                        assertThat(counters.operations, is(0L));
                        assertThat(counters.allocatedBytes, is(0L));
                    }
                }
            }
        } finally {
            logging.tearDown();
        }
        previous.forEach(LoggerSnapshot::assertRestored);
    }

    @Test
    void corruptedCiphertextFailsAuthentication() throws Exception {
        var logging = new LoggingState();
        logging.logLevel = LogLevel.OFF;
        logging.setUp();
        try {
            var state = new CodecState();
            state.payloadSize = 64;
            state.setUp(logging);
            state.setUpIteration();
            state.prepareInvocation();
            ByteBuffer packet = benchmark.encodeOneRtt(state);
            int last = packet.position() - 1;
            packet.put(last, (byte) (packet.get(last) ^ 1));
            assertThrows(QuicPacketDecodeException.class, state::decodeEncoded);
        } finally {
            logging.tearDown();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"encodeOneRtt", "decodeOneRtt", "encodeOneRttAllocation", "decodeOneRttAllocation"})
    void runnerIncludesOnlyTheNamedMethod(String method) {
        var properties = properties(method);
        var options = QuicCodecDiagnosticsJmhRunnerTest.options(properties);
        assertThat(options.getIncludes().size(), is(1));
        var include = Pattern.compile(options.getIncludes().getFirst());
        for (String candidate : METHODS) {
            assertThat(candidate,
                       include.matcher(QuicCodecDiagnosticsJmhBenchmark.class.getName() + "." + candidate).matches(),
                       is(candidate.equals(method)));
        }
        assertThat(include.matcher(QuicDiagnosticsJmhBenchmark.class.getName() + "." + method).matches(), is(false));
        assertThat(include.matcher(QuicCodecDiagnosticsJmhBenchmark.class.getName() + "." + method + "Extra").matches(),
                   is(false));
        assertThat(options.getThreads().get(), is(1));
        assertThat(options.getForkCount().get(), is(1));
        assertThat(options.getWarmupForkCount().get(), is(0));
        assertThat(options.getWarmupIterations().get(), is(3));
        assertThat(options.getMeasurementIterations().get(), is(5));
        assertThat(options.getParameter("payloadSize").get(), contains("64", "1200"));
        assertThat(options.getParameter("logLevel").get(), contains("OFF", "DEBUG"));
        assertThat(options.getProfilers().isEmpty(), is(true));
        assertThat(options.shouldFailOnError().get(), is(true));
        assertThat(options.getResultFormat().get(), is(ResultFormatType.JSON));
    }

    @Test
    void runnerSupportsExplicitBoundedComparisonSettings() {
        var properties = properties("encodeOneRtt,decodeOneRtt");
        properties.setProperty(PREFIX + "payloadSize", "1200");
        properties.setProperty(PREFIX + "logLevel", "OFF");
        properties.setProperty(PREFIX + "forks", "2");
        properties.setProperty(PREFIX + "warmupMillis", "500");
        properties.setProperty(PREFIX + "measurementMillis", "500");
        properties.setProperty(PREFIX + "result", "./target/quic-codec-timing-1.json");
        properties.setProperty(PREFIX + "output", "./target/quic-codec-timing-1.log");
        var options = QuicCodecDiagnosticsJmhRunnerTest.options(properties);
        var include = Pattern.compile(options.getIncludes().getFirst());
        assertThat(include.matcher(QuicCodecDiagnosticsJmhBenchmark.class.getName() + ".encodeOneRtt").matches(), is(true));
        assertThat(include.matcher(QuicCodecDiagnosticsJmhBenchmark.class.getName() + ".decodeOneRtt").matches(), is(true));
        assertThat(include.matcher(QuicCodecDiagnosticsJmhBenchmark.class.getName() + ".encodeOneRttAllocation").matches(),
                   is(false));
        assertThat(options.getParameter("payloadSize").get(), contains("1200"));
        assertThat(options.getParameter("logLevel").get(), contains("OFF"));
        assertThat(options.getForkCount().get(), is(2));
        assertThat(options.getWarmupTime().get(), is(TimeValue.milliseconds(500)));
        assertThat(options.getMeasurementTime().get(), is(TimeValue.milliseconds(500)));
        assertThat(options.getResult().get(), is("./target/quic-codec-timing-1.json"));
        assertThat(options.getOutput().get(), is("./target/quic-codec-timing-1.log"));
    }

    @ParameterizedTest
    @CsvSource({
            "methods,.*", "methods,receiveDispatch", "methods,'encodeOneRtt,'", "methods,'encodeOneRtt,encodeOneRtt'",
            "payloadSize,0", "payloadSize,65", "payloadSize,65527", "payloadSize,'64,64'",
            "logLevel,TRACE", "logLevel,INFO", "logLevel,'OFF,'",
            "threads,0", "threads,2", "forks,0", "forks,5",
            "warmupIterations,0", "warmupIterations,11", "measurementIterations,0", "measurementIterations,11",
            "warmupMillis,499", "warmupMillis,10001", "measurementMillis,499", "measurementMillis,10001",
            "gcProfiler,true", "gcProfiler,yes"
    })
    void runnerRejectsUnsupportedOrUnboundedRequests(String name, String value) {
        var properties = properties("encodeOneRtt");
        properties.setProperty(PREFIX + name, value);
        assertThrows(IllegalArgumentException.class, () -> QuicCodecDiagnosticsJmhRunnerTest.options(properties));
    }

    @Test
    void runnerRequiresExplicitMethods() {
        assertThrows(IllegalArgumentException.class,
                     () -> QuicCodecDiagnosticsJmhRunnerTest.options(new Properties()));
    }

    private static Properties properties(String methods) {
        var properties = new Properties();
        properties.setProperty(PREFIX + "methods", methods);
        return properties;
    }

    private static List<LoggerSnapshot> loggerSnapshots() {
        return Stream.of("io.helidon.quic", QuicPacketEncoder.class.getName(), QuicPacketDecoder.class.getName())
                .map(Logger::getLogger)
                .map(logger -> new LoggerSnapshot(logger,
                                                   logger.getLevel(),
                                                   logger.getUseParentHandlers(),
                                                   logger.getHandlers()))
                .toList();
    }

    private static void assertLogging(LoggingState logging, LogLevel level, long before, int enabledMessages) {
        assertThat("Only the measured codec operation contributes these messages",
                   logging.messages() - before, is(level == LogLevel.OFF ? 0L : enabledMessages));
        if (level == LogLevel.DEBUG) {
            assertThat(logging.lastMessage(), startsWith("[" + QuicCodecDiagnosticsJmhBenchmark.LOG_TAG + "] "));
        }
    }

    private static void assertPacket(QuicPacket packet, long packetNumber, int packetSize, byte[] payload) {
        assertThat(packet, instanceOf(OneRttPacket.class));
        assertThat(packet.packetType(), is(QuicPacket.PacketType.ONERTT));
        assertThat(packet.headersType(), is(QuicPacket.HeadersType.SHORT));
        assertThat(packet.numberSpace(), is(QuicPacket.PacketNumberSpace.APPLICATION));
        assertThat(packet.packetNumber(), is(packetNumber));
        assertThat(packet.size(), is(packetSize));
        assertThat(((OneRttPacket) packet).keyPhase(), is(0));
        assertThat(((OneRttPacket) packet).spin(), is(0));
        assertThat(packet.destinationId().asReadOnlyBuffer(),
                   is(ByteBuffer.wrap(new byte[] {3, 5, 7, 11, 13, 17, 19, 23})));
        assertThat(packet.frames().size(), is(1));
        assertThat(packet.frames().getFirst(), instanceOf(StreamFrame.class));
        var frame = (StreamFrame) packet.frames().getFirst();
        assertThat(frame.streamId(), is(QuicCodecDiagnosticsJmhBenchmark.STREAM_ID));
        assertThat(frame.offset(), is(QuicCodecDiagnosticsJmhBenchmark.STREAM_OFFSET));
        assertThat(frame.dataLength(), is(payload.length));
        assertThat(frame.isLast(), is(false));
        assertThat(frame.hasLength(), is(true));
        assertThat(frame.payload(), is(ByteBuffer.wrap(payload)));
    }

    private record LoggerSnapshot(Logger logger, Level level, boolean parentHandlers, Handler[] handlers) {
        private void assertRestored() {
            assertThat(logger.getName() + " level", logger.getLevel(), is(level));
            assertThat(logger.getName() + " parent handlers", logger.getUseParentHandlers(), is(parentHandlers));
            assertThat(logger.getName() + " handlers", logger.getHandlers(), is(handlers));
        }
    }
}
