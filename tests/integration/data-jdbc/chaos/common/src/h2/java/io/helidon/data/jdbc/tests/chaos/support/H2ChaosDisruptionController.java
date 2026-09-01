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
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Coordinates a real H2 session wait and terminates that session through an independent connection.
 */
public final class H2ChaosDisruptionController implements ChaosDisruptionController {
    private static final String BLOCKED_SESSION = """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.SESSIONS
            WHERE SESSION_ID = ?
              AND BLOCKER_ID = SESSION_ID()
              AND EXECUTING_STATEMENT LIKE '%UPDATE CHAOS_GATE%'
            """;

    private final Connection control;
    private boolean gateLocked;

    /**
     * Opens an administrative connection to the H2 chaos database.
     *
     * @throws SQLException when the control connection cannot be opened
     */
    public H2ChaosDisruptionController() throws SQLException {
        control = DriverManager.getConnection(ChaosH2Database.URL);
    }

    @Override
    public AutoCloseable lockGate() throws SQLException {
        if (gateLocked) {
            throw new IllegalStateException("The H2 chaos gate is already locked.");
        }
        control.setAutoCommit(false);
        try (PreparedStatement statement = control.prepareStatement(
                "UPDATE CHAOS_GATE SET GATE_VALUE = GATE_VALUE + 1 WHERE ID = 1")) {
            statement.executeUpdate();
        }
        gateLocked = true;
        return () -> {
            if (gateLocked) {
                control.rollback();
                control.setAutoCommit(true);
                gateLocked = false;
            }
        };
    }

    @Override
    public void awaitGateWait(long sessionId, Duration timeout) throws SQLException {
        long deadline = System.nanoTime() + timeout.toNanos();
        try (PreparedStatement statement = control.prepareStatement(BLOCKED_SESSION)) {
            statement.setLong(1, sessionId);
            while (System.nanoTime() < deadline) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    if (resultSet.getLong(1) == 1L) {
                        return;
                    }
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while observing the H2 chaos gate wait.");
                }
            }
        }
        throw new SQLException("The target H2 session did not reach the chaos gate before the observation deadline.");
    }

    @Override
    public void terminateSession(long sessionId) throws SQLException {
        try (PreparedStatement statement = control.prepareStatement("SELECT ABORT_SESSION(?)")) {
            statement.setLong(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (!resultSet.getBoolean(1)) {
                    throw new SQLException("H2 did not terminate the selected chaos session.");
                }
            }
        }
    }

    @Override
    public void close() throws SQLException {
        try {
            if (gateLocked) {
                control.rollback();
                gateLocked = false;
            }
        } finally {
            control.close();
        }
    }
}
