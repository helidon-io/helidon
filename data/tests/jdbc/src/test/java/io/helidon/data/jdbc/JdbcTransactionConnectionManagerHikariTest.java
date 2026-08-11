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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxSupport;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcTransactionConnectionManagerHikariTest {

    @Test
    void oneConnectionPoolRemainsReusableAfterCommitAndRollback() {
        try (HikariDataSource dataSource = dataSource("tx_pool")) {
            JdbcClient setupClient = new JdbcClientImpl(dataSource);
            setupClient.create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)").execute();
            ServiceRegistryManager registryManager = ServiceRegistryManager.create();
            try {
                TxSupport support = registryManager.registry().get(TxSupport.class);
                JdbcTransactionConnectionManager manager =
                        registryManager.registry().get(JdbcTransactionConnectionManager.class);
                JdbcClient client = new JdbcClientImpl(dataSource, manager);

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

    @Test
    void unknownCommitOutcomeInvalidatesTheConnectionBeforeTheNextPoolBorrower() throws Exception {
        try (HikariDataSource pool = dataSource("tx_unknown_pool")) {
            new JdbcClientImpl(pool).create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)").execute();
            Connection failingConnection = mock(Connection.class, delegatesTo(pool.getConnection()));
            doThrow(new SQLException("commit failed")).when(failingConnection).commit();
            DataSource dataSource = firstConnectionThenPool(failingConnection, pool);
            ServiceRegistryManager registryManager = ServiceRegistryManager.create();
            try {
                TxSupport support = registryManager.registry().get(TxSupport.class);
                JdbcTransactionConnectionManager manager =
                        registryManager.registry().get(JdbcTransactionConnectionManager.class);
                JdbcClient client = new JdbcClientImpl(dataSource, manager);

                TxException failure = assertThrows(TxException.class,
                                                   () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                       client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                       return null;
                                                   }));

                assertThat(failure.getMessage(), containsString("unknown outcome"));
                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one(), is(0L));
                assertPoolReusable(pool);
            } finally {
                registryManager.shutdown();
            }
        }
    }

    @Test
    void unknownRollbackOutcomeInvalidatesTheConnectionBeforeTheNextPoolBorrower() throws Exception {
        try (HikariDataSource pool = dataSource("tx_unknown_rollback_pool")) {
            new JdbcClientImpl(pool).create("CREATE TABLE ITEMS (ID INT PRIMARY KEY)").execute();
            Connection failingConnection = mock(Connection.class, delegatesTo(pool.getConnection()));
            doThrow(new SQLException("rollback failed")).when(failingConnection).rollback();
            DataSource dataSource = firstConnectionThenPool(failingConnection, pool);
            ServiceRegistryManager registryManager = ServiceRegistryManager.create();
            try {
                TxSupport support = registryManager.registry().get(TxSupport.class);
                JdbcTransactionConnectionManager manager =
                        registryManager.registry().get(JdbcTransactionConnectionManager.class);
                JdbcClient client = new JdbcClientImpl(dataSource, manager);

                TxException failure = assertThrows(TxException.class,
                                                   () -> support.transaction(Tx.Type.REQUIRED, () -> {
                                                       client.create("INSERT INTO ITEMS VALUES (1)").execute();
                                                       throw new IllegalStateException("force rollback");
                                                   }));

                assertThat(failure.getSuppressed().length, is(1));
                assertThat(failure.getSuppressed()[0].getMessage(), containsString("unknown outcome"));
                assertThat(client.create("SELECT COUNT(*) FROM ITEMS").map(Long.class).one(), is(0L));
                assertPoolReusable(pool);
            } finally {
                registryManager.shutdown();
            }
        }
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
}
