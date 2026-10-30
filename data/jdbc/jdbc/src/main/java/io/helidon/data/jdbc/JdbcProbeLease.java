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
package io.helidon.data.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * Owns one independent acquisition without transaction recovery or mode changes.
 */
final class JdbcProbeLease implements JdbcConnectionLease {
    private final Connection connection;
    private boolean unsafe;
    private boolean closed;

    private JdbcProbeLease(Connection connection) {
        this.connection = connection;
    }

    static JdbcProbeLease acquire(DataSource dataSource) {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException | RuntimeException | Error failure) {
            throw JdbcExceptionTranslator.translateFailure("probe connection acquisition", failure);
        }
        try {
            if (!connection.getAutoCommit()) {
                throw JdbcExceptionTranslator.safeException("An independent probe requires automatic commit mode.");
            }
            return new JdbcProbeLease(connection);
        } catch (SQLException | RuntimeException | Error failure) {
            throw JdbcExceptionTranslator.translateFailure("probe connection validation",
                    JdbcConnectionInvalidator.invalidateInfrastructure(connection, failure));
        }
    }

    @Override
    public Connection connection() {
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
                throw JdbcExceptionTranslator.safeException("The probe connection must retain automatic commit mode.");
            }
            if (!unsafe) {
                connection.close();
                return;
            }
        } catch (SQLException | RuntimeException | Error inspectionFailure) {
            failure = inspectionFailure;
        }
        if (failure == null) {
            failure = JdbcExceptionTranslator.safeException("The probe connection cannot be safely reused.");
        }
        throw JdbcExceptionTranslator.translateFailure("probe connection release",
                JdbcConnectionInvalidator.invalidateInfrastructure(connection, failure));
    }
}
