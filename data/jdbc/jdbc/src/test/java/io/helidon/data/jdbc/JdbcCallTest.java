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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.data.NonUniqueResultException;
import io.helidon.transaction.TxException;

import org.mockito.InOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcCallTest {
    private DataSource source;
    private Connection connection;
    private CallableStatement statement;
    private JdbcClient client;

    @BeforeEach
    void setup() throws Exception {
        source = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(CallableStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareCall(anyString())).thenReturn(statement);
        when(statement.getLargeUpdateCount()).thenReturn(-1L);
        client = JdbcTestClients.create(source);
    }

    /**
     * Staging and invalid snapshot declarations must never acquire a connection.
     */
    @Test
    void rejectsInvalidRegistrationsBeforeAcquisition() throws Exception {
        JdbcClient.Call call = client.create("{call routine(?)}").call();
        assertThrows(NullPointerException.class, () -> call.out(null, 0, JDBCType.INTEGER));
        assertThrows(IllegalArgumentException.class, () -> call.out("x", 0, JDBCType.INTEGER));
        assertThrows(IllegalArgumentException.class, () -> call.out("x", 1, JDBCType.INTEGER, 2));
        call.out("x", 1, JDBCType.REF_CURSOR);
        assertThrows(IllegalArgumentException.class, () -> call.out("x", 1, JDBCType.INTEGER));
        assertThrows(IllegalStateException.class, call::outputs);
        verify(source, never()).getConnection();
    }

    @Test
    void executesParameterlessCommandOnce() throws Exception {
        JdbcClient.Call call = client.create("{call routine()}").call();
        call.execute();
        assertThrows(IllegalStateException.class, call::execute);
        verify(statement).execute();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void executesBothEscapeFormsWithoutClassifyingRoutineNames() throws Exception {
        for (String routine : List.of("procedure", "function")) {
            for (boolean leading : List.of(false, true)) {
                for (int count = 0; count <= 2; count++) {
                    clearInvocations(source, connection, statement);
                    String arguments = switch (count) {
                    case 0 -> "";
                    case 1 -> "?";
                    default -> "?, ?";
                    };
                    String sql = (leading ? "{? = call " : "{call ") + routine + "(" + arguments + ")}";
                    JdbcClient.Statement stage = client.create(sql);
                    for (int input = 1; input <= count; input++) {
                        stage.bind(input + (leading ? 1 : 0), input);
                    }
                    JdbcClient.Call call = stage.call();
                    if (leading) {
                        call.returns(JDBCType.INTEGER);
                    }
                    verify(source, never()).getConnection();
                    call.execute();

                    var order = inOrder(connection, statement);
                    order.verify(connection).prepareCall(sql);
                    if (leading) {
                        order.verify(statement).registerOutParameter(1, JDBCType.INTEGER.getVendorTypeNumber());
                    }
                    for (int input = 1; input <= count; input++) {
                        order.verify(statement).setInt(input + (leading ? 1 : 0), input);
                    }
                    order.verify(statement).execute();
                    order.verify(statement).close();
                    order.verify(connection).close();
                    verify(statement, never()).getInt(anyInt());
                    verify(statement, never()).getObject(anyInt());
                }
            }
        }
    }

    @Test
    void rejectsMissingLeadingReturnRegistrationBeforeAcquisition() throws Exception {
        for (String sql : List.of("{?=call routine(?)}", "{ ? = CALL routine(:input)}", "{? = call routine()}")) {
            clearInvocations(source, connection, statement);
            JdbcClient.Statement stage;
            if (sql.contains(":input")) {
                stage = client.prepare(sql).get().bind("input", 7);
            } else {
                stage = client.create(sql);
                if (sql.contains("(?)")) {
                    stage.bind(2, 7);
                }
            }
            JdbcClient.Call call = stage.call();
            assertThrows(IllegalStateException.class, call::execute);
            verify(source, never()).getConnection();
            call.returns(JDBCType.INTEGER).execute();
            verify(connection).prepareCall(sql.replace(":input", "?"));
            verify(statement).registerOutParameter(1, JDBCType.INTEGER.getVendorTypeNumber());
            verify(statement).close();
            verify(connection).close();
        }
    }

    @Test
    void rejectsCompiledReturnRoleMismatchesBeforeAcquisition() throws Exception {
        for (String sql : List.of("{call private_routine(?, ?)}", "{call private_routine(:return, :input)}",
                                  "{? = call private_routine(?)}", "{? = call private_routine(:input)}")) {
            boolean expectedReturn = !sql.startsWith("{?");
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> GeneratedJdbcData.prepareCall(client, sql, List.of("return", "input"), false, expectedReturn));
            assertThat(failure.getMessage(), containsString("leading return slot"));
            assertThat(failure.getMessage(), not(containsString("private_routine")));
        }
        for (String sql : List.of("{call routine(?)}", "{call routine(:return)}")) {
            assertThrows(IllegalArgumentException.class,
                    () -> GeneratedJdbcData.prepareCall(client, sql, List.of("return"), false, true));
        }
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedJdbcData.prepareCall(client, "{? = call routine(?)}",
                                                   List.of("return", "input"), true, true));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedJdbcData.prepareCall(client, "{? = call routine(:input)}",
                                                   List.of("return", "input"), true, false));
        verify(source, never()).getConnection();
    }

    @Test
    void preparedCallContractsPreserveOrdinaryReturnNamesAndFreshStages() throws Exception {
        when(statement.getInt(1)).thenReturn(42);
        for (boolean leading : List.of(false, true)) {
            clearInvocations(source, connection, statement);
            List<String> names = new ArrayList<>(List.of("return", "input"));
            String sql = leading ? "{? = call routine(:input)}" : "{call routine(:return, :input)}";
            Supplier<JdbcClient.Statement> definition = GeneratedJdbcData.prepareCall(client, sql, names, true, leading);
            names.clear();
            JdbcClient.Statement first = definition.get().bind("input", 7);
            JdbcClient.Statement second = definition.get().bind("input", 8);
            verify(source, never()).getConnection();
            for (JdbcClient.Statement stage : List.of(first, second)) {
                JdbcClient.Call call = stage.call();
                if (leading) {
                    call.returns(JDBCType.INTEGER);
                } else {
                    call.out("return", JDBCType.INTEGER);
                }
                assertThat(call.outputs().get("return", Integer.class), is(42));
            }
            verify(statement).setInt(2, 7);
            verify(statement).setInt(2, 8);
            verify(statement, times(2)).registerOutParameter(1, JDBCType.INTEGER.getVendorTypeNumber());
            verify(statement, times(2)).close();
            verify(connection, times(2)).close();
        }
    }

    @Test
    void preparesZeroArgumentCallsWithAndWithoutReturns() throws Exception {
        when(statement.getInt(1)).thenReturn(42);
        GeneratedJdbcData.prepareCall(client, "{call routine()}", List.of(), false, false).get().call().execute();
        assertThat(GeneratedJdbcData.prepareCall(client, "{?=call routine()}", List.of("return"), false, true)
                           .get().call().returns(JDBCType.INTEGER).outputs().get("return", Integer.class), is(42));
        verify(connection).prepareCall("{call routine()}");
        verify(connection).prepareCall("{?=call routine()}");
        verify(statement, times(2)).close();
        verify(connection, times(2)).close();
    }

    @Test
    void generatedCallReferencesAreValidatedBeforeContentOrProviderChecks() throws Exception {
        JdbcClient alternate = mock(JdbcClient.class);
        assertThrows(NullPointerException.class,
                () -> GeneratedJdbcData.prepareCall(null, "", List.of(), true, true));
        for (JdbcClient target : List.of(client, alternate)) {
            assertThrows(NullPointerException.class,
                    () -> GeneratedJdbcData.prepareCall(target, null, List.of(), true, true));
            assertThrows(NullPointerException.class,
                    () -> GeneratedJdbcData.prepareCall(target, "", null, true, true));
            assertThrows(NullPointerException.class,
                    () -> GeneratedJdbcData.prepareCall(target, "", Collections.singletonList(null), true, true));
        }
        verify(alternate, never()).prepare(anyString(), any());
        verify(source, never()).getConnection();
    }

    @Test
    void preservesAlternateCallPreparationAfterContractValidation() throws Exception {
        JdbcClient alternate = mock(JdbcClient.class);
        Supplier<JdbcClient.Statement> expected = () -> mock(JdbcClient.Statement.class);
        String sql = "{? = call routine(?)}";
        when(alternate.prepare(eq(sql), any())).thenReturn(expected);
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedJdbcData.prepareCall(alternate, "{call routine(?, ?)}",
                                                   List.of("return", "input"), false, true));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedJdbcData.prepareCall(alternate, sql, List.of("return", "input"), false, false));
        assertThrows(UnsupportedOperationException.class,
                () -> GeneratedJdbcData.prepareCall(alternate, "{? = call routine(:input)}",
                                                   List.of("return", "input"), true, true));
        verify(alternate, never()).prepare(anyString(), any());
        List<String> names = new ArrayList<>(List.of("return", "input"));
        assertThat(GeneratedJdbcData.prepareCall(alternate, sql, names, false, true), sameInstance(expected));
        names.clear();
        verify(alternate).prepare(sql, List.of("return", "input"));
        String ordinarySql = "{call routine(:return, :input)}";
        when(alternate.prepare(eq(ordinarySql), any())).thenReturn(expected);
        assertThat(GeneratedJdbcData.prepareCall(alternate, ordinarySql, List.of("return", "input"), false, false),
                   sameInstance(expected));
        verify(alternate).prepare(ordinarySql, List.of("return", "input"));
        JdbcClient.Call ordinary = client.prepare("{call routine(:return, :input)}").get()
                .bind("input", 7).call().out("return", JDBCType.INTEGER);
        ordinary.execute();
        verify(statement).registerOutParameter(1, JDBCType.INTEGER.getVendorTypeNumber());
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void bindsInputAndRegistersInoutWithScale() throws Exception {
        when(statement.getInt(1)).thenReturn(42);
        JdbcClient.OutParameters result = client.create("{call routine(?, ?)}").bind(1, 7).bind(2, "input")
                .call().out("value", 1, JDBCType.INTEGER).outputs();
        assertThat(result.get("value", int.class), is(42));
        verify(statement).setInt(1, 7);
        verify(statement).setString(2, "input");
        verify(statement).registerOutParameter(1, JDBCType.INTEGER.getVendorTypeNumber());
    }

    @Test
    void bindsTypedNullThroughExistingBridge() throws Exception {
        JdbcClient.Statement stage = client.create("{call routine(?)}");
        GeneratedJdbcData.bindNull(stage, 1, JDBCType.INTEGER);
        stage.call().execute();
        verify(statement).setNull(1, JDBCType.INTEGER.getVendorTypeNumber());
    }

    @Test
    void validatesCanonicalClassEvenForNull() throws Exception {
        when(statement.wasNull()).thenReturn(true);
        JdbcClient.OutParameters result = client.create("{? = call routine()}").call()
                .out("return", 1, JDBCType.INTEGER).outputs();
        assertThat(result.optional("return", Integer.class), is(Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> result.optional("return", Long.class));
        assertThrows(IllegalArgumentException.class, () -> result.get("return", Integer.class));
        assertThrows(IllegalArgumentException.class, () -> result.get("missing", Integer.class));
    }

    @Test
    void copiesBinaryAtCaptureAndEveryRead() throws Exception {
        for (JDBCType type : List.of(JDBCType.BINARY, JDBCType.VARBINARY, JDBCType.LONGVARBINARY)) {
            byte[] bytes = {1, 2};
            when(statement.getBytes(1)).thenReturn(bytes);
            JdbcClient.OutParameters result = client.create("{call routine(?)}").call()
                    .out("value", 1, type).outputs();
            bytes[0] = 9;
            result.get("value", byte[].class)[0] = 8;
            result.optional("value", byte[].class).orElseThrow()[1] = 7;
            assertThat(type.name(), result.get("value", byte[].class), is(new byte[] {1, 2}));
        }
    }

    @Test
    void preservesZeroAndFallsBackOnce() throws Exception {
        when(statement.getLargeUpdateCount()).thenThrow(new SQLFeatureNotSupportedException("secret"));
        when(statement.getUpdateCount()).thenReturn(0, -1);
        client.create("{call routine()}").call().consume(result -> {
            assertThat(result.resultKind(), is(JdbcClient.CallResultKind.UPDATE_COUNT));
            assertThat(result.updateCount(), is(0L));
            result.next();
            assertThat(result.resultKind(), is(JdbcClient.CallResultKind.END));
            result.next();
        });
        verify(statement).getLargeUpdateCount();
        verify(statement).getMoreResults(Statement.CLOSE_CURRENT_RESULT);
    }

    @Test
    void mapsRowsWithoutReexecutionAndExpiresEvent() throws Exception {
        rows(1, 42);
        client.create("{call routine()}").call().consume(result -> {
            JdbcClient.Rows<Integer> stage = result.rows(row -> row.get(1, Integer.class));
            assertThat(stage.one(), is(42));
            assertThrows(IllegalStateException.class, stage::one);
            result.next();
            assertThrows(IllegalStateException.class, stage::list);
        });
        verify(statement).execute();
    }

    /**
     * A caught mapper failure remains primary and blocks later reads.
     */
    @Test
    void retainsCaughtMapperFailure() throws Exception {
        rows(1, 42);
        IllegalArgumentException expected = new IllegalArgumentException("application failure");
        Throwable actual = assertThrows(IllegalArgumentException.class, () -> client.create("{call routine()}")
                .call().consume(result -> {
                    assertThrows(IllegalArgumentException.class, () -> result.rows(_ -> { throw expected; }).list());
                    assertThrows(IllegalStateException.class, result::next);
                }));
        assertThat(actual, sameInstance(expected));
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void retainsCaughtReadFailureInsideMapper() throws Exception {
        rows(1, 42);
        when(statement.getResultSet().getInt(1)).thenThrow(new SQLException("secret"));
        assertThrows(DataException.class, () -> client.create("{call routine()}").call().consume(result -> {
            assertThrows(DataException.class, () -> result.rows(row -> {
                assertThrows(DataException.class, () -> row.get(1, Integer.class));
                return 1;
            }).list());
        }));
    }

    @Test
    void retainsRequiredNullFailure() throws Exception {
        when(statement.wasNull()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> client.create("{call routine(?)}").call()
                .out("value", 1, JDBCType.INTEGER).consume(result -> {
                    assertThrows(IllegalArgumentException.class, () -> result.get("value", Integer.class));
                    assertThrows(NullPointerException.class, () -> result.optional(null, Integer.class));
                    assertThrows(IllegalStateException.class, result::resultKind);
                }));
    }

    @Test
    void pureValidationDoesNotPoisonScope() {
        client.create("{call routine()}").call().consume(result -> {
            assertThrows(IllegalStateException.class, result::updateCount);
            assertThrows(IllegalArgumentException.class, () -> result.get("unknown", String.class));
            assertThat(result.resultKind(), is(JdbcClient.CallResultKind.END));
        });
    }

    @Test
    void rejectsWrongThreadAndExpiresCallback() throws Exception {
        AtomicReference<JdbcClient.CallResult> escaped = new AtomicReference<>();
        client.create("{call routine()}").call().consume(result -> {
            escaped.set(result);
            CompletableFuture<Void> worker = CompletableFuture.runAsync(() ->
                    assertThrows(IllegalStateException.class, result::resultKind));
            try {
                worker.get(5, TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            } finally {
                worker.cancel(true);
            }
        });
        assertThrows(IllegalStateException.class, escaped.get()::resultKind);
    }

    @Test
    void distinguishesNullOptionalCursorAndRequiredCursor() throws Exception {
        client.create("{call routine(?)}").call().out("cursor", 1, JDBCType.REF_CURSOR).consume(result ->
                assertThat(result.optionalCursor("cursor", _ -> 1), is(Optional.empty())));
        assertThrows(IllegalArgumentException.class, () -> client.create("{call routine(?)}").call()
                .out("cursor", 1, JDBCType.REF_CURSOR).consume(result -> result.cursor("cursor", _ -> 1).list()));
    }

    @Test
    void cursorSelectionIsLazyAndSequential() throws Exception {
        ResultSet cursor = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(statement.getObject(1)).thenReturn(cursor);
        when(cursor.getMetaData()).thenReturn(metadata);
        client.create("{call routine(?, ?)}").call().out("cursor", 1, JDBCType.REF_CURSOR)
                .out("scalar", 2, JDBCType.INTEGER).consume(result -> {
                    JdbcClient.Rows<Integer> selected = result.cursor("cursor", _ -> 1);
                    try {
                        verify(statement, never()).getObject(1);
                    } catch (SQLException failure) {
                        throw new AssertionError(failure);
                    }
                    assertThrows(IllegalStateException.class, () -> result.get("scalar", Integer.class));
                    assertThat(selected.list(), is(List.of()));
                    assertThat(result.get("scalar", Integer.class), is(0));
                    assertThrows(IllegalStateException.class, () -> result.cursor("cursor", _ -> 1));
                });
        verify(cursor).close();
    }

    @Test
    void rejectsWrongCursorObjectAndNullCallbackReturn() throws Exception {
        when(statement.getObject(1)).thenReturn("not a cursor");
        assertThrows(DataException.class, () -> client.create("{call routine(?)}").call()
                .out("cursor", 1, JDBCType.REF_CURSOR).consume(result -> result.optionalCursor("cursor", _ -> 1)));
        assertThrows(DataException.class, () -> client.create("{call routine()}").call().map(_ -> null));
    }

    @Test
    void preservesApplicationFailureDuringCleanup() throws Exception {
        IllegalArgumentException expected = new IllegalArgumentException("application");
        doThrow(new SQLException("driver secret")).when(statement).close();
        assertThat(assertThrows(IllegalArgumentException.class, () -> client.create("{call routine()}")
                .call().consume(_ -> { throw expected; })), sameInstance(expected));
        verify(connection).close();
        assertThat(expected.getSuppressed().length, is(1));
    }

    /**
     * Each canonical registration retains its exact class and temporal precision after closure.
     */
    @Test
    void capturesEveryCanonicalScalarType() throws Exception {
        LocalTime time = LocalTime.of(12, 13, 14, 123456789);
        LocalDate date = LocalDate.of(2026, 10, 28);
        LocalDateTime timestamp = LocalDateTime.of(date, time);
        Map<JDBCType, Object> samples = Map.ofEntries(
                Map.entry(JDBCType.BOOLEAN, true), Map.entry(JDBCType.BIT, true),
                Map.entry(JDBCType.TINYINT, (byte) 1), Map.entry(JDBCType.SMALLINT, (short) 2),
                Map.entry(JDBCType.INTEGER, 3), Map.entry(JDBCType.BIGINT, 4L),
                Map.entry(JDBCType.REAL, 1.5F), Map.entry(JDBCType.FLOAT, 2.5D), Map.entry(JDBCType.DOUBLE, 2.5D),
                Map.entry(JDBCType.NUMERIC, new BigDecimal("1.234")), Map.entry(JDBCType.DECIMAL, new BigDecimal("1.234")),
                Map.entry(JDBCType.CHAR, "value"), Map.entry(JDBCType.VARCHAR, "value"),
                Map.entry(JDBCType.LONGVARCHAR, "value"), Map.entry(JDBCType.NCHAR, "value"),
                Map.entry(JDBCType.NVARCHAR, "value"), Map.entry(JDBCType.LONGNVARCHAR, "value"),
                Map.entry(JDBCType.BINARY, new byte[] {0, 1, (byte) 255}),
                Map.entry(JDBCType.VARBINARY, new byte[] {0, 1, (byte) 255}),
                Map.entry(JDBCType.LONGVARBINARY, new byte[] {0, 1, (byte) 255}),
                Map.entry(JDBCType.DATE, date), Map.entry(JDBCType.TIME, time), Map.entry(JDBCType.TIMESTAMP, timestamp));
        when(statement.getBoolean(1)).thenReturn(true);
        when(statement.getByte(1)).thenReturn((byte) 1);
        when(statement.getShort(1)).thenReturn((short) 2);
        when(statement.getInt(1)).thenReturn(3);
        when(statement.getLong(1)).thenReturn(4L);
        when(statement.getFloat(1)).thenReturn(1.5F);
        when(statement.getDouble(1)).thenReturn(2.5D);
        when(statement.getBigDecimal(1)).thenReturn(new BigDecimal("1.234"));
        when(statement.getString(1)).thenReturn("value");
        when(statement.getBytes(1)).thenReturn(new byte[] {0, 1, (byte) 255});
        when(statement.getDate(1)).thenReturn(Date.valueOf(date));
        when(statement.getObject(1, LocalTime.class)).thenReturn(time);
        when(statement.getTimestamp(1)).thenReturn(Timestamp.valueOf(timestamp));
        when(statement.getTimestamp(eq(1), any(Calendar.class))).thenReturn(Timestamp.from(timestamp.toInstant(ZoneOffset.UTC)));
        for (Map.Entry<JDBCType, Object> entry : samples.entrySet()) {
            JdbcClient.OutParameters result = client.create("{call routine(?)}").call()
                    .out("value", 1, entry.getKey()).outputs();
            assertThat(result.get("value", entry.getValue().getClass()), is(entry.getValue()));
            assertThrows(IllegalArgumentException.class, () -> result.get("value", Object.class));
        }
        when(statement.wasNull()).thenReturn(true);
        when(statement.getBigDecimal(1)).thenReturn(null);
        when(statement.getString(1)).thenReturn(null);
        when(statement.getBytes(1)).thenReturn(null);
        when(statement.getDate(1)).thenReturn(null);
        when(statement.getObject(1, LocalTime.class)).thenReturn(null);
        when(statement.getTimestamp(eq(1), any(Calendar.class))).thenReturn(null);
        for (Map.Entry<JDBCType, Object> entry : samples.entrySet()) {
            JdbcClient.OutParameters result = client.create("{call routine(?)}").call()
                    .out("value", 1, entry.getKey()).outputs();
            Class<?> canonical = entry.getValue().getClass();
            assertThat(entry.getKey().name(), result.optional("value", canonical), is(Optional.empty()));
            assertThrows(IllegalArgumentException.class, () -> result.get("value", canonical));
            assertThrows(IllegalArgumentException.class, () -> result.optional("value", Object.class));
            assertThrows(IllegalArgumentException.class, () -> result.optional("missing", canonical));
        }
    }

    /**
     * The first processing failure forces rollback even when application code catches the terminal failure.
     */
    @Test
    void reservesTransactionAndRetainsRollbackOnlyAfterCaughtFailure() throws Exception {
        AtomicBoolean automatic = new AtomicBoolean(true);
        when(connection.getAutoCommit()).thenAnswer(_ -> automatic.get());
        doAnswer(invocation -> {
            automatic.set((Boolean) invocation.getArguments()[0]);
            return null;
        }).when(connection).setAutoCommit(anyBoolean());
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        JdbcRunner runner = new JdbcRunner(source, manager);
        manager.start("jdbc");
        manager.begin("call");
        when(statement.wasNull()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () ->
                new JdbcStatement(runner, JdbcConfiguredStatement.create("{call routine(:value)}")).call()
                .out("value", JDBCType.INTEGER).consume(result -> {
                    assertThrows(NullPointerException.class, () -> manager.acquire(null));
                    assertThrows(IllegalStateException.class, () -> manager.acquire(source));
                    assertThrows(IllegalStateException.class, () -> manager.suspend("call"));
                    assertThrows(IllegalArgumentException.class, () -> result.get("value", Integer.class));
                }));
        try (JdbcConnectionLease released = manager.acquire(source)) {
            assertThat(released.connection(), sameInstance(connection));
        }
        assertThrows(TxException.class, () -> manager.commit("call"));
        manager.end();
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    /**
     * A fatal cleanup failure takes precedence while retaining the application failure as evidence.
     */
    @Test
    void fatalCleanupTakesPrecedence() throws Exception {
        Error fatal = new OutOfMemoryError("test");
        doThrow(fatal).when(statement).close();
        IllegalArgumentException application = new IllegalArgumentException("application");
        assertThat(assertThrows(OutOfMemoryError.class, () -> client.create("{call routine()}").call()
                .consume(_ -> { throw application; })), sameInstance(fatal));
        verify(connection).close();
    }

    /**
     * A later callback error cannot replace the first row processing error or prevent cleanup.
     */
    @Test
    void preservesFirstErrorWhenCallbackThrowsAnotherError() throws Exception {
        rows(1, 42);
        ResultSet resultSet = statement.getResultSet();
        Error first = new AssertionError("The first mapper failed.");
        Error later = new AssertionError("The callback failed later.");

        Error observed = assertThrows(Error.class, () -> client.create("{call routine()}").call().consume(result -> {
            assertThat(assertThrows(Error.class, () -> result.rows(_ -> { throw first; }).list()), sameInstance(first));
            throw later;
        }));

        assertThat(observed, sameInstance(first));
        assertThat(observed.getSuppressed().length, is(1));
        assertThat(observed.getSuppressed()[0], sameInstance(later));
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    /**
     * Repeated retention through nested row processing boundaries suppresses a later error exactly once.
     */
    @Test
    void preservesFirstReadErrorWhenMapperThrowsAnotherError() throws Exception {
        rows(1, 42);
        ResultSet resultSet = statement.getResultSet();
        Error first = new AssertionError("The row read failed.");
        Error later = new AssertionError("The mapper failed later.");
        when(resultSet.getInt(1)).thenThrow(first);

        Error observed = assertThrows(Error.class, () -> client.create("{call routine()}").call().consume(result ->
                result.rows(row -> {
                    assertThat(assertThrows(Error.class, () -> row.get(1, Integer.class)), sameInstance(first));
                    throw later;
                }).one()));

        assertThat(observed, sameInstance(first));
        assertThat(observed.getSuppressed().length, is(1));
        assertThat(observed.getSuppressed()[0], sameInstance(later));
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    /**
     * A later fatal error still outranks an earlier recoverable mapper failure.
     */
    @Test
    void promotesCallbackErrorAfterRecoverableMapperFailure() throws Exception {
        rows(1, 42);
        IllegalArgumentException first = new IllegalArgumentException("The mapper failed.");
        Error later = new AssertionError("The callback failed later.");

        Error observed = assertThrows(Error.class, () -> client.create("{call routine()}").call().consume(result -> {
            assertThat(assertThrows(IllegalArgumentException.class, () -> result.rows(_ -> { throw first; }).list()),
                       sameInstance(first));
            throw later;
        }));

        assertThat(observed, sameInstance(later));
        assertThat(observed.getSuppressed().length, is(1));
        assertThat(observed.getSuppressed()[0], sameInstance(first));
        verify(statement).close();
        verify(connection).close();
    }

    /**
     * A recoverable mapper failure never replaces an earlier row read error.
     */
    @Test
    void preservesReadErrorWhenMapperThrowsRecoverableFailure() throws Exception {
        rows(1, 42);
        ResultSet resultSet = statement.getResultSet();
        Error first = new AssertionError("The row read failed.");
        IllegalStateException later = new IllegalStateException("The mapper failed later.");
        when(resultSet.getInt(1)).thenThrow(first);

        Error observed = assertThrows(Error.class, () -> client.create("{call routine()}").call().consume(result ->
                result.rows(row -> {
                    assertThat(assertThrows(Error.class, () -> row.get(1, Integer.class)), sameInstance(first));
                    throw later;
                }).one()));

        assertThat(observed, sameInstance(first));
        assertThat(observed.getSuppressed().length, is(1));
        assertThat(observed.getSuppressed()[0], sameInstance(later));
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void rejectsInputInLeadingReturnSlotWithoutAcquisition() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> client.create("{? = call routine()}").bind(1, 1));
        verify(source, never()).getConnection();
    }

    /**
     * A selected cursor stays usable on its invocation thread after an off-thread access is rejected before JDBC.
     */
    @Test
    void cursorIsConfinedAndSequential() throws Exception {
        ResultSet first = mock(ResultSet.class);
        ResultSet second = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(first.getMetaData()).thenReturn(metadata);
        when(second.getMetaData()).thenReturn(metadata);
        when(statement.getObject(1)).thenReturn(first);
        when(statement.getObject(2)).thenReturn(second);
        client.create("{call routine(?, ?)}").call().out("first", 1, JDBCType.REF_CURSOR)
                .out("second", 2, JDBCType.REF_CURSOR).consume(result -> {
                    JdbcClient.Rows<Integer> selected = result.cursor("first", _ -> 1);
                    result.next();
                    result.skipRemainingResults();
                    assertThat(result.resultKind(), is(JdbcClient.CallResultKind.END));
                    CompletableFuture<Void> worker = CompletableFuture.runAsync(() ->
                            assertThrows(IllegalStateException.class, selected::list));
                    try {
                        worker.get(5, TimeUnit.SECONDS);
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    } finally {
                        worker.cancel(true);
                    }
                    assertThat(selected.list(), is(List.of()));
                    assertThat(result.cursor("second", _ -> 2).list(), is(List.of()));
                });
        verify(statement).getObject(1);
        verify(statement).getObject(2);
        verify(first).close();
        verify(second).close();
    }

    @Test
    void reportsCardinalityFailureAndPreservesItAfterCleanup() throws Exception {
        rows(1, 42);
        ResultSet resultSet = statement.getResultSet();
        when(resultSet.next()).thenReturn(true, true, false);
        doThrow(new SQLException("cleanup secret")).when(resultSet).close();
        DataException failure = assertThrows(NonUniqueResultException.class,
                () -> client.create("{call routine()}").call().consume(result -> {
                    assertThrows(NonUniqueResultException.class,
                                 () -> result.rows(row -> row.get(1, Integer.class)).one());
                    throw new IllegalArgumentException("later application failure");
                }));
        assertThat(failure.getSuppressed().length, is(2));
        verify(connection).close();
    }

    @Test
    void registersNamedAndScaledOutputsBeforeBindingAndExecution() throws Exception {
        client.create("{call routine(?, ?, ?)}").bind(3, "input").call()
                .out("named", 1, JDBCType.STRUCT, "APP.TYPE")
                .out("scaled", 2, JDBCType.DECIMAL, 2)
                .out("inout", 3, JDBCType.VARCHAR).execute();
        InOrder order = inOrder(statement);
        order.verify(statement).registerOutParameter(1, JDBCType.STRUCT.getVendorTypeNumber(), "APP.TYPE");
        order.verify(statement).registerOutParameter(2, JDBCType.DECIMAL.getVendorTypeNumber(), 2);
        order.verify(statement).registerOutParameter(3, JDBCType.VARCHAR.getVendorTypeNumber());
        order.verify(statement).setString(3, "input");
        order.verify(statement).execute();
        order.verify(statement).close();
    }

    /**
     * Callable setup and read failures retain sanitized diagnostics and attempt every acquired resource.
     */
    @Test
    void sanitizesFailuresAcrossEveryJdbcBoundary() throws Exception {
        for (String phase : List.of("acquire", "prepare", "register", "bind", "execute", "read", "close")) {
            setup();
            SQLException driver = new SQLException("confidential-driver-canary", "HY000", 1234);
            switch (phase) {
            case "acquire" -> when(source.getConnection()).thenThrow(driver);
            case "prepare" -> when(connection.prepareCall(anyString())).thenThrow(driver);
            case "register" -> doThrow(driver).when(statement).registerOutParameter(1, JDBCType.INTEGER.getVendorTypeNumber());
            case "bind" -> doThrow(driver).when(statement).setInt(1, 7);
            case "execute" -> when(statement.execute()).thenThrow(driver);
            case "read" -> when(statement.getInt(1)).thenThrow(driver);
            case "close" -> doThrow(driver).when(statement).close();
            default -> throw new AssertionError(phase);
            }
            DataException failure = assertThrows(DataException.class, () -> client.create("{call confidential_sql(?)}")
                    .bind(1, 7).call().out("value", 1, JDBCType.INTEGER).outputs());
            StringWriter text = new StringWriter();
            failure.printStackTrace(new PrintWriter(text));
            assertThat(text.toString().contains("confidential-driver-canary"), is(false));
            assertThat(text.toString().contains("confidential_sql"), is(false));
            if (!phase.equals("acquire")) {
                verify(connection).close();
            }
            if (!phase.equals("acquire") && !phase.equals("prepare")) {
                verify(statement).close();
            }
        }
    }

    @Test
    void exposesLargeCountsInEncounterOrderAndEmptySnapshots() throws Exception {
        when(statement.getLargeUpdateCount()).thenReturn(3_000_000_000L, 0L, -1L);
        client.create("{call routine()}").call().consume(result -> {
            assertThat(result.updateCount(), is(3_000_000_000L));
            result.next();
            assertThat(result.updateCount(), is(0L));
            result.next();
            assertThat(result.resultKind(), is(JdbcClient.CallResultKind.END));
        });
        JdbcClient.OutParameters empty = client.create("{call routine()}").call().outputs();
        assertThrows(IllegalArgumentException.class, () -> empty.optional("missing", Integer.class));
    }

    @Test
    void resolvesConfiguredOutputsAndInoutByFullLayout() throws Exception {
        for (String sql : List.of("{call routine(:output, :seed, :input)}", "{call routine(?, ?, ?)}")) {
            setup();
            when(statement.getInt(1)).thenReturn(41);
            when(statement.getInt(2)).thenReturn(42);
            JdbcClient.OutParameters values = client.prepare(sql, List.of("output", "seed", "input")).get()
                    .bind("input", 7).bind("seed", 2).call()
                    .out("seed", JDBCType.INTEGER).out("output", JDBCType.INTEGER).outputs();
            assertThat(values.get("seed", Integer.class), is(42));
            assertThat(values.get("output", Integer.class), is(41));
            verify(statement).setInt(2, 2);
            verify(statement).setInt(3, 7);
            verify(statement).registerOutParameter(1, JDBCType.INTEGER.getVendorTypeNumber());
            verify(statement).registerOutParameter(2, JDBCType.INTEGER.getVendorTypeNumber());
            verify(statement).execute();
            verify(statement).close();
            verify(connection).close();
        }
    }

    @Test
    void resolvesReturnWithNamedArgumentsAndReorderedOutputs() throws Exception {
        var definition = client.prepare("{? = call routine(:value, :input)}", List.of("return", "input", "value"));
        JdbcClient.Statement stage = definition.get();
        assertThrows(IllegalArgumentException.class, () -> stage.bind("return", 7));
        assertThrows(IllegalArgumentException.class, () -> stage.bind(1, 7));
        JdbcClient.Call call = stage.bind("input", 9).call();
        assertThrows(IllegalArgumentException.class, () -> call.out("return", JDBCType.INTEGER));
        assertThrows(NullPointerException.class, () -> call.out("return", JDBCType.INTEGER, (String) null));
        assertThrows(NullPointerException.class, () -> call.returns(JDBCType.INTEGER, (String) null));
        when(statement.getInt(1)).thenReturn(42);
        when(statement.getInt(2)).thenReturn(43);
        JdbcClient.OutParameters result = call.out("value", JDBCType.INTEGER).returns(JDBCType.INTEGER).outputs();
        assertThat(result.get("return", Integer.class), is(42));
        assertThat(result.get("value", Integer.class), is(43));
        verify(statement).setInt(3, 9);
        verify(connection).prepareCall("{? = call routine(?, ?)}");
    }

    @Test
    void rejectsRepeatedOutputsWithoutChangingRegistration() throws Exception {
        JdbcClient.Call call = client.prepare("{call routine(:value, :value, :output)}").get()
                .bind("value", 7).call();
        assertThrows(IllegalArgumentException.class, () -> call.out("value", JDBCType.INTEGER));
        assertThrows(IllegalArgumentException.class, () -> call.out("other", 1, JDBCType.INTEGER));
        assertThrows(IllegalArgumentException.class, () -> call.out("missing", JDBCType.INTEGER));
        assertThrows(IllegalArgumentException.class, () -> call.returns(JDBCType.INTEGER));
        verify(source, never()).getConnection();
        call.out("output", JDBCType.INTEGER).execute();
        verify(statement).setInt(1, 7);
        verify(statement).setInt(2, 7);
    }

    @Test
    void rejectsPositionalNamedOutputDefinitionsBeforeAcquisition() throws Exception {
        assertThrows(IllegalArgumentException.class,
                     () -> GeneratedJdbcData.prepareNamed(client, "{call routine(?)}", List.of("output")));
        assertThrows(IllegalArgumentException.class,
                     () -> GeneratedJdbcData.prepareNamed(client, "{call routine(?, ?)}", List.of("input", "output")));
        for (String sql : List.of("{call routine(:input, ?)}", "{? = call routine(:input, ?)}")) {
            assertThrows(IllegalArgumentException.class, () -> client.prepare(sql));
        }
        verify(source, never()).getConnection();

        GeneratedJdbcData.prepareNamed(client, "{call routine(:input, :output)}", List.of("input", "output"))
                .get().bind("input", 7).call().out("output", JDBCType.INTEGER).execute();
        verify(statement).setInt(1, 7);
        verify(statement).registerOutParameter(2, JDBCType.INTEGER.getVendorTypeNumber());
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void rejectsMissingCoverageBeforeConsumingConfiguredCall() throws Exception {
        JdbcClient.Statement stage = client.prepare("{call routine(?, ?)}", List.of("input", "output")).get();
        JdbcClient.Call call = stage.call().out("output", JDBCType.INTEGER);
        assertThrows(IllegalStateException.class, call::execute);
        verify(source, never()).getConnection();
        stage.bind("input", 7);
        call.execute();
        verify(statement).setInt(1, 7);
    }

    @Test
    void restrictsMixedReturnDefinitionsToCallableExecution() throws Exception {
        var definition = GeneratedJdbcData.prepareNamed(client, "{? = call routine(:value)}", List.of("return", "value"));
        assertThrows(IllegalStateException.class, () -> definition.get().bind("value", 7).execute());
        assertThrows(IllegalStateException.class, () -> definition.get().bind("value", 7).map(Integer.class).one());
        assertThrows(IllegalStateException.class, () -> definition.get().bind("value", 7).generatedKeys()
                .map(row -> row.get(1, Integer.class)).one());
        assertThrows(IllegalArgumentException.class, () -> client.prepare("{? = call routine(:value, ?)}"));
        assertThrows(IllegalArgumentException.class, () -> client.prepare("{? = call routine(:return)}"));
        assertThrows(IllegalArgumentException.class, () -> client.prepare("{? = call routine(?)}", List.of("value", "return")));
        verify(source, never()).getConnection();
    }

    @Test
    void preservesLiteralIndexedRegistrationsAndRequiresReturnSyntax() throws Exception {
        assertThrows(IllegalStateException.class, () -> client.create("{call routine(?)}").call()
                .out("value", JDBCType.INTEGER));
        JdbcClient.Call ordinary = client.prepare("{call routine(:return)}").get().call();
        assertThrows(IllegalArgumentException.class, () -> ordinary.returns(JDBCType.INTEGER));
        ordinary.out("return", JDBCType.INTEGER).execute();
        client.create("{? = call routine()}").call().returns(JDBCType.DECIMAL, 2).execute();
        client.create("{? = call routine()}").call().out("legacy", 1, JDBCType.INTEGER).execute();
        verify(statement).registerOutParameter(1, JDBCType.DECIMAL.getVendorTypeNumber(), 2);
    }

    @Test
    void preservesAlternateCallDefaultsAndNullPrecedence() {
        JdbcClient.Call alternate = mock(JdbcClient.Call.class, CALLS_REAL_METHODS);
        assertThrows(NullPointerException.class, () -> alternate.out(null, JDBCType.INTEGER));
        assertThrows(NullPointerException.class, () -> alternate.out("value", null));
        assertThrows(NullPointerException.class, () -> alternate.out("value", JDBCType.INTEGER, (String) null));
        assertThrows(NullPointerException.class, () -> alternate.returns(null));
        assertThrows(NullPointerException.class, () -> alternate.returns(JDBCType.INTEGER, (String) null));
        assertThrows(UnsupportedOperationException.class, () -> alternate.out("value", JDBCType.INTEGER));
        assertThrows(UnsupportedOperationException.class, () -> alternate.out("value", JDBCType.INTEGER, "TYPE"));
        assertThrows(UnsupportedOperationException.class, () -> alternate.out("value", JDBCType.INTEGER, -1));
        assertThrows(UnsupportedOperationException.class, () -> alternate.returns(JDBCType.INTEGER));
        assertThrows(UnsupportedOperationException.class, () -> alternate.returns(JDBCType.INTEGER, "TYPE"));
        assertThrows(UnsupportedOperationException.class, () -> alternate.returns(JDBCType.INTEGER, -1));
    }

    @Test
    void registersLogicalTypesAndScalesWithoutJdbcWork() throws Exception {
        JdbcClient.Call call = client.prepare("{? = call routine(:named, :scaled)}").get().call();
        assertThrows(IllegalArgumentException.class, () -> call.out("named", JDBCType.VARCHAR, " "));
        assertThrows(IllegalArgumentException.class, () -> call.out("scaled", JDBCType.INTEGER, 2));
        call.out("named", JDBCType.STRUCT, "APP.VALUE").out("scaled", JDBCType.DECIMAL, 2)
                .returns(JDBCType.STRUCT, "APP.RESULT");
        verify(source, never()).getConnection();
        call.execute();
        assertThrows(NullPointerException.class, () -> call.out(null, JDBCType.INTEGER));
        assertThrows(NullPointerException.class, () -> call.returns(null));
        verify(statement).registerOutParameter(2, JDBCType.STRUCT.getVendorTypeNumber(), "APP.VALUE");
        verify(statement).registerOutParameter(3, JDBCType.DECIMAL.getVendorTypeNumber(), 2);
        verify(statement).registerOutParameter(1, JDBCType.STRUCT.getVendorTypeNumber(), "APP.RESULT");
    }

    private void rows(int size, int value) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(resultSet.next()).thenReturn(size > 0, false);
        when(resultSet.getInt(1)).thenReturn(value);
    }
}
