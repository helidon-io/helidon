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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

/**
 * Executes a probe with JDBC resources confined to the calling thread.
 * Reports success only after traversal and resource release complete.
 */
final class DataSourceHealthProbe {
    private DataSourceHealthProbe() {
    }

    static boolean execute(DataSource dataSource, String query, int timeout) {
        DataSourceProbeLease lease = null;
        PreparedStatement statement = null;
        ResultSet rows = null;
        Throwable failure = null;
        try {
            lease = DataSourceProbeLease.acquire(dataSource);
            statement = lease.connection().prepareStatement(query);
            statement.setQueryTimeout(timeout);
            boolean rowChannel = statement.execute();
            boolean valid = rowChannel;
            boolean first = true;
            // Finish processing the results before releasing the connection, even after finding an unexpected channel.
            while (true) {
                if (rowChannel) {
                    if (!first) {
                        valid = false;
                    }
                    rows = statement.getResultSet();
                    if (rows == null) {
                        throw DataSourceProbeFailures.conditionFailure("The statement reported a result set but returned none.");
                    }
                    while (rows.next()) {
                        // Validate traversal without inspecting column values.
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
                throw DataSourceProbeFailures.conditionFailure("The statement returned an unexpected result channel.");
            }
        } catch (SQLException | RuntimeException | Error processingFailure) {
            failure = DataSourceProbeFailures.mergeInfrastructure(null, "executing an independent probe", processingFailure);
        } finally {
            if (rows != null) {
                try {
                    rows.close();
                } catch (SQLException | RuntimeException | Error closeFailure) {
                    lease.unsafe();
                    failure = DataSourceProbeFailures.mergeInfrastructure(failure, "closing probe rows", closeFailure);
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException | RuntimeException | Error closeFailure) {
                    lease.unsafe();
                    failure = DataSourceProbeFailures.mergeInfrastructure(failure, "closing a probe statement", closeFailure);
                }
            }
            if (lease != null) {
                try {
                    lease.close();
                } catch (RuntimeException | Error closeFailure) {
                    failure = DataSourceProbeFailures.mergeInfrastructure(failure, "closing a probe lease", closeFailure);
                }
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return failure == null;
    }
}
