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
package io.helidon.data.jdbc.tests.chaos.support;

import java.sql.SQLException;

import io.helidon.data.jdbc.tests.chaos.contract.AbstractJdbcConnectionLossChaosContract;

import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Supplies MySQL pool and native session-control fixtures for physical connection loss tests.
 */
public abstract class AbstractMySqlConnectionLossChaosTest extends AbstractJdbcConnectionLossChaosContract {
    @Container
    protected static final MySQLContainer<?> MYSQL = ChaosMySqlDatabase.container();

    @Override
    protected ChaosDisruptionFixture beforeStartApplication() throws SQLException {
        ChaosTestConfigFactory.config(ChaosMySqlDatabase.config(MYSQL));
        HikariDataSource dataSource = ChaosMySqlDatabase.dataSource(MYSQL);
        ChaosTestDataSourceFactory.dataSource(ChaosMySqlDatabase.HIKARI_SOURCE_NAME, dataSource);
        ChaosTestConfigFactory.config(ChaosMySqlDatabase.hikariConfig());
        return new ChaosPoolDisruptionFixture(
                new MySqlChaosDisruptionController(MYSQL),
                () -> assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0)),
                dataSource);
    }
}
