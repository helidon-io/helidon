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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.data.jdbc.tests.support.SensitiveFailureAssertions;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcResourceOwnershipTest {

    private static final AtomicLong DATABASE_SEQUENCE = new AtomicLong();

    /**
     * Proves successful query, update, and generated-key materialization and
     * mapper failure always return the sole pool lease.
     */
    @Test
    void returnsEveryConnectionAfterMaterializationAndMapperFailure() {
        try (HikariDataSource dataSource = dataSource()) {
            JdbcClient client = initializedClient(dataSource);
            JdbcClient.Rows<Long> generatedKeys = client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES (?)")
                    .bind(1, "value")
                    .generatedKeys()
                    .addColumn("ID")
                    .map(row -> row.required(1, Long.class));

            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
            assertThat(generatedKeys.one(), is(1L));
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));

            for (int count = 0; count < 20; count++) {
                assertThat(client.create("UPDATE TEST_VALUE SET DATA_VALUE = ? WHERE ID = ?")
                                   .bind(1, "value")
                                   .bind(2, 1L)
                                   .execute(),
                           is(1L));
                assertThat(client.create("SELECT DATA_VALUE FROM TEST_VALUE").map(String.class).list(),
                           is(List.of("value")));
                long key = client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES (?)")
                        .bind(1, "temporary")
                        .generatedKeys()
                        .addColumn("ID")
                        .map(row -> row.required(1, Long.class))
                        .one();
                assertThat(client.create("DELETE FROM TEST_VALUE WHERE ID = ?")
                                   .bind(1, key)
                                   .execute(),
                           is(1L));
                assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
            }
            assertThrows(IllegalStateException.class,
                         () -> client.create("SELECT DATA_VALUE FROM TEST_VALUE")
                                 .map(row -> {
                                     throw new IllegalStateException("mapper failed");
                                 })
                                 .list());
            assertThrows(DataException.class,
                         () -> client.create("SELECT VALUE FROM TABLE_THAT_DOES_NOT_EXIST")
                                 .map(String.class)
                                 .list());

            assertThat(client.create("SELECT COUNT(*) FROM TEST_VALUE").map(Long.class).one(), is(1L));
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
        }
    }

    /**
     * Proves H2 constraint, truncation, and conversion failures neither leak
     * bind values nor retain the sole pool lease.
     */
    @Test
    void returnsConnectionAfterDatabaseProducedFailures() {
        try (HikariDataSource dataSource = dataSource()) {
            JdbcClient client = initializedClient(dataSource);
            String constraintCanary = "private-constraint-canary";
            client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES (?)")
                    .bind(1, constraintCanary)
                    .execute();

            DataException constraintFailure = assertThrows(
                    DataException.class,
                    () -> client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES (?)")
                            .bind(1, constraintCanary)
                            .execute());
            SensitiveFailureAssertions.assertNoSecrets(constraintFailure, constraintCanary);
            assertPoolReusable(dataSource, client, 1L);

            DataException nullFailure = assertThrows(
                    DataException.class,
                    () -> client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES (NULL)").execute());
            SensitiveFailureAssertions.assertNoSecrets(nullFailure);
            assertPoolReusable(dataSource, client, 1L);

            String truncationCanary = "private-truncation-canary-" + "x".repeat(40);
            DataException truncationFailure = assertThrows(
                    DataException.class,
                    () -> client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES (?)")
                            .bind(1, truncationCanary)
                            .execute());
            SensitiveFailureAssertions.assertNoSecrets(truncationFailure, truncationCanary);
            assertPoolReusable(dataSource, client, 1L);

            String conversionCanary = "private-conversion-canary";
            DataException conversionFailure = assertThrows(
                    DataException.class,
                    () -> client.create("SELECT CAST(? AS INTEGER)")
                            .bind(1, conversionCanary)
                            .map(Integer.class)
                            .one());
            SensitiveFailureAssertions.assertNoSecrets(conversionFailure, conversionCanary);
            assertPoolReusable(dataSource, client, 1L);
        }
    }

    /**
     * Proves label-resolution and multi-row cardinality failures close their result set, statement, and connection.
     */
    @Test
    void returnsConnectionAfterMappingAndCardinalityFailures() {
        try (HikariDataSource dataSource = dataSource()) {
            JdbcClient client = initializedClient(dataSource);
            client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES ('first')").execute();
            client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES ('second')").execute();

            assertThrows(DataException.class,
                         () -> client.create("SELECT DATA_VALUE FROM TEST_VALUE WHERE DATA_VALUE = 'first'")
                                 .map(row -> row.required("MISSING_LABEL", String.class))
                                 .one());
            assertPoolReusable(dataSource, client, 2L);

            assertThrows(io.helidon.data.NonUniqueResultException.class,
                         () -> client.create("SELECT DATA_VALUE FROM TEST_VALUE")
                                 .map(String.class)
                                 .one());
            assertPoolReusable(dataSource, client, 2L);
        }
    }

    /**
     * Proves a driver conversion failure from {@link JdbcClient.Row#required(String, Class)}
     * is reported as a sanitized {@link DataException}, not as a row-lifecycle
     * {@link IllegalStateException}. The mapper is running on a valid row, so
     * the failure category must describe unreadable column data while still
     * closing the result set, statement, and sole pool lease.
     */
    @Test
    void returnsConnectionAfterRequiredRowReadFailure() {
        try (HikariDataSource dataSource = dataSource()) {
            JdbcClient client = initializedClient(dataSource);
            String conversionCanary = "private-required-row-read-canary";
            client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES (?)")
                    .bind(1, conversionCanary)
                    .execute();

            DataException failure = assertThrows(
                    DataException.class,
                    () -> client.create("SELECT DATA_VALUE FROM TEST_VALUE")
                            .map(row -> row.required("DATA_VALUE", Integer.class))
                            .one());

            assertThat(failure.getCause(), instanceOf(SQLException.class));
            assertThat(failure.getCause().getMessage(), is("The JDBC driver reported a failure."));
            SensitiveFailureAssertions.assertNoSecrets(failure, conversionCanary);
            assertPoolReusable(dataSource, client, 1L);
        }
    }

    /**
     * Proves a driver conversion failure from {@link JdbcClient.Row#optional(int, Class)}
     * is not mistaken for an absent SQL value and is not categorized as row
     * misuse. Optional reads use the same valid mapper callback scope as
     * required reads, so a conversion error remains a sanitized data-access
     * failure and the single Hikari connection must be reusable afterwards.
     */
    @Test
    void returnsConnectionAfterOptionalRowReadFailure() {
        try (HikariDataSource dataSource = dataSource()) {
            JdbcClient client = initializedClient(dataSource);
            String conversionCanary = "private-optional-row-read-canary";
            client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES (?)")
                    .bind(1, conversionCanary)
                    .execute();

            DataException failure = assertThrows(
                    DataException.class,
                    () -> client.create("SELECT DATA_VALUE FROM TEST_VALUE")
                            .map(row -> row.optional(1, Integer.class))
                            .one());

            assertThat(failure.getCause(), instanceOf(SQLException.class));
            assertThat(failure.getCause().getMessage(), is("The JDBC driver reported a failure."));
            SensitiveFailureAssertions.assertNoSecrets(failure, conversionCanary);
            assertPoolReusable(dataSource, client, 1L);
        }
    }

    /**
     * Proves the row-read exception-category fix does not broaden row lifecycle
     * violations into {@link DataException}. A row retained past its mapper
     * callback is expired before application code can use it again, so the
     * public contract still reports {@link IllegalStateException} for invalid
     * row lifetime while leaving the provider's sole connection reusable.
     */
    @Test
    void keepsExpiredRowReadsAsIllegalStateException() {
        try (HikariDataSource dataSource = dataSource()) {
            JdbcClient client = initializedClient(dataSource);
            client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES ('value')").execute();
            AtomicReference<JdbcClient.Row> escaped = new AtomicReference<>();

            assertThat(client.create("SELECT DATA_VALUE FROM TEST_VALUE")
                               .map(row -> {
                                   escaped.set(row);
                                   return row.required(1, String.class);
                               })
                               .one(),
                       is("value"));

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                         () -> escaped.get().required(1, String.class));

            assertThat(failure.getMessage(), is("A JDBC row is valid only during its mapper callback."));
            assertPoolReusable(dataSource, client, 1L);
        }
    }

    /**
     * Proves failure while preparing an explicit generated-column request leaves the pool immediately reusable.
     */
    @Test
    void returnsConnectionAfterGeneratedKeyPreparationFailure() {
        try (HikariDataSource dataSource = dataSource()) {
            JdbcClient client = initializedClient(dataSource);

            assertThrows(DataException.class,
                         () -> client.create("INSERT INTO TEST_VALUE(DATA_VALUE) VALUES ('value')")
                                 .generatedKeys()
                                 .addColumn("MISSING_KEY")
                                 .map(row -> row.required(1, Long.class))
                                 .one());

            assertPoolReusable(dataSource, client, 0L);
        }
    }

    /**
     * Proves binary values are detached from JDBC resources and remain
     * readable after the connection is returned and reused.
     */
    @Test
    void materializesBinaryBeforeReturningConnection() {
        try (HikariDataSource dataSource = dataSource()) {
            JdbcClient client = initializedClient(dataSource);
            client.create("CREATE TABLE BINARY_VALUE (ID INTEGER PRIMARY KEY, DATA_VALUE VARBINARY(40))").execute();
            byte[] expected = {1, 2, 3, 4};
            client.create("INSERT INTO BINARY_VALUE VALUES (1, ?)").bind(1, expected).execute();

            byte[] result = client.create("SELECT DATA_VALUE FROM BINARY_VALUE WHERE ID = 1")
                    .map(byte[].class)
                    .one();

            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
            assertArrayEquals(expected, result);
            assertThat(client.create("SELECT COUNT(*) FROM BINARY_VALUE").map(Long.class).one(), is(1L));
            assertArrayEquals(expected, result);
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
        }
    }

    /**
     * Proves a close failure remains visible while the physical Hikari
     * connection is safely reusable by the next operation.
     */
    @Test
    void oneConnectionPoolRemainsReusableAfterConnectionCleanupFailure() throws Exception {
        try (HikariDataSource pool = dataSource()) {
            JdbcClient setup = new JdbcClientImpl(pool, JdbcConnectionLease.ownedProvider());
            setup.create("CREATE TABLE TEST_VALUE (ID BIGINT PRIMARY KEY, DATA_VALUE VARCHAR(40))").execute();
            setup.create("INSERT INTO TEST_VALUE VALUES (1, 'value')").execute();

            Connection pooledConnection = pool.getConnection();
            Connection failingConnection = mock(Connection.class, delegatesTo(pooledConnection));
            doAnswer(invocation -> {
                pooledConnection.close();
                throw new SQLException("connection close failed");
            }).when(failingConnection).close();
            AtomicBoolean firstBorrow = new AtomicBoolean(true);
            DataSource dataSource = mock(DataSource.class);
            when(dataSource.getConnection()).thenAnswer(invocation ->
                    firstBorrow.getAndSet(false) ? failingConnection : pool.getConnection());
            JdbcClient client = new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider());

            DataException failure = assertThrows(DataException.class,
                                                 () -> client.create("SELECT DATA_VALUE FROM TEST_VALUE")
                                                         .map(String.class)
                                                         .one());

            assertThat(failure.getCause().getMessage(), is("The JDBC driver reported a failure."));
            assertThat(client.create("SELECT COUNT(*) FROM TEST_VALUE").map(Long.class).one(), is(1L));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections(), is(0));
            assertThat(pool.getHikariPoolMXBean().getTotalConnections(), is(1));
        }
    }

    private static HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:jdbc_resource_ownership_"
                                  + DATABASE_SEQUENCE.incrementAndGet()
                                  + ";DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }

    private static JdbcClient initializedClient(HikariDataSource dataSource) {
        JdbcClient client = new JdbcClientImpl(dataSource, JdbcConnectionLease.ownedProvider());
        client.create("CREATE TABLE TEST_VALUE (ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                              + "DATA_VALUE VARCHAR(40) NOT NULL UNIQUE)")
                .execute();
        return client;
    }

    private static void assertPoolReusable(HikariDataSource dataSource, JdbcClient client, long expectedRows) {
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
        assertThat(client.create("SELECT COUNT(*) FROM TEST_VALUE").map(Long.class).one(), is(expectedRows));
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
    }
}
