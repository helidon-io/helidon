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
package io.helidon.data.sql.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;

import javax.sql.DataSource;

/**
 * Owns an independent connection without changing its transaction state or connection mode.
 */
final class DataSourceProbeLease implements AutoCloseable {
    private static final Executor ABORT_EXECUTOR = Runnable::run;

    private final Connection connection;
    private boolean unsafe;
    private boolean closed;

    private DataSourceProbeLease(Connection connection) {
        this.connection = connection;
    }

    static DataSourceProbeLease acquire(DataSource dataSource) {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException | RuntimeException | Error failure) {
            throw DataSourceProbeFailures.translateFailure("acquiring a connection", failure);
        }
        try {
            // A connection with auto-commit disabled may already have pending work.
            if (!connection.getAutoCommit()) {
                throw DataSourceProbeFailures.conditionFailure("The acquired connection has auto-commit disabled.");
            }
            return new DataSourceProbeLease(connection);
        } catch (SQLException | RuntimeException | Error failure) {
            Throwable safe = DataSourceProbeFailures.mergeInfrastructure(null, "checking auto-commit after acquisition", failure);
            throw DataSourceProbeFailures.translateFailure("validating a connection",
                    invalidate(connection, safe));
        }
    }

    Connection connection() {
        if (closed) {
            throw new IllegalStateException("The health check connection lease is closed.");
        }
        return connection;
    }

    void unsafe() {
        unsafe = true;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Throwable failure = null;
        String operation = "checking auto-commit before release";
        try {
            // The mode must still be safe before returning the connection through the normal close path.
            if (!connection.getAutoCommit()) {
                throw DataSourceProbeFailures.conditionFailure("The connection has auto-commit disabled before release.");
            }
            if (!unsafe) {
                operation = "closing a probe connection";
                connection.close();
                return;
            }
        } catch (SQLException | RuntimeException | Error releaseFailure) {
            failure = DataSourceProbeFailures.mergeInfrastructure(null, operation, releaseFailure);
        }
        if (failure == null) {
            failure = DataSourceProbeFailures.conditionFailure("An earlier cleanup failure made the connection unsafe to reuse.");
        }
        throw DataSourceProbeFailures.translateFailure("releasing a connection",
                invalidate(connection, failure));
    }

    private static Throwable invalidate(Connection connection, Throwable failure) {
        try {
            connection.abort(ABORT_EXECUTOR);
        } catch (SQLException | RuntimeException | Error abortFailure) {
            failure = DataSourceProbeFailures.mergeInfrastructure(failure, "aborting a connection", abortFailure);
        }
        // Attempt to release the borrowed handle even when abort fails.
        try {
            connection.close();
        } catch (SQLException | RuntimeException | Error closeFailure) {
            failure = DataSourceProbeFailures.mergeInfrastructure(failure, "closing an invalidated connection", closeFailure);
        }
        return failure;
    }
}
