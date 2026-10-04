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

package io.helidon.metrics.benchmark.jmh;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.ThreadParams;

/**
 * Cold, filtered HTTP transport meter resolution through the provider dispatcher.
 * Each single-shot iteration starts with fresh recorder caches. The provider filters out meter creation after counting
 * the queued resolution, keeping native meter construction out of the comparison. See the module README for timing and
 * allocation boundaries and the dedicated runner for serial and concurrent producer settings.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class HttpTransportMetricsDispatchJmhBenchmark {

    @Benchmark
    public void producerAdmission(RegistrationState state, ThreadParams thread) {
        state.probe.submit(thread.getThreadIndex());
    }

    @Benchmark
    public int completeWave(RegistrationState state, ThreadParams thread) throws Exception {
        state.probe.submit(thread.getThreadIndex());
        return state.probe.awaitDrained();
    }

    @State(Scope.Benchmark)
    public static class RegistrationState {
        @Param({"32", "128"})
        public int registrationsPerProducer;

        private HttpTransportMetricsDispatchProbe probe;

        @Setup(Level.Iteration)
        public void setup(BenchmarkParams parameters) {
            if (parameters.getMode() != Mode.SingleShotTime
                    || parameters.getWarmup().getBatchSize() != 1
                    || parameters.getMeasurement().getBatchSize() != 1) {
                throw new IllegalArgumentException("Cold dispatch requires SingleShotTime with batch size 1");
            }
            prepare(parameters.getThreads());
        }

        @TearDown(Level.Iteration)
        public void tearDown() throws Exception {
            if (probe != null) {
                try (var currentProbe = probe) {
                    currentProbe.awaitDrained();
                }
            }
        }

        void prepare(int producers) {
            if ((long) producers * registrationsPerProducer > 1024) {
                throw new IllegalArgumentException("Timed waves must fit within the 1,024-action admission budget");
            }
            probe = new HttpTransportMetricsDispatchProbe(producers, registrationsPerProducer, false);
        }
    }
}
