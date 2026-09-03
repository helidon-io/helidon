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

import javax.sql.DataSource;

import io.helidon.data.DataException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        client = JdbcTestClients.create(dataSource);
    }

    /**
     * Verifies that generic execution retains the exact large update count
     * and never passes through the legacy integer accessor.
     */
    @Test
    void returnsExactLargeUpdateCount() throws Exception {
        when(connection.prepareStatement(SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(3_000_000_000L, -1L);
        when(statement.getMoreResults()).thenReturn(false);

        assertThat(client.create(SQL).execute(), is(3_000_000_000L));

        verify(statement).execute();
        verify(statement, never()).executeLargeUpdate();
        verify(statement, never()).getUpdateCount();
    }

    /**
     * Verifies that an unsupported large-count accessor fails closed even
     * while checking for a subsequent result channel.
     */
    @Test
    void failsClosedForUnsupportedSubsequentLargeUpdateCount() throws Exception {
        String sensitiveDriverDetail = "private subsequent count detail";
        when(connection.prepareStatement(SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getMoreResults()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(3_000_000_000L)
                .thenThrow(new UnsupportedOperationException(sensitiveDriverDetail));

        DataException failure = assertThrows(DataException.class, () -> client.create(SQL).execute());

        assertThat(failure.getMessage(),
                   is("The JDBC driver does not support the large update count required for this operation."));
        assertThat(failure.getMessage(), not(containsString(sensitiveDriverDetail)));
        assertThat(failure.getCause(), nullValue());
        verify(statement, never()).getUpdateCount();
    }

    /**
     * Verifies that Java's default unsupported-operation signal fails closed
     * and does not expose driver-provided diagnostic text.
     */
    @Test
    void failsClosedForJavaDefaultUnsupportedOperation() throws Exception {
        String sensitiveDriverDetail = "private Java default detail";

        assertUnsupportedLargeUpdate(new UnsupportedOperationException(sensitiveDriverDetail),
                                     sensitiveDriverDetail);
    }

    /**
     * Verifies that a driver's checked unsupported-feature signal fails
     * closed and does not expose driver-provided diagnostic text.
     */
    @Test
    void failsClosedForCheckedUnsupportedFeature() throws Exception {
        String sensitiveDriverDetail = "private checked unsupported detail";

        assertUnsupportedLargeUpdate(new SQLFeatureNotSupportedException(sensitiveDriverDetail),
                                     sensitiveDriverDetail);
    }

    /**
     * Verifies that an unexpected runtime failure from large-count retrieval
     * is sanitized and is not mistaken for an unsupported capability.
     */
    @Test
    void sanitizesAnArbitraryRuntimeFailureWithoutTreatingItAsAnUnsupportedCapability() throws Exception {
        IllegalStateException driverFailure = new IllegalStateException("private driver failure",
                                                                         new IllegalArgumentException("private cause"));
        driverFailure.addSuppressed(new IllegalArgumentException("private suppressed detail"));
        when(connection.prepareStatement(SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenThrow(driverFailure);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> client.create(SQL).execute());

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

    private void assertUnsupportedLargeUpdate(Throwable unsupported,
                                              String sensitiveDriverDetail) throws Exception {
        when(connection.prepareStatement(SQL)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenThrow(unsupported);

        DataException failure = assertThrows(DataException.class,
                                             () -> client.create(SQL).execute());

        assertThat(failure.getMessage(),
                   is("The JDBC driver does not support the large update count required for this operation."));
        assertThat(failure.getMessage(), not(containsString(sensitiveDriverDetail)));
        assertThat(failure.getCause(), nullValue());
        assertThat(failure.getSuppressed().length, is(0));
        verify(statement).execute();
        verify(statement, never()).executeLargeUpdate();
        verify(statement, never()).getUpdateCount();
        verify(statement).close();
        verify(connection).close();
    }
}
