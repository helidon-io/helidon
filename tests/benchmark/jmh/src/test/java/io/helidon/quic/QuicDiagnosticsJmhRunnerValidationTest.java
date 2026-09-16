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
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.quic.QuicDiagnosticsJmhBenchmark.AllocationCounters;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.BufferState;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.DeadlineState;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.DeadlineWorkload;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.LogLevel;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.LoggingState;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.QueuedState;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.ReceivePath;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.ReceiveState;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.ReceiverKind;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.SynchronousState;
import io.helidon.quic.QuicDiagnosticsJmhBenchmark.TimerState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Isolated("Changes JUL logger settings")
class QuicDiagnosticsJmhRunnerValidationTest {
    private final QuicDiagnosticsJmhBenchmark benchmark = new QuicDiagnosticsJmhBenchmark();

    static Stream<Arguments> receiveCases() {
        return Stream.of(LogLevel.values())
                .flatMap(level -> Stream.of(ReceivePath.values())
                        .flatMap(path -> Stream.of(ReceiverKind.values())
                                .map(kind -> Arguments.of(level, path, kind))));
    }

    @ParameterizedTest
    @EnumSource(LogLevel.class)
    void timersRemainReusableAndLoggingIsRestored(LogLevel level) {
        Logger logger = Logger.getLogger(QuicTimerQueue.class.getName());
        var previousLevel = logger.getLevel();
        boolean previousParents = logger.getUseParentHandlers();
        var previousHandlers = logger.getHandlers();
        var logging = logging(level);
        var state = new TimerState();
        state.queueDepth = 64;
        try {
            state.setUp(logging);
            try {
                Deadline baseline = state.nextDeadline();
                long messages = logging.messages();
                for (int i = 0; i < 8; i++) {
                    assertThat(benchmark.timerOffer(state), is(true));
                    assertThat(benchmark.timerReschedule(state).isBefore(baseline), is(true));
                    assertThat(state.nextDeadline(), is(baseline));
                }
                if (level != LogLevel.OFF) {
                    assertThat(logging.messages(), greaterThan(messages));
                    assertThat(logging.lastMessage(), notNullValue());
                } else {
                    assertThat(logging.messages(), is(messages));
                }
            } finally {
                state.tearDown();
            }
            assertThat(state.nextDeadline(), is(Deadline.MAX));
        } finally {
            logging.tearDown();
        }
        assertThat(logger.getLevel(), is(previousLevel));
        assertThat(logger.getUseParentHandlers(), is(previousParents));
        assertThat(logger.getHandlers(), is(previousHandlers));
    }

    @ParameterizedTest
    @MethodSource("receiveCases")
    void receiveBatchesDispatchOnMeasuredWorkerAndCloseChannels(LogLevel level,
                                                               ReceivePath path,
                                                               ReceiverKind kind) throws Exception {
        var logging = logging(level);
        var state = new ReceiveState();
        state.receivePath = path;
        state.receiverKind = kind;
        try {
            state.setUp(logging);
            try {
                var counters = new AllocationCounters();
                counters.setUp();
                for (long batch = 1; batch <= 3; batch++) {
                    state.prepareBatch();
                    assertThat(state.buffered(), is(QuicDiagnosticsJmhBenchmark.RECEIVE_BATCH * 64));
                    long messages = logging.messages();
                    long dispatched = batch == 2
                            ? benchmark.receiveDispatchAllocation(state, counters)
                            : benchmark.receiveDispatch(state);
                    assertThat(dispatched, is(batch * QuicDiagnosticsJmhBenchmark.RECEIVE_BATCH));
                    if (level == LogLevel.OFF) {
                        assertThat(logging.messages(), is(messages));
                    } else {
                        assertThat(logging.messages(), greaterThan(messages));
                    }
                    state.finishBatch();
                    assertThat(state.buffered(), is(0));
                }
                assertThat(counters.operations, is((long) QuicDiagnosticsJmhBenchmark.RECEIVE_BATCH));
                assertThat(counters.allocatedBytes, greaterThanOrEqualTo(0L));
            } finally {
                state.tearDown();
            }
            assertThat(state.closed(), is(true));
        } finally {
            logging.tearDown();
        }
    }

    @Test
    void receiveRunnerSelectsOnlyRequestedMethodAndValidatesReceiveParameters() {
        var properties = new Properties();
        properties.setProperty("quic.diagnostics.jmh.methods", "receiveDispatchAllocation");
        properties.setProperty("quic.diagnostics.jmh.logLevel", "OFF,DEBUG,TRACE");
        var options = QuicDiagnosticsJmhRunnerTest.options(properties);
        var include = Pattern.compile(options.getIncludes().getFirst());
        assertThat(include.matcher(QuicDiagnosticsJmhBenchmark.class.getName() + ".receiveDispatchAllocation").matches(),
                   is(true));
        assertThat(include.matcher(QuicDiagnosticsJmhBenchmark.class.getName() + ".receiveDispatch").matches(), is(false));
        assertThat(include.matcher(QuicDiagnosticsJmhBenchmark.class.getName() + ".queuedDatagramsAllocation").matches(),
                   is(false));
        assertThat(options.getParameter("logLevel").get(), contains("OFF", "DEBUG", "TRACE"));
        assertThat(options.getParameter("receivePath").get(), contains("PACKET", "STATELESS_RESET"));
        assertThat(options.getParameter("receiverKind").get(), contains("SOCKET_CONTEXT", "FALLBACK"));
        properties.setProperty("quic.diagnostics.jmh.receivePath", "UNMATCHED");
        assertThrows(IllegalArgumentException.class, () -> QuicDiagnosticsJmhRunnerTest.options(properties));
        properties.setProperty("quic.diagnostics.jmh.receivePath", "");
        assertThrows(IllegalArgumentException.class, () -> QuicDiagnosticsJmhRunnerTest.options(properties));
        properties.remove("quic.diagnostics.jmh.receivePath");
        properties.setProperty("quic.diagnostics.jmh.receiverKind", "CONNECTION");
        assertThrows(IllegalArgumentException.class, () -> QuicDiagnosticsJmhRunnerTest.options(properties));
        properties.setProperty("quic.diagnostics.jmh.receiverKind", "SOCKET_CONTEXT,");
        assertThrows(IllegalArgumentException.class, () -> QuicDiagnosticsJmhRunnerTest.options(properties));
    }

