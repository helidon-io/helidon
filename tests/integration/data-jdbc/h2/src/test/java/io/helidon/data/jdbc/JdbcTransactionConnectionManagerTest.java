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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxSupport;

import org.h2.jdbc.JdbcConnection;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcTransactionConnectionManagerTest {

    // Bounds every wait in the concurrency harness so a regression fails this test instead of stalling the build.
    private static final long CONCURRENT_TEST_TIMEOUT_SECONDS = 10;

    /**
     * Proves invalid lifecycle notifications do not poison later ordinary
     * provider-owned operations.
     */
    @Test
    void ordinaryOperationsRemainUsableAfterInvalidLifecycleCalls() throws Exception {
        JdbcDataSource dataSource = initializedDataSource("no_tx_state");
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        JdbcClient client = transactionAwareClient(dataSource, manager);

        assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one(), is(0L));
        assertThrows(IllegalStateException.class, manager::end);
        assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one(), is(0L));
    }

    /**
     * Proves one lazily acquired connection is reused within a transaction and
     * released only after commit.
     */
    @Test
    void reusesAndClosesTheTransactionConnectionAtCommit() throws Exception {
        JdbcDataSource dataSource = initializedDataSource("tx_commit");
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

        JdbcClient client = transactionAwareClient(dataSource, manager);
        assertThat(client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 20).execute(), is(1L));
        int generated = client.create("INSERT INTO ITEMS DEFAULT VALUES")
                .generatedKeys()
                .addColumn("ID")
                .map(row -> row.get(1, Integer.class))
                .one();
        assertThat(generated, greaterThan(0));
        assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one(), is(2L));
        manager.commit("tx-1");
        manager.end();

        assertThat(physical.isClosed(), is(true));
        assertThat(count(dataSource), is(2));
    }

    /**
     * Proves rollback discards work and a transaction cannot enlist a second
     * datasource identity.
     */
    @Test
    void rollsBackAndRejectsASecondDatasource() throws Exception {
        JdbcDataSource firstDataSource = initializedDataSource("tx_rollback");
        JdbcDataSource secondDataSource = initializedDataSource("tx_other");
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start("jdbc");
        manager.begin("tx-2");
        JdbcClient client = transactionAwareClient(firstDataSource, manager);
        client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 1).execute();

        assertThrows(DataException.class, () -> manager.acquire(secondDataSource));
        manager.rollback("tx-2");
        manager.end();

        assertThat(count(firstDataSource), is(0));
    }

    /**
     * Proves JDBC fails before acquisition when a foreign transaction support
     * is active instead of claiming participation.
     */
    @Test
    void failsFastInsideAForeignTransaction() {
        JdbcDataSource dataSource = initializedDataSource("tx_foreign");
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
        JdbcDataSource delegate = initializedDataSource("tx_new");
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
        JdbcDataSource delegate = initializedDataSource("tx_unsupported");
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
    void transactionWithoutJdbcWorkDoesNotAcquireAConnection() {
        JdbcDataSource delegate = initializedDataSource("tx_lazy");
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
    void firstDatasourceIdentityRemainsFixedWhenAcquisitionFails() {
        JdbcDataSource firstDelegate = initializedDataSource("tx_first_failure");
        JdbcDataSource secondDelegate = initializedDataSource("tx_second_after_failure");
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
        JdbcDataSource delegate = initializedDataSource("tx_stable_identity");
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
        JdbcDataSource dataSource = initializedDataSource("tx_failed_resume");
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
        FaultFixture fixture = faultFixture("tx_commit_failure", Set.of(Failure.COMMIT));
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
        FaultFixture fixture = faultFixture("tx_rollback_failure", Set.of(Failure.ROLLBACK));
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
        FaultFixture fixture = faultFixture("tx_reset_failure", Set.of(Failure.RESET_AUTO_COMMIT));
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
        FaultFixture fixture = faultFixture("tx_close_failure", Set.of(Failure.CLOSE));
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
        FaultFixture fixture = faultFixture("tx_setup_failure", Set.of(Failure.DISABLE_AUTO_COMMIT));
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
        FaultFixture fixture = faultFixture("tx_get_auto_commit_failure", Set.of(Failure.GET_AUTO_COMMIT));
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
        FaultFixture fixture = faultFixture("tx_invalidation_failure",
                                            Set.of(Failure.COMMIT, Failure.ABORT, Failure.CLOSE));
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

    /**
     * Proves Service Registry supplies the same connection manager as both
     * transaction lifecycle listener and JDBC lease provider.
     */
    @Test
    void serviceRegistryWiresTransactionSupportToTheJdbcConnectionManager() throws Exception {
        JdbcDataSource dataSource = initializedDataSource("tx_registry");
        // Tx uses the application-wide registry, so start (and register) the same registry
        // from which the JDBC transaction manager and client are obtained.
        ServiceRegistryManager registryManager = ServiceRegistryManager.start();
        try {
            TxSupport support = registryManager.registry().get(TxSupport.class);
            JdbcTransactionConnectionManager manager =
                    registryManager.registry().get(JdbcTransactionConnectionManager.class);
            JdbcClient client = transactionAwareClient(dataSource, manager);

            assertThat(support.type(), is("jdbc"));
            Tx.transaction(Tx.Type.REQUIRED, () -> {
                client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 42).execute();
                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one(), is(1L));
                return null;
            });
            assertThat(count(dataSource), is(1));

            assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
                client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 43).execute();
                throw new IllegalStateException("rollback");
            }));
            assertThat(count(dataSource), is(1));
        } finally {
            registryManager.shutdown();
        }
    }

    /**
     * Proves a pooled executor thread can run another transaction after a
     * failure without inheriting transaction state.
     */
    @Test
    void reusesOneExecutorWorkerWithoutLeakingTransactionState() throws Exception {
        JdbcDataSource dataSource = initializedDataSource("tx_executor_reuse");
        ServiceRegistryManager registryManager = ServiceRegistryManager.create();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            TxSupport support = registryManager.registry().get(TxSupport.class);
            JdbcTransactionConnectionManager manager =
                    registryManager.registry().get(JdbcTransactionConnectionManager.class);
            JdbcClient client = transactionAwareClient(dataSource, manager);

            Future<Long> result = executor.submit(() -> {
                support.transaction(Tx.Type.REQUIRED, () -> {
                    client.create("INSERT INTO ITEMS VALUES (1)").execute();
                    return null;
                });
                assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
                    client.create("INSERT INTO ITEMS VALUES (2)").execute();
                    client.create("SELECT ID FROM ITEMS")
                            .map(row -> {
                                throw new IllegalStateException("mapper failed");
                            })
                            .list();
                    return null;
                }));
                return support.transaction(Tx.Type.REQUIRED,
                                           () -> client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one());
            });

            assertThat(result.get(), is(1L));
            assertThat(count(dataSource), is(1));
        } finally {
            registryManager.shutdown();
        }
    }

    /**
     * Proves concurrent platform and virtual threads keep local transaction
     * associations isolated, with bounded failure and cleanup paths.
     */
    @Test
    void isolatesLocalTransactionsOnPlatformAndVirtualThreads() throws Exception {
        ServiceRegistryManager registryManager = ServiceRegistryManager.create();
        try {
            TxSupport support = registryManager.registry().get(TxSupport.class);
            JdbcTransactionConnectionManager manager =
                    registryManager.registry().get(JdbcTransactionConnectionManager.class);
            assertConcurrentIsolation(
                    support,
                    manager,
                    initializedDataSource("tx_platform_isolation"),
                    Executors.newFixedThreadPool(
                            2,
                            Thread.ofPlatform()
                                    .daemon(true)
                                    .name("jdbc-tx-isolation-", 0)
                                    .factory()));
            assertConcurrentIsolation(
                    support,
                    manager,
                    initializedDataSource("tx_virtual_isolation"),
                    Executors.newVirtualThreadPerTaskExecutor());
        } finally {
            registryManager.shutdown();
        }
    }

    /**
     * Proves NEW and UNSUPPORTED work have committed outcomes independent of
     * the surrounding transaction's rollback.
     */
    @Test
    void newAndUnsupportedWorkHaveIndependentDatabaseOutcomes() throws Exception {
        JdbcDataSource dataSource = initializedDataSource("tx_propagation_outcomes");
        ServiceRegistryManager registryManager = ServiceRegistryManager.create();
        try {
            TxSupport support = registryManager.registry().get(TxSupport.class);
            JdbcTransactionConnectionManager manager =
                    registryManager.registry().get(JdbcTransactionConnectionManager.class);
            JdbcClient client = transactionAwareClient(dataSource, manager);

            assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
                support.transaction(Tx.Type.NEW, () -> {
                    client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 10).execute();
                    return null;
                });
                throw new IllegalStateException("roll back outer");
            }));
            assertThat(count(dataSource), is(1));

            support.transaction(Tx.Type.REQUIRED, () -> {
                assertThrows(TxException.class, () -> support.transaction(Tx.Type.NEW, () -> {
                    client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 20).execute();
                    throw new IllegalStateException("roll back inner");
                }));
                client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 30).execute();
                return null;
            });
            assertThat(count(dataSource), is(2));

            assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
                support.transaction(Tx.Type.UNSUPPORTED, () -> {
                    client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 40).execute();
                    return null;
                });
                client.create("INSERT INTO ITEMS VALUES (?)").bind(1, 50).execute();
                throw new IllegalStateException("roll back outer");
            }));
            assertThat(count(dataSource), is(3));
        } finally {
            registryManager.shutdown();
        }
    }

    /**
     * Executes the isolation scenario and owns bounded shutdown of its executor.
     * Completion-order observation reports an early worker failure before a
     * peer waiting at a barrier can obscure its cause.
     *
     * @param support transaction support
     * @param manager JDBC transaction connection manager
     * @param dataSource datasource dedicated to this scenario
     * @param executor executor owned by this invocation
     * @throws Exception when transaction work, coordination, or cleanup fails
     */
    private static void assertConcurrentIsolation(TxSupport support,
                                                  JdbcTransactionConnectionManager manager,
                                                  JdbcDataSource dataSource,
                                                  ExecutorService executor) throws Exception {
        JdbcClient client = transactionAwareClient(dataSource, manager);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch committed = new CountDownLatch(2);
        Callable<Long> task = () -> {
            support.transaction(Tx.Type.REQUIRED, () -> {
                client.create("INSERT INTO ITEMS DEFAULT VALUES").execute();
                // Neither transaction may commit before both transaction-scoped connections have performed work.
                entered.countDown();
                assertThat("Both transactions must reach the execution barrier.",
                           entered.await(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                           is(true));
                return null;
            });
            // The fresh read transaction must start only after both writes have committed.
            committed.countDown();
            assertThat("Both transactions must reach the commit barrier.",
                       committed.await(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                       is(true));
            return support.transaction(Tx.Type.REQUIRED,
                                       () -> client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one());
        };

        // Observe completion order so an early worker exception remains the primary test failure.
        ExecutorCompletionService<Long> completion = new ExecutorCompletionService<>(executor);
        Future<Long> first = null;
        Future<Long> second = null;
        Throwable primaryFailure = null;
        try {
            first = completion.submit(task);
            second = completion.submit(task);

            for (int remaining = 2; remaining > 0; remaining--) {
                Future<Long> completed =
                        completion.poll(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (completed == null) {
                    throw new AssertionError("Timed out waiting for a concurrent transaction task.");
                }
                assertThat(completed.get(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(2L));
            }

            assertThat(count(dataSource), is(2));
        } catch (Throwable failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (first != null) {
                first.cancel(true);
            }
            if (second != null) {
                second.cancel(true);
            }
            // ExecutorService.close waits without a bound, cancel explicitly and bound termination instead.
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    AssertionError cleanupFailure =
                            new AssertionError("Timed out terminating the concurrent transaction executor.");
                    if (primaryFailure == null) {
                        throw cleanupFailure;
                    }
                    primaryFailure.addSuppressed(cleanupFailure);
                }
            } catch (InterruptedException cleanupFailure) {
                Thread.currentThread().interrupt();
                if (primaryFailure == null) {
                    throw cleanupFailure;
                }
                primaryFailure.addSuppressed(cleanupFailure);
            }
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
                .dataSource("test-data-source")
                .buildPrototype();
        JdbcClientImpl.CachePolicy cachePolicy = JdbcClientConfigSupport.cachePolicy(config);
        return new JdbcClientImpl(config, dataSource, manager, cachePolicy);
    }

    /**
     * Creates an in-memory datasource with the transaction test table.
     *
     * @param name database name
     * @return initialized datasource
     */
    private static JdbcDataSource initializedDataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ITEMS (ID INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY)");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return dataSource;
    }

    /**
     * Reads the committed row count through a fresh connection.
     *
     * @param dataSource datasource to inspect
     * @return committed row count
     * @throws Exception when the verification query fails
     */
    private static int count(JdbcDataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
            result.next();
            return result.getInt(1);
        }
    }

    /**
     * Creates a connection whose selected JDBC operations fail deterministically.
     *
     * @param name database name
     * @param failures operations to fail
     * @return datasource and instrumented connection
     * @throws SQLException when the connection cannot be created
     */
    private static FaultFixture faultFixture(String name, Set<Failure> failures) throws SQLException {
        String url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";
        JdbcDataSource delegate = new JdbcDataSource();
        delegate.setURL(url);
        FaultConnection connection = new FaultConnection(url, failures);
        return new FaultFixture(new TestDataSource(delegate, () -> connection), connection);
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

    // Test fixture combining one datasource and its instrumented connection.
    private record FaultFixture(TestDataSource dataSource, FaultConnection connection) {
    }

    // H2 connection subclass which records transaction-boundary operations and injects selected failures.
    private static final class FaultConnection extends JdbcConnection {

        // Configured failure points
        private final Set<Failure> failures;

        // JDBC boundary calls in encounter order
        private final List<String> events = new ArrayList<>();

        // Creates an instrumented view of an H2 connection.
        private FaultConnection(String url, Set<Failure> failures) throws SQLException {
            super(url, new Properties(), null, null, false);
            this.failures = new HashSet<>(failures);
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            events.add("getAutoCommit");
            if (failures.contains(Failure.GET_AUTO_COMMIT)) {
                throw new SQLException("getAutoCommit failed");
            }
            return super.getAutoCommit();
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            events.add("setAutoCommit:" + autoCommit);
            if (!autoCommit && failures.contains(Failure.DISABLE_AUTO_COMMIT)) {
                throw new SQLException("setAutoCommit(false) failed");
            }
            if (autoCommit && failures.contains(Failure.RESET_AUTO_COMMIT)) {
                throw new SQLException("setAutoCommit(true) failed");
            }
            super.setAutoCommit(autoCommit);
        }

        @Override
        public void commit() throws SQLException {
            events.add("commit");
            if (failures.contains(Failure.COMMIT)) {
                throw new SQLException("commit failed");
            }
            super.commit();
        }

        @Override
        public void rollback() throws SQLException {
            events.add("rollback");
            if (failures.contains(Failure.ROLLBACK)) {
                throw new SQLException("rollback failed");
            }
            super.rollback();
        }

        @Override
        public void abort(Executor executor) {
            events.add("abort");
            if (failures.contains(Failure.ABORT)) {
                throw new IllegalStateException("abort failed");
            }
            super.abort(executor);
        }

        @Override
        public void close() throws SQLException {
            events.add("close");
            if (failures.contains(Failure.CLOSE)) {
                throw new SQLException("close failed");
            }
            super.close();
        }

        // Counts calls to one JDBC operation.
        private long calls(String event) {
            return events.stream().filter(event::equals).count();
        }

        // Closes the physical connection after a deliberately failed cleanup test
        private void forceClose() throws SQLException {
            failures.remove(Failure.CLOSE);
            super.close();
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

    // SQLException-capable connection supplier used by {@link TestDataSource}.
    @FunctionalInterface
    private interface ConnectionSupplier {

        // Supplies one connection.
        Connection get() throws SQLException;
    }

}
