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
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import io.helidon.data.DataException;

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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        client = new JdbcClientImpl(dataSource);
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
        assertThat(failure.getMessage(), containsString("SQL state is '42000'"));
        assertThat(failure.getMessage(), containsString("vendor code is 91"));
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
        doThrow(closeFailure).when(connection).close();

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());

        assertThat(failure.getCause(), instanceOf(SQLException.class));
        assertThat(failure.getCause().getMessage(),
                   is("Datasources used for JDBC operations must provide connections with auto-commit enabled."));
        assertThat(failure.getCause().getSuppressed().length, is(1));
        assertSafeSqlCause(failure.getCause().getSuppressed()[0], closeFailure);
        verify(connection, never()).prepareStatement("UPDATE TEST_VALUE SET VALUE = 1");
        verify(statement, never()).execute();
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
    void closesStatementAndConnectionWhenTypedNullBindingFails() throws Exception {
        SQLException bindFailure = new SQLException("null bind failed", "22000", 93);
        when(connection.prepareStatement("UPDATE TEST_VALUE SET VALUE = ?")).thenReturn(statement);
        doThrow(bindFailure).when(statement).setNull(1, JDBCType.VARCHAR.getVendorTypeNumber());

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("UPDATE TEST_VALUE SET VALUE = ?")
                                                     .bindNull(1, JDBCType.VARCHAR)
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
        doThrow(connectionClose).when(connection).close();
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
        doThrow(connectionClose).when(connection).close();

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
        doThrow(connectionClose).when(connection).close();

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
        doThrow(connectionClose).when(connection).close();

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
        doThrow(connectionClose).when(connection).close();

        io.helidon.data.NonUniqueResultException failure =
                assertThrows(io.helidon.data.NonUniqueResultException.class,
                             () -> client.create("SELECT VALUE FROM TEST_VALUE").map(String.class).one());

        assertThat(failure.getSuppressed().length, is(1));
        Throwable cleanup = failure.getSuppressed()[0].getCause();
        assertSafeSqlCause(cleanup, resultClose);
        assertSafeSqlCause(cleanup.getSuppressed()[0], statementClose);
        assertSafeSqlCause(cleanup.getSuppressed()[1], connectionClose);
    }

    @Test
    void sanitizesWarningsBeforeAttachingThemToMapperFailures() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("VALUE");
        when(resultSet.next()).thenReturn(true);
        SQLWarning first = new SQLWarning("secret result-set warning", "01001", 11);
        first.setNextWarning(new SQLWarning("secret chained warning", "01002", 12));
        when(resultSet.getWarnings()).thenReturn(first);
        when(statement.getWarnings()).thenReturn(new SQLWarning("secret statement warning", "01003", 13));
        when(connection.getWarnings()).thenReturn(new SQLWarning("secret connection warning", "01004", 14));
        IllegalStateException mapperFailure = new IllegalStateException("mapper failed");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> client.create("SELECT VALUE FROM TEST_VALUE")
                                                             .map(row -> {
                                                                 throw mapperFailure;
                                                             })
                                                             .one());

        assertThat(failure, sameInstance(mapperFailure));
        assertThat(failure.getSuppressed().length, is(4));
        assertSafeWarning(failure.getSuppressed()[0], "01001", 11);
        assertSafeWarning(failure.getSuppressed()[1], "01002", 12);
        assertSafeWarning(failure.getSuppressed()[2], "01003", 13);
        assertSafeWarning(failure.getSuppressed()[3], "01004", 14);
        for (Throwable warning : failure.getSuppressed()) {
            assertThat(warning.getMessage(), not(containsString("secret")));
        }
    }

    @Test
    void storesOnlySanitizedWarningsBeforeResultSetAdvancement() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1, String.class)).thenReturn("value");
        when(resultSet.getWarnings()).thenReturn(new SQLWarning("secret captured warning", "01005", 15));
        when(statement.getLargeUpdateCount()).thenReturn(1L, -1L);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(false);

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create("SELECT VALUE FROM TEST_VALUE")
                                                     .map(String.class)
                                                     .list());

        assertThat(failure.getMessage(), containsString("unexpected additional results"));
        assertThat(failure.getSuppressed().length, is(1));
        assertSafeWarning(failure.getSuppressed()[0], "01005", 15);
        assertThat(failure.getSuppressed()[0].getMessage(), not(containsString("secret")));
    }

    @Test
    void sanitizesWarningAccessFailuresWithoutRetainingTheirTrees() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement("SELECT VALUE FROM TEST_VALUE")).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("VALUE");
        when(resultSet.next()).thenReturn(true);
        IllegalStateException warningFailure = new IllegalStateException("secret warning access",
                                                                          new RuntimeException("secret cause"));
        when(resultSet.getWarnings()).thenThrow(warningFailure);
        UnsupportedOperationException clearFailure = new UnsupportedOperationException("secret warning clear");
        doThrow(clearFailure).when(resultSet).clearWarnings();
        IllegalArgumentException mapperFailure = new IllegalArgumentException("mapper failed");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                        () -> client.create("SELECT VALUE FROM TEST_VALUE")
                                                                .map(row -> {
                                                                    throw mapperFailure;
                                                                })
                                                                .one());

        assertThat(failure, sameInstance(mapperFailure));
        assertThat(failure.getSuppressed().length, is(2));
        Throwable diagnostic = failure.getSuppressed()[0];
        assertThat(diagnostic.getMessage(),
                   is("The JDBC provider could not process result set warnings."));
        assertThat(diagnostic.getMessage(), not(containsString("secret")));
        assertThat(diagnostic.getCause(), nullValue());
        assertThat(diagnostic.getSuppressed().length, is(0));
        Throwable clearDiagnostic = failure.getSuppressed()[1];
        assertThat(clearDiagnostic.getMessage(),
                   is("The JDBC provider could not process result set warnings."));
        assertThat(clearDiagnostic.getMessage(), not(containsString("secret")));
        assertThat(clearDiagnostic.getCause(), nullValue());
    }

    @Test
    void keepsWarningsNonFatalAfterSuccessfulWork() throws Exception {
        prepareSuccessfulUpdate();
        when(statement.getWarnings()).thenReturn(new SQLWarning("secret successful warning", "01006", 16));

        long count = client.create("UPDATE TEST_VALUE SET VALUE = 1").execute();

        assertThat(count, is(1L));
        verify(statement).getWarnings();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void keepsWarningProcessingFailuresNonFatalAfterSuccessfulWork() throws Exception {
        prepareSuccessfulUpdate();
        doThrow(new SQLException("secret connection warning failure", "01007", 17))
                .when(connection)
                .clearWarnings();
        doThrow(new UnsupportedOperationException("secret statement warning failure"))
                .when(statement)
                .clearWarnings();

        long count = client.create("UPDATE TEST_VALUE SET VALUE = 1").execute();

        assertThat(count, is(1L));
        verify(statement).execute();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void preservesFatalWarningProcessingErrorsAndClosesResources() throws Exception {
        prepareSuccessfulUpdate();
        AssertionError warningError = new AssertionError("fatal warning failure");
        doNothing().doThrow(warningError).when(statement).clearWarnings();

        AssertionError failure = assertThrows(AssertionError.class,
                                              () -> client.create("UPDATE TEST_VALUE SET VALUE = 1").execute());

        assertThat(failure, sameInstance(warningError));
        verify(statement).execute();
        verify(statement).close();
        verify(connection).close();
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

    private void prepareSuccessfulUpdate() throws Exception {
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(1L, -1L);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(false);
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
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(false);
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
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(false);
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

    private static void assertSafeWarning(Throwable actual, String sqlState, int vendorCode) {
        assertThat(actual, instanceOf(SQLWarning.class));
        SQLWarning warning = (SQLWarning) actual;
        assertThat(warning.getMessage(), is("The JDBC driver reported a warning."));
        assertThat(warning.getSQLState(), is(sqlState));
        assertThat(warning.getErrorCode(), is(vendorCode));
        assertThat(warning.getCause(), nullValue());
        assertThat(warning.getSuppressed().length, is(0));
    }

    @FunctionalInterface
    private interface ThrowingInvocation {
        void run();
    }
}
