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

import io.helidon.data.DataException;
import io.helidon.data.jdbc.tests.support.SensitiveFailureAssertions;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcDatabaseFailureTest {
    private JdbcClient client;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:jdbc_database_failures;DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS FAILURE_VALUE");
            statement.execute("DROP TABLE IF EXISTS FAILURE_PARENT");
            statement.execute("CREATE TABLE FAILURE_PARENT (ID INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE FAILURE_VALUE ("
                                      + "ID INTEGER PRIMARY KEY, "
                                      + "PARENT_ID INTEGER REFERENCES FAILURE_PARENT(ID), "
                                      + "SCORE INTEGER CHECK (SCORE BETWEEN 0 AND 10), "
                                      + "TINY_VALUE TINYINT, "
                                      + "DATA_VALUE VARCHAR(40))");
            statement.execute("INSERT INTO FAILURE_PARENT VALUES (1)");
            statement.execute("INSERT INTO FAILURE_VALUE VALUES (1, 1, 5, 5, 'baseline')");
        }
        client = JdbcTestClients.create(dataSource);
    }

    /**
     * Proves three driver-produced integrity failures are sanitized and leave
     * unchanged committed state available afterward.
     */
    @Test
    void sanitizesPrimaryKeyForeignKeyAndCheckConstraintFailures() {
        String primaryKeyCanary = "private-primary-key-canary";
        DataException primaryKeyFailure = assertThrows(
                DataException.class,
                () -> client.create("INSERT INTO FAILURE_VALUE VALUES (1, 1, 5, 5, ?)")
                        .bind(1, primaryKeyCanary)
                        .execute());
        SensitiveFailureAssertions.assertNoSecrets(primaryKeyFailure, primaryKeyCanary);

        String foreignKeyCanary = "private-foreign-key-canary";
        DataException foreignKeyFailure = assertThrows(
                DataException.class,
                () -> client.create("INSERT INTO FAILURE_VALUE VALUES (2, 999, 5, 5, ?)")
                        .bind(1, foreignKeyCanary)
                        .execute());
        SensitiveFailureAssertions.assertNoSecrets(foreignKeyFailure, foreignKeyCanary);

        String checkCanary = "private-check-canary";
        DataException checkFailure = assertThrows(
                DataException.class,
                () -> client.create("INSERT INTO FAILURE_VALUE VALUES (3, 1, 11, 5, ?)")
                        .bind(1, checkCanary)
                        .execute());
        SensitiveFailureAssertions.assertNoSecrets(checkFailure, checkCanary);

        assertThat(client.create("SELECT COUNT(*) FROM FAILURE_VALUE").map(Long.class).one(), is(1L));
    }

    /**
     * Proves conversion and numeric overflow diagnostics do not disclose
     * offending values and permit immediate recovery.
     */
    @Test
    void sanitizesConversionAndNumericRangeFailures() {
        String conversionCanary = "private-conversion-canary";
        DataException conversionFailure = assertThrows(
                DataException.class,
                () -> client.create("SELECT CAST(? AS INTEGER)")
                        .bind(1, conversionCanary)
                        .map(Integer.class)
                        .one());
        SensitiveFailureAssertions.assertNoSecrets(conversionFailure, conversionCanary);

        String rangeCanary = "private-range-canary";
        DataException rangeFailure = assertThrows(
                DataException.class,
                () -> client.create("INSERT INTO FAILURE_VALUE VALUES (2, 1, 5, ?, ?)")
                        .bind(1, 1_000)
                        .bind(2, rangeCanary)
                        .execute());
        SensitiveFailureAssertions.assertNoSecrets(rangeFailure, rangeCanary);

        assertThat(client.create("SELECT DATA_VALUE FROM FAILURE_VALUE WHERE ID = 1").map(String.class).one(),
                   is("baseline"));
    }

    /**
     * Proves a missing-table failure does not poison the client and the recreated schema can be used immediately.
     */
    @Test
    void recoversAfterSchemaRemovalAndRecreation() {
        client.create("CREATE TABLE MUTABLE_VALUE (DATA_VALUE VARCHAR(20))").execute();
        client.create("INSERT INTO MUTABLE_VALUE VALUES ('before')").execute();
        assertThat(client.create("SELECT DATA_VALUE FROM MUTABLE_VALUE").map(String.class).one(), is("before"));

        client.create("DROP TABLE MUTABLE_VALUE").execute();
        assertThrows(DataException.class,
                     () -> client.create("SELECT DATA_VALUE FROM MUTABLE_VALUE").map(String.class).one());

        client.create("CREATE TABLE MUTABLE_VALUE (DATA_VALUE VARCHAR(20))").execute();
        client.create("INSERT INTO MUTABLE_VALUE VALUES ('after')").execute();
        assertThat(client.create("SELECT DATA_VALUE FROM MUTABLE_VALUE").map(String.class).one(), is("after"));
    }
}
