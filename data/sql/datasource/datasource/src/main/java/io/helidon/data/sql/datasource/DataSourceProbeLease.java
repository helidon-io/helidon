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
            throw DataSourceProbeFailures.translateFailure("acquiring a probe connection", failure);
        }
        try {
            if (!connection.getAutoCommit()) {
                throw DataSourceProbeFailures.safeException("An independent probe requires automatic commit mode.");
            }
            return new DataSourceProbeLease(connection);
        } catch (SQLException | RuntimeException | Error failure) {
            throw DataSourceProbeFailures.translateFailure("validating a probe connection",
                    invalidate(connection, failure));
        }
    }

    Connection connection() {
        if (closed) {
            throw new IllegalStateException("The probe connection lease is closed.");
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
        try {
            if (!connection.getAutoCommit()) {
                throw DataSourceProbeFailures.safeException("The probe connection must retain automatic commit mode.");
            }
            if (!unsafe) {
                connection.close();
                return;
            }
        } catch (SQLException | RuntimeException | Error inspectionFailure) {
            failure = inspectionFailure;
        }
        if (failure == null) {
            failure = DataSourceProbeFailures.safeException("The probe connection cannot be safely reused.");
        }
        throw DataSourceProbeFailures.translateFailure("releasing a probe connection",
                invalidate(connection, failure));
    }

    private static Throwable invalidate(Connection connection, Throwable failure) {
        failure = DataSourceProbeFailures.mergeInfrastructure(null, "invalidating an unsafe connection", failure);
        try {
            connection.abort(ABORT_EXECUTOR);
        } catch (SQLException | RuntimeException | Error abortFailure) {
            failure = DataSourceProbeFailures.mergeInfrastructure(failure, "aborting a connection", abortFailure);
        }
        try {
            connection.close();
        } catch (SQLException | RuntimeException | Error closeFailure) {
            failure = DataSourceProbeFailures.mergeInfrastructure(failure, "closing an invalidated connection", closeFailure);
        }
        return failure;
    }
}
