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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import io.helidon.data.DataException;
import io.helidon.data.NoResultException;
import io.helidon.data.NonUniqueResultException;

/**
 * Maps and materializes query-shaped JDBC results.
 * <p>
 * The runner owns every resource. This handler validates the result channel,
 * applies row mapping, and checks cardinality.
 */
final class JdbcQueryHandler {

    /**
     * Executes a query and returns exactly one mapped value.
     *
     * @param scope runner-owned execution scope
     * @param mapper row mapper
     * @param <T> mapped type
     * @return exactly one value
     * @throws SQLException when JDBC execution fails
     */
    <T> T one(JdbcRunner.ExecutionScope scope, JdbcClient.RowMapper<T> mapper) throws SQLException {
        return one(scope, mapper, executeQuery(scope));
    }

    /**
     * Maps exactly one value from an already executed generated-key result.
     *
     * @param scope runner-owned execution scope
     * @param mapper row mapper
     * @param resultSet generated-key result set
     * @param <T> mapped type
     * @return exactly one value
     * @throws SQLException when row traversal fails
     */
    <T> T one(JdbcRunner.ExecutionScope scope,
              JdbcClient.RowMapper<T> mapper,
              ResultSet resultSet) throws SQLException {
        JdbcResultCursor<T> cursor = cursor(scope, mapper, resultSet);
        if (!cursor.hasNext()) {
            throw new NoResultException("JDBC operation returned no rows");
        }
        T value = cursor.next();
        if (cursor.hasNext()) {
            throw new NonUniqueResultException("JDBC operation returned more than one row");
        }
        return value;
    }

    /**
     * Executes a query and returns zero or one mapped value.
     *
     * @param scope runner-owned execution scope
     * @param mapper row mapper
     * @param <T> mapped type
     * @return optional value
     * @throws SQLException when JDBC execution fails
     */
    <T> Optional<T> optional(JdbcRunner.ExecutionScope scope,
                             JdbcClient.RowMapper<T> mapper) throws SQLException {
        return optional(scope, mapper, executeQuery(scope));
    }

    /**
     * Maps zero or one scalar value, treating one SQL {@code NULL} as an empty
     * optional.
     *
     * @param scope runner-owned execution scope
     * @param scalarType scalar type
     * @param <T> scalar type
     * @return optional scalar
     * @throws SQLException when JDBC execution fails
     */
    <T> Optional<T> optionalScalar(JdbcRunner.ExecutionScope scope, Class<T> scalarType) throws SQLException {
        return optionalScalar(scope, scalarType, executeQuery(scope));
    }

    /**
     * Maps a nullable scalar from an already executed row result.
     *
     * @param scope runner-owned execution scope
     * @param scalarType scalar type
     * @param resultSet current result set
     * @param <T> scalar type
     * @return optional scalar
     * @throws SQLException when row traversal fails
     */
    <T> Optional<T> optionalScalar(JdbcRunner.ExecutionScope scope,
                                   Class<T> scalarType,
                                   ResultSet resultSet) throws SQLException {
        Optional<Optional<T>> row = optional(scope,
                                             current -> current.optional(1, scalarType),
                                             resultSet);
        return row.flatMap(value -> value);
    }

    /**
     * Maps zero or one value from an already executed generated-key result.
     *
     * @param scope runner-owned execution scope
     * @param mapper row mapper
     * @param resultSet generated-key result set
     * @param <T> mapped type
     * @return optional value
     * @throws SQLException when row traversal fails
     */
    <T> Optional<T> optional(JdbcRunner.ExecutionScope scope,
                            JdbcClient.RowMapper<T> mapper,
                            ResultSet resultSet) throws SQLException {
        JdbcResultCursor<T> cursor = cursor(scope, mapper, resultSet);
        if (!cursor.hasNext()) {
            return Optional.empty();
        }
        T value = cursor.next();
        if (cursor.hasNext()) {
            throw new NonUniqueResultException("JDBC operation returned more than one row");
        }
        return Optional.of(value);
    }

    /**
     * Executes a query and materializes all mapped rows.
     *
     * @param scope runner-owned execution scope
     * @param mapper row mapper
     * @param <T> mapped type
     * @return materialized values
     * @throws SQLException when JDBC execution fails
     */
    <T> List<T> list(JdbcRunner.ExecutionScope scope,
                     JdbcClient.RowMapper<T> mapper) throws SQLException {
        return list(scope, mapper, executeQuery(scope));
    }

