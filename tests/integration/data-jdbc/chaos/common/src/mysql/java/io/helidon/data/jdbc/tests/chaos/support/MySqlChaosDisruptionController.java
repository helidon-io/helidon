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

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.testcontainers.containers.MySQLContainer;

/**
 * Observes and terminates MySQL application sessions through a direct control connection.
 */
public final class MySqlChaosDisruptionController extends AbstractNativeChaosDisruptionController {
    /**
     * Opens a MySQL control connection which bypasses the application pool.
     *
     * @param container running MySQL container
     * @throws SQLException when the control connection cannot be opened
     */
    public MySqlChaosDisruptionController(MySQLContainer<?> container) throws SQLException {
        super(DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword()));
    }

    @Override
    protected boolean isWaitingOnGate(long sessionId) throws SQLException {
        try (PreparedStatement statement = control().prepareStatement("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.PROCESSLIST
                WHERE ID = ?
                  AND INFO LIKE '%CHAOS_GATE%'
                  AND STATE IS NOT NULL
                """)) {
            statement.setLong(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1) == 1L;
            }
        }
    }

    @Override
    protected void terminate(long sessionId) throws SQLException {
        try (Statement statement = control().createStatement()) {
            statement.execute("KILL CONNECTION " + sessionId);
        }
    }
}
