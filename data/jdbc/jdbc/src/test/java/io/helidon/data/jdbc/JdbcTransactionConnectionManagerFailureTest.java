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
import java.sql.SQLException;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.data.jdbc.JdbcTransactionConnectionManager.CompletionOutcome;
import io.helidon.data.jdbc.JdbcTransactionConnectionManager.CompletionReceipt;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcTransactionConnectionManagerFailureTest {

    /**
     * Verifies that invalid lifecycle arguments fail before accessing or
     * installing thread-local state.
     */
    @Test
    void validatesLifecycleArgumentsBeforeAccessingThreadState() {
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();

        assertNullArgument(() -> manager.start(null), "The transaction type must not be null.");
        IllegalStateException failure = assertThrows(IllegalStateException.class, manager::end);
        assertThat(failure.getMessage(),
                   is("The provider cannot end a transaction lifecycle without a matching transaction lifecycle start."));

        assertNullArgument(() -> manager.begin(null), "The transaction identity must not be null.");
        assertNullArgument(() -> manager.commit(null), "The transaction identity must not be null.");
        assertNullArgument(() -> manager.rollback(null), "The transaction identity must not be null.");
        assertNullArgument(() -> manager.suspend(null), "The transaction identity must not be null.");
        assertNullArgument(() -> manager.resume(null), "The transaction identity must not be null.");
    }

    /**
     * Verifies that completing a transaction which acquired no connection
     * still returns a confirmed logical commit.
     */
    @Test
    void returnsConfirmedCommitWithoutAConnection() {
        JdbcTransactionConnectionManager manager = activeManager("empty-commit");

        CompletionReceipt receipt = manager.commitLocal("empty-commit");

        assertThat(receipt.outcome(), is(CompletionOutcome.COMMITTED));
        assertThat(receipt.failure(), nullValue());
        manager.end();
    }

    /**
     * Verifies that the local completion receipt distinguishes a confirmed
     * rollback from connection cleanup.
     */
    @Test
    void returnsConfirmedRollbackAfterConnectionCleanup() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true, false, false);
        JdbcTransactionConnectionManager manager = activeManager("confirmed-rollback");
        manager.acquire(dataSource).close();

        CompletionReceipt receipt = manager.rollbackLocal("confirmed-rollback");

        assertThat(receipt.outcome(), is(CompletionOutcome.ROLLED_BACK));
        assertThat(receipt.failure(), nullValue());
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
        verify(connection).close();
        manager.end();
    }

    /**
     * Verifies that cleanup failure cannot erase a confirmed commit outcome.
     */
    @Test
    void retainsConfirmedCommitWhenAutoCommitRestorationFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SQLException restoreFailure = new SQLException("restore failed", "08006", 93);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true, false, false);
        doThrow(restoreFailure).when(connection).setAutoCommit(true);
        JdbcTransactionConnectionManager manager = activeManager("commit-cleanup");
        manager.acquire(dataSource).close();

        CompletionReceipt receipt = manager.commitLocal("commit-cleanup");

        assertThat(receipt.outcome(), is(CompletionOutcome.COMMITTED));
        assertThat(receipt.failure(), instanceOf(TxException.class));
        assertThat(receipt.failure().getMessage(),
                   is("The local JDBC transaction was committed, but the provider failed to restore automatic "
                              + "commit mode."));
        verify(connection).commit();
        verify(connection).abort(any());
        verify(connection).close();
        manager.end();
    }

    /**
     * Verifies that a failed commit returns an unknown outcome and invalidates
     * the connection before returning the receipt.
     */
    @Test
    void returnsUnknownOutcomeAfterCommitFailure() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SQLException commitFailure = new SQLException("commit failed", "08006", 94);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true, false, false);
        doThrow(commitFailure).when(connection).commit();
        JdbcTransactionConnectionManager manager = activeManager("unknown-commit");
        manager.acquire(dataSource).close();

        CompletionReceipt receipt = manager.commitLocal("unknown-commit");

        assertThat(receipt.outcome(), is(CompletionOutcome.UNKNOWN));
        assertThat(receipt.failure(), instanceOf(TxException.class));
        assertThat(receipt.failure().getMessage(),
                   is("The local JDBC transaction commit failed, and the outcome is unknown."));
        verify(connection).rollback();
        verify(connection).abort(any());
        verify(connection).close();
        manager.end();
    }

    /**
     * Verifies that transaction connection acquisition translates checked SQL
     * failures without retaining the driver exception or its message.
     */
    @Test
    void sanitizesSqlConnectionAcquisitionFailure() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        SQLException driverFailure = new SQLException("private transaction URL", "08001", 91);
        when(dataSource.getConnection()).thenThrow(driverFailure);
        JdbcTransactionConnectionManager manager = activeManager("sql-acquisition");

        DataException failure = assertThrows(DataException.class,
                                             () -> manager.acquire(dataSource));

        assertThat(failure.getMessage(), containsString("The JDBC transaction connection acquisition failed."));
        assertThat(failure.getMessage(), not(containsString("private transaction URL")));
        assertThat(failure.getCause(), instanceOf(SQLException.class));
        SQLException safeCause = (SQLException) failure.getCause();
        assertThat(safeCause, not(sameInstance(driverFailure)));
        assertThat(safeCause.getMessage(), is("The JDBC driver reported a failure."));
        assertThat(safeCause.getSQLState(), is("08001"));
        assertThat(safeCause.getErrorCode(), is(91));
        manager.rollback("sql-acquisition");
        manager.end();
    }

    @Test
    void sanitizesRuntimeConnectionAcquisitionFailure() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        IllegalStateException driverFailure = driverRuntimeFailure("private transaction URL");
        when(dataSource.getConnection()).thenThrow(driverFailure);
        JdbcTransactionConnectionManager manager = activeManager("acquisition");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> manager.acquire(dataSource));

        assertSanitized(failure, driverFailure, "acquiring a transaction connection");
        assertThrows(DataException.class, () -> manager.acquire(dataSource));
        manager.rollback("acquisition");
        manager.end();
    }

    @Test
    void sanitizesRuntimeConnectionInspectionFailureBeforeInvalidation() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        IllegalStateException driverFailure = driverRuntimeFailure("private transaction connection properties");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenThrow(driverFailure);
        JdbcTransactionConnectionManager manager = activeManager("inspection");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> manager.acquire(dataSource));

        assertSanitized(failure, driverFailure, "inspecting transaction automatic commit mode");
        verify(connection).abort(any());
        verify(connection).close();
        manager.rollback("inspection");
        manager.end();
    }

    @Test
    void sanitizesRuntimeConnectionSetupFailureBeforeInvalidation() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        IllegalStateException driverFailure = driverRuntimeFailure("private transaction setup detail");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(driverFailure).when(connection).setAutoCommit(false);
        JdbcTransactionConnectionManager manager = activeManager("setup");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> manager.acquire(dataSource));

        assertSanitized(failure, driverFailure, "disabling automatic commit mode");
        verify(connection).abort(any());
        verify(connection).close();
        manager.rollback("setup");
        manager.end();
    }

    @Test
    void reportsMultipleTransactionDataSourcesUsingStandardTerminology() throws Exception {
        DataSource firstDataSource = mock(DataSource.class);
        DataSource secondDataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(firstDataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true, false);
        JdbcTransactionConnectionManager manager = activeManager("multiple-data-sources");
        manager.acquire(firstDataSource).close();

        DataException failure = assertThrows(DataException.class,
                                             () -> manager.acquire(secondDataSource));

        assertThat(failure.getMessage(),
                   is("A local JDBC transaction cannot use more than one data source."));
        manager.rollback("multiple-data-sources");
        manager.end();
    }

    /**
     * Verifies that enabling auto-commit makes the transaction outcome unknown,
     * invalidates the connection, and prevents any later rollback attempt.
     */
    @Test
    void invalidatesTransactionConnectionAfterAutoCommitIsEnabled() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true, true);
        JdbcTransactionConnectionManager manager = activeManager("enabled-auto-commit");

        manager.acquire(dataSource).close();

        verify(connection).abort(any());
        verify(connection).close();
        assertThrows(DataException.class, () -> manager.acquire(dataSource));
        TxException failure = assertThrows(TxException.class, () -> manager.commit("enabled-auto-commit"));
        assertThat(failure.getMessage(), containsString("outcome is unknown"));
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        manager.end();
    }

    /**
     * Verifies that a failure to inspect auto-commit is reported by the
     * operation and still leaves the transaction with an unknown outcome.
     */
    @Test
    void invalidatesTransactionConnectionWhenAutoCommitCannotBeInspected() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SQLException driverFailure = new SQLException("private connection state", "08006", 92);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true).thenThrow(driverFailure);
        JdbcTransactionConnectionManager manager = activeManager("unreadable-auto-commit");

        DataException operationFailure = assertThrows(DataException.class, () -> manager.acquire(dataSource).close());

        assertThat(operationFailure.getMessage(), containsString("transaction connection validation failed"));
        assertThat(operationFailure.getMessage(), not(containsString("private connection state")));
        verify(connection).abort(any());
        verify(connection).close();
        TxException completionFailure = assertThrows(
                TxException.class,
                () -> manager.rollback("unreadable-auto-commit"));
        assertThat(completionFailure.getMessage(), containsString("outcome is unknown"));
        verify(connection, never()).rollback();
        manager.end();
    }

    /**
     * Verifies that transaction completion performs its own auto-commit check
     * even when the preceding operation observed the expected connection mode.
     */
    @Test
    void validatesAutoCommitAgainBeforeTransactionCompletion() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true, false, true);
        JdbcTransactionConnectionManager manager = activeManager("completion-auto-commit");
        manager.acquire(dataSource).close();

        TxException failure = assertThrows(TxException.class, () -> manager.rollback("completion-auto-commit"));

        assertThat(failure.getMessage(), containsString("outcome is unknown"));
        verify(connection).abort(any());
        verify(connection).close();
        verify(connection, never()).rollback();
        manager.end();
    }

    @Test
    void reportsTransactionAutoCommitFailuresUsingStandardTerminology() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(false);
        JdbcTransactionConnectionManager manager = activeManager("auto-commit");

        DataException failure = assertThrows(DataException.class,
                                             () -> manager.acquire(dataSource));

        assertThat(failure.getCause().getMessage(),
                   is("Data sources used for local JDBC transactions must provide connections "
                              + "with auto-commit enabled."));
        manager.rollback("auto-commit");
        manager.end();
    }

    private static JdbcTransactionConnectionManager activeManager(String identity) {
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start(Jdbc.PROVIDER);
        manager.begin(identity);
        return manager;
    }

    private static void assertNullArgument(Executable invocation, String message) {
        NullPointerException failure = assertThrows(NullPointerException.class, invocation);
        assertThat(failure.getMessage(), is(message));
    }

    private static IllegalStateException driverRuntimeFailure(String secret) {
        IllegalStateException failure = new IllegalStateException(secret, new IllegalArgumentException("private cause"));
        failure.addSuppressed(new IllegalArgumentException("private suppressed"));
        return failure;
    }

    private static void assertSanitized(IllegalStateException actual,
                                        RuntimeException original,
                                        String operation) {
        assertThat(actual, not(sameInstance(original)));
        assertThat(actual.getMessage(),
                   is("The JDBC provider encountered an exception of type '" + original.getClass().getName()
                              + "' while " + operation + "."));
        assertThat(actual.getMessage(), not(containsString("private")));
        assertThat(actual.getCause(), nullValue());
        assertThat(actual.getSuppressed().length, is(0));
    }
}
