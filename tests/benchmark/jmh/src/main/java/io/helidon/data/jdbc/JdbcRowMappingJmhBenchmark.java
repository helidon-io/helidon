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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
 * Measures ordinary row mapping using only APIs common to both sides of the CALL change.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class JdbcRowMappingJmhBenchmark {

    private static final String CALL_SQL = "{call benchmark_rows()}";
    private static final List<String> LABELS = List.of("ID", "NAME", "REVISION");
    private static final JdbcClient.RowMapper<Integer> INDEXED = row -> row.get(1, Integer.class);
    private static final JdbcClient.RowMapper<NamedRow> NAMED = row ->
            new NamedRow(row.get("ID", Integer.class), row.get("NAME", String.class), row.get("REVISION", Long.class));

    /**
     * Maps the rows from an ordinary SELECT.
     *
     * @param state The state supplies the result set and mapper.
     * @return The list contains all mapped rows.
     * @throws NullPointerException The state is null.
     */
    @Benchmark
    public List<?> select(RowState state) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        return state.named ? state.query().map(NAMED).list() : state.query().map(INDEXED).list();
    }

    /**
     * Maps the generated keys from an INSERT.
     *
     * @param state The state supplies the generated keys and mapper.
     * @return The list contains all mapped keys.
     * @throws NullPointerException The state is null.
     */
    @Benchmark
    public List<?> generatedKeys(RowState state) {
        Objects.requireNonNull(state, "The benchmark state must not be null.");
        JdbcClient.GeneratedKeys keys = state.update().generatedKeys().addColumn("ID");
        if (state.named) {
            return keys.addColumn("NAME").addColumn("REVISION").map(NAMED).list();
        }
        return keys.map(INDEXED).list();
    }

    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    /**
     * Supplies prepared statements and reusable row stubs for one benchmark thread.
     */
    @State(Scope.Thread)
    public static class RowState {
        @Param({"1", "128"})
        private int rowCount;

        @Param({"indexed", "named"})
        private String mapping = "indexed";

        private Supplier<JdbcClient.Statement> call;
        private Supplier<JdbcClient.Statement> cursor;
        private Supplier<JdbcClient.Statement> query;
        private Supplier<JdbcClient.Statement> update;
        private int rowIndex;
        private boolean updateCountPending;
        private boolean named;

        void rowCount(int count) {
            rowCount = count;
        }

        void mapping(String mapping) {
            this.mapping = mapping;
        }

        JdbcClient.Statement call() {
            return call.get();
        }

        JdbcClient.Statement cursor() {
            return cursor.get();
        }

        JdbcClient.Statement query() {
            return query.get();
        }

        JdbcClient.Statement update() {
            return update.get();
        }

        /**
         * Prepares each definition and a result set that starts over on every execution.
         */
        @Setup
        public void setup() {
            named = mapping.equals("named");
            ResultSetMetaData columns = stub(ResultSetMetaData.class, (_, method, arguments) -> switch (method.getName()) {
                case "getColumnCount" -> named ? LABELS.size() : 1;
                case "getColumnLabel", "getColumnName" -> LABELS.get((Integer) arguments[0] - 1);
                default -> throw new UnsupportedOperationException("The JDBC stub does not support " + method.getName() + ".");
            });
            ResultSet rows = stub(ResultSet.class, (_, method, _) -> switch (method.getName()) {
                case "getMetaData" -> columns;
                case "next" -> ++rowIndex < rowCount;
                case "getInt" -> rowIndex;
                case "getString" -> "bench";
                case "getLong" -> 47L;
                case "wasNull" -> false;
                case "close" -> null;
                default -> throw new UnsupportedOperationException("The JDBC stub does not support " + method.getName() + ".");
            });
            CallableStatement callable = rowStatement(CallableStatement.class, rows, false);
            CallableStatement cursorStatement = stub(CallableStatement.class, (_, method, _) -> switch (method.getName()) {
                case "execute" -> {
                    rowIndex = -1;
                    yield false;
                }
                case "getLargeUpdateCount" -> -1L;
                case "getObject" -> rows;
                case "registerOutParameter", "close" -> null;
                default -> throw new UnsupportedOperationException("The JDBC stub does not support " + method.getName() + ".");
            });
            PreparedStatement select = rowStatement(PreparedStatement.class, rows, false);
            PreparedStatement insert = rowStatement(PreparedStatement.class, rows, true);
            Connection connection = stub(Connection.class, (_, method, arguments) -> switch (method.getName()) {
                case "getAutoCommit" -> true;
                case "prepareCall" -> arguments[0].equals(CALL_SQL) ? callable : cursorStatement;
                case "prepareStatement" -> arguments.length == 1 ? select : insert;
                case "close" -> null;
                default -> throw new UnsupportedOperationException("The JDBC stub does not support " + method.getName() + ".");
            });
            DataSource dataSource = stub(DataSource.class, (_, method, _) -> switch (method.getName()) {
                case "getConnection" -> connection;
                default -> throw new UnsupportedOperationException("The JDBC stub does not support " + method.getName() + ".");
            });
            JdbcClient client = JdbcClient.builder().dataSource(dataSource).build();
            call = client.prepare(CALL_SQL);
            cursor = client.prepare("{? = call benchmark_rows()}");
            query = client.prepare("SELECT ID FROM BENCHMARK_VALUE");
            update = client.prepare("INSERT INTO BENCHMARK_VALUE (ID) VALUES (DEFAULT)");
        }

        private <T extends PreparedStatement> T rowStatement(Class<T> type, ResultSet rows, boolean keys) {
            return stub(type, (_, method, _) -> switch (method.getName()) {
                case "execute" -> {
                    rowIndex = -1;
                    updateCountPending = keys;
                    yield !keys;
                }
                case "getResultSet", "getGeneratedKeys" -> {
                    if (keys != method.getName().equals("getGeneratedKeys")) {
                        throw new IllegalStateException("The JDBC stub was asked for the wrong result channel.");
                    }
                    yield rows;
                }
                case "getLargeUpdateCount" -> updateCountPending ? (long) rowCount : -1L;
                case "getMoreResults" -> {
                    updateCountPending = false;
                    yield false;
                }
                case "close" -> null;
                default -> throw new UnsupportedOperationException("The JDBC stub does not support " + method.getName() + ".");
            });
        }
    }

    record NamedRow(int id, String name, long revision) {
    }
}
