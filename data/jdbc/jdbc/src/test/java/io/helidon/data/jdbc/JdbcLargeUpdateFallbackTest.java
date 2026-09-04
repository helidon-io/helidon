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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;

import javax.sql.DataSource;

import io.helidon.data.DataException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcLargeUpdateFallbackTest {

    private static final String UPDATE_SQL = "UPDATE TEST_VALUE SET VALUE = 1";
    private static final String QUERY_SQL = "SELECT VALUE FROM TEST_VALUE";
    private static final String INSERT_SQL = "INSERT INTO TEST_VALUE DEFAULT VALUES";

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
        client = JdbcTestClients.create(dataSource);
    }

    /**
     * Verifies that generic execution retains the exact large update count
     * and never passes through the legacy integer accessor.
     */
    @Test
    void returnsExactLargeUpdateCount() throws Exception {
        when(connection.prepareStatement(UPDATE_SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(3_000_000_000L, -1L);
        when(statement.getMoreResults()).thenReturn(false);

        assertThat(client.create(UPDATE_SQL).execute(), is(3_000_000_000L));

        verify(statement).execute();
        verify(statement, never()).executeLargeUpdate();
        verify(statement, never()).getUpdateCount();
    }

    /**
     * Verifies that losing large update count support during result completion
     * does not replace an exact primary count with a failure.
     */
    @Test
    void fallsBackForUnsupportedSubsequentLargeUpdateCount() throws Exception {
        when(connection.prepareStatement(UPDATE_SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getMoreResults()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(3_000_000_000L)
                .thenThrow(new UnsupportedOperationException("not implemented"));
        when(statement.getUpdateCount()).thenReturn(-1);

        assertThat(client.create(UPDATE_SQL).execute(), is(3_000_000_000L));

        verify(statement, times(2)).getLargeUpdateCount();
        verify(statement).getUpdateCount();
    }

    /**
     * Verifies that the Java default unsupported signal selects the legacy
     * update count without reporting a completed update as failed.
     */
    @Test
    void fallsBackForJavaDefaultUnsupportedOperation() throws Exception {
        assertUnsupportedLargeUpdateFallsBack(new UnsupportedOperationException("not implemented"));
    }

    /**
     * Verifies that the checked unsupported feature signal selects the legacy
     * update count without reporting a completed update as failed.
     */
    @Test
    void fallsBackForCheckedUnsupportedFeature() throws Exception {
        assertUnsupportedLargeUpdateFallsBack(new SQLFeatureNotSupportedException("not supported"));
    }

    /**
     * Verifies that query completion uses the legacy count only to establish
     * that no result channel follows the materialized rows.
     */
    @Test
    void completesQueryWhenLargeUpdateCountsAreUnsupported() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement(QUERY_SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString(1)).thenReturn("value");
        when(statement.getMoreResults()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenThrow(new UnsupportedOperationException("not implemented"));
        when(statement.getUpdateCount()).thenReturn(-1);

        assertThat(client.create(QUERY_SQL).map(String.class).one(), is("value"));

        verify(statement).getLargeUpdateCount();
        verify(statement).getUpdateCount();
        verify(statement).close();
        verify(connection).close();
    }

    /**
     * Verifies that an unsupported large update count does not prevent a
     * completed insert from returning its generated key.
     */
    @Test
    void readsGeneratedKeyWhenLargeUpdateCountsAreUnsupported() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenThrow(new UnsupportedOperationException("not implemented"));
        when(statement.getUpdateCount()).thenReturn(1, -1);
        when(statement.getGeneratedKeys()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong(1)).thenReturn(41L);
        when(statement.getMoreResults()).thenReturn(false);

        long key = client.create(INSERT_SQL)
                .generatedKeys()
                .map(row -> row.get(1, Long.class))
                .one();

        assertThat(key, is(41L));
        verify(statement).getLargeUpdateCount();
        verify(statement, times(2)).getUpdateCount();
        verify(statement).getGeneratedKeys();
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    /**
     * Verifies that a checked failure from the legacy accessor remains a
     * sanitized operation failure and that every owned resource is released.
     */
    @Test
    void translatesFallbackUpdateCountFailure() throws Exception {
        String sensitiveDriverDetail = "private legacy update count detail";
        SQLException driverFailure = new SQLException(sensitiveDriverDetail, "HY000", 91);
        when(connection.prepareStatement(UPDATE_SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenThrow(new UnsupportedOperationException("not implemented"));
        when(statement.getUpdateCount()).thenThrow(driverFailure);

        DataException failure = assertThrows(DataException.class, () -> client.create(UPDATE_SQL).execute());

        assertThat(failure.getMessage(), not(containsString(sensitiveDriverDetail)));
        assertThat(failure.getCause(), notNullValue());
        assertThat(failure.getCause(), not(sameInstance(driverFailure)));
        verify(statement).close();
        verify(connection).close();
    }

    /**
     * Verifies that an unexpected runtime failure from large update count retrieval
     * is sanitized and is not mistaken for an unsupported capability.
     */
    @Test
    void sanitizesAnArbitraryRuntimeFailureWithoutTreatingItAsAnUnsupportedCapability() throws Exception {
        IllegalStateException driverFailure = new IllegalStateException("private driver failure",
                                                                         new IllegalArgumentException("private cause"));
        driverFailure.addSuppressed(new IllegalArgumentException("private suppressed detail"));
        when(connection.prepareStatement(UPDATE_SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenThrow(driverFailure);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> client.create(UPDATE_SQL).execute());

        assertThat(failure, not(sameInstance(driverFailure)));
        assertThat(failure.getMessage(),
                   is("The JDBC provider encountered an exception of type 'java.lang.IllegalStateException' "
                              + "while reading a JDBC large update count."));
        assertThat(failure.getCause(), nullValue());
        assertThat(failure.getSuppressed().length, is(0));
        verify(statement, never()).getUpdateCount();
        verify(statement).close();
        verify(connection).close();
    }

    private void assertUnsupportedLargeUpdateFallsBack(Throwable unsupported) throws Exception {
        when(connection.prepareStatement(UPDATE_SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenThrow(unsupported);
        when(statement.getUpdateCount()).thenReturn(7, -1);
        when(statement.getMoreResults()).thenReturn(false);

        assertThat(client.create(UPDATE_SQL).execute(), is(7L));

        verify(statement).execute();
        verify(statement, never()).executeLargeUpdate();
        verify(statement).getLargeUpdateCount();
        verify(statement, times(2)).getUpdateCount();
        verify(statement).close();
        verify(connection).close();
    }
}
