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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class JdbcHealthSupportTest {
    /**
     * Validates construction without touching either the datasource or an unsupported client.
     */
    @Test
    void validatesBeforeAcquisitionAndRejectsCustomClients() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcTestClients.create(source);
        for (String sql : List.of("", "WITH x AS (SELECT 1) SELECT * FROM x", "CALL x()", "EXPLAIN SELECT 1",
                                  "SELECTED 1", "SELECTé 1", "'SELECT'", "SELECT ?", "SELECT :value", "SELECT 1;;",
                                  "SELECT 1; SELECT 2", "SELECT 1; 'x'", "SELECT /* unfinished")) {
            assertThrows(IllegalArgumentException.class, () -> JdbcHealthSupport.createProbe(client, sql, Duration.ofSeconds(2)));
        }
        for (Duration timeout : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMillis(1500),
                                        Duration.ofSeconds((long) Integer.MAX_VALUE + 1))) {
            assertThrows(IllegalArgumentException.class, () -> JdbcHealthSupport.createProbe(client, "SELECT 1", timeout));
        }
        for (String sql : List.of("SELECT 1", " /* comment */ select 1; -- end\n", "SELECT '?;:x'", "SELECT $$?;:x$$",
                                  "SELECT q'[?;:x]'", "SELECT nq'[?;:x]'", "SELECT `?`, \":x\"", "SELECT 1 /* ? */")) {
            try {
                JdbcHealthSupport.createProbe(client, sql, Duration.ofSeconds(Integer.MAX_VALUE));
            } catch (IllegalArgumentException failure) {
                throw new AssertionError(sql, failure);
            }
        }
        JdbcClient custom = mock(JdbcClient.class);
        assertThrows(IllegalArgumentException.class,
                     () -> JdbcHealthSupport.createProbe(custom, "SELECT 1", Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> JdbcHealthSupport.createProbe(custom, null, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> JdbcHealthSupport.createProbe(client, "", null));
        assertThrows(NullPointerException.class, () -> JdbcHealthSupport.createProbe(null, "", Duration.ZERO));
        verifyZeroInteractions(source, custom);
    }

    /**
     * Uses the resolved source even when the public prototype describes a different named source.
     */
    @Test
    void retainsActualResolvedSourceAndExecutesFreshEmptyAndNonemptyProbes() throws Exception {
        Fixture fixture = fixture();
        when(fixture.rows().next()).thenReturn(true, true, false);
        BooleanSupplier probe = JdbcHealthSupport.createProbe(JdbcTestClients.create(fixture.source()),
                                                             "SELECT 1;", Duration.ofSeconds(3));
        verifyZeroInteractions(fixture.source());
        assertThat(probe.getAsBoolean(), is(true));
        assertThat(probe.getAsBoolean(), is(true));
        verify(fixture.source(), times(2)).getConnection();
        verify(fixture.connection(), times(2)).prepareStatement("SELECT 1");
        verify(fixture.statement(), times(2)).setQueryTimeout(3);
        verify(fixture.rows(), times(2)).close();
        verify(fixture.statement(), times(2)).close();
        verify(fixture.connection(), times(2)).close();
        noTransactionControl(fixture.connection());
    }

    /**
     * Contains driver failures while releasing every resource already acquired by the probe.
     */
    @Test
    void containsEveryInfrastructureFailureAndAttemptsCleanup() throws Exception {
        for (String fault : List.of("acquire", "mode", "prepare", "timeout", "execute", "rows", "next", "rowClose",
                                    "more", "count", "statementClose", "connectionClose", "runtime")) {
            Fixture fixture = fixture();
            SQLException failure = new SQLException("Secret SQL and credentials", "08001", 123);
            switch (fault) {
                case "acquire" -> when(fixture.source().getConnection()).thenThrow(failure);
                case "mode" -> when(fixture.connection().getAutoCommit()).thenThrow(failure);
                case "prepare" -> when(fixture.connection().prepareStatement(anyString())).thenThrow(failure);
                case "timeout" -> doThrow(failure).when(fixture.statement()).setQueryTimeout(anyInt());
                case "execute" -> when(fixture.statement().execute()).thenThrow(failure);
                case "rows" -> when(fixture.statement().getResultSet()).thenThrow(failure);
                case "next" -> when(fixture.rows().next()).thenThrow(failure);
                case "rowClose" -> doThrow(failure).when(fixture.rows()).close();
                case "more" -> when(fixture.statement().getMoreResults(anyInt())).thenThrow(failure);
                case "count" -> when(fixture.statement().getUpdateCount()).thenThrow(failure);
                case "statementClose" -> doThrow(failure).when(fixture.statement()).close();
                case "connectionClose" -> doThrow(failure).when(fixture.connection()).close();
                case "runtime" -> when(fixture.rows().next()).thenThrow(new IllegalStateException("Secret driver state."));
                default -> throw new AssertionError(fault);
            }
            assertThat(fault, probe(fixture).getAsBoolean(), is(false));
            noTransactionControl(fixture.connection());
            if (List.of("mode", "rowClose", "statementClose", "connectionClose").contains(fault)) {
                verify(fixture.connection()).abort(any());
            }
            if (List.of("timeout", "execute", "rows", "next", "rowClose", "more", "count", "statementClose", "connectionClose")
                    .contains(fault)) {
                verify(fixture.statement()).close();
            }
        }
    }

    /**
     * Rejects unsafe connection modes and drains unexpected channels without changing transaction mode.
     */
    @Test
    void rejectsUnsafeModesAndWrongChannels() throws Exception {
        for (String fault : List.of("initialMode", "finalMode", "unreadableFinalMode", "noRows",
                                    "missingRows", "extraRows", "extraCount")) {
            Fixture fixture = fixture();
            switch (fault) {
                case "initialMode" -> when(fixture.connection().getAutoCommit()).thenReturn(false);
                case "finalMode" -> when(fixture.connection().getAutoCommit()).thenReturn(true, false);
                case "unreadableFinalMode" -> when(fixture.connection().getAutoCommit()).thenReturn(true)
                        .thenThrow(new SQLException("Secret mode diagnostic."));
                case "noRows" -> when(fixture.statement().execute()).thenReturn(false);
                case "missingRows" -> when(fixture.statement().getResultSet()).thenReturn(null);
                case "extraRows" -> when(fixture.statement().getMoreResults(anyInt())).thenReturn(true, false);
                case "extraCount" -> when(fixture.statement().getUpdateCount()).thenReturn(0, -1);
                default -> throw new AssertionError(fault);
            }
            assertThat(fault, probe(fixture).getAsBoolean(), is(false));
            if (fault.equals("initialMode") || fault.equals("finalMode") || fault.equals("unreadableFinalMode")) {
                verify(fixture.connection()).abort(any());
            }
            noTransactionControl(fixture.connection());
        }
    }

    /**
     * Preserves a fatal cleanup error over an earlier SQL failure and still releases the connection.
     */
    @Test
    void cleanupFatalErrorTakesPrecedenceOverSqlFailure() throws Exception {
        Fixture fixture = fixture();
        when(fixture.rows().next()).thenThrow(new SQLException("Secret"));
        Error fatal = new OutOfMemoryError("fatal");
        doThrow(fatal).when(fixture.statement()).close();
        assertThat(assertThrows(OutOfMemoryError.class, () -> probe(fixture).getAsBoolean()), sameInstance(fatal));
        verify(fixture.connection()).close();
        noTransactionControl(fixture.connection());
    }

    /**
     * Preserves a fatal abort error while attempting the close fallback without transaction recovery.
     */
    @Test
    void invalidationPreservesFatalErrorsAndStillAttemptsClose() throws Exception {
        Fixture fixture = fixture();
        when(fixture.connection().getAutoCommit()).thenReturn(false);
        Error fatal = new OutOfMemoryError("fatal");
        doThrow(fatal).when(fixture.connection()).abort(any());
        doThrow(new SQLException("Secret close diagnostic.")).when(fixture.connection()).close();
        assertThat(assertThrows(OutOfMemoryError.class, () -> probe(fixture).getAsBoolean()), sameInstance(fatal));
        verify(fixture.connection()).close();
        noTransactionControl(fixture.connection());
    }

    /**
     * Rejects active and failed foreign contexts and failed local contexts before acquisition.
     */
    @Test
    void rejectsForeignAndFailedContextsWithoutAcquisition() throws Exception {
        Fixture fixture = fixture();
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        JdbcClientConfig config = JdbcClientConfig.builder().dataSource(fixture.source()).buildPrototype();
        JdbcClient client = new JdbcClientImpl(config, fixture.source(), manager, JdbcClientConfigSupport.cachePolicy(config));
        BooleanSupplier probe = JdbcHealthSupport.createProbe(client, "SELECT 1", Duration.ofSeconds(2));
        manager.start("foreign");
        manager.begin("foreign-id");
        try {
            assertThat(probe.getAsBoolean(), is(false));
            verifyZeroInteractions(fixture.source());
            assertThrows(IllegalStateException.class, () -> manager.suspend("wrong-id"));
            assertThat(probe.getAsBoolean(), is(false));
            verifyZeroInteractions(fixture.source());
        } finally {
            manager.rollback("foreign-id");
            manager.end();
        }
        manager.start(Jdbc.PROVIDER);
        manager.begin("local-id");
        try {
            assertThrows(IllegalStateException.class, () -> manager.suspend("wrong-id"));
            assertThat(probe.getAsBoolean(), is(false));
            verifyZeroInteractions(fixture.source());
        } finally {
            manager.rollback("local-id");
            manager.end();
        }
    }

    /**
     * Holds both invocations open together to prove that their connections and statements remain independent.
     */
    @Test
    void concurrentInvocationsOwnDistinctResources() throws Exception {
        Fixture first = fixture();
        Fixture second = fixture();
        when(first.source().getConnection()).thenReturn(first.connection(), second.connection());
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        for (Fixture fixture : List.of(first, second)) {
            when(fixture.statement().execute()).thenAnswer(_ -> {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new SQLException("The coordination wait expired.");
                }
                return true;
            });
        }
        var executor = Executors.newFixedThreadPool(2);
        var probe = probe(first);
        List<Future<Boolean>> workers = new ArrayList<>();
        try {
            workers.add(executor.submit(probe::getAsBoolean));
            workers.add(executor.submit(probe::getAsBoolean));
            assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
            release.countDown();
            for (Future<Boolean> worker : workers) {
                assertThat(worker.get(5, TimeUnit.SECONDS), is(true));
            }
            for (Fixture fixture : List.of(first, second)) {
                verify(fixture.connection()).close();
                noTransactionControl(fixture.connection());
            }
        } finally {
            release.countDown();
            workers.forEach(worker -> worker.cancel(true));
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
        }
    }

    /**
     * Observes an acquisition failure before peer coordination and releases the successful invocation independently.
     */
    @Test
    void failedConcurrentProbeDoesNotStrandItsPeer() throws Exception {
        Fixture fixture = fixture();
        when(fixture.source().getConnection()).thenThrow(new SQLException("Secret acquisition failure."))
                .thenReturn(fixture.connection());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(fixture.statement().execute()).thenAnswer(_ -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new SQLException("The coordination wait expired.");
            }
            return true;
        });
        var executor = Executors.newFixedThreadPool(2);
        var completed = new ExecutorCompletionService<Boolean>(executor);
        BooleanSupplier probe = probe(fixture);
        List<Future<Boolean>> workers = new ArrayList<>();
        try {
            workers.add(completed.submit(probe::getAsBoolean));
            workers.add(completed.submit(probe::getAsBoolean));
            Future<Boolean> failure = completed.poll(5, TimeUnit.SECONDS);
            assertThat(failure, notNullValue());
            assertThat(failure.get(5, TimeUnit.SECONDS), is(false));
            assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
            release.countDown();
            Future<Boolean> success = completed.poll(5, TimeUnit.SECONDS);
            assertThat(success, notNullValue());
            assertThat(success.get(5, TimeUnit.SECONDS), is(true));
        } finally {
            release.countDown();
            workers.forEach(worker -> worker.cancel(true));
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
        }
        verify(fixture.rows()).close();
        verify(fixture.statement()).close();
        verify(fixture.connection()).close();
        noTransactionControl(fixture.connection());
        assertThat(probe.getAsBoolean(), is(true));
    }

    private static BooleanSupplier probe(Fixture fixture) {
        return JdbcHealthSupport.createProbe(JdbcTestClients.create(fixture.source()), "SELECT 1", Duration.ofSeconds(2));
    }

    private static void noTransactionControl(Connection connection) throws Exception {
        verify(connection, never()).setAutoCommit(anyBoolean());
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    private static Fixture fixture() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(rows);
        when(statement.getUpdateCount()).thenReturn(-1);
        return new Fixture(source, connection, statement, rows);
    }

    private record Fixture(DataSource source, Connection connection, PreparedStatement statement, ResultSet rows) {
    }
}
