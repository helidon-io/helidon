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
import java.util.Map;
import java.util.function.BooleanSupplier;

import javax.sql.DataSource;

import io.helidon.health.HealthCheckResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class JdbcClientProbeTest {
    /**
     * Validates construction without touching either the data source or an unsupported client.
     */
    @Test
    void validatesBeforeAcquisitionAndRejectsCustomClients() {
        DataSource source = mock(DataSource.class);
        JdbcClient client = JdbcTestClients.create(source);
        assertThrows(IllegalArgumentException.class,
                     () -> probe(client, "/*! EXPLAIN */ SELECT 1", Duration.ofSeconds(2)));
        assertThrows(IllegalArgumentException.class, () -> probe(client, "SELECT 1", Duration.ZERO));
        JdbcClient custom = mock(JdbcClient.class);
        assertThrows(IllegalArgumentException.class,
                     () -> probe(custom, "SELECT 1", Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> probe(custom, null, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> probe(client, "", null));
        assertThrows(NullPointerException.class, () -> probe(null, "", Duration.ZERO));
        verifyZeroInteractions(source, custom);
    }

    /**
     * Uses the resolved source even when the public prototype describes a different named source.
     */
    @Test
    void retainsActualResolvedSourceAndExecutesFreshEmptyAndNonemptyProbes() throws Exception {
        Fixture fixture = fixture();
        when(fixture.rows().next()).thenReturn(true, true, false);
        BooleanSupplier probe = probe(JdbcTestClients.create(fixture.source()),
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
     * Delegates a SQL failure to a sanitized response and permits a later invocation to succeed.
     */
    @Test
    void delegatedSqlFailureIsSanitizedAndAllowsLaterReuse() throws Exception {
        Fixture fixture = fixture();
        when(fixture.statement().execute()).thenThrow(new SQLException("Secret SQL and credentials."));
        var check = JdbcClientHealthCheck.builder().client(JdbcTestClients.create(fixture.source())).build();

        HealthCheckResponse response = check.call();
        assertThat(response.status(), is(HealthCheckResponse.Status.DOWN));
        assertThat(response.details(), is(Map.of()));
        doReturn(true).when(fixture.statement()).execute();
        HealthCheckResponse recovered = check.call();
        assertThat(recovered.status(), is(HealthCheckResponse.Status.UP));
        assertThat(recovered.details(), is(Map.of()));
        verify(fixture.source(), times(2)).getConnection();
        verify(fixture.statement(), times(2)).execute();
        verify(fixture.statement(), times(2)).close();
        verify(fixture.connection(), times(2)).close();
        noTransactionControl(fixture.connection());
    }

    /**
     * Preserves a fatal failure from the delegated probe and releases its connection.
     */
    @Test
    void delegatedFatalErrorPropagatesUnchanged() throws Exception {
        Fixture fixture = fixture();
        Error fatal = new OutOfMemoryError("The delegated execution failed.");
        when(fixture.statement().execute()).thenThrow(fatal);
        assertThat(assertThrows(OutOfMemoryError.class, () -> probe(fixture).getAsBoolean()), sameInstance(fatal));
        verify(fixture.statement()).close();
        verify(fixture.connection()).close();
        noTransactionControl(fixture.connection());
    }

    /**
     * Consults the lease provider for each invocation while acquiring and releasing independent probe resources.
     */
    @Test
    void validatesLeaseProviderContextBeforeEachIndependentAcquisition() throws Exception {
        Fixture fixture = fixture();
        JdbcConnectionLease.Provider provider = mock(JdbcConnectionLease.Provider.class);
        JdbcClientConfig config = JdbcClientConfig.builder().dataSource(fixture.source()).buildPrototype();
        JdbcClient client = new JdbcClientImpl(config, fixture.source(), provider, JdbcClientConfigSupport.cachePolicy(config));
        BooleanSupplier probe = probe(client, "SELECT 1", Duration.ofSeconds(2));
        verifyZeroInteractions(provider, fixture.source());

        assertThat(probe.getAsBoolean(), is(true));
        assertThat(probe.getAsBoolean(), is(true));

        var order = inOrder(provider, fixture.source());
        for (int invocation = 0; invocation < 2; invocation++) {
            order.verify(provider).checkProbeContext();
            order.verify(fixture.source()).getConnection();
        }
        // An ordinary lease can reuse a caller connection, so the probe uses the provider only to check eligibility.
        verify(provider, never()).acquire(any());
        verify(fixture.rows(), times(2)).close();
        verify(fixture.statement(), times(2)).close();
        verify(fixture.connection(), times(2)).close();
        noTransactionControl(fixture.connection());
    }

    /**
     * A rejected context reports no diagnostics and performs no JDBC work, but a later invocation can succeed.
     */
    @Test
    void rejectedLeaseProviderContextReturnsSanitizedDownAndAllowsLaterReuse() throws Exception {
        Fixture fixture = fixture();
        JdbcConnectionLease.Provider provider = mock(JdbcConnectionLease.Provider.class);
        doThrow(new IllegalStateException("Secret transaction diagnostic.")).when(provider).checkProbeContext();
        JdbcClientConfig config = JdbcClientConfig.builder().dataSource(fixture.source()).buildPrototype();
        JdbcClient client = new JdbcClientImpl(config, fixture.source(), provider, JdbcClientConfigSupport.cachePolicy(config));
        JdbcClientHealthCheck check = JdbcClientHealthCheck.builder().client(client).build();
        verifyZeroInteractions(provider, fixture.source());

        HealthCheckResponse response = check.call();
        assertThat(response.status(), is(HealthCheckResponse.Status.DOWN));
        assertThat(response.details(), is(Map.of()));
        verifyZeroInteractions(fixture.source(), fixture.connection(), fixture.statement(), fixture.rows());

        doNothing().when(provider).checkProbeContext();
        assertThat(check.call().status(), is(HealthCheckResponse.Status.UP));
        verify(provider, times(2)).checkProbeContext();
        verify(provider, never()).acquire(any());
        verify(fixture.source()).getConnection();
        verify(fixture.rows()).close();
        verify(fixture.statement()).close();
        verify(fixture.connection()).close();
        noTransactionControl(fixture.connection());
    }

    /**
     * A fatal context failure escapes unchanged before any probe resource is acquired.
     */
    @Test
    void leaseProviderContextPreservesFatalErrorsBeforeAcquisition() throws Exception {
        Fixture fixture = fixture();
        JdbcConnectionLease.Provider provider = mock(JdbcConnectionLease.Provider.class);
        OutOfMemoryError fatal = new OutOfMemoryError("The context check failed.");
        doThrow(fatal).when(provider).checkProbeContext();
        JdbcClientConfig config = JdbcClientConfig.builder().dataSource(fixture.source()).buildPrototype();
        JdbcClient client = new JdbcClientImpl(config, fixture.source(), provider, JdbcClientConfigSupport.cachePolicy(config));
        BooleanSupplier probe = probe(client, "SELECT 1", Duration.ofSeconds(2));

        assertThat(assertThrows(OutOfMemoryError.class, probe::getAsBoolean), sameInstance(fatal));
        verify(provider).checkProbeContext();
        verify(provider, never()).acquire(any());
        verifyZeroInteractions(fixture.source(), fixture.connection(), fixture.statement(), fixture.rows());
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
        BooleanSupplier probe = probe(client, "SELECT 1", Duration.ofSeconds(2));
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

    private static BooleanSupplier probe(JdbcClient client, String query, Duration timeout) {
        JdbcClientHealthCheck check = JdbcClientHealthCheck.builder().client(client).query(query).queryTimeout(timeout).build();
        return () -> check.call().status() == HealthCheckResponse.Status.UP;
    }

    private static BooleanSupplier probe(Fixture fixture) {
        return probe(JdbcTestClients.create(fixture.source()), "SELECT 1", Duration.ofSeconds(2));
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
