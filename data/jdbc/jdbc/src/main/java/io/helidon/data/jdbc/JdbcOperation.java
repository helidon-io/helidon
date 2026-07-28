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
        this.preparationPlan = Objects.requireNonNull(preparationPlan, "Preparation plan must not be null");
    }

    /**
     * Counts positional markers for a statement stage.
     *
     * <p>The scanner is deliberately lexical rather than a SQL parser. It
     * ignores question marks in quoted values, quoted identifiers, comments,
     * and common vendor operators. Declarative named markers have already been
     * rewritten by the annotation processor. A runtime named marker is
     * rejected because this client stage accepts positional JDBC SQL.</p>
     *
     * @param sql SQL text to scan
     * @return number of positional bind markers
     */
    static int parameterCount(String sql) {
        Objects.requireNonNull(sql, "SQL must not be null");
        if (sql.isBlank()) {
            throw new IllegalArgumentException("SQL must not be blank");
        }
        return MarkerScanner.count(sql);
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
     * Describes the operation without exposing bound values.
     *
     * @return operation kind and bind count
     */
    @Override
    public String toString() {
        return "JdbcOperation[" + preparationPlan.resultKind() + ", parameters=" + binds.length + "]";
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
    }

    /**
     * Lexically counts positional markers without interpreting SQL grammar.
     *
     * <p>Quoted text, comments, and common vendor operators are treated as
     * opaque regions so their question marks are not mistaken for bind
     * markers. The scanner intentionally leaves SQL validation to the JDBC
     * driver and database.</p>
     */
    private static final class MarkerScanner {

        private final String sql;
        private final int length;
        private int index;
        private int count;

        /**
         * Creates a scanner positioned before the first character.
         *
         * @param sql SQL text
         */
        private MarkerScanner(String sql) {
            this.sql = sql;
            this.length = sql.length();
        }

        /**
         * Scans one SQL string and returns its positional marker count.
         *
         * @param sql SQL text
         * @return marker count
         */
        static int count(String sql) {
            MarkerScanner scanner = new MarkerScanner(sql);
            scanner.scan();
            return scanner.count;
        }

        private void scan() {
            while (index < length) {
                char current = sql.charAt(index);
                if (current == '\'') {
                    quoted('\'');
                } else if (current == '"') {
                    quoted('"');
                } else if (current == '`') {
                    quoted('`');
                } else if (current == '[') {
                    bracketIdentifier();
                } else if (current == '-' && peek(1) == '-') {
                    lineComment();
                } else if (current == '/' && peek(1) == '*') {
                    blockComment();
                } else if ((current == 'q' || current == 'Q') && peek(1) == '\'' && index + 2 < length) {
                    oracleQuoted();
                } else if (current == '$' && dollarQuoted()) {
                    // The helper advances past the complete dollar-quoted region.
                } else if (current == '?') {
                    positionalMarker();
                } else if (current == ':') {
                    namedMarker();
                } else {
                    index++;
                }
            }
        }

        // Question mark operators must not consume a bind position.
        private void positionalMarker() {
            char next = peek(1);
            if (next == '?' || next == '|' || next == '&') {
                index += 2;
            } else {
                count++;
                index++;
            }
        }

        // Generated code rewrites named markers before reaching the client.
        private void namedMarker() {
            char next = peek(1);
            if (next == ':' || next == '=') {
                index += 2;
                return;
            }
            if (Character.isJavaIdentifierStart(next)) {
                throw new IllegalArgumentException(
                        "JdbcClient SQL accepts positional '?' markers only; named marker found at offset " + index);
            }
            index++;
        }

        // SQL escapes a delimiter by doubling it.
        private void quoted(char delimiter) {
            index++;
            while (index < length) {
                if (sql.charAt(index) == delimiter) {
                    if (peek(1) == delimiter) {
                        index += 2;
                    } else {
                        index++;
                        return;
                    }
                } else {
                    index++;
                }
            }
            throw malformed("Unterminated quoted SQL region");
        }

        private void bracketIdentifier() {
            index++;
            while (index < length) {
                if (sql.charAt(index) == ']') {
                    if (peek(1) == ']') {
                        index += 2;
                    } else {
                        index++;
                        return;
                    }
                } else {
                    index++;
                }
            }
            throw malformed("Unterminated bracket-quoted identifier");
        }

        private void lineComment() {
            index += 2;
            while (index < length) {
                char current = sql.charAt(index++);
                if (current == '\n' || current == '\r') {
                    return;
                }
            }
        }

        // Some databases allow block comments to be nested.
        private void blockComment() {
            index += 2;
            int depth = 1;
            while (index < length) {
                if (sql.charAt(index) == '/' && peek(1) == '*') {
                    depth++;
                    index += 2;
                } else if (sql.charAt(index) == '*' && peek(1) == '/') {
                    depth--;
                    index += 2;
                    if (depth == 0) {
                        return;
                    }
                } else {
                    index++;
                }
            }
            throw malformed("Unterminated block comment");
        }

        private void oracleQuoted() {
            char opening = sql.charAt(index + 2);
            char closing = switch (opening) {
                case '[' -> ']';
                case '(' -> ')';
                case '{' -> '}';
                case '<' -> '>';
                default -> opening;
            };
            index += 3;
            while (index + 1 < length) {
                if (sql.charAt(index) == closing && sql.charAt(index + 1) == '\'') {
                    index += 2;
                    return;
                }
                index++;
            }
            throw malformed("Unterminated alternative quoted string");
        }

        // A lone dollar sign is ordinary SQL text, not an opening delimiter.
        private boolean dollarQuoted() {
            int delimiterEnd = index + 1;
            while (delimiterEnd < length && sql.charAt(delimiterEnd) != '$') {
                if (!Character.isJavaIdentifierPart(sql.charAt(delimiterEnd))) {
                    return false;
                }
                delimiterEnd++;
            }
            if (delimiterEnd >= length) {
                return false;
            }
            String delimiter = sql.substring(index, delimiterEnd + 1);
            int contentEnd = sql.indexOf(delimiter, delimiterEnd + 1);
            if (contentEnd < 0) {
                throw malformed("Unterminated dollar-quoted string");
            }
            index = contentEnd + delimiter.length();
            return true;
        }

        /**
         * Reads one character relative to the current offset.
         *
         * @param offset relative character offset
         * @return character, or the NUL sentinel beyond the SQL text
         */
        private char peek(int offset) {
            int target = index + offset;
            return target < length ? sql.charAt(target) : '\0';
        }

        /**
         * Creates a malformed-SQL diagnostic for an unterminated protected region.
         *
         * @param message diagnostic text
         * @return exception to throw
         */
        private IllegalArgumentException malformed(String message) {
            return new IllegalArgumentException(message + " near offset " + index);
        }
    }
}
