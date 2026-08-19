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

import io.helidon.data.jdbc.tests.chaos.contract.AbstractJdbcChaosSmokeContract;

import com.zaxxer.hikari.HikariDataSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Supplies H2-specific configuration and one-connection pool assertions for chaos smoke tests.
 */
public abstract class AbstractH2ChaosSmokeTest extends AbstractJdbcChaosSmokeContract {
    private HikariDataSource dataSource;

    @Override
    protected void beforeStartApplication() {
        ChaosTestConfigFactory.config(ChaosH2Database.config());
    }

    @Override
    protected void assertPoolIdle() {
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    @Override
    protected AutoCloseable beforeStartPooledApplication() {
        dataSource = ChaosH2Database.dataSource();
        ChaosTestDataSourceFactory.dataSource(ChaosH2Database.HIKARI_SOURCE_NAME, dataSource);
        ChaosTestConfigFactory.config(ChaosH2Database.hikariConfig());
        return () -> {
            dataSource.close();
            dataSource = null;
        };
    }
}
