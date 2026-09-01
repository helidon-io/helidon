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

import com.zaxxer.hikari.HikariDataSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Owns the one-connection H2 pool and independent disruption controller.
 */
public final class H2ChaosDisruptionFixture implements ChaosDisruptionFixture {
    private final HikariDataSource dataSource;
    private final H2ChaosDisruptionController controller;

    /**
     * Creates an H2 physical-session disruption fixture.
     *
     * @param dataSource application pool
     * @throws SQLException when the control connection cannot be opened
     */
    public H2ChaosDisruptionFixture(HikariDataSource dataSource) throws SQLException {
        this.dataSource = dataSource;
        controller = new H2ChaosDisruptionController();
    }

    @Override
    public ChaosDisruptionController controller() {
        return controller;
    }

    @Override
    public void assertPoolIdle() {
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    @Override
    public void close() throws Exception {
        try {
            controller.close();
        } finally {
            dataSource.close();
        }
    }
}
