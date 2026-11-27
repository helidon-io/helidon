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
import java.io.StringWriter;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcCallableFailureTest {
    /**
     * Caught metadata failures remain fatal to every materialized callable row shape and require rollback.
     */
    @Test
    void retainsCaughtMaterializedMetadataFailures() throws Exception {
        for (boolean transactional : new boolean[] {false, true}) {
            for (boolean cursor : new boolean[] {false, true}) {
                for (boolean optional : new boolean[] {false, true}) {
                    for (boolean physicalName : new boolean[] {false, true}) {
                        for (int shape = 0; shape < 3; shape++) {
                            try (Fixture fixture = new Fixture(cursor, transactional)) {
                                if (physicalName) {
                                    when(fixture.metadata.getColumnLabel(1)).thenReturn("");
                                    when(fixture.metadata.getColumnName(1)).thenThrow(
                                            new IllegalArgumentException("Secret metadata name failure."));
                                } else {
                                    when(fixture.metadata.getColumnLabel(1)).thenThrow(
                                            new IllegalArgumentException("Secret metadata label failure."));
                                }
                                int selected = shape;
                                String description = "The metadata failure case uses transactional=" + transactional
                                        + ", cursor=" + cursor + ", optional getter=" + optional
                                        + ", physical name=" + physicalName + ", terminal=" + switch (selected) {
                                            case 0 -> "list";
                                            case 1 -> "one";
                                            case 2 -> "optional";
                                            default -> throw new AssertionError("The row shape is unknown.");
                                        } + ".";
                                AtomicReference<IllegalStateException> first = new AtomicReference<>();
                                IllegalStateException observed = assertThrows(IllegalStateException.class,
                                        () -> fixture.call().consume(result -> {
                                            Objects.requireNonNull(result, "The callable result must not be null.");
                                            JdbcClient.RowMapper<Integer> mapper = row -> {
                                                Objects.requireNonNull(row, "The current row must not be null.");
                                                first.set(assertThrows(IllegalStateException.class, () -> {
                                                    if (optional) {
                                                        row.optional("VALUE", Integer.class);
                                                    } else {
                                                        row.get("VALUE", Integer.class);
                                                    }
                                                }, description));
                                                assertThrows(IllegalStateException.class,
                                                        () -> row.get(1, Integer.class), description);
                                                return 7;
                                            };
                                            JdbcClient.Rows<Integer> rows = fixture.rows(result, mapper);
                                            assertThat(description, assertThrows(IllegalStateException.class, () -> {
                                                switch (selected) {
                                                case 0 -> rows.list();
                                                case 1 -> rows.one();
                                                case 2 -> rows.optional();
                                                default -> throw new AssertionError("The row shape is unknown.");
                                                }
                                            }, description), sameInstance(first.get()));
                                            assertThrows(IllegalStateException.class, result::resultKind, description);
                                        }), description);
                                assertThat(description, observed, sameInstance(first.get()));
                                assertThat(description, render(observed), not(containsString("Secret metadata")));
                                verify(fixture.result, times(1).description(description)).next();
                                verify(fixture.result, never().description(description)).getInt(1);
                                fixture.complete(true, description);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Argument validation stays recoverable in materialized callable row mappers.
     */
    @Test
    void keepsPureRowValidationRecoverable() throws Exception {
        for (boolean cursor : new boolean[] {false, true}) {
            String description = "The recoverable validation case uses cursor=" + cursor + ".";
            try (Fixture fixture = new Fixture(cursor, true)) {
                fixture.call().consume(result -> {
                    Objects.requireNonNull(result, "The callable result must not be null.");
                    JdbcClient.Rows<Integer> rows = fixture.rows(result, row -> {
                        Objects.requireNonNull(row, "The current row must not be null.");
                        assertThrows(NullPointerException.class, () -> row.get(1, null), description);
                        assertThrows(NullPointerException.class, () -> row.optional(null, Integer.class), description);
                        assertThrows(IllegalArgumentException.class, () -> row.get(" ", Integer.class), description);
                        assertThrows(IllegalArgumentException.class, () -> row.optional(0, Integer.class), description);
                        assertThrows(IllegalArgumentException.class, () -> row.get(1, Object.class), description);
                        return row.get(1, Integer.class);
                    });
                    assertThat(description, rows.list(), is(List.of(42)));
                });
                fixture.complete(false, description);
            }
        }
    }

    /**
     * Materialized ordinary queries preserve their existing behavior when a mapper handles a metadata failure.
     */
    @Test
    void preservesOrdinaryMaterializedQueryBehavior() throws Exception {
        try (Fixture fixture = new Fixture(false, true)) {
            when(fixture.metadata.getColumnLabel(1)).thenThrow(new IllegalArgumentException("Secret metadata failure."));
            when(fixture.callable.getMoreResults()).thenAnswer(_ -> {
                fixture.result.close();
                return false;
            });
            assertThat(fixture.statement("SELECT VALUE").map(row -> {
                Objects.requireNonNull(row, "The current row must not be null.");
                assertThrows(IllegalStateException.class, () -> row.get("VALUE", Integer.class));
                return row.get(1, Integer.class);
            }).list(), is(List.of(42)));
            fixture.complete(false, "The ordinary materialized query should commit.");
        }
    }

    /**
     * Fatal row, statement, and lease cleanup preserves the original application failure by identity.
     */
    @Test
    void preservesApplicationIdentityDuringFatalCleanup() throws Exception {
        for (String resource : List.of("rows", "statement", "lease")) {
            for (int mode = 0; mode < 2; mode++) {
                for (boolean dataException : new boolean[] {false, true}) {
                    String description = "The identity case uses resource=" + resource + ", failure source="
                            + (mode == 0 ? "callback" : "row mapper")
                            + " and dataException=" + dataException + ".";
                    try (Fixture fixture = new Fixture(false, false)) {
                        Error fatal = new AssertionError("The cleanup failed fatally.");
                        switch (resource) {
                        case "rows" -> doThrow(fatal).when(fixture.result).close();
                        case "statement" -> doThrow(fatal).when(fixture.callable).close();
                        case "lease" -> doThrow(fatal).when(fixture.connection).close();
                        default -> throw new AssertionError("The cleanup resource is unknown.");
                        }
                        RuntimeException application = dataException ? new DataException("The application failed.")
                                : new IllegalArgumentException("The application failed.");
                        int selected = mode;
                        Error observed = assertThrows(Error.class, () -> fixture.call().consume(result -> {
                            Objects.requireNonNull(result, "The callable result must not be null.");
                            if (selected == 0) {
                                throw application;
                            }
                            JdbcClient.Rows<Integer> rows = result.rows(_ -> {
                                throw application;
                            });
                            rows.list();
                        }), description);
                        assertThat(description, observed, sameInstance(fatal));
                        assertThat(description, observed.getSuppressed(), is(new Throwable[] {application}));
                        fixture.verifyClosed(resource.equals("lease") ? 2 : 1, description);
                    }
                }
            }
        }
    }

    /**
     * Secondary JDBC errors are sanitized while the first application error retains its identity and requires rollback.
     */
    @Test
    void retainsFirstError() throws Exception {
        for (boolean transactional : new boolean[] {false, true}) {
            for (boolean cursor : new boolean[] {false, true}) {
                String description = "The cleanup case uses transactional=" + transactional + " and cursor=" + cursor + ".";
                try (Fixture fixture = new Fixture(cursor, transactional)) {
                    Error first = new AssertionError("The application failed first.");
                    Error row = new AssertionError("The JDBC_CANARY row cleanup failed.");
                    row.initCause(new IllegalArgumentException("The JDBC_CANARY cause was reported."));
                    row.addSuppressed(new SQLException("The JDBC_CANARY failure was suppressed."));
                    Error statement = new AssertionError("The JDBC_CANARY statement cleanup failed.");
                    doThrow(row).when(fixture.result).close();
                    doThrow(statement).when(fixture.callable).close();

                    Error observed = assertThrows(Error.class, () -> fixture.call().consume(result -> {
                        if (cursor) {
                            fixture.rows(result, _ -> {
                                throw first;
                            }).list();
                        } else {
                            throw first;
                        }
                    }), description);

                    assertThat(description, observed, sameInstance(first));
                    assertThat(description, first.getSuppressed().length, is(2));
                    assertThat(description, first.getSuppressed()[0], not(sameInstance(row)));
                    assertThat(description, first.getSuppressed()[1], not(sameInstance(statement)));
                    assertThat(description, render(first), not(containsString("JDBC_CANARY")));
                    fixture.complete(true, description);
                }
            }
        }
    }

    /**
     * Raw setup SQL failures are sanitized before a fatal cleanup failure becomes primary.
     */
    @Test
    void sanitizesSetupFailuresBeforeFatalCleanup() throws Exception {
        for (String setup : List.of("prepare", "register", "bind")) {
            String description = "The setup failure occurs during " + setup + ".";
            try (Fixture fixture = new Fixture(false, false)) {
                SQLException original = new SQLException("Secret setup SQL and value.", "22003", 42);
                Error fatal = new AssertionError("Cleanup failed fatally.");
                if (setup.equals("prepare")) {
                    when(fixture.connection.prepareCall(anyString())).thenThrow(original);
                    doThrow(fatal).when(fixture.connection).close();
                } else {
                    doThrow(fatal).when(fixture.callable).close();
                    if (setup.equals("register")) {
                        doThrow(original).when(fixture.callable).registerOutParameter(1, JDBCType.INTEGER.getVendorTypeNumber());
                    } else {
                        doThrow(original).when(fixture.callable).setInt(1, 1);
                    }
                }
                Error observed = assertThrows(Error.class, () -> fixture.statement("{call routine(?)}").bind(1, 1)
                        .call().out("value", 1, JDBCType.INTEGER).execute(), description);
                assertThat(description, observed, sameInstance(fatal));
                assertThat(description, observed.getSuppressed().length, is(1));
                assertThat(description, render(observed), not(containsString(original.getMessage())));
                assertThat(description, render(observed), containsString("22003"));
                verify(fixture.callable, never().description(description)).execute();
                verify(fixture.connection, times(setup.equals("prepare") ? 2 : 1).description(description)).close();
            }
        }
    }

    /**
     * Multiple infrastructure failures remain sanitized while fatal cleanup retains the original application failure.
     */
    @Test
    void sanitizesCleanupWithoutReplacingApplicationFailure() throws Exception {
        try (Fixture fixture = new Fixture(false, false)) {
            RuntimeException application = new IllegalArgumentException("The application failed.");
            Error fatal = new AssertionError("Connection cleanup failed fatally.");
            doThrow(new SQLException("Secret row cleanup.")).when(fixture.result).close();
            doThrow(new IllegalArgumentException("Secret statement cleanup.")).when(fixture.callable).close();
            doThrow(fatal).when(fixture.connection).close();
            assertThat(assertThrows(Error.class, () -> fixture.call().consume(_ -> {
                throw application;
            })), sameInstance(fatal));
            assertThat(fatal.getSuppressed(), is(new Throwable[] {application}));
            assertThat(application.getSuppressed().length, is(2));
            assertThat(render(fatal), not(containsString("Secret")));
            fixture.verifyClosed(2, "Fatal cleanup should release the connection after retry.");
        }
    }

    @Test
    void closesAfterFatalTranslation() throws Exception {
        for (boolean transactional : new boolean[] {false, true}) {
            for (boolean setup : new boolean[] {false, true}) {
                String description = "transactional=" + transactional + ", setup=" + setup;
                try (Fixture fixture = new Fixture(false, transactional)) {
                    LinkageError fatal = new LinkageError("The JDBC metadata accessor failed.");
                    SQLException sqlFailure = mock(SQLException.class);
                    when(sqlFailure.getSQLState()).thenThrow(fatal);
                    if (setup) {
                        doThrow(sqlFailure).when(fixture.callable)
                                .registerOutParameter(1, JDBCType.INTEGER.getVendorTypeNumber());
                    } else {
                        doThrow(sqlFailure).when(fixture.callable).close();
                    }
                    JdbcClient.Call call = fixture.statement("{call routine(?)}").call().out("value", 1, JDBCType.INTEGER);
                    assertThat(description, assertThrows(LinkageError.class, call::execute, description), sameInstance(fatal));
                    verify(fixture.callable, times(1).description(description)).close();
                    if (transactional) {
                        try {
                            assertThrows(TxException.class, () -> fixture.manager.commit("failure"), description);
                            verify(fixture.connection, times(1).description(description)).rollback();
                        } finally {
                            fixture.active = false;
                            fixture.manager.end();
                        }
                    }
                    verify(fixture.connection, times(1).description(description)).close();
                }
            }
        }
    }

    @Test
    void retainsCaughtRowTranslationError() throws Exception {
        try (Fixture fixture = new Fixture(false, true)) {
            LinkageError fatal = new LinkageError("The JDBC metadata accessor failed.");
            SQLException sqlFailure = mock(SQLException.class);
            when(sqlFailure.getSQLState()).thenThrow(fatal);
            when(fixture.result.getInt(1)).thenThrow(sqlFailure);
            JdbcClient.Call call = fixture.call();
            LinkageError observed = assertThrows(LinkageError.class, () -> call.consume(result -> {
                JdbcClient.Rows<Integer> rows = fixture.rows(result, row -> {
                    assertThat(assertThrows(LinkageError.class, () -> row.get(1, Integer.class)), sameInstance(fatal));
                    return 42;
                });
                assertThat(assertThrows(LinkageError.class, rows::one), sameInstance(fatal));
            }));
            assertThat(observed, sameInstance(fatal));
            fixture.complete(true, "The row translation error should require rollback.");
        }
    }

    @Test
    void avoidsSuppressingCallbackWrapper() throws Exception {
        try (Fixture fixture = new Fixture(false, true)) {
            when(fixture.result.wasNull()).thenReturn(true);
            AtomicReference<DataException> original = new AtomicReference<>();
            DataException observed = assertThrows(DataException.class, () -> fixture.call().consume(result -> {
                JdbcClient.Rows<Integer> rows = fixture.rows(result, row -> row.get(1, Integer.class));
                original.set(assertThrows(DataException.class, rows::one));
                throw new IllegalStateException("The callback wrapped the processing failure.", original.get());
            }));
            assertThat(observed, sameInstance(original.get()));
            assertThat(observed.getSuppressed(), is(new Throwable[0]));
            fixture.complete(true, "The callback wrapper should require rollback.");
        }
    }

    @Test
    void retainsFatalWhenCauseAccessorFails() throws Exception {
        for (boolean transactional : new boolean[] {false, true}) {
            String description = "transactional=" + transactional;
            try (Fixture fixture = new Fixture(false, transactional)) {
                AssertionError original = new AssertionError("The row mapper failed.");
                LinkageError accessorFailure = new LinkageError("The callback cause accessor failed.");
                Error observed = assertThrows(Error.class, () -> fixture.call().consume(result -> {
                    JdbcClient.Rows<Integer> rows = fixture.rows(result, _ -> {
                        throw original;
                    });
                    assertThat(description, assertThrows(AssertionError.class, rows::one, description), sameInstance(original));
                    throw new IllegalStateException("The callback wrapped the processing failure.", original) {
                        @Override
                        public Throwable getCause() {
                            throw accessorFailure;
                        }
                    };
                }), description);
                assertThat(description, observed, sameInstance(original));
                assertThat(description, observed.getSuppressed(), is(new Throwable[0]));
                fixture.complete(true, description);
            }
        }
    }

    @Test
    void retainsCaughtDateFailures() throws Exception {
        for (boolean transactional : new boolean[] {false, true}) {
            String description = "transactional=" + transactional;
            try (Fixture fixture = new Fixture(false, transactional)) {
                when(fixture.callable.getDate(eq(1), any(Calendar.class)))
                        .thenThrow(new SQLException("The driver DATE conversion failed."));
                AtomicReference<DataException> first = new AtomicReference<>();
                DataException observed = assertThrows(DataException.class, () -> fixture.statement("{call routine(?)}")
                        .call().out("value", 1, JDBCType.DATE).consume(result -> {
                            result.skipRemainingResults();
                            first.set(assertThrows(DataException.class,
                                    () -> result.optional("value", LocalDate.class), description));
                        }), description);
                assertThat(description, observed, sameInstance(first.get()));
                fixture.complete(true, description);
            }
        }
    }

    private static String render(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }

    private static final class Fixture implements AutoCloseable {
        private final DataSource source = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final CallableStatement callable = mock(CallableStatement.class);
        private final ResultSet result = mock(ResultSet.class);
        private final ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        private final AtomicBoolean automatic = new AtomicBoolean(true);
        private final JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        private final boolean cursor;
        private boolean active;

        private Fixture(boolean cursor, boolean transactional) throws Exception {
            this.cursor = cursor;
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenAnswer(_ -> automatic.get());
            doAnswer(invocation -> {
                automatic.set(invocation.getArgument(0));
                return null;
            })
                    .when(connection).setAutoCommit(anyBoolean());
            when(connection.prepareCall(anyString())).thenReturn(callable);
            when(connection.prepareStatement(anyString())).thenReturn(callable);
            when(callable.execute()).thenReturn(!cursor);
            when(callable.getLargeUpdateCount()).thenReturn(-1L);
            when(callable.getResultSet()).thenReturn(result);
            when(callable.getObject(1)).thenReturn(result);
            when(result.getMetaData()).thenReturn(metadata);
            when(metadata.getColumnCount()).thenReturn(1);
            when(metadata.getColumnLabel(1)).thenReturn("VALUE");
            when(result.next()).thenReturn(true, false);
            when(result.getInt(1)).thenReturn(42);
            if (transactional) {
                manager.start("jdbc");
                manager.begin("failure");
                active = true;
            }
        }

        @Override
        public void close() {
            if (active) {
                try {
                    manager.rollback("failure");
                } finally {
                    manager.end();
                }
            }
        }

        private JdbcStatement statement(String sql) {
            return new JdbcStatement(new JdbcRunner(source, manager), sql, sql.contains("?") ? 1 : 0);
        }

        private JdbcClient.Call call() {
            JdbcClient.Call call = statement(cursor ? "{call routine(?)}" : "{call routine()}").call();
            return cursor ? call.out("rows", 1, JDBCType.REF_CURSOR) : call;
        }

        private JdbcClient.Rows<Integer> rows(JdbcClient.CallResult result, JdbcClient.RowMapper<Integer> mapper) {
            return cursor ? result.cursor("rows", mapper) : result.rows(mapper);
        }

        private void complete(boolean rollback, String description) throws Exception {
            if (active) {
                try {
                    if (rollback) {
                        assertThrows(TxException.class, () -> manager.commit("failure"), description);
                        verify(connection, times(1).description(description)).rollback();
                        verify(connection, never().description(description)).commit();
                    } else {
                        manager.commit("failure");
                        verify(connection, times(1).description(description)).commit();
                        verify(connection, never().description(description)).rollback();
                    }
                } finally {
                    active = false;
                    manager.end();
                }
            }
            verifyClosed(1, description);
        }

        private void verifyClosed(int connectionCloses, String description) throws Exception {
            verify(result, times(1).description(description)).close();
            verify(callable, times(1).description(description)).close();
            verify(connection, times(connectionCloses).description(description)).close();
        }
    }
}
