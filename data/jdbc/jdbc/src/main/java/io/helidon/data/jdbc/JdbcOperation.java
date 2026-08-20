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

import java.sql.Date;
import java.sql.JDBCType;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Objects;

import io.helidon.data.jdbc.lexical.JdbcSqlLexicalProfile;
import io.helidon.data.jdbc.lexical.JdbcSqlScanHandler;
import io.helidon.data.jdbc.lexical.JdbcSqlScanner;

/**
 * Immutable execution snapshot created immediately before a terminal operation.
 *
 * <p>The fluent stages are mutable while an application or generated
 * repository assembles a statement. This object freezes the SQL text, ordered
 * bindings and preparation contract before the runner borrows any
 * JDBC resource. The runner can therefore execute a stable operation even
 * though the original statement stage is no longer accessible for mutation.</p>
 */
final class JdbcOperation {

    // Named markers have already been rewritten before this operation is created.
    private final String sql;

    // The statement stage clones this array before construction.
    private final Bind[] binds;

    private final JdbcPreparationPlan preparationPlan;

    /**
     * Creates an immutable operation snapshot.
     *
     * @param sql SQL to execute
     * @param binds ordered bind snapshots
     * @param preparationPlan statement result contract
     */
    JdbcOperation(String sql,
                  Bind[] binds,
                  JdbcPreparationPlan preparationPlan) {
        this.sql = sql;
        this.binds = binds;
        this.preparationPlan = Objects.requireNonNull(preparationPlan, "The preparation plan must not be null.");
    }

    /**
     * Counts positional markers for a statement stage.
     *
     * <p>The scanner is deliberately lexical rather than a SQL parser. The
     * portable profile protects unambiguous quoted values, quoted identifiers,
     * and comments while treating every question mark as a bind marker.
     * Declarative named markers have already been rewritten by the annotation
     * processor. A runtime named marker is rejected because this client stage
     * accepts positional JDBC SQL.</p>
     *
     * @param sql SQL text to scan
     * @return number of positional bind markers
     */
    static int parameterCount(String sql) {
        Objects.requireNonNull(sql, "The SQL statement must not be null.");
        if (sql.isBlank()) {
            throw new IllegalArgumentException("The SQL statement must not be blank.");
        }
        MarkerCounter counter = new MarkerCounter();
        JdbcSqlScanner.scan(sql, JdbcSqlLexicalProfile.PORTABLE, counter);
        return counter.count;
    }

    /**
     * Describes the operation without exposing bound values.
     *
     * @return operation kind and bind count
     */
    @Override
    public String toString() {
        return "JdbcOperation[" + preparationPlan.resultKind() + ", parameters=" + binds.length + "]";
    }

    /**
     * Returns the operation SQL.
     *
     * @return SQL text
     */
    String sql() {
        return sql;
    }

    /**
     * Returns the ordered bind snapshots.
     *
     * @return bind array owned by this operation
     */
    Bind[] binds() {
        return binds;
    }

    /**
     * Returns the result/preparation contract selected by the terminal.
     *
     * @return preparation plan
     */
    JdbcPreparationPlan preparationPlan() {
        return preparationPlan;
    }

    /**
     * Immutable value and optional JDBC type for one positional parameter.
     *
     * <p>A {@code null} entry in the statement's bind array means that the
     * position was never assigned. A {@code Bind} whose value is null is a
     * deliberate typed SQL NULL and remains distinguishable from an unassigned
     * position.</p>
     */
    static final class Bind {

        // A null value is valid only when an explicit JDBC type is present.
        private final Object value;

        private final JDBCType type;

        /**
         * Creates a bind snapshot.
         *
         * @param value value to bind, possibly null when {@code type} is explicit
         * @param type explicit JDBC type, or null for an untyped value
         */
        Bind(Object value, JDBCType type) {
            this.value = snapshot(value);
            this.type = type;
        }

        /**
         * Returns the value to bind.
         *
         * @return value, possibly null
         */
        Object value() {
            return value;
        }

        /**
         * Returns the explicit JDBC type.
         *
         * @return JDBC type, or null when untyped
         */
        JDBCType type() {
            return type;
        }

        /**
         * Tests whether the JDBC type was explicitly supplied.
         *
         * @return true for a typed bind
         */
        boolean typed() {
            return type != null;
        }

        /**
         * Captures a stable bind value without copying immutable scalar types.
         *
         * <p>The supported mutable scalar types are copied when the bind is
         * assembled, before a terminal operation can acquire JDBC resources.
         * The application can therefore mutate its original value after
         * {@code bind} returns without changing the operation observed by the
         * runner.</p>
         *
         * @param value application bind value, possibly null for a typed SQL NULL
         * @return captured value
         */
        private static Object snapshot(Object value) {
            if (value instanceof byte[] bytes) {
                return bytes.clone();
            }
            // Timestamp extends Date, so preserve its nanoseconds before the Date branch.
            if (value instanceof Timestamp timestamp) {
                Timestamp copy = new Timestamp(timestamp.getTime());
                copy.setNanos(timestamp.getNanos());
                return copy;
            }
            if (value instanceof Time time) {
                return new Time(time.getTime());
            }
            if (value instanceof Date date) {
                return new Date(date.getTime());
            }
            return value;
        }
    }

    /**
     * Counts positional marker events and rejects named marker events for the
     * imperative JDBC client contract.
     */
    private static final class MarkerCounter implements JdbcSqlScanHandler {

        private int count;

        @Override
        public void namedMarker(int start, int end) {
            throw new IllegalArgumentException(
                    "JdbcClient SQL accepts only positional '?' markers. A named marker was found for lexical "
                            + "profile " + JdbcSqlLexicalProfile.PORTABLE + " at offset " + start + ".");
        }

        @Override
        public void positionalMarker(int offset) {
            count++;
        }
    }
}
