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

import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * Observes and terminates PostgreSQL application sessions through a direct control connection.
 */
public final class PostgreSqlChaosDisruptionController extends AbstractNativeChaosDisruptionController {
    /**
     * Opens a PostgreSQL control connection which bypasses the application pool.
     *
     * @param container running PostgreSQL container
     * @throws SQLException when the control connection cannot be opened
     */
    public PostgreSqlChaosDisruptionController(JdbcDatabaseContainer<?> container) throws SQLException {
        super(DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword()));
    }

    @Override
    protected boolean isWaitingOnGate(long sessionId) throws SQLException {
        try (PreparedStatement statement = control().prepareStatement("""
                SELECT COUNT(*)
                FROM pg_stat_activity
                WHERE pid = ?
                  AND wait_event_type = 'Lock'
                  AND query LIKE '%CHAOS_GATE%'
                """)) {
            statement.setInt(1, Math.toIntExact(sessionId));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1) == 1L;
            }
        }
    }

    @Override
    protected void terminate(long sessionId) throws SQLException {
        try (PreparedStatement statement = control().prepareStatement("SELECT pg_terminate_backend(?)")) {
            statement.setInt(1, Math.toIntExact(sessionId));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (!resultSet.getBoolean(1)) {
                    throw new SQLException("PostgreSQL did not terminate the selected chaos session.");
                }
            }
        }
    }
}
