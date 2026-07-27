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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Handles update counts and materialized generated-key results.
 */
final class JdbcUpdateHandler {
    /** Shared materialized row handling for generated keys. */
    private final JdbcQueryHandler queryHandler;

    /**
     * Creates an update handler.
     *
     * @param queryHandler row materialization support
     */
    JdbcUpdateHandler(JdbcQueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }

    /**
     * Executes an ordinary update and returns its count.
     *
     * @param scope runner-owned execution scope
     * @return update count
     * @throws SQLException when JDBC execution fails
     */
    long execute(JdbcRunner.ExecutionScope scope) throws SQLException {
        scope.require(JdbcPreparationPlan.ResultKind.UPDATE);
        boolean resultSetAvailable = scope.statement().execute();
        if (resultSetAvailable) {
            boolean unexpected = scope.drainFromCurrent(true);
            throw scope.unexpectedResult(unexpected);
        }
        long count = scope.largeUpdateCount();
        if (count < 0) {
            throw scope.unexpectedResult(false);
        }
        scope.rejectFollowingResults();
        return count;
    }

    /**
     * Executes an update and maps exactly one generated key.
     *
     * @param scope runner-owned execution scope
     * @param mapper key mapper
     * @param <T> mapped type
     * @return generated key
     * @throws SQLException when JDBC processing fails
     */
    <T> T one(JdbcRunner.ExecutionScope scope, JdbcClient.RowMapper<T> mapper) throws SQLException {
        return queryHandler.one(scope, mapper, executeForKeys(scope));
    }

    /**
     * Executes an update and maps zero or one generated key.
     *
     * @param scope runner-owned execution scope
     * @param mapper key mapper
     * @param <T> mapped type
     * @return optional generated key
     * @throws SQLException when JDBC processing fails
     */
    <T> Optional<T> optional(JdbcRunner.ExecutionScope scope,
                             JdbcClient.RowMapper<T> mapper) throws SQLException {
        return queryHandler.optional(scope, mapper, executeForKeys(scope));
    }

    /**
     * Executes an update and maps a nullable scalar generated key.
     *
     * @param scope runner-owned execution scope
     * @param scalarType scalar key type
     * @param <T> scalar type
     * @return optional generated key
     * @throws SQLException when JDBC processing fails
     */
    <T> Optional<T> optionalScalar(JdbcRunner.ExecutionScope scope, Class<T> scalarType) throws SQLException {
        return queryHandler.optionalScalar(scope, scalarType, executeForKeys(scope));
    }

    /**
     * Executes an update and materializes all generated keys.
     *
     * @param scope runner-owned execution scope
     * @param mapper key mapper
     * @param <T> mapped type
     * @return generated keys
     * @throws SQLException when JDBC processing fails
     */
    <T> List<T> list(JdbcRunner.ExecutionScope scope,
                     JdbcClient.RowMapper<T> mapper) throws SQLException {
        return queryHandler.list(scope, mapper, executeForKeys(scope));
    }

    /**
     * Executes the update channel and obtains its generated-key result set.
     *
     * @param scope runner-owned execution scope
     * @return generated-key result set
     * @throws SQLException when execution or key retrieval fails
     */
    private static ResultSet executeForKeys(JdbcRunner.ExecutionScope scope) throws SQLException {
        scope.require(JdbcPreparationPlan.ResultKind.GENERATED_KEYS);
        boolean resultSetAvailable = scope.statement().execute();
        if (resultSetAvailable) {
            boolean unexpected = scope.drainFromCurrent(true);
            throw scope.unexpectedResult(unexpected);
        }
        if (scope.largeUpdateCount() < 0) {
            throw scope.unexpectedResult(false);
        }
        return scope.statement().getGeneratedKeys();
    }
}
