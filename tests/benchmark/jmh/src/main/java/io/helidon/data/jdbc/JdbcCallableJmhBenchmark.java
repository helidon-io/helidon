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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.JDBCType;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import javax.sql.DataSource;

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
 * Measures callable output handling and row mapping for calls, queries, and generated keys.
 *
 * <p>The output terminal benchmarks include registration and resource cleanup. The JDBC stub returns
 * each output's index as its value. Row workloads map a single integer column. The stubs perform
 * no database work. Results include provider work, stub dispatch, and their allocations.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class JdbcCallableJmhBenchmark {

    private static final JdbcClient.RowMapper<Integer> ROW_MAPPER = row -> row.get(1, Integer.class);

    /**
     * Creates a fresh call and registers every output without acquiring a connection.
     *
     * @param state The state supplies a prepared definition and output names.
     * @return The registered call remains unexecuted.
     * @throws NullPointerException The state is null.
     */
    @Benchmark
    public JdbcClient.Call registerOutputs(OutputState state) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        return register(state);
    }

    /**
     * Registers and executes a call, including marker coverage validation and cleanup.
     *
     * @param state The state supplies a prepared definition and output names.
     * @throws NullPointerException The state is null.
     */
    @Benchmark
    public void execute(OutputState state) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        register(state).execute();
    }

    /**
     * Registers and executes a call, then reads every output by name inside its scope.
     *
     * @param state The state supplies a prepared definition and output names.
     * @return The sum retains every retrieved value for JMH consumption.
     * @throws NullPointerException The state is null.
     */
    @Benchmark
    public int lookupOutputs(OutputState state) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        return register(state).map(result -> {
            int sum = 0;
            for (String name : state.outputNames) {
                sum += result.get(name, Integer.class);
            }
            return sum;
        });
    }

    /**
     * Registers and executes a call, then captures all outputs in a detached scalar snapshot.
     *
     * @param state The state supplies a prepared definition and output names.
     * @return The snapshot retains every captured value for JMH consumption.
     * @throws NullPointerException The state is null.
     */
    @Benchmark
    public JdbcClient.OutParameters snapshotOutputs(OutputState state) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        return register(state).outputs();
    }

    /**
     * Maps and materializes the direct result set from a callable statement.
     *
     * @param state The state supplies the prepared call and result rows.
     * @return All mapped rows in encounter order.
     * @throws NullPointerException The state is null.
     */
    @Benchmark
    public List<Integer> mapCallableRows(RowState state) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        return state.rows.call().call().map(result -> result.rows(ROW_MAPPER).list());
    }

    /**
     * Maps and materializes the result set from a query.
     *
     * @param state The state supplies the prepared query and result rows.
     * @return All mapped rows in encounter order.
     * @throws NullPointerException The state is null.
     */
    @Benchmark
    public List<Integer> mapQueryRows(RowState state) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        return state.rows.query().map(ROW_MAPPER).list();
    }

    /**
     * Maps and materializes the generated keys from an update.
     *
     * @param state The state supplies the prepared update and generated keys.
     * @return All mapped keys in encounter order.
     * @throws NullPointerException The state is null.
     */
    @Benchmark
    public List<Integer> mapGeneratedKeys(RowState state) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        return state.rows.update().generatedKeys().addColumn("ID").map(ROW_MAPPER).list();
    }

    /**
     * Reads zero or one row through a direct result or an output cursor.
     *
     * @param state The state supplies the result channel and terminal.
     * @return The result is the required row or an optional containing zero or one row.
     * @throws NullPointerException The state is null.
     */
    @Benchmark
    public Object mapSingularCall(SingularState state) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        JdbcClient.Call call = state.cursor ? state.rows.cursor().call().returns(JDBCType.REF_CURSOR)
                : state.rows.call().call();
        return call.map(result -> {
            if (state.cursor) {
                result.skipRemainingResults();
            }
            JdbcClient.Rows<Integer> rows = state.cursor ? result.cursor("return", ROW_MAPPER) : result.rows(ROW_MAPPER);
            return state.required ? rows.one() : rows.optional();
        });
    }

    private static JdbcClient.Call register(OutputState state) {
        JdbcClient.Call call = state.definition.get().call();
        for (String name : state.outputNames) {
            call.out(name, JDBCType.INTEGER);
        }
        return call;
    }

    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    /**
     * Supplies one reusable definition and JDBC stub per benchmark thread.
     */
    @State(Scope.Thread)
    public static class OutputState {

        /**
         * The output count includes a small call and two larger calls for scaling comparisons.
         */
        @Param({"1", "8", "64"})
        private int outputCount;

        private List<String> outputNames;
        private Supplier<JdbcClient.Statement> definition;

        void outputCount(int count) {
            outputCount = count;
        }

        /**
         * Prepares the immutable SQL definition outside the measured operations.
         */
        @Setup
        public void setup() {
            // Reuse the stubs so the measured allocation belongs to the provider and proxy dispatch.
            CallableStatement statement = stub(CallableStatement.class, (_, method, arguments) -> switch (method.getName()) {
                case "execute", "wasNull" -> false;
                case "getLargeUpdateCount" -> -1L;
                case "getInt" -> arguments[0];
                case "registerOutParameter", "close" -> null;
                default -> throw new UnsupportedOperationException("The JDBC stub does not support " + method.getName() + ".");
            });
            Connection connection = stub(Connection.class, (_, method, _) -> switch (method.getName()) {
                case "getAutoCommit" -> true;
                case "prepareCall" -> statement;
                case "close" -> null;
                default -> throw new UnsupportedOperationException("The JDBC stub does not support " + method.getName() + ".");
            });
            DataSource dataSource = stub(DataSource.class, (_, method, _) -> switch (method.getName()) {
                case "getConnection" -> connection;
                default -> throw new UnsupportedOperationException("The JDBC stub does not support " + method.getName() + ".");
            });
            JdbcClient client = JdbcClient.builder().dataSource(dataSource).build();
            outputNames = IntStream.range(0, outputCount).mapToObj(index -> "output" + index).toList();
            definition = client.prepare("{call benchmark_outputs(:" + String.join(", :", outputNames) + ")}");
        }
    }

    /**
     * Supplies the indexed row workload for the original row mapping benchmarks.
     */
    @State(Scope.Thread)
    public static class RowState {

        private final JdbcRowMappingJmhBenchmark.RowState rows = new JdbcRowMappingJmhBenchmark.RowState();

        @Param({"1", "128"})
        private int rowCount;

        void rowCount(int count) {
            rowCount = count;
        }

        /**
         * Prepares the indexed result set before timing starts.
         */
        @Setup
        public void setup() {
            rows.rowCount(rowCount);
            rows.setup();
        }
    }

    /**
     * Selects a direct result or a REF_CURSOR and the cardinality of its terminal.
     */
    @State(Scope.Thread)
    public static class SingularState {

        private final JdbcRowMappingJmhBenchmark.RowState rows = new JdbcRowMappingJmhBenchmark.RowState();

        @Param({"directOptionalEmpty", "directOptionalOne", "directOne",
                "cursorOptionalEmpty", "cursorOptionalOne", "cursorOne"})
        private String workload;

        private boolean cursor;
        private boolean required;

        void workload(String workload) {
            this.workload = workload;
        }

        /**
         * Prepares the selected result cardinality outside the measured call.
         */
        @Setup
        public void setup() {
            cursor = workload.startsWith("cursor");
            required = !workload.contains("Optional");
            rows.rowCount(workload.endsWith("Empty") ? 0 : 1);
            rows.setup();
        }
    }
}
