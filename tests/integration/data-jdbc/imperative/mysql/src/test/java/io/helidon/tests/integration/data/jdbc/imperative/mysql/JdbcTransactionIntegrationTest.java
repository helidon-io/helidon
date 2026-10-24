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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import io.helidon.data.jdbc.JdbcClient;
import io.helidon.tests.integration.data.jdbc.database.mysql.MySqlTestDatabase;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(MySqlTestDatabase.class)
class JdbcTransactionIntegrationTest {
    private static final long CONCURRENT_TEST_TIMEOUT_SECONDS = 10;

    /**
     * Proves queries, updates, and generated keys share one real transaction connection, released at commit.
     */
    @Test
    void reusesOneConnectionForQueriesAndGeneratedKeys() throws Exception {
        DataSource delegate = initializedDataSource("tx_public_connection_reuse");
        AtomicInteger acquisitions = new AtomicInteger();
        AtomicReference<Connection> physical = new AtomicReference<>();
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(_ -> {
            acquisitions.incrementAndGet();
            Connection connection = delegate.getConnection();
            physical.set(connection);
            return connection;
        });
        try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource)) {
            JdbcClient client = registry.client();
            registry.support().transaction(Tx.Type.REQUIRED, () -> {
                long session = client.create("SELECT CONNECTION_ID()").map(Long.class).one();
                client.create("INSERT INTO ITEMS VALUES (20)").execute();
                long generated = client.create("INSERT INTO ITEMS () VALUES ()")
                        .generatedKeys().addColumn("ID").map(row -> row.get(1, Long.class)).one();
                assertThat(generated, greaterThan(0L));
                assertThat(client.create("SELECT CONNECTION_ID()").map(Long.class).one(), is(session));
                assertThat(physical.get().isClosed(), is(false));
                assertThat(acquisitions.get(), is(1));
                return 0;
            });
            assertThat(physical.get().isClosed(), is(true));
            assertThat(count(delegate), is(2));
        }
    }

    /**
     * Proves the public transaction boundary cannot claim rollback after SQL commits the pending work.
     */
    @Test
    void doesNotReportRollbackAfterSqlEnablesAutoCommit() throws Exception {
        DataSource delegate = initializedDataSource("tx_sql_enables_auto_commit");
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(_ -> {
            Connection physical = delegate.getConnection();
            Connection observed = mock(Connection.class, delegatesTo(physical));
            doAnswer(_ -> {
                try (var statement = physical.createStatement();
                     var rows = statement.executeQuery("SELECT @@autocommit")) {
                    rows.next();
                    return rows.getBoolean(1);
                }
            }).when(observed).getAutoCommit();
            return observed;
        });
        try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource)) {
            JdbcClient client = registry.client();
            TxException failure = assertThrows(TxException.class,
                                               () -> registry.support().transaction(Tx.Type.REQUIRED, () -> {
                                                   client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                   client.create("SET AUTOCOMMIT = 1").execute();
                                                   throw new IllegalStateException("request rollback");
                                               }));
            assertThat(failure.getSuppressed().length, is(1));
            assertThat(failure.getSuppressed()[0].getMessage(), containsString("outcome is unknown"));
            assertThat(count(delegate), is(1));
        }
    }

    /**
     * Proves public registry wiring joins JDBC work to commit and rollback boundaries.
     */
    @Test
    void serviceRegistryManagedClientCommitsAndRollsBack() throws Exception {
        DataSource dataSource = initializedDataSource("tx_registry");
        try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource)) {
            TxSupport support = registry.support();
            JdbcClient client = registry.client();

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
        }
    }

    /**
     * Proves a pooled executor thread can run another transaction after a
     * failure without inheriting transaction state.
     */
    @Test
    void reusesOneExecutorWorkerWithoutLeakingTransactionState() throws Exception {
        DataSource dataSource = initializedDataSource("tx_executor_reuse");
        try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource)) {
            ExecutorService executor = Executors.newSingleThreadExecutor(
                    Thread.ofPlatform().daemon(true).name("jdbc-tx-reuse-", 0).factory());
            Throwable primaryFailure = null;
            try {
                TxSupport support = registry.support();
                JdbcClient client = registry.client();

                Future<Long> result = executor.submit(() -> {
                    support.transaction(Tx.Type.REQUIRED, () -> {
                        client.create("INSERT INTO ITEMS VALUES (1)").execute();
                        return null;
                    });
                    assertThrows(TxException.class, () -> support.transaction(Tx.Type.REQUIRED, () -> {
                        client.create("INSERT INTO ITEMS VALUES (2)").execute();
                        client.create("SELECT ID FROM ITEMS")
                                .map(_ -> {
                                    throw new IllegalStateException("mapper failed");
                                })
                                .list();
                        return null;
                    }));
                    return support.transaction(Tx.Type.REQUIRED,
                                               () -> client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one());
                });

                assertThat(result.get(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(1L));
                assertThat(count(dataSource), is(1));
            } catch (Throwable failure) {
                primaryFailure = failure;
                throw failure;
            } finally {
                stopExecutor(executor, primaryFailure);
            }
        }
    }

    /**
     * Proves NEW and UNSUPPORTED work have committed outcomes independent of
     * the surrounding transaction's rollback.
     */
    @Test
    void newAndUnsupportedWorkHaveIndependentDatabaseOutcomes() throws Exception {
        DataSource dataSource = initializedDataSource("tx_propagation_outcomes");
        try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(dataSource)) {
            TxSupport support = registry.support();
            JdbcClient client = registry.client();

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
        }
    }

    /**
     * Proves concurrent platform and virtual threads isolate their local transactions and committed results.
     */
    @Test
    void isolatesLocalTransactionsOnPlatformAndVirtualThreads() throws Exception {
        DataSource platform = initializedDataSource("tx_platform_isolation");
        try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(platform)) {
            assertConcurrentIsolation(registry.support(), registry.client(), platform,
                                      Executors.newFixedThreadPool(2,
                                                                   Thread.ofPlatform().daemon(true)
                                                                           .name("jdbc-tx-isolation-", 0).factory()));
        }
        DataSource virtual = initializedDataSource("tx_virtual_isolation");
        try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(virtual)) {
            assertConcurrentIsolation(registry.support(), registry.client(), virtual,
                                      Executors.newVirtualThreadPerTaskExecutor());
        }
    }

    /**
     * Executes the isolation scenario and owns bounded shutdown of its executor.
     * Completion-order observation reports an early worker failure before a
     * peer waiting at a barrier can obscure its cause.
     *
     * @param support transaction support
     * @param client registry-managed JDBC client
     * @param dataSource datasource dedicated to this scenario
     * @param executor executor owned by this invocation
     * @throws Exception when transaction work, coordination, or cleanup fails
     */
    private static void assertConcurrentIsolation(TxSupport support,
                                                  JdbcClient client,
                                                  DataSource dataSource,
                                                  ExecutorService executor) throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch committed = new CountDownLatch(2);
        Callable<Long> task = () -> {
            support.transaction(Tx.Type.REQUIRED, () -> {
                client.create("INSERT INTO ITEMS () VALUES ()").execute();
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
            stopExecutor(executor, primaryFailure);
        }
    }

    /**
     * Bounds executor cleanup and preserves an earlier scenario failure.
     *
     * @param executor executor owned by the scenario
     * @param primaryFailure earlier failure, or null when the scenario succeeded
     * @throws InterruptedException if cleanup is interrupted without an earlier failure
     */
    private static void stopExecutor(ExecutorService executor, Throwable primaryFailure) throws InterruptedException {
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

    /**
     * Creates an isolated MySQL datasource with the transaction test table.
     *
     * @param name database name
     * @return initialized datasource
     */
    private static DataSource initializedDataSource(String name) {
        DataSource dataSource = MySqlTestDatabase.dataSource(name);
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ITEMS (ID INT AUTO_INCREMENT PRIMARY KEY)");
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
    private static int count(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
            result.next();
            return result.getInt(1);
        }
    }

}
