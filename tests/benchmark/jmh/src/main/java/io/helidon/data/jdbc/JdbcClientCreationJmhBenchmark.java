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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import javax.sql.DataSource;

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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures statement creation and positional binding before any JDBC resource is acquired.
 *
 * <p>The cached cases establish the imperative baseline. The known-count cases
 * model generated repositories and must remain free of shared cache mutation.
 * Every measured thread shares one client, matching the production service
 * lifecycle.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@SuppressWarnings("helidon:api:internal")
public class JdbcClientCreationJmhBenchmark {

    private static final int SQL_COUNT = 32;
    private static final int PARAMETER_COUNT = 2;
    private static final String[] SQL = IntStream.range(0, SQL_COUNT)
            .mapToObj(index -> "SELECT ? FROM BENCHMARK_VALUE WHERE ID = ? /* statement " + index + " */")
            .toArray(String[]::new);

    /**
     * Creates one generated statement from a repeated SQL string.
     *
     * @param state shared client state
     * @param blackhole prevents dead-code elimination
     */
    @Benchmark
    public void generatedRepeatedSql(SharedState state, Blackhole blackhole) {
        blackhole.consume(GeneratedJdbcData.createGenerated(state.client, SQL[0], PARAMETER_COUNT));
    }

    /**
     * Creates generated statements from several SQL strings.
     *
     * @param state shared client state
     * @param cursor thread-local SQL selection state
     * @param blackhole prevents dead-code elimination
     */
    @Benchmark
    public void generatedMultipleSql(SharedState state, Cursor cursor, Blackhole blackhole) {
        blackhole.consume(GeneratedJdbcData.createGenerated(state.client, SQL[cursor.next()], PARAMETER_COUNT));
    }

    /**
     * Creates one imperative statement from a warmed marker-count cache entry.
     *
     * @param state shared client state
     * @param blackhole prevents dead-code elimination
     */
    @Benchmark
    public void cachedRepeatedSql(SharedState state, Blackhole blackhole) {
        blackhole.consume(state.client.create(SQL[0]));
    }

    /**
     * Creates imperative statements from several warmed cache entries.
     *
     * @param state shared client state
     * @param cursor thread-local SQL selection state
     * @param blackhole prevents dead-code elimination
     */
    @Benchmark
    public void cachedMultipleSql(SharedState state, Cursor cursor, Blackhole blackhole) {
        blackhole.consume(state.client.create(SQL[cursor.next()]));
    }

    /**
     * Creates an imperative statement and assigns every input.
     *
     * @param state The state supplies the client and SQL.
     * @param blackhole The blackhole retains the bound statement.
     * @throws NullPointerException A supplied argument is null.
     */
    @Benchmark
    public void cachedCreateAndBind(BindingState state, Blackhole blackhole) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        Objects.requireNonNull(blackhole, "The blackhole must not be null.");
        JdbcClient.Statement statement = state.client.create(state.sql);
        for (int index = 1; index <= state.parameterCount; index++) {
            statement.bind(index, index);
        }
        blackhole.consume(statement);
    }

    /**
     * Creates a generated statement with a known marker count and assigns every input.
     *
     * @param state The state supplies the client and SQL.
     * @param blackhole The blackhole retains the bound statement.
     * @throws NullPointerException A supplied argument is null.
     */
    @Benchmark
    public void generatedCreateAndBind(BindingState state, Blackhole blackhole) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        Objects.requireNonNull(blackhole, "The blackhole must not be null.");
        JdbcClient.Statement statement = GeneratedJdbcData.createGenerated(state.client, state.sql, state.parameterCount);
        for (int index = 1; index <= state.parameterCount; index++) {
            statement.bind(index, index);
        }
        blackhole.consume(statement);
    }

    /**
     * Creates a statement from an immutable definition and assigns every input.
     *
     * @param state The state supplies the prepared definition.
     * @param blackhole The blackhole retains the bound statement.
     * @throws NullPointerException A supplied argument is null.
     */
    @Benchmark
    public void preparedCreateAndBind(BindingState state, Blackhole blackhole) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        Objects.requireNonNull(blackhole, "The blackhole must not be null.");
        JdbcClient.Statement statement = state.definition.get();
        for (int index = 1; index <= state.parameterCount; index++) {
            statement.bind(index, index);
        }
        blackhole.consume(statement);
    }

    /**
     * One client shared by every benchmark thread.
     */
    @State(Scope.Benchmark)
    public static class SharedState {
        // Keep the production contract type so measured calls use the same
        // interface dispatch as generated repositories and applications.
        private JdbcClient client;

        /**
         * Creates the client and warms every imperative cache entry before
         * measurement so the cached cases measure hit contention, not scans.
         */
        @Setup(Level.Trial)
        public void setup() {
            JdbcClientConfig config = JdbcClientConfig.builder()
                    .dataSourceName("benchmark-data-source")
                    .buildPrototype();
            client = new JdbcClientImpl(config,
                                        new UnusedDataSource(),
                                        JdbcConnectionLease.ownedProvider(),
                                        JdbcClientConfigSupport.cachePolicy(config));
            for (String sql : SQL) {
                client.create(sql);
            }
        }
    }

    /**
     * Selects multiple SQL strings without introducing shared counter traffic.
     */
    @State(Scope.Thread)
    public static class Cursor {
        private int index;

        int next() {
            int result = index;
            index = (index + 1) & (SQL_COUNT - 1);
            return result;
        }
    }

    /**
     * Supplies ordinary SELECT and UPDATE statements with several input counts.
     */
    @State(Scope.Benchmark)
    public static class BindingState {
        @Param({"1", "2", "8", "32"})
        public int parameterCount;
        @Param({"SELECT", "UPDATE"})
        public String operation;

        private JdbcClient client;
        private String sql;
        private Supplier<JdbcClient.Statement> definition;

        /**
         * Prepares SQL and warms the imperative marker cache before measurement.
         */
        @Setup(Level.Trial)
        public void setup() {
            JdbcClientConfig config = JdbcClientConfig.builder().dataSourceName("benchmark-data-source").buildPrototype();
            client = new JdbcClientImpl(config, new UnusedDataSource(), JdbcConnectionLease.ownedProvider(),
                                        JdbcClientConfigSupport.cachePolicy(config));
            String parameters = String.join(", ", IntStream.range(0, parameterCount)
                    .mapToObj(index -> operation.equals("SELECT") ? "?" : "VALUE_" + index + " = ?").toList());
            sql = operation.equals("SELECT") ? "SELECT " + parameters + " FROM BENCHMARK_VALUE"
                    : "UPDATE BENCHMARK_VALUE SET " + parameters;
            definition = client.prepare(sql);
            client.create(sql);
        }
    }

    /**
     * Datasource sentinel: statement creation must never invoke it.
     */
    private static final class UnusedDataSource implements DataSource {

        @Override
        public Connection getConnection() {
            throw unexpectedAccess();
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw unexpectedAccess();
        }

        @Override
        public PrintWriter getLogWriter() {
            throw unexpectedAccess();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            throw unexpectedAccess();
        }

        @Override
        public void setLoginTimeout(int seconds) {
            throw unexpectedAccess();
        }

        @Override
        public int getLoginTimeout() {
            throw unexpectedAccess();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw unexpectedAccess();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw unexpectedAccess();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            throw unexpectedAccess();
        }

        private static AssertionError unexpectedAccess() {
            return new AssertionError("Statement creation must not access the datasource.");
        }
    }
}
