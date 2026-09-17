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

import java.util.Properties;

import io.helidon.quic.QuicPathJmhBenchmark.AckAllocationCounters;
import io.helidon.quic.QuicPathJmhBenchmark.AckPacketSpaceLifecycle;
import io.helidon.quic.QuicPathJmhBenchmark.AckRecoveryScenario;
import io.helidon.quic.QuicPathJmhBenchmark.AckRecoveryState;
import io.helidon.quic.QuicPathJmhBenchmark.AckState;
import io.helidon.quic.QuicPathJmhBenchmark.AckWorkload;
import io.helidon.quic.QuicRttEstimator.QuicRttEstimatorState;
import io.helidon.quic.frame.AckFrame;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicPathJmhRunnerValidationTest {
    private static final String PREFIX = "quic.path.jmh.";
    private static final String ACK_INCLUDE = "^io\\.helidon\\.quic\\.QuicPathJmhBenchmark\\.establishedPathAckRanges$";
    private static final String ACK_RECOVERY_INCLUDE = "^io\\.helidon\\.quic\\.QuicPathJmhBenchmark\\.ackRecovery$";

    @ParameterizedTest
    @CsvSource({"64,1", "64,32", "4096,1", "4096,32", "4096,1024", "13981,1", "13981,32", "13981,1024"})
    void ackRangesCoverTheWholeFlightWithPositiveGaps(int inFlightPackets, int ackRangeCount) {
        long firstPacketNumber = 10_000;
        var ranges = AckState.createRanges(inFlightPackets, ackRangeCount);
        AckFrame frame = AckFrame.create(firstPacketNumber + inFlightPackets - 1, 0, ranges);
        assertThat(frame.smallestAcknowledged(), is(firstPacketNumber));
        assertThat(frame.largestAcknowledged(), is(firstPacketNumber + inFlightPackets - 1));
        assertThat(ranges.size(), is(ackRangeCount));

        int acknowledged = 0;
        int observedRanges = 0;
        boolean previousAcknowledged = false;
        for (long packetNumber = firstPacketNumber; packetNumber < firstPacketNumber + inFlightPackets; packetNumber++) {
            boolean currentAcknowledged = frame.isAcknowledging(packetNumber);
            if (currentAcknowledged) {
                acknowledged++;
                if (!previousAcknowledged) {
                    observedRanges++;
                }
            }
            previousAcknowledged = currentAcknowledged;
        }
        assertThat(acknowledged, is(ackRangeCount == 1 ? inFlightPackets : (inFlightPackets + 1) / 2));
        assertThat(observedRanges, is(ackRangeCount));
        assertThat(frame.isAcknowledging(firstPacketNumber - 1), is(false));
        assertThat(frame.isAcknowledging(firstPacketNumber + inFlightPackets), is(false));
    }

    @ParameterizedTest
    @CsvSource({"64,1,REUSED,NEW_ACK", "64,32,REUSED,NEW_ACK", "4096,1024,REUSED,NEW_ACK",
            "13981,1024,REUSED,NEW_ACK", "64,1,FIRST_ACK,NEW_ACK", "64,32,FIRST_ACK,NEW_ACK",
            "13981,1024,FIRST_ACK,NEW_ACK", "13981,1,REUSED,DUPLICATE", "13981,1024,REUSED,DUPLICATE"})
    void repeatedInvocationsPreserveTheRequestedLifecycle(int inFlightPackets,
                                                         int ackRangeCount,
                                                         AckPacketSpaceLifecycle lifecycle,
                                                         AckWorkload workload) {
        var benchmark = new QuicPathJmhBenchmark();
        var state = new AckState();
        state.inFlightPackets = inFlightPackets;
        state.ackRangeCount = ackRangeCount;
        state.ackPacketSpaceLifecycle = lifecycle;
        state.ackWorkload = workload;
        try {
            state.setUpTrial();
            for (int invocation = 0; invocation < 3; invocation++) {
                state.setUpInvocation();
                try {
                    long expectedLargest = lifecycle == AckPacketSpaceLifecycle.FIRST_ACK || workload == AckWorkload.DUPLICATE
                            ? inFlightPackets - 1L : (invocation + 2L) * inFlightPackets - 1;
                    assertThat(benchmark.establishedPathAckRanges(state), is(expectedLargest));
                } finally {
                    state.tearDownInvocation();
                }
            }
        } finally {
            state.tearDownTrial();
        }
    }

    @Test
    void allocationCountersCountOnlyInvokedAckOperations() {
        var benchmark = new QuicPathJmhBenchmark();
        var state = new AckState();
        var counters = new AckAllocationCounters();
        state.inFlightPackets = 64;
        state.ackRangeCount = 32;
        state.ackPacketSpaceLifecycle = AckPacketSpaceLifecycle.REUSED;
        state.ackWorkload = AckWorkload.NEW_ACK;
        try {
            counters.setUp();
            state.setUpTrial();
            assertThat(counters.ackOperations, is(0L));
            for (int invocation = 0; invocation < 2; invocation++) {
                state.setUpInvocation();
                try {
                    assertThat(benchmark.establishedPathAckRangesAllocation(state, counters),
                               is((invocation + 2L) * state.inFlightPackets - 1));
                } finally {
                    state.tearDownInvocation();
                }
            }
            assertThat(counters.ackOperations, is(2L));
            assertThat(counters.allocatedBytes, greaterThanOrEqualTo(0L));
        } finally {
            state.tearDownTrial();
        }
    }

    @ParameterizedTest
    @EnumSource(AckRecoveryScenario.class)
    void recoveryInvocationsHaveTheRequestedRoleAndAPreviousRttSample(AckRecoveryScenario scenario) {
        var benchmark = new QuicPathJmhBenchmark();
        var state = new AckRecoveryState();
        state.ackRecoveryScenario = scenario;
        try {
            state.setUpTrial();
            for (int invocation = 0; invocation < 3; invocation++) {
                state.setUpInvocation();
                try {
                    assertThat(state.clientMode(), is(scenario != AckRecoveryScenario.SERVER_INITIAL));
                    assertThat(state.packetNumberSpace(), is(scenario == AckRecoveryScenario.APPLICATION
                            ? PacketNumberSpace.APPLICATION : PacketNumberSpace.INITIAL));
                    assertThat(state.rttState(), is(QuicRttEstimatorState.create(10_000, 10_000, 10_000, 5_000, 1)));
                    assertThat(state.ptoBackoff(), is(4L));
                    long packetNumber = state.packetNumber();
                    assertThat(packetNumber, is(1L));
                    assertThat(benchmark.ackRecovery(state), is(packetNumber));
                    assertThat(state.rttState().rttSampleCount(), is(2L));
                } finally {
                    // The shared benchmark must also run against the former server-Initial backoff behavior.
                    state.tearDownInvocation();
                }
            }
        } finally {
            state.tearDownTrial();
        }
    }

    @ParameterizedTest
    @EnumSource(AckRecoveryScenario.class)
    void recoveryAllocationCountersExcludeFixturePreparation(AckRecoveryScenario scenario) {
        var benchmark = new QuicPathJmhBenchmark();
        var state = new AckRecoveryState();
        var counters = new AckAllocationCounters();
        state.ackRecoveryScenario = scenario;
        try {
            counters.setUp();
            state.setUpTrial();
            assertThat(counters.ackOperations, is(0L));
            for (int invocation = 0; invocation < 2; invocation++) {
                state.setUpInvocation();
                try {
                    assertThat(benchmark.ackRecoveryAllocation(state, counters), is(state.packetNumber()));
                } finally {
                    state.tearDownInvocation();
                }
            }
            assertThat(counters.ackOperations, is(2L));
            assertThat(counters.allocatedBytes, greaterThanOrEqualTo(0L));
        } finally {
            state.tearDownTrial();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ackRecovery", "ackRecoveryAllocation"})
    void runnerPassesRecoveryScenariosWithAnExactInclude(String method) {
        String include = "^io\\.helidon\\.quic\\.QuicPathJmhBenchmark\\." + method + "$";
        var properties = new Properties();
        properties.setProperty(PREFIX + "include", include);
        properties.setProperty(PREFIX + "ackRecoveryScenario", "APPLICATION, SERVER_INITIAL, CLIENT_INITIAL");

        var options = QuicPathJmhRunnerTest.options(properties);

        assertThat(options.getIncludes(), contains(include));
        assertThat(options.getParameter("ackRecoveryScenario").get(),
                   contains("APPLICATION", "SERVER_INITIAL", "CLIENT_INITIAL"));
    }

    @Test
    void invalidRecoveryScenarioFailsBeforeJmh() {
        var properties = new Properties();
        properties.setProperty(PREFIX + "include", ACK_RECOVERY_INCLUDE);
        properties.setProperty(PREFIX + "ackRecoveryScenario", "UNKNOWN");

        assertThrows(IllegalArgumentException.class, () -> QuicPathJmhRunnerTest.options(properties));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ackRecovery", "ackRecoveryAllocation"})
    void gcProfilerCannotBeMistakenForIsolatedRecoveryAllocation(String method) {
        var properties = new Properties();
        properties.setProperty(PREFIX + "include", "^io\\.helidon\\.quic\\.QuicPathJmhBenchmark\\." + method + "$");
        properties.setProperty(PREFIX + "gcProfiler", "true");

        var exception = assertThrows(IllegalArgumentException.class, () -> QuicPathJmhRunnerTest.options(properties));

        assertThat(exception.getMessage(), containsString("GCProfiler includes ACK fixture allocation"));
        assertThat(exception.getMessage(), containsString("ackRecoveryAllocation"));
    }

    @Test
    void runnerPassesSeparateParameterValuesAndTheExactInclude() {
        var properties = new Properties();
        properties.setProperty(PREFIX + "include", ACK_INCLUDE);
        properties.setProperty(PREFIX + "inFlightPackets", "4096, 13981");
        properties.setProperty(PREFIX + "ackRangeCount", "1,32,1024");
        properties.setProperty(PREFIX + "ackPacketSpaceLifecycle", "REUSED,FIRST_ACK");
        properties.setProperty(PREFIX + "ackWorkload", "NEW_ACK");

        var options = QuicPathJmhRunnerTest.options(properties);
        assertThat(options.getIncludes(), contains(ACK_INCLUDE));
        assertThat(options.getParameter("inFlightPackets").get(), contains("4096", "13981"));
        assertThat(options.getParameter("ackRangeCount").get(), contains("1", "32", "1024"));
        assertThat(options.getParameter("ackPacketSpaceLifecycle").get(), contains("REUSED", "FIRST_ACK"));
    }

    @ParameterizedTest
    @CsvSource({"0,1", "13982,1", "64,0", "64,33", "13981,1025"})
    void invalidFlightAndRangeCombinationsFailBeforeJmh(int inFlightPackets, int ackRangeCount) {
        var properties = new Properties();
        properties.setProperty(PREFIX + "include", ACK_INCLUDE);
        properties.setProperty(PREFIX + "inFlightPackets", Integer.toString(inFlightPackets));
        properties.setProperty(PREFIX + "ackRangeCount", Integer.toString(ackRangeCount));
        assertThrows(IllegalArgumentException.class, () -> QuicPathJmhRunnerTest.options(properties));
    }

    @Test
    void invalidCartesianProductFailsBeforeJmh() {
        var properties = new Properties();
        properties.setProperty(PREFIX + "include", ACK_INCLUDE);
        properties.setProperty(PREFIX + "inFlightPackets", "64,13981");
        properties.setProperty(PREFIX + "ackRangeCount", "1,1024");
        assertThrows(IllegalArgumentException.class, () -> QuicPathJmhRunnerTest.options(properties));
    }

    @Test
    void firstAckCannotBeADuplicate() {
        var properties = new Properties();
        properties.setProperty(PREFIX + "include", ACK_INCLUDE);
        properties.setProperty(PREFIX + "ackPacketSpaceLifecycle", "FIRST_ACK");
        properties.setProperty(PREFIX + "ackWorkload", "DUPLICATE");
        var exception = assertThrows(IllegalArgumentException.class, () -> QuicPathJmhRunnerTest.options(properties));
        assertThat(exception.getMessage(), containsString("DUPLICATE requires a REUSED packet space"));
    }

    @Test
    void gcProfilerCannotBeMistakenForIsolatedAckAllocation() {
        var properties = new Properties();
        properties.setProperty(PREFIX + "include", ACK_INCLUDE);
        properties.setProperty(PREFIX + "gcProfiler", "true");
        var exception = assertThrows(IllegalArgumentException.class, () -> QuicPathJmhRunnerTest.options(properties));
        assertThat(exception.getMessage(), containsString("GCProfiler includes ACK fixture allocation"));
    }

    @Test
    void emptyParameterValuesFailBeforeJmh() {
        var properties = new Properties();
        properties.setProperty(PREFIX + "include", ACK_INCLUDE);
        properties.setProperty(PREFIX + "ackRangeCount", "1,");
        assertThrows(IllegalArgumentException.class, () -> QuicPathJmhRunnerTest.options(properties));
    }
}