    @ParameterizedTest
    @EnumSource(DeadlineWorkload.class)
    void deadlineWorkloadsAreStable(DeadlineWorkload workload) {
        var logging = logging(LogLevel.OFF);
        var state = new DeadlineState();
        state.deadlineWorkload = workload;
        try {
            state.setUp(logging);
            try {
                Deadline first = benchmark.packetDeadline(state);
                assertThat(benchmark.packetDeadline(state), is(first));
                if (workload == DeadlineWorkload.IDLE) {
                    assertThat(first, is(Deadline.MAX));
                } else {
                    assertThat(first, not(Deadline.MAX));
                    assertThat(first, not(Deadline.MIN));
                }
            } finally {
                state.tearDown();
            }
        } finally {
            logging.tearDown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void actualConnectionReusesOnlyPooledBuffers(boolean pool) {
        var logging = logging(LogLevel.OFF);
        var state = new BufferState();
        state.bufferPool = pool;
        try {
            state.setUp(logging);
            try {
                ByteBuffer first = benchmark.outgoingBuffer(state);
                ByteBuffer second = benchmark.outgoingBuffer(state);
                assertThat(first.isDirect(), is(pool));
                assertThat(second.isDirect(), is(pool));
                assertThat(second.remaining(), is(1200));
                assertThat(second, pool ? sameInstance(first) : not(sameInstance(first)));
                var counters = new AllocationCounters();
                counters.setUp();
                benchmark.outgoingBufferAllocation(state, counters);
                assertThat(counters.operations, is(1L));
                assertThat(counters.allocatedBytes, greaterThanOrEqualTo(0L));
            } finally {
                state.tearDown();
            }
        } finally {
            logging.tearDown();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 64, 4096})
    void queuedBatchesPreserveBacklogAndCloseTheirParkedWriter(int depth) throws Exception {
        var logging = logging(LogLevel.OFF);
        var state = new QueuedState();
        state.queueDepth = depth;
        try {
            state.setUp(logging);
            try {
                // Enough batches to wrap the payload ring, even at maximum backlog.
                int batches = depth / QuicDiagnosticsJmhBenchmark.SEND_BATCH + 3;
                for (int i = 0; i < batches; i++) {
                    assertThat(state.backlog(), is((long) depth));
                    benchmark.queuedDatagrams(state);
                    assertThat(state.backlog(), is((long) depth + QuicDiagnosticsJmhBenchmark.SEND_BATCH));
                    state.finishBatch();
                    assertThat(state.backlog(), is((long) depth));
                }
            } finally {
                state.tearDown();
            }
            assertThat(state.writerTerminated(), is(true));
            assertThat(state.dropped(), is((long) depth));
        } finally {
            logging.tearDown();
        }
    }

    @Test
    void synchronousSendsCompleteAndRecycleThePayload() throws Exception {
        var logging = logging(LogLevel.TRACE);
        var state = new SynchronousState();
        try {
            state.setUp(logging);
            try {
                for (long i = 1; i <= 16; i++) {
                    assertThat(benchmark.synchronousDatagram(state), is(i));
                    assertThat(state.payloadRemaining(), is(1200));
                }
            } finally {
                state.tearDown();
            }
        } finally {
            logging.tearDown();
        }
    }

    @Test
    void runnerRestrictsSelectionAndRejectsUnboundedParameters() {
        var properties = new Properties();
        properties.setProperty("quic.diagnostics.jmh.methods", "queuedDatagramsAllocation");
        var options = QuicDiagnosticsJmhRunnerTest.options(properties);
        var include = Pattern.compile(options.getIncludes().getFirst());
        assertThat(include.matcher(QuicDiagnosticsJmhBenchmark.class.getName() + ".queuedDatagramsAllocation").matches(),
                   is(true));
        assertThat(include.matcher(QuicDiagnosticsJmhBenchmark.class.getName() + ".queuedDatagrams").matches(), is(false));
        assertThat(include.matcher(QuicPathJmhBenchmark.class.getName() + ".establishedPathDatagramSend").matches(),
                   is(false));
        properties.setProperty("quic.diagnostics.jmh.queueDepth", "4097");
        assertThrows(IllegalArgumentException.class, () -> QuicDiagnosticsJmhRunnerTest.options(properties));
        properties.remove("quic.diagnostics.jmh.queueDepth");
        properties.setProperty("quic.diagnostics.jmh.gcProfiler", "true");
        assertThrows(IllegalArgumentException.class, () -> QuicDiagnosticsJmhRunnerTest.options(properties));
        properties.remove("quic.diagnostics.jmh.gcProfiler");
        properties.setProperty("quic.diagnostics.jmh.methods", ".*");
        assertThrows(IllegalArgumentException.class, () -> QuicDiagnosticsJmhRunnerTest.options(properties));
    }

    private static LoggingState logging(LogLevel level) {
        var state = new LoggingState();
        state.logLevel = level;
        state.setUp();
        return state;
    }
}
