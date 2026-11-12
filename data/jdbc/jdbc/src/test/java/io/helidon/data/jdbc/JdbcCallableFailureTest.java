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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
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
                                                }));
                                                assertThrows(IllegalStateException.class, () -> row.get(1, Integer.class));
                                                return 7;
                                            };
                                            JdbcClient.Rows<Integer> rows = fixture.rows(result, mapper);
                                            assertThat(assertThrows(IllegalStateException.class, () -> {
                                                switch (selected) {
                                                case 0 -> rows.list();
                                                case 1 -> rows.one();
                                                case 2 -> rows.optional();
                                                default -> throw new AssertionError("The row shape is unknown.");
                                                }
                                            }), sameInstance(first.get()));
                                            assertThrows(IllegalStateException.class, result::resultKind);
                                        }));
                                assertThat(observed, sameInstance(first.get()));
                                assertThat(render(observed), not(containsString("Secret metadata")));
                                verify(fixture.result, times(1)).next();
                                verify(fixture.result, never()).getInt(1);
                                fixture.complete(true);
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
            try (Fixture fixture = new Fixture(cursor, true)) {
                fixture.call().consume(result -> {
                    Objects.requireNonNull(result, "The callable result must not be null.");
                    JdbcClient.Rows<Integer> rows = fixture.rows(result, row -> {
                        Objects.requireNonNull(row, "The current row must not be null.");
                        assertThrows(NullPointerException.class, () -> row.get(1, null));
                        assertThrows(NullPointerException.class, () -> row.optional(null, Integer.class));
                        assertThrows(IllegalArgumentException.class, () -> row.get(" ", Integer.class));
                        assertThrows(IllegalArgumentException.class, () -> row.optional(0, Integer.class));
                        assertThrows(IllegalArgumentException.class, () -> row.get(1, Object.class));
                        return row.get(1, Integer.class);
                    });
                    assertThat(rows.list(), is(List.of(42)));
                });
                fixture.complete(false);
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
            fixture.complete(false);
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
                            JdbcClient.Rows<Integer> rows = result.rows(_ -> { throw application; });
                            rows.list();
                        }));
                        assertThat(observed, sameInstance(fatal));
                        assertThat(observed.getSuppressed(), is(new Throwable[] {application}));
                        fixture.verifyClosed(resource.equals("lease") ? 2 : 1);
                    }
                }
            }
        }
    }

    /**
     * Later cleanup errors cannot replace an earlier application Error or add duplicate suppression.
     */
    @Test
    void preservesFirstErrorThroughMultipleCleanupFailures() throws Exception {
        try (Fixture fixture = new Fixture(false, false)) {
            Error first = new AssertionError("The application failed first.");
            Error row = new AssertionError("Row cleanup failed.");
            Error statement = new AssertionError("Statement cleanup failed.");
            doThrow(row).when(fixture.result).close();
            doThrow(statement).when(fixture.callable).close();
            assertThat(assertThrows(Error.class, () -> fixture.call().consume(_ -> { throw first; })), sameInstance(first));
            assertThat(first.getSuppressed(), is(new Throwable[] {row, statement}));
            fixture.verifyClosed(1);
        }
    }

    /**
     * Raw setup SQL failures are sanitized before a fatal cleanup failure becomes primary.
     */
    @Test
    void sanitizesSetupFailuresBeforeFatalCleanup() throws Exception {
        for (String setup : List.of("prepare", "register", "bind")) {
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
                        .call().out("value", 1, JDBCType.INTEGER).execute());
                assertThat(observed, sameInstance(fatal));
                assertThat(observed.getSuppressed().length, is(1));
                assertThat(render(observed), not(containsString(original.getMessage())));
                assertThat(render(observed), containsString("22003"));
                verify(fixture.callable, never()).execute();
                verify(fixture.connection, times(setup.equals("prepare") ? 2 : 1)).close();
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
            assertThat(assertThrows(Error.class, () -> fixture.call().consume(_ -> { throw application; })), sameInstance(fatal));
            assertThat(fatal.getSuppressed(), is(new Throwable[] {application}));
            assertThat(application.getSuppressed().length, is(2));
            assertThat(render(fatal), not(containsString("Secret")));
            fixture.verifyClosed(2);
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
            doAnswer(invocation -> { automatic.set(invocation.getArgument(0)); return null; })
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

        private void complete(boolean rollback) throws Exception {
            if (active) {
                try {
                    if (rollback) {
                        assertThrows(TxException.class, () -> manager.commit("failure"));
                        verify(connection).rollback();
                        verify(connection, never()).commit();
                    } else {
                        manager.commit("failure");
                        verify(connection).commit();
                        verify(connection, never()).rollback();
                    }
                } finally {
                    active = false;
                    manager.end();
                }
            }
            verifyClosed(1);
        }

        private void verifyClosed(int connectionCloses) throws Exception {
            verify(result).close();
            verify(callable).close();
            verify(connection, times(connectionCloses)).close();
        }
    }
}
