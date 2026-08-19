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
package io.helidon.data.jdbc.tests.application.transaction;

/**
 * Portable SQL shared by both transaction-matrix application styles.
 */
public final class TransactionSql {
    /**
     * Counts transaction-matrix rows.
     */
    public static final String QUERY = "SELECT COUNT(*) FROM TX_MATRIX";

    /**
     * Lists transaction-matrix values in deterministic order for exact state assertions.
     */
    public static final String LIST_VALUES = "SELECT DATA_VALUE FROM TX_MATRIX ORDER BY DATA_VALUE";

    /**
     * Removes the baseline row.
     */
    public static final String UPDATE = "DELETE FROM TX_MATRIX WHERE DATA_VALUE = 'baseline'";

    /**
     * Inserts a row and exposes its generated identifier.
     */
    public static final String GENERATED_KEY = "INSERT INTO TX_MATRIX (DATA_VALUE) VALUES ('generated')";

    /**
     * Inserts one named transaction-matrix value.
     */
    public static final String INSERT_VALUE = "INSERT INTO TX_MATRIX (DATA_VALUE) VALUES (?)";

    /**
     * Inserts one named transaction-matrix value using a generated repository named parameter.
     */
    public static final String INSERT_NAMED_VALUE = "INSERT INTO TX_MATRIX (DATA_VALUE) VALUES (:value)";

    /**
     * Invalid query used to force a driver failure inside a joined transaction.
     */
    public static final String INVALID_QUERY = "SELECT DATA_VALUE FROM TX_MATRIX_MISSING";

    /**
     * Removes all transaction-matrix rows before a scenario.
     */
    public static final String RESET = "DELETE FROM TX_MATRIX";

    /**
     * Restores the baseline row before a scenario.
     */
    public static final String RESTORE = "INSERT INTO TX_MATRIX (DATA_VALUE) VALUES ('baseline')";

    private TransactionSql() {
    }
}
