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
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcLargeUpdateFallbackTest {

    private static final String SQL = "UPDATE TEST_VALUE SET VALUE = 1";

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
        client = new JdbcClientImpl(dataSource);
    }

    @Test
    void cachesJavaDefaultFallbackOnlyForTheCurrentPreparedStatement() throws Exception {
        PreparedStatement capableStatement = mock(PreparedStatement.class);
        when(connection.prepareStatement(SQL)).thenReturn(statement, capableStatement);

        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenThrow(new UnsupportedOperationException("not implemented"));
        when(statement.getUpdateCount()).thenReturn(7, -1);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(false);

        when(capableStatement.execute()).thenReturn(false);
        when(capableStatement.getLargeUpdateCount()).thenReturn(3_000_000_000L, -1L);
        when(capableStatement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(false);

        assertThat(client.create(SQL).execute(), is(7L));
        assertThat(client.create(SQL).execute(), is(3_000_000_000L));

        verify(statement).getLargeUpdateCount();
        verify(statement, times(2)).getUpdateCount();
        verify(capableStatement, times(2)).getLargeUpdateCount();
        verify(capableStatement, never()).getUpdateCount();
    }

    @Test
    void fallsBackWhenTheDriverReportsFeatureNotSupported() throws Exception {
        when(connection.prepareStatement(SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenThrow(new SQLFeatureNotSupportedException("not supported"));
        when(statement.getUpdateCount()).thenReturn(Integer.MAX_VALUE, -1);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(false);

        assertThat(client.create(SQL).execute(), is((long) Integer.MAX_VALUE));

        verify(statement).getLargeUpdateCount();
        verify(statement, times(2)).getUpdateCount();
    }

    @Test
    void doesNotTreatAnArbitraryRuntimeFailureAsAnUnsupportedCapability() throws Exception {
        IllegalStateException driverFailure = new IllegalStateException("driver failure");
        when(connection.prepareStatement(SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenThrow(driverFailure);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> client.create(SQL).execute());

        assertThat(failure, sameInstance(driverFailure));
        verify(statement, never()).getUpdateCount();
        verify(statement).close();
        verify(connection).close();
    }
}
