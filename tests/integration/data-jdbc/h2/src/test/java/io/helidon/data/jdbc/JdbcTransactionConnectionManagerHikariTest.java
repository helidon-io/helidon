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
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxLifeCycle;
import io.helidon.transaction.spi.TxSupport;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcTransactionConnectionManagerHikariTest {

    /**
     * Proves confirmed commit and rollback both return a reusable connection
     * to a one-connection pool.
     */
    @Test
    void oneConnectionPoolRemainsReusableAfterCommitAndRollback() {
        try (HikariDataSource dataSource = dataSource("tx_pool")) {
            JdbcClient setupClient = JdbcTestClients.create(dataSource);
            setupClient.create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)").execute();
            ServiceRegistryManager registryManager = ServiceRegistryManager.create();
            try {
                TxSupport support = registryManager.registry().get(TxSupport.class);
                JdbcTransactionConnectionManager manager =
                        registryManager.registry().get(JdbcTransactionConnectionManager.class);
                JdbcClient client = transactionAwareClient(dataSource, manager);

                support.transaction(Tx.Type.REQUIRED, () -> {
                    client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 1).execute();
                    return null;
                });
                assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
                    client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 2).execute();
                    client.create("SELECT ID FROM ITEMS")
                            .map(row -> {
                                throw new IllegalStateException("mapper failed");
                            })
                            .list();
                    return null;
                }));

                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one(), is(1L));
                assertPoolReusable(dataSource);
            } finally {
                registryManager.shutdown();
            }
        }
    }

    /**
     * Proves repeated references to one commit-listener failure do not prevent
     * the JDBC manager from committing and releasing its pooled connection.
     */
    @Test
    void sharedCommitListenerFailureDoesNotRetainTheConnection() {
        assertSharedCompletionFailureReleasesConnection("tx_shared_commit_failure", true);
    }

    /**
     * Proves repeated references to one rollback-listener failure do not
     * prevent the JDBC manager from rolling back and releasing its connection.
     */
    @Test
    void sharedRollbackListenerFailureDoesNotRetainTheConnection() {
        assertSharedCompletionFailureReleasesConnection("tx_shared_rollback_failure", false);
    }

    /**
     * Proves a connection with an unknown commit outcome is invalidated before
     * the next pool borrower can observe it.
     */
    @Test
    void unknownCommitOutcomeInvalidatesTheConnectionBeforeTheNextPoolBorrower() throws Exception {
        try (HikariDataSource pool = dataSource("tx_unknown_pool")) {
            JdbcTestClients.create(pool)
                    .create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)")
                    .execute();
            Connection failingConnection = mock(Connection.class, delegatesTo(pool.getConnection()));
            doThrow(new SQLException("commit failed")).when(failingConnection).commit();
            DataSource dataSource = firstConnectionThenPool(failingConnection, pool);
            ServiceRegistryManager registryManager = ServiceRegistryManager.create();
            try {
                TxSupport support = registryManager.registry().get(TxSupport.class);
                JdbcTransactionConnectionManager manager =
                        registryManager.registry().get(JdbcTransactionConnectionManager.class);
                JdbcClient client = transactionAwareClient(dataSource, manager);

                TxException failure = assertThrows(TxException.class,
                                                   () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                       client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                       return null;
                                                   }));

                assertThat(failure.getMessage(),
                           is("The local JDBC transaction commit failed, and the outcome is unknown."));
                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one(), is(0L));
                assertPoolReusable(pool);
            } finally {
                registryManager.shutdown();
            }
        }
    }

    /**
     * Proves a connection with an unknown rollback outcome is invalidated
     * before the next pool borrower can observe it.
     */
    @Test
    void unknownRollbackOutcomeInvalidatesTheConnectionBeforeTheNextPoolBorrower() throws Exception {
        try (HikariDataSource pool = dataSource("tx_unknown_rollback_pool")) {
            JdbcTestClients.create(pool)
                    .create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)")
                    .execute();
            Connection failingConnection = mock(Connection.class, delegatesTo(pool.getConnection()));
            doThrow(new SQLException("rollback failed")).when(failingConnection).rollback();
            DataSource dataSource = firstConnectionThenPool(failingConnection, pool);
            ServiceRegistryManager registryManager = ServiceRegistryManager.create();
            try {
                TxSupport support = registryManager.registry().get(TxSupport.class);
                JdbcTransactionConnectionManager manager =
                        registryManager.registry().get(JdbcTransactionConnectionManager.class);
                JdbcClient client = transactionAwareClient(dataSource, manager);

                TxException failure = assertThrows(TxException.class,
                                                   () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                       client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                       throw new IllegalStateException("force rollback");
                                                   }));

                assertThat(failure.getSuppressed().length, is(1));
                assertThat(failure.getSuppressed()[0].getMessage(),
                           is("The local JDBC transaction rollback failed, and the outcome is unknown."));
                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one(), is(0L));
                assertPoolReusable(pool);
            } finally {
                registryManager.shutdown();
            }
        }
    }

    /**
     * Exercises two listeners which throw one shared failure before the JDBC
     * connection manager, then proves completion and same-thread reuse.
     *
     * @param databaseName isolated H2 database name
     * @param commit whether to exercise commit rather than rollback
     */
    private static void assertSharedCompletionFailureReleasesConnection(String databaseName, boolean commit) {
        try (HikariDataSource dataSource = dataSource(databaseName)) {
            JdbcTestClients.create(dataSource)
                    .create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)")
                    .execute();
            JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
            IllegalStateException sharedFailure = new IllegalStateException("completion listener failed");
            JdbcTxSupport support = new JdbcTxSupport(List.of(
                    new OneShotCompletionFailure(commit, sharedFailure),
                    new OneShotCompletionFailure(commit, sharedFailure),
                    manager));
            JdbcClient client = transactionAwareClient(dataSource, manager);

            RuntimeException reportedFailure;
            long expectedRows;
            if (commit) {
                reportedFailure = assertThrows(RuntimeException.class,
                                               () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                   client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                   return null;
                                               }));
                expectedRows = 1;
            } else {
                reportedFailure = assertThrows(RuntimeException.class,
                                               () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                   client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                   throw new IllegalStateException("force rollback");
                                               }));
                expectedRows = 0;
            }

            assertPoolReusable(dataSource);
            assertThat(reportedFailure, instanceOf(TxException.class));
            TxException failure = (TxException) reportedFailure;
            if (commit) {
                assertThat(failure.getCause(), sameInstance(sharedFailure));
            } else {
                assertThat(failure.getSuppressed().length, is(1));
                assertThat(failure.getSuppressed()[0].getCause(), sameInstance(sharedFailure));
            }
            assertThat(support.transaction(Tx.Type.REQUIRED,
                                           () -> client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one()),
                       is(expectedRows));
            assertPoolReusable(dataSource);
        }
    }

    /**
     * Creates a client with the configuration retained by the runtime and the
     * transaction connection manager exercised by these tests.
     *
     * @param dataSource data source used by the client
     * @param manager transaction connection manager under test
     * @return transaction aware client
     */
    private static JdbcClient transactionAwareClient(DataSource dataSource,
                                                     JdbcTransactionConnectionManager manager) {
        JdbcClientConfig config = JdbcClientConfig.builder()
                .dataSourceName("test-data-source")
                .buildPrototype();
        JdbcClientImpl.CachePolicy cachePolicy = JdbcClientConfigSupport.cachePolicy(config);
        return new JdbcClientImpl(config, dataSource, manager, cachePolicy);
    }

    private static HikariDataSource dataSource(String databaseName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }

    private static DataSource firstConnectionThenPool(Connection first, HikariDataSource pool) throws SQLException {
        AtomicBoolean firstBorrow = new AtomicBoolean(true);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(invocation ->
                firstBorrow.getAndSet(false) ? first : pool.getConnection());
        return dataSource;
    }

    private static void assertPoolReusable(HikariDataSource pool) {
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        assertThat(pool.getHikariPoolMXBean().getTotalConnections(), is(1));
    }

    /**
     * Listener which fails one selected completion event exactly once.
     */
    private static final class OneShotCompletionFailure implements TxLifeCycle {
        private final boolean failCommit;
        private final RuntimeException failure;
        private boolean failed;

        /**
         * Creates a listener for one completion event.
         *
         * @param failCommit whether commit rather than rollback fails
         * @param failure shared failure to throw
         */
        private OneShotCompletionFailure(boolean failCommit, RuntimeException failure) {
            this.failCommit = failCommit;
            this.failure = failure;
        }

        @Override
        public void start(String type) {
            Objects.requireNonNull(type, "The transaction type must not be null.");
        }

        @Override
        public void end() {
        }

        @Override
        public void begin(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        }

        @Override
        public void commit(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
            if (failCommit) {
                fail();
            }
        }

        @Override
        public void rollback(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
            if (!failCommit) {
                fail();
            }
        }

        @Override
        public void suspend(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        }

        @Override
        public void resume(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        }

        private void fail() {
            if (!failed) {
                failed = true;
                throw failure;
            }
        }
    }
}
