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

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcTransactionConnectionManagerTest {

    /**
     * Proves invalid lifecycle notifications do not poison later ordinary
     * provider-owned operations.
     */
    @Test
    void ordinaryOperationsRemainUsableAfterInvalidLifecycleCalls() throws Exception {
        DataSource dataSource = mockDataSource();
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        try (JdbcConnectionLease lease = manager.acquire(dataSource)) {
            assertThat(lease.connection().getAutoCommit(), is(true));
        }
        assertThrows(IllegalStateException.class, manager::end);
        try (JdbcConnectionLease lease = manager.acquire(dataSource)) {
            assertThat(lease.connection().getAutoCommit(), is(true));
        }
    }

    /**
     * Proves one lazily acquired connection is reused within a transaction and
     * released only after commit.
     */
    @Test
    void reusesAndClosesTheTransactionConnectionAtCommit() throws Exception {
        DataSource dataSource = mockDataSource();
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("tx-1");

        JdbcConnectionLease first = manager.acquire(dataSource);
        Connection physical = first.connection();
        assertThat(physical.getAutoCommit(), is(false));
        first.close();
        assertThat(physical.isClosed(), is(false));
        try (JdbcConnectionLease second = manager.acquire(dataSource)) {
            assertThat(second.connection(), sameInstance(physical));
        }

        manager.commit("tx-1");
        manager.end();

        assertThat(physical.isClosed(), is(true));
        verify(physical).commit();
    }

    /**
     * Proves rollback discards work and a transaction cannot enlist a second
     * datasource identity.
     */
    @Test
    void rollsBackAndRejectsASecondDatasource() throws Exception {
        DataSource firstDataSource = mockDataSource();
        DataSource secondDataSource = mockDataSource();
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("tx-2");
        Connection physical;
        try (JdbcConnectionLease lease = manager.acquire(firstDataSource)) {
            physical = lease.connection();
        }

        assertThrows(DataException.class, () -> manager.acquire(secondDataSource));
        manager.rollback("tx-2");
        manager.end();

        verify(physical).rollback();
        verify(secondDataSource, never()).getConnection();
    }

    /**
     * Proves JDBC fails before acquisition when a foreign transaction support
     * is active instead of claiming participation.
     */
    @Test
    void failsFastInsideAForeignTransaction() throws Exception {
        DataSource dataSource = mockDataSource();
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jta");
        manager.begin("foreign-1");

        assertThrows(DataException.class, () -> manager.acquire(dataSource));

        manager.rollback("foreign-1");
        manager.end();
    }

    /**
     * Proves NEW leases an independent connection and restores the suspended
     * outer association afterward.
     */
    @Test
    void newTransactionUsesAnIndependentConnectionAndRestoresOuterAssociation() throws Exception {
        DataSource delegate = mockDataSource();
        TestDataSource dataSource = new TestDataSource(delegate, delegate::getConnection);
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("outer");
        Connection outer = manager.acquire(dataSource).connection();

        manager.start("jdbc");
        manager.suspend("outer");
        manager.begin("inner");
        Connection inner = manager.acquire(dataSource).connection();
        assertThat(inner, not(sameInstance(outer)));
        manager.commit("inner");
        manager.resume("outer");
        manager.end();

        try (JdbcConnectionLease lease = manager.acquire(dataSource)) {
            assertThat(lease.connection(), sameInstance(outer));
        }
        manager.commit("outer");
        manager.end();

        assertThat(dataSource.connectionRequests(), is(2));
    }

    /**
     * Proves UNSUPPORTED work uses an operation-owned connection independent
     * of the suspended transaction.
     */
    @Test
    void unsupportedWorkUsesAnOwnedConnection() throws Exception {
        DataSource delegate = mockDataSource();
        TestDataSource dataSource = new TestDataSource(delegate, delegate::getConnection);
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("outer");
        Connection outer = manager.acquire(dataSource).connection();

        manager.start("jdbc");
        manager.suspend("outer");
        Connection unsupported;
        try (JdbcConnectionLease lease = manager.acquire(dataSource)) {
            unsupported = lease.connection();
            assertThat(unsupported, not(sameInstance(outer)));
        }
        assertThat(unsupported.isClosed(), is(true));
        manager.resume("outer");
        manager.end();

        try (JdbcConnectionLease lease = manager.acquire(dataSource)) {
            assertThat(lease.connection(), sameInstance(outer));
        }
        manager.rollback("outer");
        manager.end();
    }

    /**
     * Proves beginning and completing a transaction without JDBC work never
     * requests a connection.
     */
    @Test
    void transactionWithoutJdbcWorkDoesNotAcquireAConnection() throws Exception {
        DataSource delegate = mockDataSource();
        TestDataSource dataSource = new TestDataSource(delegate, delegate::getConnection);
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();

        manager.start("jdbc");
        manager.begin("lazy");
        manager.commit("lazy");
        manager.end();

        assertThat(dataSource.connectionRequests(), is(0));
    }

    /**
     * Proves a failed first acquisition still fixes the transaction's
     * datasource identity and rejects a later different datasource.
     */
    @Test
    void firstDatasourceIdentityRemainsFixedWhenAcquisitionFails() throws Exception {
        DataSource firstDelegate = mockDataSource();
        DataSource secondDelegate = mockDataSource();
        TestDataSource failing = new TestDataSource(firstDelegate, () -> {
            throw new SQLException("acquisition failed");
        });
        TestDataSource second = new TestDataSource(secondDelegate, secondDelegate::getConnection);
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("fixed");

        assertThrows(DataException.class, () -> manager.acquire(failing));
        assertThrows(DataException.class, () -> manager.acquire(failing));
        assertThrows(DataException.class, () -> manager.acquire(second));
        assertThat(failing.connectionRequests(), is(1));
        assertThat(second.connectionRequests(), is(0));

        manager.rollback("fixed");
        manager.end();
    }

    /**
     * Proves equivalent stable direct-datasource identities share one
     * transaction association.
     */
    @Test
    void equivalentStableDatasourceIdentitiesReuseTheTransactionConnection() throws Exception {
        DataSource delegate = mockDataSource();
        TestStableIdentity identity = new TestStableIdentity("tx_stable_identity");
        IdentityDataSource first = new IdentityDataSource(delegate, delegate::getConnection, identity);
        IdentityDataSource equivalent = new IdentityDataSource(delegate, delegate::getConnection, identity);
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("stable");

        Connection connection = manager.acquire(first).connection();
        try (JdbcConnectionLease lease = manager.acquire(equivalent)) {
            assertThat(lease.connection(), sameInstance(connection));
        }
        assertThat(first.connectionRequests(), is(1));
        assertThat(equivalent.connectionRequests(), is(0));

        manager.commit("stable");
        manager.end();
    }

    /**
     * Proves failed resume marks the context unusable so later acquisition
     * fails closed instead of escaping to auto-commit work.
     */
    @Test
    void failedResumeMakesSubsequentAcquisitionFailClosed() throws Exception {
        DataSource dataSource = mockDataSource();
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("outer");
        manager.acquire(dataSource).close();
        manager.suspend("outer");

        assertThrows(IllegalStateException.class, () -> manager.resume("unknown"));
        assertThrows(DataException.class, () -> manager.acquire(dataSource));

        manager.rollback("outer");
        manager.end();
    }

    /**
     * Proves commit failure has unknown outcome, invalidates the connection,
     * and never restores auto-commit.
     */
    @Test
    void commitFailureHasUnknownOutcomeAndNeverEnablesAutoCommit() throws Exception {
        FaultFixture fixture = faultFixture(Set.of(Failure.COMMIT));
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("commit-failure");
        manager.acquire(fixture.dataSource()).close();

        TxException failure = assertThrows(TxException.class, () -> manager.commit("commit-failure"));
        manager.end();

        assertThat(failure.getMessage(),
                   is("The local JDBC transaction commit failed, and the outcome is unknown."));
        assertThat(fixture.connection().calls("commit"), is(1L));
        assertThat(fixture.connection().calls("rollback"), is(1L));
        assertThat(fixture.connection().calls("setAutoCommit:true"), is(0L));
        assertThat(fixture.connection().calls("abort"), is(1L));
        fixture.connection().forceClose();
    }

    /**
     * Proves rollback failure has unknown outcome, invalidates the connection,
     * and never restores auto-commit.
     */
    @Test
    void rollbackFailureHasUnknownOutcomeAndNeverEnablesAutoCommit() throws Exception {
        FaultFixture fixture = faultFixture(Set.of(Failure.ROLLBACK));
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("rollback-failure");
        manager.acquire(fixture.dataSource()).close();

        TxException failure = assertThrows(TxException.class, () -> manager.rollback("rollback-failure"));
        manager.end();

        assertThat(failure.getMessage(),
                   is("The local JDBC transaction rollback failed, and the outcome is unknown."));
        assertThat(fixture.connection().calls("setAutoCommit:true"), is(0L));
        assertThat(fixture.connection().calls("abort"), is(1L));
        fixture.connection().forceClose();
    }

    /**
     * Proves failure restoring auto-commit after confirmed commit invalidates
     * the connection without changing the committed outcome.
     */
    @Test
    void autoCommitRestorationFailureInvalidatesACommittedConnection() throws Exception {
        FaultFixture fixture = faultFixture(Set.of(Failure.RESET_AUTO_COMMIT));
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("reset-failure");
        manager.acquire(fixture.dataSource()).close();

        TxException failure = assertThrows(TxException.class, () -> manager.commit("reset-failure"));
        manager.end();

        assertThat(failure.getMessage(), containsString("was committed"));
        assertThat(failure.getMessage(), containsString("restore automatic commit mode"));
        assertThat(fixture.connection().calls("commit"), is(1L));
        assertThat(fixture.connection().calls("abort"), is(1L));
        fixture.connection().forceClose();
    }

    /**
     * Proves close failure after confirmed commit triggers abort as the final
     * invalidation attempt.
     */
    @Test
    void closeFailureAttemptsAbortAfterConfirmedCommit() throws Exception {
        FaultFixture fixture = faultFixture(Set.of(Failure.CLOSE));
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("close-failure");
        manager.acquire(fixture.dataSource()).close();

        TxException failure = assertThrows(TxException.class, () -> manager.commit("close-failure"));
        manager.end();

        assertThat(failure.getMessage(), containsString("was committed"));
        assertThat(failure.getMessage(), containsString("close the connection"));
        assertThat(fixture.connection().calls("setAutoCommit:true"), is(1L));
        assertThat(fixture.connection().calls("abort"), is(1L));
        fixture.connection().forceClose();
    }

    /**
     * Proves setup failure invalidates a connection before it can become the
     * transaction's published lease.
     */
    @Test
    void setupFailureInvalidatesConnectionBeforeItCanBePublished() throws Exception {
        FaultFixture fixture = faultFixture(Set.of(Failure.DISABLE_AUTO_COMMIT));
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("setup-failure");

        assertThrows(DataException.class, () -> manager.acquire(fixture.dataSource()));
        assertThat(fixture.connection().calls("abort"), is(1L));
        assertThat(fixture.connection().calls("close"), is(1L));
        assertThrows(DataException.class, () -> manager.acquire(fixture.dataSource()));

        TxException failure = assertThrows(TxException.class, () -> manager.commit("setup-failure"));
        manager.end();

        assertThat(failure.getMessage(), containsString("rolled back instead of committed"));
        fixture.connection().forceClose();
    }

    /**
     * Proves failure inspecting auto-commit invalidates the connection and
     * fails the transaction before application work begins.
     */
    @Test
    void getAutoCommitFailureInvalidatesConnectionAndFailsTheTransaction() throws Exception {
        FaultFixture fixture = faultFixture(Set.of(Failure.GET_AUTO_COMMIT));
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("get-auto-commit-failure");

        assertThrows(DataException.class, () -> manager.acquire(fixture.dataSource()));
        assertThat(fixture.connection().calls("abort"), is(1L));
        assertThat(fixture.connection().calls("close"), is(1L));
        assertThrows(DataException.class, () -> manager.acquire(fixture.dataSource()));

        assertThrows(TxException.class, () -> manager.commit("get-auto-commit-failure"));
        manager.end();
        fixture.connection().forceClose();
    }

    /**
     * Proves abort and close failures remain suppressed on the primary
     * completion failure in encounter order.
     */
    @Test
    void invalidationFailuresAreSuppressedOnTheCompletionFailure() throws Exception {
        FaultFixture fixture = faultFixture(Set.of(Failure.COMMIT, Failure.ABORT, Failure.CLOSE));
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("invalidation-failure");
        manager.acquire(fixture.dataSource()).close();

        TxException failure = assertThrows(TxException.class, () -> manager.commit("invalidation-failure"));
        manager.end();

        assertThat(failure.getCause().getMessage(), is("The JDBC driver reported a failure."));
        // The two invalidation failures and the marker for omitted driver-owned relationships are all retained.
        assertThat(failure.getCause().getSuppressed().length, is(2));
        assertThat(failure.getCause().getSuppressed()[0].getMessage(), not(containsString("abort failed")));
        assertThat(failure.getCause().getSuppressed()[1].getMessage(), not(containsString("close failed")));
        assertThat(fixture.connection().calls("setAutoCommit:true"), is(0L));
        fixture.connection().forceClose();
    }

    private static DataSource mockDataSource() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(_ -> mockConnection());
        return dataSource;
    }

    private static Connection mockConnection() throws SQLException {
        Connection connection = mock(Connection.class);
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        AtomicBoolean closed = new AtomicBoolean();
        when(connection.getAutoCommit()).thenAnswer(_ -> autoCommit.get());
        when(connection.isClosed()).thenAnswer(_ -> closed.get());
        doAnswer(invocation -> {
            autoCommit.set(invocation.getArgument(0));
            return null;
        }).when(connection).setAutoCommit(anyBoolean());
        doAnswer(_ -> {
            closed.set(true);
            return null;
        }).when(connection).close();
        doAnswer(_ -> {
            closed.set(true);
            return null;
        }).when(connection).abort(any());
        return connection;
    }

    private static FaultFixture faultFixture(Set<Failure> failures) throws SQLException {
        DataSource delegate = mockDataSource();
        FaultConnection connection = new FaultConnection(mockConnection(), failures);
        return new FaultFixture(new TestDataSource(delegate, () -> connection.connection), connection);
    }

    // JDBC operations which may be failed by {@link FaultConnection}.
    private enum Failure {
        GET_AUTO_COMMIT,
        COMMIT,
        ROLLBACK,
        DISABLE_AUTO_COMMIT,
        RESET_AUTO_COMMIT,
        ABORT,
        CLOSE
    }

    // SQLException-capable connection supplier used by {@link TestDataSource}.
    @FunctionalInterface
    private interface ConnectionSupplier {

        // Supplies one connection.
        Connection get() throws SQLException;
    }

    // Test fixture combining one datasource and its instrumented connection.
    private record FaultFixture(TestDataSource dataSource, FaultConnection connection) {
    }

    // JDBC connection proxy which records transaction-boundary operations and injects selected failures.
    private static final class FaultConnection implements InvocationHandler {

        private final Connection delegate;
        private final Connection connection;

        // Configured failure points
        private final Set<Failure> failures;

        // JDBC boundary calls in encounter order
        private final List<String> events = new ArrayList<>();

        private FaultConnection(Connection delegate, Set<Failure> failures) {
            this.delegate = delegate;
            this.failures = new HashSet<>(failures);
            connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                                                            new Class<?>[] {Connection.class},
                                                            this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "getAutoCommit" -> record("getAutoCommit", Failure.GET_AUTO_COMMIT);
                case "setAutoCommit" -> {
                    boolean autoCommit = (boolean) args[0];
                    record("setAutoCommit:" + autoCommit,
                           autoCommit ? Failure.RESET_AUTO_COMMIT : Failure.DISABLE_AUTO_COMMIT);
                }
                case "commit" -> record("commit", Failure.COMMIT);
                case "rollback" -> record("rollback", Failure.ROLLBACK);
                case "abort" -> {
                    events.add("abort");
                    if (failures.contains(Failure.ABORT)) {
                        throw new IllegalStateException("abort failed");
                    }
                }
                case "close" -> record("close", Failure.CLOSE);
                default -> {
                }
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        private void record(String event, Failure failure) throws SQLException {
            events.add(event);
            if (failures.contains(failure)) {
                throw new SQLException(event + " failed");
            }
        }

        // Counts calls to one JDBC operation.
        private long calls(String event) {
            return events.stream().filter(event::equals).count();
        }

        // Closes the physical connection after a deliberately failed cleanup test.
        private void forceClose() throws SQLException {
            delegate.close();
        }
    }

    // Counting datasource with a test-controlled connection supplier.
    private static class TestDataSource implements DataSource {

        // Delegate for standard datasource properties.
        private final DataSource delegate;

        // Test-controlled connection creation.
        private final ConnectionSupplier connections;

        // Number of uncredentialed connection requests.
        private int connectionRequests;

        // Creates a test datasource.
        private TestDataSource(DataSource delegate, ConnectionSupplier connections) {
            this.delegate = delegate;
            this.connections = connections;
        }

        @Override
        public Connection getConnection() throws SQLException {
            connectionRequests++;
            return connections.get();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return delegate.getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }

        // Returns the connection request count.
        int connectionRequests() {
            return connectionRequests;
        }
    }

    // Immutable value identity used by equivalent test datasource adapters.
    private record TestStableIdentity(String value) implements JdbcTransactionConnectionManager.StableIdentity {
    }

    // Test datasource adapter which exposes a stable transaction identity
    private static final class IdentityDataSource
            extends TestDataSource
            implements JdbcTransactionConnectionManager.IdentitySource {

        // Stable identity returned to the connection manager.
        private final TestStableIdentity identity;

        // Creates an identity-aware test datasource.
        private IdentityDataSource(DataSource delegate,
                                   ConnectionSupplier connections,
                                   TestStableIdentity identity) {
            super(delegate, connections);
            this.identity = identity;
        }

        @Override
        public TestStableIdentity transactionIdentity() {
            return identity;
        }
    }

}
