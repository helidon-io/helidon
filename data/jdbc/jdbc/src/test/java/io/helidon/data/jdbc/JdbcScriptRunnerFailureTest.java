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
        when(statement.getUpdateCount()).thenReturn(-1);
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
        assertThat(failure.getMessage(), containsString("SQLState=08003"));
        verify(statement, times(1)).close();
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

        assertThat(failure.getMessage(), containsString("bootstrap commit failed with unknown outcome"));
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

        assertThat(failure.getMessage(), containsString("bootstrap commit failed with unknown outcome"));
        assertThat(failure.getCause().getMessage(),
                   is("JDBC bootstrap commit failure [java.lang.IllegalStateException]"));
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
    void reportsConnectionCloseFailureAfterSuccessfulExecution() throws Exception {
        SQLException connectionClose = new SQLException("connection close failed", "08003", 57);
        doThrow(connectionClose).when(connection).close();

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Resource.create(
                                                                                    "jdbc-bootstrap-init.sql"))));

        assertSafeSqlCause(failure.getCause(), connectionClose);
        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).close();
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

            assertThat(failure.getMessage(), containsString("init bootstrap resource #1 (classpath)"));
            assertThat(failure.getMessage(), containsString("failed during close"));
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

        assertThat(failure.getMessage(), containsString("init bootstrap resource #1 (supplied stream)"));
        assertThat(failure.getMessage(), containsString("failed during read"));
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
                   containsString("init bootstrap resource #2 (supplied stream) failed during release close"));
        assertSafeResourceCause(failure.getSuppressed()[0].getCause(), "release close", IOException.class);
        assertThat(unreadable.closed(), is(true));
        assertThat(remaining.closed(), is(true));
        verify(dataSource, never()).getConnection();
    }

    @Test
    void configuredTextFailureDoesNotExposeSql() throws Exception {
        String sql = "PRIVATE INVALID SQL TOKEN";
        SQLException executeFailure = new SQLException("driver repeated " + sql, "42000", 58);
        when(statement.execute(anyString())).thenThrow(executeFailure);

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute(
                                                     "test",
                                                     dataSource,
                                                     List.of(Resource.create("private description", sql))));

        assertThat(failure.getMessage(), containsString("init bootstrap resource #1 (configured text)"));
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
                   containsString("does not support URI-backed init bootstrap resource #1 (URI)"));
        verify(input).close();
        verify(resource, never()).location();
        verify(dataSource, never()).getConnection();
    }

    @Test
    void rejectsMalformedUtf8BeforeAcquiringAConnection() throws Exception {
        byte[] malformed = {(byte) 0xc3, 0x28};

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute(
                                                     "test",
                                                     dataSource,
                                                     List.of(Resource.create("malformed", malformed))));

        assertThat(failure.getMessage(), containsString("configured binary"));
        assertThat(failure.getMessage(), containsString("failed during UTF-8 decoding"));
        assertThat(failure.getCause().getMessage(), not(containsString("malformed")));
        assertThat(failure.getCause().getCause(), nullValue());
        verify(dataSource, never()).getConnection();
    }

    @Test
    void rejectsResourceAboveThePerResourceByteLimit() throws Exception {
        SizedInputStream input = new SizedInputStream(8 * 1024 * 1024 + 1);

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute(
                                                     "test",
                                                     dataSource,
                                                     List.of(Resource.create("oversized", input))));

        assertThat(failure.getMessage(), containsString("per-resource bootstrap byte limit of 8388608"));
        assertThat(input.closed(), is(true));
        verify(dataSource, never()).getConnection();
    }

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

    private static void assertSafeSqlCause(Throwable actual, SQLException expected) {
        assertThat(actual, instanceOf(SQLException.class));
        SQLException safe = (SQLException) actual;
        assertThat(safe.getMessage(), is("JDBC driver failure"));
        assertThat(safe.getSQLState(), is(expected.getSQLState()));
        assertThat(safe.getErrorCode(), is(expected.getErrorCode()));
    }

    private static void assertSafeResourceCause(Throwable actual,
                                                String phase,
                                                Class<? extends Throwable> failureType) {
        assertThat(actual.getMessage(),
                   is("JDBC bootstrap resource " + phase + " failure [" + failureType.getName() + "]"));
        assertThat(actual.getCause(), nullValue());
        assertThat(actual.getSuppressed().length, is(0));
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
