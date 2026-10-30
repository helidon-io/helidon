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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

/**
 * Executes one invocation using resources confined to the invocation thread.
 */
final class JdbcHealthProbe {
    private JdbcHealthProbe() {
    }

    static boolean execute(DataSource dataSource, String query, int timeout) {
        JdbcProbeLease lease = null;
        PreparedStatement statement = null;
        ResultSet rows = null;
        Throwable failure = null;
        try {
            lease = JdbcProbeLease.acquire(dataSource);
            statement = lease.connection().prepareStatement(query);
            statement.setQueryTimeout(timeout);
            boolean rowChannel = statement.execute();
            boolean valid = rowChannel;
            boolean first = true;
            while (true) {
                if (rowChannel) {
                    if (!first) {
                        valid = false;
                    }
                    rows = statement.getResultSet();
                    if (rows == null) {
                        throw JdbcExceptionTranslator.safeException("The probe did not provide its result set.");
                    }
                    while (rows.next()) {
                        // Traversal deliberately neither reads nor retains column values.
                    }
                    try {
                        rows.close();
                    } catch (SQLException | RuntimeException | Error closeFailure) {
                        lease.unsafe();
                        throw closeFailure;
                    }
                    rows = null;
                } else if (statement.getUpdateCount() == -1) {
                    break;
                } else {
                    valid = false;
                }
                first = false;
                rowChannel = statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
            }
            if (!valid) {
                throw JdbcExceptionTranslator.safeException("The probe returned an unexpected result channel.");
            }
        } catch (SQLException | RuntimeException | Error processingFailure) {
            failure = JdbcExceptionTranslator.mergeInfrastructure(null, "executing an independent probe", processingFailure);
        } finally {
            if (rows != null) {
                try {
                    rows.close();
                } catch (SQLException | RuntimeException | Error closeFailure) {
                    lease.unsafe();
                    failure = JdbcExceptionTranslator.mergeInfrastructure(failure, "closing probe rows", closeFailure);
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException | RuntimeException | Error closeFailure) {
                    lease.unsafe();
                    failure = JdbcExceptionTranslator.mergeInfrastructure(failure, "closing a probe statement", closeFailure);
                }
            }
            if (lease != null) {
                try {
                    lease.close();
                } catch (RuntimeException | Error closeFailure) {
                    failure = JdbcExceptionTranslator.mergeInfrastructure(failure, "closing a probe lease", closeFailure);
                }
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return failure == null;
    }
}
