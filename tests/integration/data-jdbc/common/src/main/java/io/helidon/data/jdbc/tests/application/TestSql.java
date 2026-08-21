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
package io.helidon.data.jdbc.tests.application;

/**
 * Portable SQL shared by declarative and imperative application adapters.
 */
public final class TestSql {
    /**
     * Canary which must never appear in an application-visible SQL failure.
     */
    public static final String INVALID_QUERY_CANARY = "private-sql-canary";

    /**
     * Selects contacts in identifier order.
     */
    public static final String FIND_ALL = "SELECT ID, NAME, EMAIL FROM CONTACT ORDER BY ID";

    /**
     * Selects a contact by name.
     */
    public static final String FIND_BY_NAME = "SELECT ID, NAME, EMAIL FROM CONTACT WHERE NAME = ?";

    /**
     * Selects contacts by name in identifier order.
     */
    public static final String FIND_ALL_BY_NAME = "SELECT ID, NAME, EMAIL FROM CONTACT WHERE NAME = ? ORDER BY ID";

    /**
     * Selects contacts by an exact name or email value in identifier order.
     */
    public static final String FIND_ALL_BY_NAME_OR_EMAIL = """
            SELECT ID, NAME, EMAIL
            FROM CONTACT
            WHERE NAME = ? OR EMAIL = ?
            ORDER BY ID
            """;

    /**
     * Selects contacts by an optional email filter.
     */
    public static final String FIND_BY_EMAIL = """
            SELECT ID, NAME, EMAIL
            FROM CONTACT
            WHERE (? IS NULL OR EMAIL = ?)
            ORDER BY ID
            """;

    /**
     * Selects one nullable email by contact identifier.
     */
    public static final String FIND_EMAIL_BY_ID = "SELECT EMAIL FROM CONTACT WHERE ID = ?";

    /**
     * Selects all contacts for singular-cardinality validation.
     */
    public static final String FIND_ONE_FROM_ALL = "SELECT ID, NAME, EMAIL FROM CONTACT ORDER BY ID";

    /**
     * Selects a record projection without its required name label.
     */
    public static final String FIND_WITH_MISSING_LABEL = """
            SELECT ID AS id,
                   NAME AS display_name,
                   EMAIL AS email
            FROM CONTACT
            WHERE ID = ?
            """;

    /**
     * References a column which does not exist.
     */
    public static final String INVALID_QUERY = "SELECT MISSING_COLUMN FROM CONTACT /* "
            + INVALID_QUERY_CANARY
            + " */";

    /**
     * Inserts a contact with an email address.
     */
    public static final String INSERT = "INSERT INTO CONTACT (NAME, EMAIL) VALUES (?, ?)";

    /**
     * Inserts an SQL {@code NULL} into the required name column.
     */
    public static final String INSERT_NULL_NAME = "INSERT INTO CONTACT (NAME, EMAIL) VALUES (NULL, ?)";

    /**
     * Inserts a contact with an SQL {@code NULL} email.
     */
    public static final String INSERT_WITHOUT_EMAIL = "INSERT INTO CONTACT (NAME, EMAIL) VALUES (?, NULL)";

    /**
     * Renames a contact.
     */
    public static final String RENAME = "UPDATE CONTACT SET NAME = ? WHERE ID = ?";

    /**
     * Renames a contact while leaving email unchanged.
     */
    public static final String RENAME_BY_NAME = "UPDATE CONTACT SET NAME = ? WHERE NAME = ?";

    /**
     * Renames every contact.
     */
    public static final String RENAME_ALL = "UPDATE CONTACT SET NAME = ?";

    /**
     * Deletes a contact by identifier.
     */
    public static final String DELETE = "DELETE FROM CONTACT WHERE ID = ?";

    /**
     * Deletes contacts by exact name.
     */
    public static final String DELETE_BY_NAME = "DELETE FROM CONTACT WHERE NAME = ?";

    /**
     * Removes all application rows before a contract scenario.
     */
    public static final String RESET = "DELETE FROM CONTACT";

    /**
     * Restores one baseline row with an explicit identifier.
     */
    public static final String RESTORE_WITH_EMAIL = "INSERT INTO CONTACT (ID, NAME, EMAIL) VALUES (?, ?, ?)";

    /**
     * Restores one baseline row with an explicit identifier and SQL {@code NULL} email.
     */
    public static final String RESTORE_WITHOUT_EMAIL = "INSERT INTO CONTACT (ID, NAME, EMAIL) VALUES (?, ?, NULL)";

    private TestSql() {
    }
}