    /**
     * Materializes an already executed generated-key result.
     *
     * @param scope runner-owned execution scope
     * @param mapper row mapper
     * @param resultSet generated-key result set
     * @param <T> mapped type
     * @return materialized values
     * @throws SQLException when row traversal fails
     */
    <T> List<T> list(JdbcRunner.ExecutionScope scope,
                     JdbcClient.RowMapper<T> mapper,
                     ResultSet resultSet) throws SQLException {
        JdbcResultCursor<T> cursor = cursor(scope, mapper, resultSet);
        List<T> values = new ArrayList<>();
        while (cursor.hasNext()) {
            values.add(cursor.next());
        }
        return List.copyOf(values);
    }

    /**
     * Executes the primary query channel and obtains its result set.
     *
     * @param scope runner-owned execution scope
     * @return query result set
     * @throws SQLException when execution or result advancement fails
     */
    private static ResultSet executeQuery(JdbcRunner.ExecutionScope scope) throws SQLException {
        scope.require(JdbcPreparationPlan.ResultKind.QUERY);
        boolean resultSetAvailable = scope.statement().execute();
        if (!resultSetAvailable) {
            boolean unexpected = scope.drainFromCurrent(false);
            throw scope.unexpectedResult(unexpected);
        }
        return scope.statement().getResultSet();
    }

    /**
     * Creates the private cursor shared by all materialized terminals.
     *
     * @param scope runner-owned execution scope
     * @param mapper row mapper
     * @param resultSet provider-owned result set
     * @param <T> mapped type
     * @return cursor
     * @throws SQLException when result metadata cannot be read
     */
    private static <T> JdbcResultCursor<T> cursor(JdbcRunner.ExecutionScope scope,
                                                  JdbcClient.RowMapper<T> mapper,
                                                  ResultSet resultSet) throws SQLException {
        if (resultSet == null) {
            throw new DataException("JDBC " + scope.operation().preparationPlan().resultKind()
                                            + " did not provide an expected result set");
        }
        scope.resultSet(resultSet);
        JdbcColumnLayout columns = JdbcColumnLayout.create(resultSet.getMetaData(), scope.operation());
        return new JdbcResultCursor<>(scope, resultSet, columns, mapper);
    }

    /**
     * Small internal cursor that confines a row view to one mapper call.
     *
     * @param <T> mapped type
     */
    private static final class JdbcResultCursor<T> {
        /** Runner scope used to validate trailing result channels. */
        private final JdbcRunner.ExecutionScope scope;
        /** Current result set. */
        private final ResultSet resultSet;
        /** Application or generated mapper. */
        private final JdbcClient.RowMapper<T> mapper;
        /** Scoped row view reused for this result set. */
        private final JdbcRow row;
        /** Whether a row has already been advanced and awaits mapping. */
        private boolean ready;
        /** Whether JDBC exhaustion and trailing-result validation have completed. */
        private boolean exhausted;

        /**
         * Creates a cursor over one provider-owned result set.
         *
         * @param scope runner scope
         * @param resultSet result set
         * @param columns cached column layout
         * @param mapper row mapper
         */
        private JdbcResultCursor(JdbcRunner.ExecutionScope scope,
                                 ResultSet resultSet,
                                 JdbcColumnLayout columns,
                                 JdbcClient.RowMapper<T> mapper) {
            this.scope = scope;
            this.resultSet = resultSet;
            this.mapper = mapper;
            this.row = new JdbcRow(resultSet, columns, scope.operation());
        }

        /**
         * Advances lazily and validates that no result channel follows the
         * accepted one.
         *
         * @return whether a row is ready
         * @throws SQLException when result advancement fails
         */
        private boolean hasNext() throws SQLException {
            if (ready) {
                return true;
            }
            if (exhausted) {
                return false;
            }
            ready = resultSet.next();
            if (!ready) {
                exhausted = true;
                scope.rejectFollowingResults();
            }
            return ready;
        }

        /**
         * Maps the row currently prepared by {@link #hasNext()}.
         *
         * @return mapped value
         * @throws SQLException when lazy advancement fails
         */
        private T next() throws SQLException {
            if (!hasNext()) {
                throw new NoSuchElementException("No more JDBC rows");
            }
            ready = false;
            row.activate();
            try {
                T value = mapper.map(row);
                if (value == null) {
                    throw new DataException("JDBC row mapper returned null");
                }
                return value;
            } finally {
                row.deactivate();
            }
        }
    }
}
