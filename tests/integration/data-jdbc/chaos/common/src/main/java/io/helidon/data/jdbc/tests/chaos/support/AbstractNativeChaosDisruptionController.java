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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Coordinates a gate lock and bounded session-state observation through one native database control connection.
 */
public abstract class AbstractNativeChaosDisruptionController implements ChaosDisruptionController {
    private final Connection control;
    private boolean gateLocked;

    /**
     * Creates a controller around an independent administrative connection.
     *
     * @param control administrative connection
     */
    protected AbstractNativeChaosDisruptionController(Connection control) {
        this.control = control;
    }

    @Override
    public final AutoCloseable lockGate() throws SQLException {
        if (gateLocked) {
            throw new IllegalStateException("The JDBC chaos gate is already locked.");
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
    public final void awaitGateWait(long sessionId, Duration timeout) throws SQLException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isWaitingOnGate(sessionId)) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while observing the JDBC chaos gate wait.");
            }
        }
        throw new SQLException("The target database session did not reach the chaos gate before the observation deadline.");
    }

    @Override
    public final void terminateSession(long sessionId) throws SQLException {
        terminate(sessionId);
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

    /**
     * Returns the independent administrative connection.
     *
     * @return control connection
     */
    protected final Connection control() {
        return control;
    }

    /**
     * Determines whether the selected database session is waiting on the chaos gate.
     *
     * @param sessionId database session identifier
     * @return whether the target is blocked on the gate
     * @throws SQLException when session state cannot be inspected
     */
    protected abstract boolean isWaitingOnGate(long sessionId) throws SQLException;

    /**
     * Terminates the selected database session.
     *
     * @param sessionId database session identifier
     * @throws SQLException when session termination fails
     */
    protected abstract void terminate(long sessionId) throws SQLException;
}
