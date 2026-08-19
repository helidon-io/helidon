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
package io.helidon.data.jdbc.tests.chaos.application;

/**
 * SQL text owned by the JDBC chaos test suite.
 */
public final class ChaosSql {
    /**
     * Canary embedded in malformed SQL that must not appear in public diagnostics.
     */
    public static final String MALFORMED_SQL_CANARY = "private-chaos-malformed-sql-canary";

    /**
     * Canary embedded in a bind value that must not appear in public diagnostics.
     */
    public static final String BIND_VALUE_CANARY = "private-chaos-bind-value-canary";

    /**
     * Canary embedded in selected text that must not appear in conversion-failure diagnostics.
     */
    public static final String CONVERSION_VALUE_CANARY = "private-chaos-conversion-value-canary";

    /**
     * Canary embedded in a transaction row that must not commit after rollback.
     */
    public static final String TRANSACTION_ROLLBACK_CANARY = "private-chaos-transaction-rollback-canary";

    /**
     * Malformed query used by the first smoke scenario to trigger driver failure handling.
     */
    public static final String MALFORMED_QUERY = "SELECT MISSING_CHAOS_COLUMN FROM CHAOS_CONTACT /* "
            + MALFORMED_SQL_CANARY
            + " */";

    /**
     * Insert used to produce a deterministic primary-key violation.
     */
    public static final String INSERT_CONTACT = "INSERT INTO CHAOS_CONTACT (ID, NAME) VALUES (?, ?)";

    /**
     * Insert used by generated-key smoke scenarios.
     */
    public static final String INSERT_GENERATED_CONTACT = "INSERT INTO CHAOS_GENERATED (NAME) VALUES (?)";

    /**
     * Query used to produce a deterministic scalar conversion failure.
     */
    public static final String CONVERSION_FAILURE_QUERY = "SELECT '"
            + CONVERSION_VALUE_CANARY
            + "' FROM CHAOS_CONTACT WHERE ID = 1";

    /**
     * Counts committed contact rows for recovery checks.
     */
    public static final String COUNT_CONTACTS = "SELECT COUNT(*) FROM CHAOS_CONTACT";

    /**
     * Counts committed contact rows with a specific name.
     */
    public static final String COUNT_CONTACTS_BY_NAME = "SELECT COUNT(*) FROM CHAOS_CONTACT WHERE NAME = ?";

    /**
     * Counts committed generated-key rows with a specific name.
     */
    public static final String COUNT_GENERATED_BY_NAME = "SELECT COUNT(*) FROM CHAOS_GENERATED WHERE NAME = ?";

    /**
     * Removes chaos contacts before restoring deterministic baseline rows.
     */
    public static final String RESET_CONTACTS = "DELETE FROM CHAOS_CONTACT";

    /**
     * Removes generated-key rows before each chaos smoke scenario.
     */
    public static final String RESET_GENERATED = "DELETE FROM CHAOS_GENERATED";

    /**
     * Restores one chaos contact with an explicit identifier.
     */
    public static final String RESTORE_CONTACT = "INSERT INTO CHAOS_CONTACT (ID, NAME) VALUES (?, ?)";

    private ChaosSql() {
    }
}
