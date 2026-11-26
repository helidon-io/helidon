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
package io.helidon.tests.integration.data.jdbc.imperative.mysql;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import io.helidon.data.jdbc.JdbcClient;
import io.helidon.tests.integration.data.jdbc.database.mysql.MySqlTestDatabase;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxLifeCycle;
import io.helidon.transaction.spi.TxSupport;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(MySqlTestDatabase.class)
class JdbcTransactionPoolTest {

    /**
     * Proves confirmed commit and rollback both return a reusable connection
     * to a one-connection pool.
     */
    @Test
    void oneConnectionPoolRemainsReusableAfterCommitAndRollback() {
        try (HikariDataSource dataSource = dataSource("tx_pool")) {
            JdbcClient setupClient = JdbcClient.builder().dataSource(dataSource).build();
            setupClient.create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)").execute();
            try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource)) {
                TxSupport support = registry.support();
                JdbcClient client = registry.client();

                support.transaction(Tx.Type.REQUIRED, () -> {
                    client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 1).execute();
                    return null;
                });
                assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
                    client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 2).execute();
                    client.create("SELECT ID FROM ITEMS")
                            .map(_ -> {
                                throw new IllegalStateException("mapper failed");
                            })
                            .list();
                    return null;
                }));

                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one(), is(1L));
                assertPoolReusable(dataSource);
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
     * Proves the committed data is visible before a completion observer runs.
     */
    @Test
    void commitCompletesBeforeCompletionObserversRun() {
        try (HikariDataSource dataSource = dataSource("tx_commit_before_observer")) {
            JdbcClient setupClient = JdbcClient.builder().dataSource(dataSource).build();
            setupClient.create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)").execute();
            AtomicBoolean committedVisible = new AtomicBoolean();
            IllegalStateException observerFailure = new IllegalStateException("commit observer failed");
            TxLifeCycle observer = new OneShotCompletionFailure(
                    true,
                    observerFailure,
                    () -> committedVisible.set(setupClient.create("SELECT COUNT(*) FROM ITEMS")
                                                       .map(Long.class)
                                                       .one() == 1L));
            try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource, observer)) {
                TxSupport support = registry.support();
                JdbcClient client = registry.client();

                TxException failure = assertThrows(TxException.class,
                                                   () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                       client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                       return null;
                                                   }));

                assertThat(committedVisible.get(), is(true));
                assertThat(failure.getMessage(),
                           is("The local JDBC transaction was committed, but a later transaction lifecycle notification "
                                      + "failed during commit. The committed work must not be retried automatically."));
                assertThat(failure.getCause().getCause(), sameInstance(observerFailure));
                assertPoolReusable(dataSource);
            }
        }
    }

    /**
     * Proves a fatal observer failure remains fatal after a confirmed commit,
     * while every JDBC resource is released for the next transaction.
     */
    @Test
    void fatalCommitObserverFailureRemainsFatalAfterConfirmedCommit() {
        try (HikariDataSource dataSource = dataSource("tx_fatal_commit_observer")) {
            JdbcClient setupClient = JdbcClient.builder().dataSource(dataSource).build();
            setupClient.create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)").execute();
            IllegalStateException runtimeFailure = new IllegalStateException("commit observer failed");
            OutOfMemoryError fatalFailure = new OutOfMemoryError("commit observer fatal failure");
            OneShotCompletionFailure first = new OneShotCompletionFailure(true, runtimeFailure);
            OneShotCompletionFailure second = new OneShotCompletionFailure(true, fatalFailure);
            try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource, first, second)) {
                TxSupport support = registry.support();
                JdbcClient client = registry.client();

                OutOfMemoryError reportedFailure = assertThrows(OutOfMemoryError.class,
                                                                () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                                    client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                                    return null;
                                                                }));

                assertThat(reportedFailure, sameInstance(fatalFailure));
                assertThat(fatalFailure.getSuppressed().length, is(1));
                assertThat(fatalFailure.getSuppressed()[0], sameInstance(runtimeFailure));
                assertThat(first.failed, is(true));
                assertThat(second.failed, is(true));
                assertThat(support.transaction(Tx.Type.REQUIRED,
                                               () -> client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one()),
                           is(1L));
                assertPoolReusable(dataSource);
            }
        }
    }

    /**
     * Proves a fatal end failure outranks an earlier recoverable commit
     * observer failure without making the durable commit ambiguous.
     */
    @Test
    void fatalEndFailureOutranksCommitObserverFailureAfterRealCommit() {
        try (HikariDataSource dataSource = dataSource("tx_fatal_end_after_commit")) {
            JdbcClient.builder().dataSource(dataSource).build()
                    .create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)")
                    .execute();
            IllegalStateException commitFailure = new IllegalStateException("commit observer failed");
            OutOfMemoryError endFailure = new OutOfMemoryError("end observer failed");
            OneShotEndFailure endObserver = new OneShotEndFailure(endFailure);
            try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(
                    dataSource, new OneShotCompletionFailure(true, commitFailure), endObserver)) {
                TxSupport support = registry.support();
                JdbcClient client = registry.client();

                OutOfMemoryError reportedFailure = assertThrows(OutOfMemoryError.class,
                                                                () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                                    client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                                    return null;
                                                                }));

                assertThat(reportedFailure, sameInstance(endFailure));
                assertThat(endFailure.getSuppressed().length, is(1));
                assertThat(endFailure.getSuppressed()[0], instanceOf(TxException.class));
                assertThat(endFailure.getSuppressed()[0].getMessage(),
                           is("The local JDBC transaction was committed, but a later transaction lifecycle notification "
                                      + "failed during commit. The committed work must not be retried automatically."));
                assertThat(endFailure.getSuppressed()[0].getCause().getCause(), sameInstance(commitFailure));
                assertThat(endObserver.failed, is(true));
                assertThat(support.transaction(Tx.Type.REQUIRED,
                                               () -> client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one()),
                           is(1L));
                assertPoolReusable(dataSource);
            }
        }
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
     * Proves a fatal rollback observer failure outranks the provider's
     * synthetic rollback-only exception after the database rollback succeeds.
     */
    @Test
    void fatalRollbackObserverOutranksSyntheticFailureAfterRealRollback() {
        try (HikariDataSource dataSource = dataSource("tx_fatal_synthetic_rollback")) {
            JdbcClient.builder().dataSource(dataSource).build()
                    .create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)")
                    .execute();
            OutOfMemoryError rollbackFailure = new OutOfMemoryError("rollback observer failed");
            OneShotCompletionFailure observer = new OneShotCompletionFailure(false, rollbackFailure);
            try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource, observer)) {
                TxSupport support = registry.support();
                JdbcClient client = registry.client();

                OutOfMemoryError reportedFailure = assertThrows(OutOfMemoryError.class,
                                                                () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                                    client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                                    assertThrows(TxException.class,
                                                                                 () -> support.transaction(
                                                                                         Tx.Type.SUPPORTED,
                                                                                         () -> {
                                                                                             throw new IllegalStateException(
                                                                                                     "joined task failed");
                                                                                         }));
                                                                    return null;
                                                                }));

                assertThat(reportedFailure, sameInstance(rollbackFailure));
                assertThat(rollbackFailure.getSuppressed().length, is(1));
                assertThat(rollbackFailure.getSuppressed()[0], instanceOf(TxException.class));
                assertThat(rollbackFailure.getSuppressed()[0].getMessage(),
                           is("The local JDBC transaction was marked for rollback."));
                assertThat(observer.failed, is(true));
                assertThat(support.transaction(Tx.Type.REQUIRED,
                                               () -> client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one()),
                           is(0L));
                assertPoolReusable(dataSource);
            }
        }
    }

    /**
     * Proves a connection with an unknown commit outcome is invalidated before
     * the next pool borrower can observe it.
     */
    @Test
    void unknownCommitOutcomeInvalidatesTheConnectionBeforeTheNextPoolBorrower() throws Exception {
        try (HikariDataSource pool = dataSource("tx_unknown_pool")) {
            JdbcClient.builder().dataSource(pool).build()
                    .create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)")
                    .execute();
            Connection failingConnection = mock(Connection.class, delegatesTo(pool.getConnection()));
            doThrow(new SQLException("commit failed")).when(failingConnection).commit();
            DataSource dataSource = firstConnectionThenPool(failingConnection, pool);
            try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource)) {
                TxSupport support = registry.support();
                JdbcClient client = registry.client();

                TxException failure = assertThrows(TxException.class,
                                                   () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                       client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                       return null;
                                                   }));

                assertThat(failure.getMessage(),
                           is("The local JDBC transaction commit failed, and the outcome is unknown."));
                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one(), is(0L));
                assertPoolReusable(pool);
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
            JdbcClient.builder().dataSource(pool).build()
                    .create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)")
                    .execute();
            Connection failingConnection = mock(Connection.class, delegatesTo(pool.getConnection()));
            doThrow(new SQLException("rollback failed")).when(failingConnection).rollback();
            DataSource dataSource = firstConnectionThenPool(failingConnection, pool);
            try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource)) {
                TxSupport support = registry.support();
                JdbcClient client = registry.client();

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
            }
        }
    }

    /**
     * A single connection pool must recover after success, early return, and a caught callback processing failure.
     */
    @Test
    void reusesPoolAfterCall() {
        try (HikariDataSource pool = dataSource("callable_pool")) {
            JdbcClient setup = JdbcClient.builder().dataSource(pool).build();
            setup.create("CREATE TABLE ITEMS (ID INTEGER)").execute();
            setup.create("CREATE PROCEDURE sp_write(IN p_value INTEGER, OUT p_result INTEGER) "
                                 + "BEGIN INSERT INTO ITEMS VALUES (p_value); SET p_result = NULL; SELECT p_value; END")
                    .execute();
            setup.create("{call sp_write(?, ?)}").bind(1, 1).call().out("result", 2, JDBCType.INTEGER)
                    .consume(_ -> { });
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(pool)) {
                JdbcClient client = registry.client();
                assertThrows(TxException.class, () -> registry.support().transaction(Tx.Type.REQUIRED, () -> {
                    assertThrows(IllegalArgumentException.class, () -> client.create("{call sp_write(?, ?)}").bind(1, 2)
                            .call().out("result", 2, JDBCType.INTEGER).consume(result -> {
                                assertThat(result.rows(row -> row.get(1, Integer.class)).one(), is(2));
                                assertThrows(IllegalStateException.class,
                                             () -> client.create("SELECT 1").map(Integer.class).one());
                                result.skipRemainingResults();
                                assertThrows(IllegalArgumentException.class, () -> result.get("result", Integer.class));
                            }));
                    return null;
                }));
                assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Integer.class).one(), is(1));
                Error first = new AssertionError("The callable mapper failed.");
                Error later = new AssertionError("The callable callback failed later.");
                assertThrows(TxException.class, () -> registry.support().transaction(Tx.Type.REQUIRED, () -> {
                    Error observed = assertThrows(Error.class, () -> client.create("{call sp_write(?, ?)}").bind(1, 4)
                            .call().out("result", 2, JDBCType.INTEGER).consume(result -> {
                                Objects.requireNonNull(result, "The callable result must not be null.");
                                assertThat(assertThrows(Error.class, () -> result.rows(row -> {
                                    Objects.requireNonNull(row, "The current row must not be null.");
                                    row.get(1, Integer.class);
                                    throw first;
                                }).list()), sameInstance(first));
                                throw later;
                            }));
                    assertThat(observed, sameInstance(first));
                    assertThat(observed.getSuppressed().length, is(1));
                    assertThat(observed.getSuppressed()[0], sameInstance(later));
                    return null;
                }));
                assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Integer.class).one(), is(1));
                client.create("{call sp_write(?, ?)}").bind(1, 3).call().out("result", 2, JDBCType.INTEGER).execute();
                assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Integer.class).one(), is(2));
                client.create("{call sp_write(?, ?)}").bind(1, 5).call().out("result", 2, JDBCType.INTEGER)
                        .consume(result -> {
                            Objects.requireNonNull(result, "The callable result must not be null.");
                            assertThat(result.rows(row -> {
                                Objects.requireNonNull(row, "The current row must not be null.");
                                return row.get(1, Integer.class);
                            }).one(), is(5));
                            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(1));
                        });
                assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
                IllegalArgumentException mappingFailure = new IllegalArgumentException("The callable mapper failed.");
                assertThrows(TxException.class, () -> registry.support().transaction(Tx.Type.REQUIRED, () -> {
                    assertThat(assertThrows(IllegalArgumentException.class, () -> client.create("{call sp_write(?, ?)}")
                            .bind(1, 6).call().out("result", 2, JDBCType.INTEGER).consume(result -> {
                                Objects.requireNonNull(result, "The callable result must not be null.");
                                assertThat(assertThrows(IllegalArgumentException.class, () -> result.rows(row -> {
                                    Objects.requireNonNull(row, "The current row must not be null.");
                                    row.get(1, Integer.class);
                                    throw mappingFailure;
                                }).list()), sameInstance(mappingFailure));
                                assertThrows(IllegalStateException.class,
                                             () -> client.create("SELECT 1").map(Integer.class).one());
                            })), sameInstance(mappingFailure));
                    return null;
                }));
                assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Integer.class).one(), is(3));
            }
            assertThat(pool.isClosed(), is(false));
        }
    }

    /**
     * Verifies that the transaction completes and releases its connection when two lifecycle observers throw the same
     * exception. A subsequent transaction on the same thread checks that the connection can be reused.
     *
     * @param databaseName The name identifies the isolated MySQL database.
     * @param commit Use {@code true} to exercise commit and {@code false} to exercise rollback.
     */
    private static void assertSharedCompletionFailureReleasesConnection(String databaseName, boolean commit) {
        try (HikariDataSource dataSource = dataSource(databaseName)) {
            JdbcClient.builder().dataSource(dataSource).build()
                    .create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)")
                    .execute();
            IllegalStateException sharedFailure = new IllegalStateException("completion listener failed");
            OneShotCompletionFailure first = new OneShotCompletionFailure(commit, sharedFailure);
            OneShotCompletionFailure second = new OneShotCompletionFailure(commit, sharedFailure);
            try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource, first, second)) {
                TxSupport support = registry.support();
                JdbcClient client = registry.client();

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
                assertThat(first.failed, is(true));
                assertThat(second.failed, is(true));
                assertThat(reportedFailure, instanceOf(TxException.class));
                TxException failure = (TxException) reportedFailure;
                if (commit) {
                    assertThat(failure.getMessage(),
                               is("The local JDBC transaction was committed, but a later transaction lifecycle "
                                          + "notification failed during commit. The committed work must not be retried "
                                          + "automatically."));
                    assertThat(failure.getCause().getCause(), sameInstance(sharedFailure));
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
    }

    private static HikariDataSource dataSource(String databaseName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MySqlTestDatabase.jdbcUrl(databaseName));
        config.setUsername(MySqlTestDatabase.username());
        config.setPassword(MySqlTestDatabase.password());
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }

    private static DataSource firstConnectionThenPool(Connection first, HikariDataSource pool) throws SQLException {
        AtomicBoolean firstBorrow = new AtomicBoolean(true);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(_ ->
                firstBorrow.getAndSet(false) ? first : pool.getConnection());
        return dataSource;
    }

    private static void assertPoolReusable(HikariDataSource pool) {
        assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        assertThat(pool.getHikariPoolMXBean().getTotalConnections(), is(1));
    }

    /**
     * Listener which fails its first end event.
     */
    private static final class OneShotEndFailure implements TxLifeCycle {
        private final Throwable failure;
        private boolean failed;

        private OneShotEndFailure(Throwable failure) {
            this.failure = failure;
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if type is null
         */
        @Override
        public void start(String type) {
            Objects.requireNonNull(type, "The transaction type must not be null.");
        }

        @Override
        public void end() {
            if (!failed) {
                failed = true;
                if (failure instanceof Error error) {
                    throw error;
                }
                throw (RuntimeException) failure;
            }
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if txIdentity is null
         */
        @Override
        public void begin(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if txIdentity is null
         */
        @Override
        public void commit(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if txIdentity is null
         */
        @Override
        public void rollback(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if txIdentity is null
         */
        @Override
        public void suspend(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if txIdentity is null
         */
        @Override
        public void resume(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        }
    }

    /**
     * Listener which fails one selected completion event exactly once.
     */
    private static final class OneShotCompletionFailure implements TxLifeCycle {
        private final boolean failCommit;
        private final Throwable failure;
        private final Runnable beforeFailure;
        private boolean failed;

        /**
         * Creates a listener for one completion event.
         *
         * @param failCommit whether commit rather than rollback fails
         * @param failure shared failure to throw
         */
        private OneShotCompletionFailure(boolean failCommit, Throwable failure) {
            this(failCommit, failure, () -> {
            });
        }

        /**
         * Creates a listener which runs an assertion probe before failing one
         * completion event.
         *
         * @param failCommit whether commit rather than rollback fails
         * @param failure shared failure to throw
         * @param beforeFailure probe invoked before the failure
         */
        private OneShotCompletionFailure(boolean failCommit,
                                         Throwable failure,
                                         Runnable beforeFailure) {
            this.failCommit = failCommit;
            this.failure = failure;
            this.beforeFailure = beforeFailure;
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if type is null
         */
        @Override
        public void start(String type) {
            Objects.requireNonNull(type, "The transaction type must not be null.");
        }

        @Override
        public void end() {
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if txIdentity is null
         */
        @Override
        public void begin(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if txIdentity is null
         */
        @Override
        public void commit(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
            if (failCommit) {
                fail();
            }
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if txIdentity is null
         */
        @Override
        public void rollback(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
            if (!failCommit) {
                fail();
            }
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if txIdentity is null
         */
        @Override
        public void suspend(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException if txIdentity is null
         */
        @Override
        public void resume(String txIdentity) {
            Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        }

        private void fail() {
            if (!failed) {
                failed = true;
                beforeFailure.run();
                if (failure instanceof Error error) {
                    throw error;
                }
                throw (RuntimeException) failure;
            }
        }
    }
}
