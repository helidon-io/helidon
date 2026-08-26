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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import io.helidon.common.configurable.Resource;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcScriptRunnerFailureTest {
    private DataSource dataSource;
    private Connection connection;
    private Statement statement;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(-1L);
        when(statement.getUpdateCount()).thenReturn(-1);
    }

    /**
     * Verifies configured byte and statement limits accept their exact
     * boundaries while scripts remain detached from their resources.
     */
    @Test
    void acceptsExactConfiguredBootstrapLimits() throws Exception {
        JdbcScriptRunner.BootstrapPolicy policy = new JdbcScriptRunner.BootstrapPolicy(4, 6, 2);

        JdbcScriptRunner.PreparedScripts scripts = JdbcScriptRunner.load(
                "test",
                List.of(bootstrapResource(JdbcBootstrapResource.Role.DROP, 1, "A;  "),
                        bootstrapResource(JdbcBootstrapResource.Role.INIT, 2, "B;")),
                policy);

        JdbcScriptRunner.execute("test", dataSource, scripts);
        verify(dataSource).getConnection();
    }

    /**
     * Verifies configured resource and aggregate byte limits reject a single
     * overflow byte before datasource acquisition.
     */
    @Test
    void rejectsOneByteAboveConfiguredBootstrapLimits() throws Exception {
        JdbcScriptRunner.BootstrapPolicy resourcePolicy = new JdbcScriptRunner.BootstrapPolicy(4, 8, 10);
        DataException resourceFailure = assertThrows(
                DataException.class,
                () -> JdbcScriptRunner.load(
                        "test",
                        List.of(bootstrapResource(JdbcBootstrapResource.Role.INIT, 1, "A;   ")),
                        resourcePolicy));
        JdbcScriptRunner.BootstrapPolicy aggregatePolicy = new JdbcScriptRunner.BootstrapPolicy(4, 6, 10);
        DataException aggregateFailure = assertThrows(
                DataException.class,
                () -> JdbcScriptRunner.load(
                        "test",
                        List.of(bootstrapResource(JdbcBootstrapResource.Role.DROP, 1, "    "),
                                bootstrapResource(JdbcBootstrapResource.Role.INIT, 2, "   ")),
                        aggregatePolicy));

        assertThat(resourceFailure.getMessage(), containsString("per resource bootstrap byte limit of 4"));
        assertThat(aggregateFailure.getMessage(), containsString("aggregate bootstrap byte limit of 6"));
        verify(dataSource, never()).getConnection();
    }

    /**
     * Verifies configured statement limits are carried by the same bootstrap
     * budget as configured byte limits.
     */
    @Test
    void rejectsConfiguredStatementLimitOverflow() throws Exception {
        JdbcScriptRunner.BootstrapPolicy policy = new JdbcScriptRunner.BootstrapPolicy(16, 16, 2);

        DataException failure = assertThrows(
                DataException.class,
                () -> JdbcScriptRunner.load(
                        "test",
                        List.of(bootstrapResource(JdbcBootstrapResource.Role.INIT, 1, "A;B;C;")),
                        policy));

        assertThat(failure.getMessage(), containsString("bootstrap statement limit of 2"));
        verify(dataSource, never()).getConnection();
    }

    @Test
    void closesTheConnectionWhenStatementCloseFailsAfterSuccessfulExecution() throws Exception {
        SQLException statementClose = new SQLException("statement close failed", "08003", 51);
        doThrow(statementClose).when(statement).close();

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Resource.create(
                                                                                    "jdbc-bootstrap-init.sql"))));

        assertSafeSqlCause(failure.getCause(), statementClose);
        assertThat(failure.getMessage(), containsString("a connection exception"));
        assertThat(failure.getMessage(), containsString("SQLSTATE '08003'"));
        verify(statement, times(1)).close();
        verify(connection).close();
    }

    /**
     * Verifies that an unchecked failure from the pre-commit statement close
     * is rebuilt before bootstrap reports it and still permits transaction and
     * connection cleanup.
     */
    @Test
    void sanitizesRuntimeFailureFromBootstrapStatementClose() throws Exception {
        IllegalStateException statementClose = driverRuntimeFailure("private bootstrap statement close detail");
        doThrow(statementClose).when(statement).close();

        assertSanitizedBootstrapRuntime(statementClose, "closing a bootstrap statement");

        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).rollback();
        order.verify(connection).close();
    }

    @Test
    void usesBaselineAdvancementAndSanitizesDriverFailures() throws Exception {
        IllegalStateException advancementFailure = driverRuntimeFailure("private bootstrap result detail");
        when(statement.execute(anyString())).thenReturn(true);
        when(statement.getMoreResults()).thenThrow(advancementFailure);

        assertSanitizedBootstrapRuntime(advancementFailure, "advancing to the next bootstrap JDBC result");

        verify(statement).getMoreResults();
        verify(statement, never()).getMoreResults(anyInt());
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void drainsBootstrapResultsWithLargeUpdateCounts() throws Exception {
        long largeCount = (long) Integer.MAX_VALUE + 1;
        when(statement.getLargeUpdateCount()).thenReturn(largeCount, -1L);

        JdbcScriptRunner.execute("test",
                                 dataSource,
                                 List.of(Resource.create("large update", "UPDATE TEST_VALUE SET VALUE = 1")));

        InOrder order = inOrder(statement);
        order.verify(statement).execute(anyString());
        order.verify(statement).getLargeUpdateCount();
        order.verify(statement).getMoreResults();
        order.verify(statement).getLargeUpdateCount();
        verify(statement, never()).getUpdateCount();
    }

    @Test
    void cachesLargeUpdateCountFallbackAfterUnsupportedOperation() throws Exception {
        when(statement.getLargeUpdateCount()).thenThrow(new UnsupportedOperationException("unsupported"));
        when(statement.getUpdateCount()).thenReturn(1, -1);

        JdbcScriptRunner.execute("test",
                                 dataSource,
                                 List.of(Resource.create("legacy update", "UPDATE TEST_VALUE SET VALUE = 1")));

        verify(statement, times(1)).getLargeUpdateCount();
        verify(statement, times(2)).getUpdateCount();
    }

    @Test
    void cachesLargeUpdateCountFallbackAfterFeatureNotSupported() throws Exception {
        when(statement.getLargeUpdateCount()).thenThrow(new SQLFeatureNotSupportedException("unsupported"));
        when(statement.getUpdateCount()).thenReturn(1, -1);

        JdbcScriptRunner.execute("test",
                                 dataSource,
                                 List.of(Resource.create("legacy update", "UPDATE TEST_VALUE SET VALUE = 1")));

        verify(statement, times(1)).getLargeUpdateCount();
        verify(statement, times(2)).getUpdateCount();
    }

    @Test
    void sanitizesRuntimeFailureFromLargeBootstrapUpdateCount() throws Exception {
        IllegalStateException countFailure = driverRuntimeFailure("private large update count detail");
        when(statement.getLargeUpdateCount()).thenThrow(countFailure);

        assertSanitizedBootstrapRuntime(countFailure, "reading a bootstrap JDBC large update count");

        verify(statement, never()).getUpdateCount();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void preservesExecutionFailureAndSuppressesRollbackAndCleanupFailures() throws Exception {
        SQLException executeFailure = new SQLException("execute failed", "42000", 52);
        SQLException rollbackFailure = new SQLException("rollback failed", "08007", 53);
        SQLException statementClose = new SQLException("statement close failed", "08003", 54);
        SQLException abortFailure = new SQLException("connection abort failed", "08003", 55);
        SQLException connectionClose = new SQLException("connection close failed", "08003", 55);
        when(connection.getAutoCommit()).thenReturn(false);
        when(statement.execute(anyString())).thenThrow(executeFailure);
        doThrow(rollbackFailure).when(connection).rollback();
        doThrow(statementClose).when(statement).close();
        doThrow(abortFailure).when(connection).abort(any());
        doThrow(connectionClose).when(connection).close();

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Resource.create(
                                                                                    "jdbc-bootstrap-init.sql"))));

        assertSafeSqlCause(failure.getCause(), executeFailure);
        assertThat(failure.getSuppressed().length, is(4));
        assertSafeSqlCause(failure.getSuppressed()[0], rollbackFailure);
        assertSafeSqlCause(failure.getSuppressed()[1], statementClose);
        assertSafeSqlCause(failure.getSuppressed()[2], abortFailure);
        assertSafeSqlCause(failure.getSuppressed()[3], connectionClose);
        InOrder order = inOrder(connection, statement);
        order.verify(connection).rollback();
        order.verify(statement).close();
        order.verify(connection).abort(any());
        order.verify(connection).close();
    }

    @Test
    void invalidatesAfterSqlCommitFailureEvenWhenRollbackSucceeds() throws Exception {
        SQLException commitFailure = new SQLException("commit failed", "08007", 56);
        when(connection.getAutoCommit()).thenReturn(false);
        doThrow(commitFailure).when(connection).commit();

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Resource.create(
                                                                                    "jdbc-bootstrap-init.sql"))));

        assertThat(failure.getMessage(), containsString("could not commit the bootstrap transaction"));
        assertSafeSqlCause(failure.getCause(), commitFailure);
        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).commit();
        order.verify(connection).rollback();
        order.verify(connection).abort(any());
        order.verify(connection).close();
    }

    @Test
    void invalidatesAfterRuntimeCommitAndRollbackFailures() throws Exception {
        RuntimeException commitFailure = new IllegalStateException("commit failed");
        SQLException rollbackFailure = new SQLException("rollback failed", "08007", 57);
        when(connection.getAutoCommit()).thenReturn(false);
        doThrow(commitFailure).when(connection).commit();
        doThrow(rollbackFailure).when(connection).rollback();

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Resource.create(
                                                                                    "jdbc-bootstrap-init.sql"))));

        assertThat(failure.getMessage(), containsString("could not commit the bootstrap transaction"));
        assertThat(failure.getCause().getMessage(),
                   is("The JDBC provider encountered an exception of type 'java.lang.IllegalStateException' while "
                              + "committing a bootstrap transaction."));
        assertThat(failure.getCause().getMessage(), not(containsString("commit failed")));
        assertThat(failure.getCause().getCause(), nullValue());
        assertThat(failure.getSuppressed().length, is(1));
        assertSafeSqlCause(failure.getSuppressed()[0], rollbackFailure);
        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).commit();
        order.verify(connection).rollback();
        order.verify(connection).abort(any());
        order.verify(connection).close();
    }

    @Test
    void invalidatesBeforeRethrowingCommitError() throws Exception {
        Error commitFailure = new AssertionError("commit failed");
        when(connection.getAutoCommit()).thenReturn(false);
        doThrow(commitFailure).when(connection).commit();

        Error failure = assertThrows(Error.class,
                                     () -> JdbcScriptRunner.execute("test",
                                                                    dataSource,
                                                                    List.of(Resource.create(
                                                                            "jdbc-bootstrap-init.sql"))));

        assertThat(failure, sameInstance(commitFailure));
        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).commit();
        order.verify(connection).rollback();
        order.verify(connection).abort(any());
        order.verify(connection).close();
    }

    @Test
    void invalidatesAfterConnectionCloseFailureFollowingSuccessfulExecution() throws Exception {
        SQLException connectionClose = new SQLException("connection close failed", "08003", 57);
        doThrow(connectionClose).doNothing().when(connection).close();

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Resource.create(
                                                                                    "jdbc-bootstrap-init.sql"))));

        assertSafeSqlCause(failure.getCause(), connectionClose);
        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).close();
        order.verify(connection).abort(any());
        order.verify(connection).close();
    }

    /**
     * Verifies that an unchecked bootstrap connection-close failure is rebuilt
     * before reporting and still causes abort followed by the final close
     * fallback.
     */
    @Test
    void sanitizesRuntimeFailureFromBootstrapConnectionClose() throws Exception {
        IllegalStateException connectionClose = driverRuntimeFailure("private bootstrap connection close detail");
        doThrow(connectionClose).doNothing().when(connection).close();

        assertSanitizedBootstrapRuntime(connectionClose, "closing a bootstrap connection");

        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verify(connection).abort(any());
        order.verify(connection).close();
    }

    @Test
    void preservesConnectionCloseFailureWhenInvalidationAlsoFails() throws Exception {
        SQLException connectionClose = new SQLException("private initial close failure", "08003", 57);
        IllegalStateException abortFailure = driverRuntimeFailure("private abort failure");
        SQLException fallbackClose = new SQLException("private fallback close failure", "08007", 58);
        doThrow(connectionClose).doThrow(fallbackClose).when(connection).close();
        doThrow(abortFailure).when(connection).abort(any());

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Resource.create(
                                                                                    "jdbc-bootstrap-init.sql"))));

        assertSafeSqlCause(failure.getCause(), connectionClose);
        assertThat(failure.getCause().getSuppressed().length, is(2));
        assertThat(failure.getCause().getSuppressed()[0].getMessage(),
                   is("The JDBC provider encountered an exception of type 'java.lang.IllegalStateException' while "
                              + "aborting a connection."));
        assertThat(failure.getCause().getSuppressed()[0].getCause(), nullValue());
        assertSafeSqlCause(failure.getCause().getSuppressed()[1], fallbackClose);
        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).close();
        order.verify(connection).abort(any());
        order.verify(connection).close();
    }

    @Test
    void sanitizesRuntimeFailuresAcrossBootstrapJdbcSetupAndExecution() throws Exception {
        IllegalStateException acquisitionFailure = driverRuntimeFailure("private bootstrap URL");
        when(dataSource.getConnection()).thenThrow(acquisitionFailure);

        assertSanitizedBootstrapRuntime(acquisitionFailure, "acquiring a bootstrap connection");

        setUp();
        IllegalStateException inspectionFailure = driverRuntimeFailure("private bootstrap connection");
        when(connection.getAutoCommit()).thenThrow(inspectionFailure);

        assertSanitizedBootstrapRuntime(inspectionFailure, "inspecting bootstrap automatic commit mode");
        verify(connection).close();

        setUp();
        IllegalStateException creationFailure = driverRuntimeFailure("private bootstrap statement setup");
        when(connection.createStatement()).thenThrow(creationFailure);

        assertSanitizedBootstrapRuntime(creationFailure, "creating a bootstrap JDBC statement");
        verify(connection).close();

        setUp();
        IllegalStateException executionFailure = driverRuntimeFailure("private bootstrap SQL");
        when(statement.execute(anyString())).thenThrow(executionFailure);

        assertSanitizedBootstrapRuntime(executionFailure, "executing a bootstrap JDBC statement");
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void rejectsInputStreamCloseFailureBeforeAcquiringAConnection() throws Exception {
        String resource = "close-failure.sql";
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader failingLoader = new ClassLoader(previous) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (!resource.equals(name)) {
                    return super.getResourceAsStream(name);
                }
                return new ByteArrayInputStream("SELECT 1".getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public void close() throws IOException {
                        throw new IOException("input close failed");
                    }
                };
            }
        };

        try {
            Thread.currentThread().setContextClassLoader(failingLoader);

            DataException failure = assertThrows(DataException.class,
                                                 () -> JdbcScriptRunner.execute("test",
                                                                                dataSource,
                                                                                List.of(Resource.create(resource))));

            assertThat(failure.getMessage(),
                       is("JDBC persistence unit 'test' could not close the classpath init script."));
            assertThat(failure.getMessage(), not(containsString(resource)));
            assertSafeResourceCause(failure.getCause(), "close", IOException.class);
            verify(dataSource, never()).getConnection();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void closesConfiguredStreamBeforeAcquiringAConnection() throws Exception {
        TrackingInputStream input = new TrackingInputStream("SELECT 1", false, false);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            assertThat(input.closed(), is(true));
            return connection;
        });

        JdbcScriptRunner.execute("test", dataSource, List.of(Resource.create("tracked", input)));

        assertThat(input.closed(), is(true));
        verify(dataSource).getConnection();
    }

    @Test
    void closesUnreadableConfiguredStreamBeforeAcquiringAConnection() throws Exception {
        TrackingInputStream input = new TrackingInputStream("ignored", true, false);

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Resource.create("unreadable", input))));

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit 'test' could not read the supplied stream init script."));
        assertSafeResourceCause(failure.getCause(), "read", IOException.class);
        assertThat(input.closed(), is(true));
        verify(dataSource, never()).getConnection();
    }

    @Test
    void closesConfiguredStreamWhenParsingFails() throws Exception {
        TrackingInputStream input = new TrackingInputStream("SELECT 'unterminated", false, false);

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Resource.create("malformed", input))));

        assertThat(failure.getMessage(), containsString("unterminated single-quoted string"));
        assertThat(input.closed(), is(true));
        verify(dataSource, never()).getConnection();
    }

    @Test
    void closesLaterUnconsumedResourcesAfterLoadFailure() throws Exception {
        TrackingInputStream unreadable = new TrackingInputStream("ignored", true, false);
        TrackingInputStream remaining = new TrackingInputStream("SELECT 2", false, false);

        assertThrows(DataException.class,
                     () -> JdbcScriptRunner.execute("test",
                                                    dataSource,
                                                    List.of(Resource.create("unreadable", unreadable),
                                                            Resource.create("remaining", remaining))));

        assertThat(unreadable.closed(), is(true));
        assertThat(remaining.closed(), is(true));
        verify(dataSource, never()).getConnection();
    }

    @Test
    void suppressesFailureClosingALaterUnconsumedResource() throws Exception {
        TrackingInputStream unreadable = new TrackingInputStream("ignored", true, false);
        TrackingInputStream remaining = new TrackingInputStream("SELECT 2", false, true);

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute(
                                                     "test",
                                                     dataSource,
                                                     List.of(Resource.create("unreadable", unreadable),
                                                             Resource.create("remaining", remaining))));

        assertThat(failure.getSuppressed().length, is(1));
        assertThat(failure.getSuppressed()[0].getMessage(),
                   is("JDBC persistence unit 'test' could not close the supplied stream init script."));
        assertSafeResourceCause(failure.getSuppressed()[0].getCause(), "close", IOException.class);
        assertThat(unreadable.closed(), is(true));
        assertThat(remaining.closed(), is(true));
        verify(dataSource, never()).getConnection();
    }

    @Test
    void configuredTextFailureDoesNotExposeSql() throws Exception {
        String sql = "PRIVATE INVALID SQL TOKEN";
        String script = "\n-- retained comment\n/* another retained comment */\n" + sql;
        SQLException executeFailure = new SQLException("driver repeated " + sql, "42000", 58);
        when(statement.execute(anyString())).thenThrow(executeFailure);

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute(
                                                     "test",
                                                     dataSource,
                                                     List.of(Resource.create("private description", script))));

        assertThat(failure.getMessage(), containsString("failed to execute the statement beginning at line 4"));
        assertThat(failure.getMessage(), containsString("configured init script"));
        assertThat(failure.getMessage(), not(containsString("configured text")));
        assertThat(failure.getMessage(), not(containsString("statement 1")));
        assertThat(failure.getMessage(), not(containsString(sql)));
        assertThat(failure.getMessage(), not(containsString("private description")));
        assertSafeSqlCause(failure.getCause(), executeFailure);
    }

    @Test
    void reportsTheStartingLineAndRoleOfALaterDropStatement() throws Exception {
        String sql = "PRIVATE DROP FAILURE";
        String script = "\n-- leading comment\nSELECT 1;\n/* retained\ncomment */\n" + sql;
        SQLException executeFailure = new SQLException("driver repeated " + sql, "42000", 59);
        when(statement.execute(anyString())).thenReturn(false).thenThrow(executeFailure);
        JdbcBootstrapResource resource = JdbcBootstrapResource.create(JdbcBootstrapResource.Role.DROP,
                                                                      1,
                                                                      Resource.create("private description", script));
        JdbcScriptRunner.PreparedScripts preparedScripts = JdbcScriptRunner.load("test", List.of(resource));

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test", dataSource, preparedScripts));

        assertThat(failure.getMessage(), containsString("failed to execute the statement beginning at line 6"));
        assertThat(failure.getMessage(), containsString("configured drop script"));
        assertThat(failure.getMessage(), not(containsString("configured text")));
        assertThat(failure.getMessage(), not(containsString("statement 2")));
        assertThat(failure.getMessage(), not(containsString(sql)));
        assertThat(failure.getMessage(), not(containsString("private description")));
        assertSafeSqlCause(failure.getCause(), executeFailure);
    }

    @Test
    void rejectsUriResourcesWithoutExecutingTheirContent() throws Exception {
        Resource resource = mock(Resource.class);
        InputStream input = mock(InputStream.class);
        when(resource.sourceType()).thenReturn(Resource.Source.URL);
        when(resource.stream()).thenReturn(input);

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test", dataSource, List.of(resource)));

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit 'test' does not support a URI value for the 'init-script' "
                              + "configuration key."));
        verify(input).close();
        verify(resource, never()).location();
        verify(dataSource, never()).getConnection();
    }

    /**
     * Proves malformed binary script input is rejected and sanitized before
     * datasource acquisition.
     */
    @Test
    void rejectsMalformedUtf8BeforeAcquiringAConnection() throws Exception {
        byte[] malformed = {(byte) 0xc3, 0x28};

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute(
                                                     "test",
                                                     dataSource,
                                                     List.of(Resource.create("malformed", malformed))));

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit 'test' could not decode the configured binary init script."));
        assertThat(failure.getCause().getMessage(), not(containsString("malformed")));
        assertThat(failure.getCause().getCause(), nullValue());
        verify(dataSource, never()).getConnection();
    }

    /**
     * Proves a single bootstrap resource cannot exceed its bounded read
     * budget and is closed before datasource acquisition.
     */
    @Test
    void rejectsResourceAboveThePerResourceByteLimit() throws Exception {
        SizedInputStream input = new SizedInputStream(8 * 1024 * 1024 + 1);

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute(
                                                     "test",
                                                     dataSource,
                                                     List.of(Resource.create("oversized", input))));

        assertThat(failure.getMessage(), containsString("per resource bootstrap byte limit of 8388608"));
        assertThat(input.closed(), is(true));
        verify(dataSource, never()).getConnection();
    }

    /**
     * Proves individually valid resources cannot collectively exceed the
     * plan-wide byte budget and both consumed streams are closed.
     */
    @Test
    void rejectsPlansAboveTheAggregateByteLimit() throws Exception {
        SizedInputStream first = new SizedInputStream(8 * 1024 * 1024);
        SizedInputStream second = new SizedInputStream(4 * 1024 * 1024);
        SizedInputStream third = new SizedInputStream(4 * 1024 * 1024 + 1);

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute(
                                                     "test",
                                                     dataSource,
                                                     List.of(Resource.create("first", first),
                                                             Resource.create("second", second),
                                                             Resource.create("third", third))));

        assertThat(failure.getMessage(), containsString("aggregate bootstrap byte limit of 16777216"));
        assertThat(first.closed(), is(true));
        assertThat(second.closed(), is(true));
        assertThat(third.closed(), is(true));
        verify(dataSource, never()).getConnection();
    }

    /**
     * Proves bootstrap parsing stops at the plan-wide statement budget without
     * exposing the oversized script or acquiring a datasource connection.
     */
    @Test
    void rejectsPlansAboveTheStatementLimit() throws Exception {
        String content = "SELECT 1;".repeat(10_001);

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute(
                                                     "test",
                                                     dataSource,
                                                     List.of(Resource.create("too-many-statements", content))));

        assertThat(failure.getMessage(), containsString("bootstrap statement limit of 10000"));
        assertThat(failure.getMessage(), not(containsString(content)));
        verify(dataSource, never()).getConnection();
    }

    /**
     * Creates one safely described bootstrap resource for configured policy
     * tests.
     *
     * @param role bootstrap role
     * @param ordinal resource position
     * @param content resource content
     * @return described bootstrap resource
     */
    private static JdbcBootstrapResource bootstrapResource(JdbcBootstrapResource.Role role,
                                                           int ordinal,
                                                           String content) {
        return JdbcBootstrapResource.create(role, ordinal, Resource.create("configured test script", content));
    }

    private static void assertSafeSqlCause(Throwable actual, SQLException expected) {
        assertThat(actual, instanceOf(SQLException.class));
        SQLException safe = (SQLException) actual;
        assertThat(safe.getMessage(), is("The JDBC driver reported a failure."));
        assertThat(safe.getSQLState(), is(expected.getSQLState()));
        assertThat(safe.getErrorCode(), is(expected.getErrorCode()));
    }

    private static void assertSafeResourceCause(Throwable actual,
                                                String action,
                                                Class<? extends Throwable> failureType) {
        String operation = switch (action) {
        case "close" -> "closing";
        case "read" -> "reading";
        default -> throw new AssertionError("Unexpected test resource action: " + action);
        };
        assertThat(actual.getMessage(),
                   is("The JDBC provider encountered an exception of type '" + failureType.getName()
                              + "' while " + operation + " a bootstrap resource."));
        assertThat(actual.getCause(), nullValue());
        assertThat(actual.getSuppressed().length, is(0));
    }

    private void assertSanitizedBootstrapRuntime(RuntimeException original, String operation) {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> JdbcScriptRunner.execute("test",
                                               dataSource,
                                               List.of(Resource.create("private description", "SELECT 1"))));

        assertThat(failure, not(sameInstance(original)));
        assertThat(failure.getMessage(),
                   is("The JDBC provider encountered an exception of type '" + original.getClass().getName()
                              + "' while " + operation + "."));
        assertThat(failure.getMessage(), not(containsString("private")));
        assertThat(failure.getCause(), nullValue());
        assertThat(failure.getSuppressed().length, is(0));
    }

    private static IllegalStateException driverRuntimeFailure(String secret) {
        IllegalStateException failure = new IllegalStateException(secret, new IllegalArgumentException("private cause"));
        failure.addSuppressed(new IllegalArgumentException("private suppressed"));
        return failure;
    }

    private static final class TrackingInputStream extends InputStream {
        private final byte[] content;
        private final boolean failRead;
        private final boolean failClose;
        private int index;
        private boolean closed;

        private TrackingInputStream(String content, boolean failRead, boolean failClose) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
            this.failRead = failRead;
            this.failClose = failClose;
        }

        @Override
        public int read() throws IOException {
            if (failRead) {
                throw new IOException("input read failed");
            }
            return index == content.length ? -1 : content[index++] & 0xff;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (failClose) {
                throw new IOException("input close failed");
            }
        }

        private boolean closed() {
            return closed;
        }
    }

    private static final class SizedInputStream extends InputStream {
        private int remaining;
        private boolean closed;

        private SizedInputStream(int remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            return ' ';
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (remaining == 0) {
                return -1;
            }
            int count = Math.min(remaining, length);
            remaining -= count;
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }

        private boolean closed() {
            return closed;
        }
    }
}
