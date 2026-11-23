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
package io.helidon.data.jdbc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures batch capture allocations before JDBC acquisition or driver buffering.
 * Every invocation retains all captured rows until it returns, as the runtime does.
 * Payloads and named parameter metadata are prepared outside the measurement.
 * Use the GC profiler to compare allocated bytes per operation across revisions.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@SuppressWarnings("helidon:api:internal")
public class JdbcBatchCaptureJmhBenchmark {

    /**
     * Captures physical markers as generated literal repositories do.
     *
     * @param input The state supplies the payloads and marker counts.
     * @return The list retains every captured row.
     * @throws NullPointerException The capture state is null.
     */
    @Benchmark
    public Object indexedBinary(CaptureState input) {
        Objects.requireNonNull(input, "The capture state must not be null.");
        List<JdbcOperation.Bind[]> rows = new ArrayList<>(input.rowCount);
        for (int row = 0; row < input.rowCount; row++) {
            JdbcBatchBindings bindings = new JdbcBatchBindings(input.repetitions + 1, null);
            try {
                bindings.bind(1, row);
                for (int marker = 2; marker <= input.repetitions + 1; marker++) {
                    bindings.bind(marker, input.payloads[row]);
                }
                rows.add(bindings.complete());
            } finally {
                bindings.close();
            }
        }
        return rows;
    }

    /**
     * Captures one named binary input that fans out to repeated physical markers.
     *
     * @param input The state supplies the payloads and marker counts.
     * @return The list retains every captured row.
     * @throws NullPointerException The capture state is null.
     */
    @Benchmark
    public Object namedBinary(CaptureState input) {
        Objects.requireNonNull(input, "The capture state must not be null.");
        List<JdbcOperation.Bind[]> rows = new ArrayList<>(input.rowCount);
        for (int row = 0; row < input.rowCount; row++) {
            JdbcBatchBindings bindings = new JdbcBatchBindings(input.repetitions + 1, input.configured);
            try {
                bindings.bind("id", row).bind("payload", input.payloads[row]);
                rows.add(bindings.complete());
            } finally {
                bindings.close();
            }
        }
        return rows;
    }

    /**
     * Establishes the control for scalar inputs that need no mutable snapshot.
     *
     * @param input The state supplies the payloads and marker counts.
     * @return The list retains every captured row.
     * @throws NullPointerException The capture state is null.
     */
    @Benchmark
    public Object indexedScalar(CaptureState input) {
        Objects.requireNonNull(input, "The capture state must not be null.");
        List<JdbcOperation.Bind[]> rows = new ArrayList<>(input.rowCount);
        for (int row = 0; row < input.rowCount; row++) {
            JdbcBatchBindings bindings = new JdbcBatchBindings(input.repetitions + 1, null);
            try {
                bindings.bind(1, row);
                for (int marker = 2; marker <= input.repetitions + 1; marker++) {
                    bindings.bind(marker, "unchanged");
                }
                rows.add(bindings.complete());
            } finally {
                bindings.close();
            }
        }
        return rows;
    }

    /**
     * Captures a new value after a mutation near the end of each binary input.
     *
     * @param input The state supplies the payloads and marker counts.
     * @return The list retains every captured row.
     * @throws NullPointerException The capture state is null.
     */
    @Benchmark
    public Object indexedMutatingBinary(CaptureState input) {
        Objects.requireNonNull(input, "The capture state must not be null.");
        List<JdbcOperation.Bind[]> rows = new ArrayList<>(input.rowCount);
        for (int row = 0; row < input.rowCount; row++) {
            JdbcBatchBindings bindings = new JdbcBatchBindings(input.repetitions + 1, null);
            try {
                bindings.bind(1, row);
                byte[] value = input.payloads[row];
                for (int marker = 2; marker <= input.repetitions + 1; marker++) {
                    if (value.length != 0) {
                        value[value.length - 1]++;
                    }
                    bindings.bind(marker, value);
                }
                rows.add(bindings.complete());
            } finally {
                bindings.close();
            }
        }
        return rows;
    }

    /**
     * Captures distinct binary inputs that cannot share snapshots by identity.
     *
     * @param input The state supplies independent payloads for each marker.
     * @return The list retains every captured row.
     * @throws NullPointerException The capture state is null.
     */
    @Benchmark
    public Object indexedIndependentBinary(IndependentState input) {
        Objects.requireNonNull(input, "The capture state must not be null.");
        List<JdbcOperation.Bind[]> rows = new ArrayList<>(input.rowCount);
        for (int row = 0; row < input.rowCount; row++) {
            JdbcBatchBindings bindings = new JdbcBatchBindings(input.repetitions + 1, null);
            try {
                bindings.bind(1, row);
                for (int marker = 2; marker <= input.repetitions + 1; marker++) {
                    bindings.bind(marker, input.independentPayloads[row][marker - 2]);
                }
                rows.add(bindings.complete());
            } finally {
                bindings.close();
            }
        }
        return rows;
    }

    /**
     * Supplies finite batches with a scalar identifier as well as the repeated payload.
     */
    @State(Scope.Thread)
    public static class CaptureState {
        /**
         * The number of items captured before acquisition.
         */
        @Param({"32"})
        public int rowCount;

        /**
         * The number of physical markers for one binary or scalar input.
         */
        @Param({"1", "8"})
        public int repetitions;

        /**
         * The size of each application buffer in bytes.
         */
        @Param({"64", "65536"})
        public int payloadBytes;

        private byte[][] payloads;
        private JdbcConfiguredStatement configured;

        /**
         * Prepares application buffers and named marker metadata outside the timed capture.
         */
        @Setup
        public void setup() {
            payloads = new byte[rowCount][payloadBytes];
            configured = JdbcConfiguredStatement.create("INSERT INTO T VALUES (:id, "
                    + String.join(", ", Collections.nCopies(repetitions, ":payload")) + ")");
        }
    }

    /**
     * Prepares independent inputs without charging application buffer allocation to capture.
     */
    @State(Scope.Thread)
    public static class IndependentState extends CaptureState {
        private byte[][][] independentPayloads;

        @Override
        @Setup
        public void setup() {
            super.setup();
            independentPayloads = new byte[rowCount][repetitions][payloadBytes];
        }
    }
}
