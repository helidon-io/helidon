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

import java.sql.SQLException;

import io.helidon.data.DataException;

/**
 * Creates consistent data exceptions without including bind values.
 */
final class JdbcExceptionTranslator {

    // Limits the amount of SQL copied into an exception message.
    private static final int MAX_SQL_LENGTH = 512;

    private JdbcExceptionTranslator() {
    }

    /**
     * Translates a driver failure using the operation's safe metadata.
     *
     * @param operation failed operation
     * @param cause driver failure
     * @return data-layer failure
     */
    static DataException translate(JdbcOperation operation, SQLException cause) {
        return translate(operation.preparationPlan().resultKind().name(), operation.sql(), cause);
    }

    /**
     * Translates a driver failure without including any bound values.
     *
     * @param operation operation name
     * @param sql statement text
     * @param cause driver failure
     * @return data-layer failure
     */
    static DataException translate(String operation, String sql, SQLException cause) {
        String state = cause.getSQLState() == null ? "unknown" : cause.getSQLState();
        String message = "JDBC " + operation + " failed [SQLState=" + state
                + ", vendorCode=" + cause.getErrorCode() + ", SQL=" + sanitized(sql) + "]";
        return new DataException(message, cause);
    }

    /**
     * Collapses whitespace and limits SQL included in a diagnostic.
     *
     * @param sql SQL text
     * @return bounded diagnostic text
     */
    static String sanitized(String sql) {
        String normalized = sql.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_SQL_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SQL_LENGTH) + "...";
    }
}
