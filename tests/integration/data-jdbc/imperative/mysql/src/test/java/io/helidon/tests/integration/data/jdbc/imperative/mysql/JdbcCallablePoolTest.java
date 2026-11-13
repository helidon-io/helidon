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

import java.sql.JDBCType;
import java.util.Objects;

import io.helidon.data.jdbc.JdbcClient;
import io.helidon.tests.integration.data.jdbc.database.mysql.MySqlTestDatabase;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(MySqlTestDatabase.class)
class JdbcCallablePoolTest {
    /**
     * A single connection pool must recover after success, early return, and a caught callback processing failure.
     */
    @Test
    void returnsSolePoolConnectionAfterCallableCompletionAndRollback() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MySqlTestDatabase.jdbcUrl("callable_pool"));
        config.setUsername(MySqlTestDatabase.username());
        config.setPassword(MySqlTestDatabase.password());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1000);
        try (HikariDataSource pool = new HikariDataSource(config)) {
            JdbcClient setup = JdbcClient.builder().dataSource(pool).build();
            setup.create("CREATE TABLE ITEMS (ID INTEGER)").execute();
            setup.create("CREATE PROCEDURE sp_write(IN p_value INTEGER, OUT p_result INTEGER) "
                                 + "BEGIN INSERT INTO ITEMS VALUES (p_value); SET p_result = NULL; SELECT p_value; END").execute();
            setup.create("{call sp_write(?, ?)}").bind(1, 1).call().out("result", 2, JDBCType.INTEGER)
                    .consume(_ -> { });
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            try (MySqlTransactionRegistry registry = new MySqlTransactionRegistry(pool)) {
                JdbcClient client = registry.client();
                assertThrows(TxException.class, () -> registry.support().transaction(Tx.Type.REQUIRED, () -> {
                    assertThrows(IllegalArgumentException.class, () -> client.create("{call sp_write(?, ?)}").bind(1, 2)
                            .call().out("result", 2, JDBCType.INTEGER).consume(result -> {
                                assertThat(result.rows(row -> row.get(1, Integer.class)).one(), is(2));
                                assertThrows(IllegalStateException.class, () -> client.create("SELECT 1").map(Integer.class).one());
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
}
