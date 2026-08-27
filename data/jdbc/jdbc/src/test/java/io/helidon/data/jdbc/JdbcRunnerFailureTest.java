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
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.data.NonUniqueResultException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("helidon:api:internal")
class JdbcRunnerFailureTest {
    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement statement;
    private JdbcClient client;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement("UPDATE TEST_VALUE SET VALUE = 1")).thenReturn(statement);
        when(connection.prepareStatement("INSERT INTO TEST_VALUE DEFAULT VALUES",
                                         Statement.RETURN_GENERATED_KEYS)).thenReturn(statement);
        client = new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider());
    }

    @Test
    void rejectsMissingUpdateCountForOrdinaryAndGeneratedKeyUpdates() throws Exception {
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(-1L);

        DataException updateFailure = assertThrows(DataException.class,
                                                   () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());
        assertThat(updateFailure.getMessage(), containsString("no expected result"));
        verify(statement).close();
        verify(connection).close();

        setUp();
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(-1L);
        DataException keyFailure = assertThrows(DataException.class,
                                                () -> client.create("INSERT INTO TEST_VALUE DEFAULT VALUES")
                                                        .generatedKeys()
                                                        .map(row -> row.required(1, Long.class))
                                                        .one());
        assertThat(keyFailure.getMessage(), containsString("no expected result"));
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void closesTheConnectionWhenPreparationFailsAndRetainsSqlDiagnostics() throws Exception {
        SQLException prepareFailure = new SQLException("prepare failed", "42000", 91);
        when(connection.prepareStatement("UPDATE TEST_VALUE SET VALUE = 1")).thenThrow(prepareFailure);

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());

        assertSafeSqlCause(failure.getCause(), prepareFailure);
        assertThat(failure.getMessage(), containsString("SQLSTATE '42000'"));
        assertThat(failure.getMessage(), containsString("vendor code 91"));
        verify(connection).close();
    }

    @Test
    void translatesConnectionAcquisitionFailureWithoutTouchingJdbcResources() throws Exception {
        SQLException acquisitionFailure = new SQLException("acquire failed", "08001", 90);
        when(dataSource.getConnection()).thenThrow(acquisitionFailure);

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());

        assertSafeSqlCause(failure.getCause(), acquisitionFailure);
        verify(connection, never()).clearWarnings();
        verify(statement, never()).execute();
    }

    @Test
    void rejectsOwnedConnectionsWithoutAutoCommitBeforeEveryExecutionChannel() throws Exception {
        when(connection.getAutoCommit()).thenReturn(false);

        assertOwnedAutoCommitFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());
        verify(connection, never()).prepareStatement("UPDATE TEST_VALUE SET VALUE = 1");

        setUp();
        when(connection.getAutoCommit()).thenReturn(false);

        assertOwnedAutoCommitFailure(() -> client.create("SELECT VALUE FROM TEST_VALUE")
                .map(String.class)
                .list());
        verify(connection, never()).prepareStatement("SELECT VALUE FROM TEST_VALUE");

        setUp();
        when(connection.getAutoCommit()).thenReturn(false);

        assertOwnedAutoCommitFailure(() -> client.create("INSERT INTO TEST_VALUE DEFAULT VALUES")
                .generatedKeys()
                .map(row -> row.required(1, Long.class))
                .list());
        verify(connection, never()).prepareStatement("INSERT INTO TEST_VALUE DEFAULT VALUES",
                                                     Statement.RETURN_GENERATED_KEYS);
    }

    @Test
    void closesTheOwnedConnectionWhenAutoCommitInspectionFails() throws Exception {
        SQLException inspectionFailure = new SQLException("auto-commit inspection failed", "08000", 96);
        when(connection.getAutoCommit()).thenThrow(inspectionFailure);

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());

        assertSafeSqlCause(failure.getCause(), inspectionFailure);
        verify(connection).close();
        verify(connection, never()).prepareStatement("UPDATE TEST_VALUE SET VALUE = 1");
        verify(statement, never()).execute();
    }

    @Test
    void keepsTheAutoCommitInvariantFailurePrimaryWhenConnectionCloseFails() throws Exception {
        SQLException closeFailure = new SQLException("connection close failed");
        when(connection.getAutoCommit()).thenReturn(false);
        doThrow(closeFailure).doNothing().when(connection).close();

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());

        assertThat(failure.getCause(), instanceOf(SQLException.class));
        assertThat(failure.getCause().getMessage(),
                   is("Datasources used for JDBC operations must provide connections with auto-commit enabled."));
        assertThat(failure.getCause().getSuppressed().length, is(1));
        assertSafeSqlCause(failure.getCause().getSuppressed()[0], closeFailure);
        verify(connection).abort(any());
        verify(connection, times(2)).close();
        verify(connection, never()).prepareStatement("UPDATE TEST_VALUE SET VALUE = 1");
        verify(statement, never()).execute();
    }

    @Test
    void failedOwnedLeaseCloseInvalidatesOnlyOnceBeforeBecomingTerminal() throws Exception {
        SQLException closeFailure = new SQLException("connection close failed", "08006", 97);
        AtomicBoolean released = new AtomicBoolean();
        doAnswer(invocation -> {
            if (!released.get()) {
                // Model a pool/driver close that fails before releasing its physical resource.
                throw closeFailure;
            }
            return null;
        }).when(connection).close();
        doAnswer(invocation -> {
            released.set(true);
            return null;
        }).when(connection).abort(any());
        JdbcConnectionLease lease = JdbcConnectionLease.ownedProvider().acquire(dataSource);

        SQLException failure = assertThrows(SQLException.class, lease::close);

        assertThat(failure, sameInstance(closeFailure));
        InOrder cleanup = inOrder(connection);
        cleanup.verify(connection).close();
        cleanup.verify(connection).abort(any());
        cleanup.verify(connection).close();
        assertThat(released.get(), is(true));

        // Invalidation exhausts the cleanup path. A later close must not touch the unsafe connection again.
        lease.close();
        verify(connection, times(2)).close();
        verify(connection).abort(any());
        IllegalStateException closed = assertThrows(IllegalStateException.class, lease::connection);
        assertThat(closed.getMessage(), is("The connection lease is closed."));
    }

    @Test
    void sanitizesOwnedConnectionInvalidationFailuresWithoutReplacingTheFirstCloseFailure() throws Exception {
        SQLException closeFailure = new SQLException("private initial close failure", "08006", 97);
        IllegalStateException abortFailure = new IllegalStateException("private abort failure");
        SQLException fallbackFailure = new SQLException("private fallback close failure", "08007", 98);
        prepareSuccessfulUpdate();
        doThrow(closeFailure).doThrow(fallbackFailure).when(connection).close();
        doThrow(abortFailure).when(connection).abort(any());

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());

        assertThat(failure.getMessage(), containsString("The JDBC update failed."));
        assertThat(failure.getMessage(), not(containsString("private")));
        assertSafeSqlCause(failure.getCause(), closeFailure);
        assertThat(failure.getCause(), not(sameInstance(closeFailure)));
        assertThat(failure.getCause().getSuppressed().length, is(2));
        Throwable safeAbort = failure.getCause().getSuppressed()[0];
        assertThat(safeAbort.getMessage(),
                   is("The JDBC provider encountered an exception of type 'java.lang.IllegalStateException' "
                              + "while aborting a connection."));
        assertThat(safeAbort.getCause(), nullValue());
        assertSafeSqlCause(failure.getCause().getSuppressed()[1], fallbackFailure);
        InOrder cleanup = inOrder(statement, connection);
        cleanup.verify(statement).close();
        cleanup.verify(connection).close();
        cleanup.verify(connection).abort(any());
        cleanup.verify(connection).close();
    }

    @Test
    void closesStatementAndConnectionWhenBindingFails() throws Exception {
        SQLException bindFailure = new SQLException("bind failed", "22000", 92);
        when(connection.prepareStatement("UPDATE TEST_VALUE SET VALUE = ?")).thenReturn(statement);
        doThrow(bindFailure).when(statement).setObject(1, "value");

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("UPDATE TEST_VALUE SET VALUE = ?")
                                                     .bind(1, "value")
                                                     .execute());

        assertSafeSqlCause(failure.getCause(), bindFailure);
        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).close();
    }

    @Test
    @SuppressWarnings("helidon:api:internal")
    void closesStatementAndConnectionWhenTypedNullBindingFails() throws Exception {
        SQLException bindFailure = new SQLException("null bind failed", "22000", 93);
        when(connection.prepareStatement("UPDATE TEST_VALUE SET VALUE = ?")).thenReturn(statement);
        doThrow(bindFailure).when(statement).setNull(1, JDBCType.VARCHAR.getVendorTypeNumber());

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcClient.bindNull(
                                                             client.create("UPDATE TEST_VALUE SET VALUE = ?"),
                                                             1,
                                                             JDBCType.VARCHAR)
                                                     .execute());

        assertSafeSqlCause(failure.getCause(), bindFailure);
        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).close();
    }

    @Test
    void closesOwnedResourcesForEveryExecutionChannelFailure() throws Exception {
        SQLException executeFailure = new SQLException("execute failed", "42000", 94);
        when(statement.execute()).thenThrow(executeFailure);
        assertExecutionFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute(),
                               executeFailure);

        setUp();
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenThrow(executeFailure);
        assertExecutionFailure(() -> client.create("SELECT VALUE FROM TEST_VALUE")
                .map(String.class)
                .list(), executeFailure);

        setUp();
        when(statement.execute()).thenThrow(executeFailure);
        assertExecutionFailure(() -> client.create("INSERT INTO TEST_VALUE DEFAULT VALUES")
                .generatedKeys()
                .map(row -> row.required(1, Long.class))
                .list(), executeFailure);
    }

    @Test
    void rejectsUnexpectedUpdateResultSetAndClosesItBeforeOtherResources() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(statement.getLargeUpdateCount()).thenReturn(-1L);

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());

        assertThat(failure.getMessage(), containsString("incompatible result"));
        InOrder order = inOrder(resultSet, statement, connection);
        order.verify(resultSet).close();
        order.verify(statement).close();
        order.verify(connection).close();
    }

    @Test
    void closesResourcesAfterMetadataAndRowReadFailures() throws Exception {
        ResultSet metadataResultSet = mock(ResultSet.class);
        SQLException metadataFailure = new SQLException("metadata failed");
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(metadataResultSet);
        when(metadataResultSet.getMetaData()).thenThrow(metadataFailure);

        assertExecutionFailure(() -> client.create("SELECT VALUE FROM TEST_VALUE")
                                       .map(String.class)
                                       .list(),
                               metadataFailure,
                               metadataResultSet);

        setUp();
        ResultSet rowResultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        SQLException readFailure = new SQLException("read failed");
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(rowResultSet);
        when(rowResultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(rowResultSet.next()).thenThrow(readFailure);

        assertExecutionFailure(() -> client.create("SELECT VALUE FROM TEST_VALUE")
                                       .map(String.class)
                                       .list(),
                               readFailure,
                               rowResultSet);
    }

    @Test
    void closesResourcesWhenGeneratedKeyRetrievalFails() throws Exception {
        SQLException keyFailure = new SQLException("generated keys failed");
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(1L);
        when(statement.getGeneratedKeys()).thenThrow(keyFailure);

        assertExecutionFailure(() -> client.create("INSERT INTO TEST_VALUE DEFAULT VALUES")
                .generatedKeys()
                .map(row -> row.required(1, Long.class))
                .one(), keyFailure);
    }

    @Test
    void preservesMapperFailureAndSuppressedCleanupFailuresInCloseOrder() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("VALUE");
        when(resultSet.next()).thenReturn(true);
        SQLException resultClose = new SQLException("result close failed");
        SQLException statementClose = new SQLException("statement close failed");
        SQLException connectionClose = new SQLException("connection close failed");
        doThrow(resultClose).when(resultSet).close();
        doThrow(statementClose).when(statement).close();
        doThrow(connectionClose).doNothing().when(connection).close();
        IllegalStateException mapperFailure = new IllegalStateException("mapper failed");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> client.create("SELECT VALUE FROM TEST_VALUE")
                                                             .map(row -> {
                                                                 throw mapperFailure;
                                                             })
                                                             .one());

        assertThat(failure, sameInstance(mapperFailure));
        assertThat(failure.getSuppressed().length, is(1));
        assertThat(failure.getSuppressed()[0], instanceOf(DataException.class));
        Throwable cleanup = failure.getSuppressed()[0].getCause();
        assertSafeSqlCause(cleanup, resultClose);
        assertThat(cleanup.getSuppressed().length, is(2));
        assertSafeSqlCause(cleanup.getSuppressed()[0], statementClose);
        assertSafeSqlCause(cleanup.getSuppressed()[1], connectionClose);
        InOrder order = inOrder(resultSet, statement, connection);
        order.verify(resultSet).close();
        order.verify(statement).close();
        order.verify(connection).close();
    }

    /**
     * Verifies that unchecked statement and connection close failures are
     * rebuilt before they become application-visible, while a failed
     * connection close still triggers invalidation in ownership order.
     */
    @Test
    void sanitizesRuntimeFailuresFromStatementAndConnectionCleanup() throws Exception {
        prepareSuccessfulUpdate();
        IllegalStateException statementCloseFailure = driverRuntimeFailure("private statement close detail");
        doThrow(statementCloseFailure).when(statement).close();

        assertSanitizedRuntimeFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute(),
                                      statementCloseFailure,
                                      "closing a statement");
        InOrder statementCleanup = inOrder(statement, connection);
        statementCleanup.verify(statement).close();
        statementCleanup.verify(connection).close();

        setUp();
        prepareSuccessfulUpdate();
        IllegalStateException connectionCloseFailure = driverRuntimeFailure("private connection close detail");
        doThrow(connectionCloseFailure).doNothing().when(connection).close();

        assertSanitizedRuntimeFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute(),
                                      connectionCloseFailure,
                                      "closing a connection lease");
        InOrder connectionCleanup = inOrder(statement, connection);
        connectionCleanup.verify(statement).close();
        connectionCleanup.verify(connection).close();
        connectionCleanup.verify(connection).abort(any());
        connectionCleanup.verify(connection).close();
    }

    /**
     * Verifies that an application mapper failure retains its identity while
     * an unchecked result-set cleanup failure is attached only in sanitized
     * form.
     */
    @Test
    void preservesMapperFailureWhenRuntimeCleanupFails() throws Exception {
        ResultSet resultSet = prepareSuccessfulQuery();
        IllegalStateException closeFailure = driverRuntimeFailure("private mapper cleanup detail");
        doThrow(closeFailure).when(resultSet).close();
        IllegalStateException mapperFailure = new IllegalStateException("mapper failed");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> client.create("SELECT VALUE FROM TEST_VALUE")
                                                             .map(row -> {
                                                                 throw mapperFailure;
                                                             })
                                                             .one());

        assertThat(failure, sameInstance(mapperFailure));
        assertThat(failure.getSuppressed().length, is(1));
        Throwable cleanup = failure.getSuppressed()[0];
        assertThat(cleanup, not(sameInstance(closeFailure)));
        assertThat(cleanup.getMessage(),
                   is("The JDBC provider encountered an exception of type 'java.lang.IllegalStateException' while "
                              + "closing a result set."));
        assertThat(cleanup.getMessage(), not(containsString("private")));
        assertThat(cleanup.getCause(), nullValue());
        assertThat(cleanup.getSuppressed().length, is(0));
        InOrder order = inOrder(resultSet, statement, connection);
        order.verify(resultSet).close();
        order.verify(statement).close();
        order.verify(connection).close();
    }

    @Test
    void reportsEveryOrdinaryUpdateCleanupFailure() throws Exception {
        SQLException statementClose = new SQLException("update statement close failed");
        prepareSuccessfulUpdate();
        doThrow(statementClose).when(statement).close();

        assertCleanupFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute(), statementClose);
        verify(connection).close();

        setUp();
        SQLException connectionClose = new SQLException("update connection close failed");
        prepareSuccessfulUpdate();
        doThrow(connectionClose).doNothing().when(connection).close();

        assertCleanupFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute(), connectionClose);
        verify(statement).close();
    }

    @Test
    void reportsEveryQueryCleanupFailure() throws Exception {
        SQLException statementClose = new SQLException("query statement close failed");
        ResultSet resultSet = prepareSuccessfulQuery();
        doThrow(statementClose).when(statement).close();

        assertCleanupFailure(() -> client.create("SELECT VALUE FROM TEST_VALUE").map(String.class).one(),
                             statementClose);
        verify(connection).close();

        setUp();
        SQLException connectionClose = new SQLException("query connection close failed");
        resultSet = prepareSuccessfulQuery();
        doThrow(connectionClose).doNothing().when(connection).close();

        assertCleanupFailure(() -> client.create("SELECT VALUE FROM TEST_VALUE").map(String.class).one(),
                             connectionClose);
        verify(statement).close();
    }

    @Test
    void reportsEveryGeneratedKeyCleanupFailure() throws Exception {
        ResultSet resultSet = prepareSuccessfulGeneratedKeys();
        SQLException resultClose = new SQLException("key result close failed");
        doThrow(resultClose).when(resultSet).close();

        assertCleanupFailure(this::generatedKey, resultClose);
        verify(statement).close();
        verify(connection).close();

        setUp();
        resultSet = prepareSuccessfulGeneratedKeys();
        SQLException statementClose = new SQLException("key statement close failed");
        doThrow(statementClose).when(statement).close();

        assertCleanupFailure(this::generatedKey, statementClose);
        verify(resultSet).close();
        verify(connection).close();

        setUp();
        resultSet = prepareSuccessfulGeneratedKeys();
        SQLException connectionClose = new SQLException("key connection close failed");
        doThrow(connectionClose).doNothing().when(connection).close();

        assertCleanupFailure(this::generatedKey, connectionClose);
        verify(resultSet).close();
        verify(statement).close();
    }

    @Test
    void keepsCardinalityFailurePrimaryWhenEveryCleanupStepAlsoFails() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(resultSet.next()).thenReturn(true, true);
        when(resultSet.getObject(1, String.class)).thenReturn("first", "second");
        SQLException resultClose = new SQLException("result close failed");
        SQLException statementClose = new SQLException("statement close failed");
        SQLException connectionClose = new SQLException("connection close failed");
        doThrow(resultClose).when(resultSet).close();
        doThrow(statementClose).when(statement).close();
        doThrow(connectionClose).doNothing().when(connection).close();

        NonUniqueResultException failure =
                assertThrows(NonUniqueResultException.class,
                             () -> client.create("SELECT VALUE FROM TEST_VALUE").map(String.class).one());

        assertThat(failure.getSuppressed().length, is(1));
        Throwable cleanup = failure.getSuppressed()[0].getCause();
        assertSafeSqlCause(cleanup, resultClose);
        assertSafeSqlCause(cleanup.getSuppressed()[0], statementClose);
        assertSafeSqlCause(cleanup.getSuppressed()[1], connectionClose);
    }

    /**
     * Verifies that an application mapper failure remains unchanged without
     * accessing any JDBC warning channel.
     */
    @Test
    void doesNotInspectWarningsWhenMapperFails() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("VALUE");
        when(resultSet.next()).thenReturn(true);
        IllegalStateException mapperFailure = new IllegalStateException("mapper failed");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> client.create("SELECT VALUE FROM TEST_VALUE")
                                                             .map(row -> {
                                                                 throw mapperFailure;
                                                             })
                                                             .one());

        assertThat(failure, sameInstance(mapperFailure));
        assertThat(failure.getSuppressed().length, is(0));
        verify(resultSet, never()).getWarnings();
        verify(resultSet, never()).clearWarnings();
        verify(statement, never()).getWarnings();
        verify(statement, never()).clearWarnings();
        verify(connection, never()).getWarnings();
        verify(connection, never()).clearWarnings();
    }

    /**
     * Verifies successful query mapping advances rows in order without
     * accessing any JDBC warning channel.
     */
    @Test
    void doesNotAccessWarningsWhileMappingQueryRows() throws Exception {
        ResultSet resultSet = prepareSuccessfulQuery();

        String value = client.create("SELECT VALUE FROM TEST_VALUE").map(String.class).one();

        assertThat(value, is("value"));
        InOrder order = inOrder(resultSet);
        order.verify(resultSet).next();
        order.verify(resultSet).getObject(1, String.class);
        order.verify(resultSet).next();
        verify(resultSet, never()).getWarnings();
        verify(resultSet, never()).clearWarnings();
        verify(statement, never()).getWarnings();
        verify(statement, never()).clearWarnings();
        verify(connection, never()).getWarnings();
        verify(connection, never()).clearWarnings();
        verify(statement).close();
        verify(connection).close();
    }

    /**
     * Verifies successful generated-key mapping completes without accessing
     * any JDBC warning channel.
     */
    @Test
    void doesNotAccessWarningsWhileMappingGeneratedKeys() throws Exception {
        ResultSet resultSet = prepareSuccessfulGeneratedKeys();

        long key = generatedKey();

        assertThat(key, is(1L));
        verify(resultSet, never()).getWarnings();
        verify(resultSet, never()).clearWarnings();
        verify(statement, never()).getWarnings();
        verify(statement, never()).clearWarnings();
        verify(connection, never()).getWarnings();
        verify(connection, never()).clearWarnings();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void advancesAndDrainsResultsWithTheBaselineJdbcMethod() throws Exception {
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(1L, 2L, -1L);
        when(statement.getMoreResults()).thenReturn(false, false);

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());

        assertThat(failure.getMessage(), is("The JDBC update returned unexpected additional results."));
        verify(statement, times(2)).getMoreResults();
        verify(statement, never()).getMoreResults(anyInt());
    }

    @Test
    void sanitizesRuntimeFailureWhileAdvancingToTheNextResult() throws Exception {
        prepareSuccessfulUpdate();
        IllegalStateException advancementFailure = driverRuntimeFailure("private result advancement detail");
        when(statement.getMoreResults()).thenThrow(advancementFailure);

        assertSanitizedRuntimeFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute(),
                                      advancementFailure,
                                      "advancing to the next JDBC result");
        verify(statement, never()).getMoreResults(anyInt());
    }

    @Test
    void sanitizesSqlFailureWhileAdvancingToTheNextResult() throws Exception {
        prepareSuccessfulUpdate();
        SQLException advancementFailure = new SQLException("private result advancement detail", "HY000", 117);
        when(statement.getMoreResults()).thenThrow(advancementFailure);

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());

        assertThat(failure.getMessage(), containsString("The JDBC update failed."));
        assertThat(failure.getMessage(), containsString("outside the recognized portable SQLSTATE catalog"));
        assertThat(failure.getMessage(), containsString("SQLSTATE 'HY000'"));
        assertThat(failure.getMessage(), not(containsString("SQLSTATE class")));
        assertThat(failure.getMessage(), containsString("vendor code 117"));
        assertThat(failure.getMessage(), not(containsString("private")));
        assertSafeSqlCause(failure.getCause(), advancementFailure);
        verify(statement, never()).getMoreResults(anyInt());
    }

    /**
     * Verifies that a successful non-row operation does not access connection
     * or statement warnings.
     */
    @Test
    void doesNotAccessGeneralWarningsAfterSuccessfulUpdate() throws Exception {
        prepareSuccessfulUpdate();

        long count = client.create("UPDATE TEST_VALUE SET VALUE = 1").execute();

        assertThat(count, is(1L));
        verify(statement, never()).getWarnings();
        verify(statement, never()).clearWarnings();
        verify(connection, never()).getWarnings();
        verify(connection, never()).clearWarnings();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void sanitizesRuntimeFailuresFromConnectionAcquisitionAndInspection() throws Exception {
        IllegalStateException acquisitionFailure = driverRuntimeFailure("private acquisition URL");
        when(dataSource.getConnection()).thenThrow(acquisitionFailure);

        assertSanitizedRuntimeFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute(),
                                      acquisitionFailure,
                                      "acquiring a connection");

        setUp();
        IllegalStateException inspectionFailure = driverRuntimeFailure("private connection properties");
        when(connection.getAutoCommit()).thenThrow(inspectionFailure);

        assertSanitizedRuntimeFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute(),
                                      inspectionFailure,
                                      "inspecting automatic commit mode");
        verify(connection).close();
    }

    @Test
    void sanitizesRuntimeFailuresFromPreparationAndBinding() throws Exception {
        IllegalStateException preparationFailure = driverRuntimeFailure("private prepared SQL");
        when(connection.prepareStatement("UPDATE TEST_VALUE SET VALUE = 1")).thenThrow(preparationFailure);

        assertSanitizedRuntimeFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute(),
                                      preparationFailure,
                                      "preparing a JDBC statement");
        verify(connection).close();

        setUp();
        IllegalStateException bindFailure = driverRuntimeFailure("private bound value");
        when(connection.prepareStatement("UPDATE TEST_VALUE SET VALUE = ?")).thenReturn(statement);
        doThrow(bindFailure).when(statement).setObject(1, "private-value");

        assertSanitizedRuntimeFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = ?")
                                              .bind(1, "private-value")
                                              .execute(),
                                      bindFailure,
                                      "binding a JDBC parameter");
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void sanitizesRuntimeFailuresFromExecutionAndResultTraversal() throws Exception {
        IllegalStateException executionFailure = driverRuntimeFailure("private execution detail");
        when(statement.execute()).thenThrow(executionFailure);

        assertSanitizedRuntimeFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute(),
                                      executionFailure,
                                      "executing a JDBC update");

        setUp();
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        IllegalStateException traversalFailure = driverRuntimeFailure("private row detail");
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(resultSet.next()).thenThrow(traversalFailure);

        assertSanitizedRuntimeFailure(() -> client.create("SELECT VALUE FROM TEST_VALUE")
                                              .map(String.class)
                                              .list(),
                                      traversalFailure,
                                      "advancing a JDBC result set");
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void sanitizesRuntimeFailuresFromResultMetadata() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        IllegalStateException metadataFailure = driverRuntimeFailure("private metadata detail");
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenThrow(metadataFailure);

        assertSanitizedRuntimeFailure(() -> client.create("SELECT VALUE FROM TEST_VALUE")
                                              .map(String.class)
                                              .list(),
                                      metadataFailure,
                                      "reading JDBC result metadata");

        setUp();
        resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        IllegalStateException labelFailure = driverRuntimeFailure("private column label");
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(resultSet.next()).thenReturn(true);
        when(metadata.getColumnLabel(1)).thenThrow(labelFailure);

        assertSanitizedRuntimeFailure(() -> client.create("SELECT VALUE FROM TEST_VALUE")
                                              .map(row -> row.required("VALUE", String.class))
                                              .one(),
                                      labelFailure,
                                      "reading a JDBC result column label");
    }

    @Test
    void sanitizesRuntimeFailuresFromUpdateCountsAndGeneratedKeys() throws Exception {
        IllegalStateException countFailure = driverRuntimeFailure("private update count");
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenThrow(countFailure);

        assertSanitizedRuntimeFailure(() -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute(),
                                      countFailure,
                                      "reading a JDBC large update count");

        setUp();
        IllegalStateException keysFailure = driverRuntimeFailure("private generated key detail");
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(1L);
        when(statement.getGeneratedKeys()).thenThrow(keysFailure);

        assertSanitizedRuntimeFailure(this::generatedKey,
                                      keysFailure,
                                      "reading JDBC generated keys");
    }

    @Test
    void limitsEverySingularQueryTerminalToTwoRows() throws Exception {
        prepareSuccessfulQuery();

        assertThat(client.create("SELECT VALUE FROM TEST_VALUE").map(String.class).one(), is("value"));
        verify(statement).setMaxRows(2);

        setUp();
        prepareSuccessfulQuery();

        assertThat(client.create("SELECT VALUE FROM TEST_VALUE").map(row -> "value").optional(),
                   is(Optional.of("value")));
        verify(statement).setMaxRows(2);

        setUp();
        prepareSuccessfulQuery();

        assertThat(client.create("SELECT VALUE FROM TEST_VALUE").map(String.class).optional(),
                   is(Optional.of("value")));
        verify(statement).setMaxRows(2);
    }

    @Test
    void leavesListAndGeneratedKeyTerminalsUnbounded() throws Exception {
        prepareSuccessfulQuery();

        assertThat(client.create("SELECT VALUE FROM TEST_VALUE").map(String.class).list(),
                   is(List.of("value")));
        verify(statement, never()).setMaxRows(2);

        setUp();
        prepareSuccessfulGeneratedKeys();

        assertThat(generatedKey(), is(1L));
        verify(statement, never()).setMaxRows(2);
    }

    @Test
    void retainsCursorCardinalityCheckWhenMaximumRowsAreUnsupported() throws Exception {
        prepareSuccessfulQuery();
        doThrow(new SQLFeatureNotSupportedException("unsupported")).when(statement).setMaxRows(2);

        assertThat(client.create("SELECT VALUE FROM TEST_VALUE").map(String.class).one(), is("value"));

        verify(statement).setMaxRows(2);
        verify(statement).execute();
    }

    @Test
    void sanitizesUnexpectedMaximumRowsFailure() throws Exception {
        prepareSuccessfulQuery();
        doThrow(new IllegalStateException("secret maximum rows failure")).when(statement).setMaxRows(2);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> client.create("SELECT VALUE FROM TEST_VALUE")
                                                             .map(String.class)
                                                             .one());

        assertThat(failure.getMessage(),
                   is("The JDBC provider encountered an exception of type 'java.lang.IllegalStateException' while "
                              + "setting the maximum row count for a query."));
        assertThat(failure.getCause(), nullValue());
        verify(statement, never()).execute();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void sanitizesRuntimeFailureWhileReadingAnObjectResultValue() throws Exception {
        ResultSet resultSet = prepareSuccessfulQuery();
        IllegalStateException driverFailure = new IllegalStateException("secret result value",
                                                                         new IllegalArgumentException("secret cause"));
        driverFailure.addSuppressed(new IllegalArgumentException("secret suppressed"));
        when(resultSet.getObject(1, String.class)).thenThrow(driverFailure);

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("SELECT VALUE FROM TEST_VALUE")
                                                     .map(String.class)
                                                     .one());

        assertSanitizedResultValueFailure(failure);
        assertThat(failure, not(sameInstance(driverFailure)));
        InOrder order = inOrder(resultSet, statement, connection);
        order.verify(resultSet).close();
        order.verify(statement).close();
        order.verify(connection).close();
    }

    @Test
    void sanitizesRuntimeFailureWhileReadingABinaryResultValue() throws Exception {
        ResultSet resultSet = prepareSuccessfulQuery();
        UnsupportedOperationException driverFailure =
                new UnsupportedOperationException("secret binary result value");
        when(resultSet.getBytes(1)).thenThrow(driverFailure);

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("SELECT VALUE FROM TEST_VALUE")
                                                     .map(byte[].class)
                                                     .one());

        assertSanitizedResultValueFailure(failure);
        assertThat(failure, not(sameInstance(driverFailure)));
        InOrder order = inOrder(resultSet, statement, connection);
        order.verify(resultSet).close();
        order.verify(statement).close();
        order.verify(connection).close();
    }

    private void prepareSuccessfulUpdate() throws Exception {
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(1L, -1L);
        when(statement.getMoreResults()).thenReturn(false);
    }

    private ResultSet prepareSuccessfulQuery() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1, String.class)).thenReturn("value");
        when(statement.getLargeUpdateCount()).thenReturn(-1L);
        when(statement.getMoreResults()).thenReturn(false);
        return resultSet;
    }

    private ResultSet prepareSuccessfulGeneratedKeys() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(1L, -1L);
        when(statement.getGeneratedKeys()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1, Long.class)).thenReturn(1L);
        when(statement.getMoreResults()).thenReturn(false);
        return resultSet;
    }

    private long generatedKey() {
        return client.create("INSERT INTO TEST_VALUE DEFAULT VALUES")
                .generatedKeys()
                .map(row -> row.required(1, Long.class))
                .one();
    }

    private static void assertCleanupFailure(ThrowingInvocation invocation, SQLException expected) {
        DataException failure = assertThrows(DataException.class, invocation::run);

        assertSafeSqlCause(failure.getCause(), expected);
    }

    private void assertOwnedAutoCommitFailure(ThrowingInvocation invocation) throws SQLException {
        DataException failure = assertThrows(DataException.class, invocation::run);

        assertThat(failure.getCause(), instanceOf(SQLException.class));
        assertThat(failure.getCause().getMessage(),
                   is("Datasources used for JDBC operations must provide connections with auto-commit enabled."));
        verify(connection).getAutoCommit();
        verify(connection).close();
        verify(connection, never()).clearWarnings();
        verify(statement, never()).execute();
    }

    private void assertExecutionFailure(ThrowingInvocation invocation,
                                        SQLException expected) throws SQLException {
        DataException failure = assertThrows(DataException.class, invocation::run);

        assertSafeSqlCause(failure.getCause(), expected);
        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).close();
    }

    private void assertExecutionFailure(ThrowingInvocation invocation,
                                        SQLException expected,
                                        ResultSet resultSet) throws SQLException {
        DataException failure = assertThrows(DataException.class, invocation::run);

        assertSafeSqlCause(failure.getCause(), expected);
        InOrder order = inOrder(resultSet, statement, connection);
        order.verify(resultSet).close();
        order.verify(statement).close();
        order.verify(connection).close();
    }

    private static void assertSafeSqlCause(Throwable actual, SQLException expected) {
        assertThat(actual, instanceOf(SQLException.class));
        SQLException safe = (SQLException) actual;
        assertThat(safe.getMessage(), is("The JDBC driver reported a failure."));
        assertThat(safe.getSQLState(), is(expected.getSQLState()));
        assertThat(safe.getErrorCode(), is(expected.getErrorCode()));
    }

    private static void assertSanitizedResultValueFailure(DataException failure) {
        assertThat(failure.getMessage(), is("The JDBC provider could not read a result value."));
        assertThat(failure.getCause(), nullValue());
        assertThat(failure.getSuppressed().length, is(0));
    }

    private static IllegalStateException driverRuntimeFailure(String secret) {
        IllegalStateException failure = new IllegalStateException(secret, new IllegalArgumentException("private cause"));
        failure.addSuppressed(new IllegalArgumentException("private suppressed"));
        return failure;
    }

    private static void assertSanitizedRuntimeFailure(ThrowingInvocation invocation,
                                                      RuntimeException original,
                                                      String operation) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, invocation::run);

        assertThat(failure, not(sameInstance(original)));
        assertThat(failure.getMessage(),
                   is("The JDBC provider encountered an exception of type '" + original.getClass().getName()
                              + "' while " + operation + "."));
        assertThat(failure.getMessage(), not(containsString("private")));
        assertThat(failure.getCause(), nullValue());
        assertThat(failure.getSuppressed().length, is(0));
    }

    @FunctionalInterface
    private interface ThrowingInvocation {
        void run();
    }
}
