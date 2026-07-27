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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import io.helidon.data.DataException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                                                                            List.of(Path.of(
                                                                                    "jdbc-bootstrap-init.sql"))));

        assertThat(failure.getCause(), sameInstance(statementClose));
        assertThat(failure.getMessage(), containsString("SQLState=08003"));
        verify(statement, times(1)).close();
        verify(connection).close();
    }

    @Test
    void preservesExecutionFailureAndSuppressesRollbackAndCleanupFailures() throws Exception {
        SQLException executeFailure = new SQLException("execute failed", "42000", 52);
        SQLException rollbackFailure = new SQLException("rollback failed", "08007", 53);
        SQLException statementClose = new SQLException("statement close failed", "08003", 54);
        SQLException connectionClose = new SQLException("connection close failed", "08003", 55);
        when(connection.getAutoCommit()).thenReturn(false);
        when(statement.execute(anyString())).thenThrow(executeFailure);
        doThrow(rollbackFailure).when(connection).rollback();
        doThrow(statementClose).when(statement).close();
        doThrow(connectionClose).when(connection).close();

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Path.of(
                                                                                    "jdbc-bootstrap-init.sql"))));

        assertThat(failure.getCause(), sameInstance(executeFailure));
        assertThat(failure.getSuppressed().length, is(3));
        assertThat(failure.getSuppressed()[0], sameInstance(rollbackFailure));
        assertThat(failure.getSuppressed()[1], sameInstance(statementClose));
        assertThat(failure.getSuppressed()[2], sameInstance(connectionClose));
        InOrder order = inOrder(connection, statement);
        order.verify(connection).rollback();
        order.verify(statement).close();
        order.verify(connection).close();
    }

    @Test
    void rollsBackAndClosesAfterCommitFailure() throws Exception {
        SQLException commitFailure = new SQLException("commit failed", "08007", 56);
        when(connection.getAutoCommit()).thenReturn(false);
        doThrow(commitFailure).when(connection).commit();

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Path.of(
                                                                                    "jdbc-bootstrap-init.sql"))));

        assertThat(failure.getCause(), sameInstance(commitFailure));
        InOrder order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).commit();
        order.verify(connection).rollback();
        order.verify(connection).close();
    }

    @Test
    void reportsConnectionCloseFailureAfterSuccessfulExecution() throws Exception {
        SQLException connectionClose = new SQLException("connection close failed", "08003", 57);
        doThrow(connectionClose).when(connection).close();

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute("test",
                                                                            dataSource,
                                                                            List.of(Path.of(
                                                                                    "jdbc-bootstrap-init.sql"))));

        assertThat(failure.getCause(), sameInstance(connectionClose));
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
                                                                                List.of(Path.of(resource))));

            assertThat(failure.getCause().getMessage(), is("input close failed"));
            verify(dataSource, never()).getConnection();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
}
