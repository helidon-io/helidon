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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.data.jdbc.tests.application.transaction.ManagedClientTransactionCaller;
import io.helidon.data.jdbc.tests.application.transaction.ManagedClientTransactionService;
import io.helidon.data.jdbc.tests.application.transaction.StandaloneClientTransactionService;
import io.helidon.data.jdbc.tests.support.TestDataSourceFactory;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.transaction.TxException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcClientTransactionBoundaryTest {

    /**
     * Removes dynamically published data sources after each registry test.
     */
    @AfterEach
    void resetDataSources() {
        TestDataSourceFactory.reset();
    }

    /**
     * Verifies injected clients commit, roll back, reuse one transaction
     * connection, and honor new transaction suspension while returning every
     * pool lease.
     */
    @Test
    void registryManagedClientParticipatesInAnnotatedTransactions() {
        try (HikariDataSource pool = pool()) {
            CountingDataSource dataSource = new CountingDataSource(pool);
            JdbcClient setup = JdbcTestClients.create(dataSource);
            initializeSchema(setup);
            dataSource.reset();
            ServiceRegistryManager manager = manager(dataSource);
            try {
                ManagedClientTransactionService service = Services.get(ManagedClientTransactionService.class);
                ManagedClientTransactionCaller caller = Services.get(ManagedClientTransactionCaller.class);

                assertThat(service.insertAndCount(1), is(1L));
                assertThat(dataSource.connectionCount(), is(1));
                assertThrows(TxException.class, () -> service.insertAndFail(2));
                assertThat(count(setup), is(1L));

                assertThrows(TxException.class, () -> caller.insertWithNewAndFail(3, 4));
                assertThat(count(setup), is(2L));
                assertThat(count(setup, 3), is(0L));
                assertThat(count(setup, 4), is(1L));
            } finally {
                manager.shutdown();
            }
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        }
    }

    /**
     * Verifies a directly constructed client uses an operation owned
     * connection even when its calling service method is transactional.
     */
    @Test
    void standaloneClientDoesNotParticipateInAnnotatedTransaction() {
        try (HikariDataSource pool = pool()) {
            CountingDataSource dataSource = new CountingDataSource(pool);
            JdbcClient setup = JdbcTestClients.create(dataSource);
            initializeSchema(setup);
            ServiceRegistryManager manager = manager(dataSource);
            try {
                StandaloneClientTransactionService service = Services.get(StandaloneClientTransactionService.class);

                assertThrows(TxException.class, () -> service.insertAndFail(10));

                assertThat(count(setup), is(1L));
            } finally {
                manager.shutdown();
            }
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
        }
    }

    private static ServiceRegistryManager manager(DataSource dataSource) {
        TestDataSourceFactory.dataSource("standalone-source", dataSource);
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        Services.set(JdbcClientConfig.class,
                     JdbcClient.builder().dataSource("standalone-source").buildPrototype());
        return manager;
    }

    private static HikariDataSource pool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:jdbc_client_transaction_boundary;DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(2);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }

    private static long count(JdbcClient client) {
        return client.create("SELECT COUNT(*) FROM TX_BOUNDARY").map(Long.class).one();
    }

    private static long count(JdbcClient client, int id) {
        return client.create("SELECT COUNT(*) FROM TX_BOUNDARY WHERE ID = ?")
                .bind(1, id)
                .map(Long.class)
                .one();
    }

    private static void initializeSchema(JdbcClient client) {
        client.create("DROP TABLE IF EXISTS TX_BOUNDARY").execute();
        client.create("CREATE TABLE TX_BOUNDARY (ID INT PRIMARY KEY)").execute();
    }

    /**
     * Data source wrapper that records physical lease requests.
     */
    private static final class CountingDataSource implements DataSource {

        private final DataSource delegate;
        private final AtomicInteger connectionCount = new AtomicInteger();

        private CountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            connectionCount.incrementAndGet();
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            connectionCount.incrementAndGet();
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
        public <T> T unwrap(Class<T> type) throws SQLException {
            return delegate.unwrap(type);
        }

        @Override
        public boolean isWrapperFor(Class<?> type) throws SQLException {
            return delegate.isWrapperFor(type);
        }

        private int connectionCount() {
            return connectionCount.get();
        }

        private void reset() {
            connectionCount.set(0);
        }
    }
}
