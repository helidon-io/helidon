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
import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.jdbc.JdbcBatchException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.tests.integration.data.jdbc.contract.AbstractJdbcBatchContract;
import io.helidon.tests.integration.data.jdbc.support.TestConfigFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Executes the imperative batch contract against MySQL.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlImperativeBatchTest extends AbstractJdbcBatchContract {
    @Container
    static final MySQLContainer<?> MYSQL = MySqlImperativeTestSupport.MYSQL;

    @Override
    protected void beforeStartApplication() {
        TestConfigFactory.config(MySqlImperativeTestSupport.config());
    }
    @Override
    protected List<String> committedAfterPartialFailure() {
        return List.of("batch-before", "batch-after");
    }

    /**
     * A pool with one connection remains available after prepared capture and real driver failures.
     */
    @Test
    void preparedBatchesReturnTheOnlyPoolConnectionAfterSuccessAndFailure() {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(MYSQL.getJdbcUrl());
        configuration.setUsername(MYSQL.getUsername());
        configuration.setPassword(MYSQL.getPassword());
        configuration.setMaximumPoolSize(1);
        configuration.setConnectionTimeout(1_000);
        Config sql = Config.just(ConfigSources.classpath("configured-batches/reordered/application.yaml"));
        try (HikariDataSource pool = new HikariDataSource(configuration)) {
            JdbcClient client = JdbcClient.builder().dataSource(pool).build();
            var definition = client.prepare(sql.get("application.batches.insert").asString().get(), List.of("name", "email"));
            assertThat(definition.get().executeBatch(List.of("pooled-success"),
                    (bindings, name) -> bindings.bind("name", name).bindNull("email", JDBCType.VARCHAR)).entries().size(), is(1));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            assertThrows(IllegalArgumentException.class, () -> definition.get().executeBatch(List.of("capture"),
                    (bindings, name) -> bindings.bind("name", name).bind("name", name)));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            JdbcBatchException failure = assertThrows(JdbcBatchException.class,
                    () -> definition.get().executeBatch(List.of(0, 1), (bindings, index) -> {
                        if (index == 0) {
                            bindings.bind("name", "pooled-before-failure");
                        } else {
                            bindings.bindNull("name", JDBCType.VARCHAR);
                        }
                        bindings.bindNull("email", JDBCType.VARCHAR);
                    }));
            assertThat(failure.result().entries().size(), is(2));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            definition.get().executeBatch(List.of("pooled-reused"),
                    (bindings, name) -> bindings.bind("name", name).bindNull("email", JDBCType.VARCHAR));
            assertThat(client.create("SELECT COUNT(*) FROM CONTACT WHERE NAME = 'pooled-reused'").map(Long.class).one(), is(1L));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            assertThat(pool.isClosed(), is(false));
        }
    }

}
