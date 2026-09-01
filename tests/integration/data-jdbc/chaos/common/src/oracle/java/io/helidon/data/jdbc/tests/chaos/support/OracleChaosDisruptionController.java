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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.testcontainers.containers.GenericContainer;

/**
 * Observes and terminates Oracle application sessions through independent SYSTEM connections.
 */
public final class OracleChaosDisruptionController extends AbstractNativeChaosDisruptionController {
    private final Connection administrative;

    /**
     * Opens separate Oracle connections for the gate transaction and session termination.
     *
     * @param container running Oracle Database container
     * @throws SQLException when either control connection cannot be opened
     */
    public OracleChaosDisruptionController(GenericContainer<?> container) throws SQLException {
        super(openControlConnection(container));
        Connection administrative = null;
        try {
            administrative = openControlConnection(container);
            try (Statement statement = control().createStatement()) {
                statement.execute("ALTER SESSION SET CURRENT_SCHEMA = " + ChaosOracleDatabase.activeUsername());
            }
            this.administrative = administrative;
        } catch (SQLException | RuntimeException | Error failure) {
            if (administrative != null) {
                try {
                    administrative.close();
                } catch (SQLException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            try {
                super.close();
            } catch (SQLException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public void close() throws SQLException {
        try {
            super.close();
        } finally {
            administrative.close();
        }
    }

    @Override
    protected boolean isWaitingOnGate(long sessionId) throws SQLException {
        try (PreparedStatement statement = administrative.prepareStatement("""
                SELECT COUNT(*)
                FROM V$SESSION
                WHERE AUDSID = ?
                  AND BLOCKING_SESSION IS NOT NULL
                  AND STATUS = 'ACTIVE'
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
        long sid;
        long serial;
        try (PreparedStatement statement = administrative.prepareStatement(
                "SELECT SID, SERIAL# FROM V$SESSION WHERE AUDSID = ?")) {
            statement.setLong(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("The selected Oracle chaos session no longer exists.");
                }
                sid = resultSet.getLong(1);
                serial = resultSet.getLong(2);
            }
        }
        try (Statement statement = administrative.createStatement()) {
            try {
                statement.execute("ALTER SYSTEM KILL SESSION '" + sid + "," + serial + "' IMMEDIATE");
            } catch (SQLException failure) {
                // ORA-00031 confirms the live session was marked for asynchronous termination.
                if (failure.getErrorCode() != 31) {
                    throw failure;
                }
            }
        }
    }

    private static Connection openControlConnection(GenericContainer<?> container) throws SQLException {
        return DriverManager.getConnection(ChaosOracleDatabase.controlJdbcUrl(container),
                                           "system",
                                           ChaosOracleDatabase.controlPassword());
    }
}
