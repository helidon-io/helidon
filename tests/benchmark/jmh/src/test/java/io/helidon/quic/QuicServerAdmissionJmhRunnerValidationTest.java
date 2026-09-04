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

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.infra.ThreadParams;
import org.openjdk.jmh.profile.GCProfiler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuicServerAdmissionJmhRunnerValidationTest {
    @Test
    void repeatsSyntheticEstablishedConnectionLifecycle() throws Exception {
        var benchmark = new QuicServerAdmissionJmhBenchmark();
        var runtimeState = new QuicServerAdmissionJmhBenchmark.ServerRuntimeState();
        var initialState = new QuicServerAdmissionJmhBenchmark.ServerInitialState();
        runtimeState.pendingLimit = 1;
        initialState.setUp(new ThreadParams(0, 1, 0, 1, 0, 1, 0, 1, 0, 1));

        runtimeState.setUp();
        try {
            assertTrue(benchmark.serverEstablishedConnectionLifecycle(runtimeState, initialState));
            assertTrue(benchmark.serverEstablishedConnectionLifecycle(runtimeState, initialState));
        } finally {
            runtimeState.tearDown();
        }
    }

    @Test
    void rejectsGcAliasWithProcessProfiler() {
        assertThrows(IllegalArgumentException.class,
                     () -> validate(false, true, false, true, false, "gc", 5));
    }

    @Test
    void rejectsGcClassWithProcessProfiler() {
        assertThrows(IllegalArgumentException.class,
                     () -> validate(false, true, false, false, true, GCProfiler.class.getName(), 5));
    }

    @Test
    void rejectsDuplicateGcSelection() {
        assertThrows(IllegalArgumentException.class,
                     () -> validate(false, false, true, false, false, "gc", 5));
    }

    @Test
    void rejectsStandardGcForFullLifecycle() {
        assertThrows(IllegalArgumentException.class,
                     () -> validate(false, true, false, false, false, "gc", 5));
    }

    @Test
    void acceptsProcessAllocationForFullLifecycle() {
        assertDoesNotThrow(() -> validate(false, true, false, true, false, null, 5));
    }

    @Test
    void rejectsEvidenceResultAndOutputOverrides() {
        assertThrows(IllegalArgumentException.class,
                     () -> QuicServerAdmissionJmhRunnerTest.validateEvidenceOutputOverrides(
                             true,
                             "result.json",
                             null));
        assertThrows(IllegalArgumentException.class,
                     () -> QuicServerAdmissionJmhRunnerTest.validateEvidenceOutputOverrides(
                             true,
                             null,
                             "output.log"));
        assertDoesNotThrow(() -> QuicServerAdmissionJmhRunnerTest.validateEvidenceOutputOverrides(
                false,
                "result.json",
                "output.log"));
    }

    private static void validate(boolean lifecycleHandshakeReadySelected,
                                 boolean lifecyclePeerTerminationSelected,
                                 boolean gcProfiler,
                                 boolean processAllocationProfiler,
                                 boolean processCpuProfiler,
                                 String profiler,
                                 int measurementIterations) {
        QuicServerAdmissionJmhRunnerTest.validateProfilerSelection(lifecycleHandshakeReadySelected,
                                                                  lifecyclePeerTerminationSelected,
                                                                  gcProfiler,
                                                                  processAllocationProfiler,
                                                                  processCpuProfiler,
                                                                  profiler,
                                                                  measurementIterations);
    }
}
