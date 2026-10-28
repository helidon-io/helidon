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
package io.helidon.tests.integration.data.jdbc.imperative.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.tests.integration.data.jdbc.database.mysql.MySqlTestDatabase;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(MySqlTestDatabase.class)
class JdbcClientPublicConstructionTest {

    private static final Duration SESSION_CLEANUP_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SESSION_POLL_INTERVAL = Duration.ofMillis(20);
    private static final AtomicLong DATABASE_SEQUENCE = new AtomicLong();
    private static final JdbcClient.RowMapper<Pokemon> POKEMON_MAPPER = row ->
            new Pokemon(row.get("ID", Integer.class), row.get("NAME", String.class));

    /**
     * Verifies a named data source is resolved through the SQL data source
     * registry and that a missing source reports both safe configured names.
     */
    @Test
    void executesThroughNamedDataSourceAndReportsMissingSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MySqlTestDatabase.jdbcUrl("jdbc_client_public_" + DATABASE_SEQUENCE.incrementAndGet()));
        config.setUsername(MySqlTestDatabase.username());
        config.setPassword(MySqlTestDatabase.password());
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1_000);
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            Services.setNamed(DataSource.class, dataSource, "pokemon-source");
            JdbcClient client = JdbcClient.builder()
                    .name("pokemon-client")
                    .dataSourceName("pokemon-source")
                    .build();

            client.create("CREATE TABLE POKEMON (ID INT PRIMARY KEY, NAME VARCHAR(40) NOT NULL)")
                    .execute();
            assertPoolIdle(dataSource);
            assertThat(client.create("INSERT INTO POKEMON (ID, NAME) VALUES (?, ?)")
                               .bind(1, 4)
                               .bind(2, "Charmander")
                               .execute(),
                       is(1L));
            assertPoolIdle(dataSource);
            assertThat(client.create("SELECT ID, NAME FROM POKEMON")
                               .map(POKEMON_MAPPER)
                               .list(),
                       is(List.of(new Pokemon(4, "Charmander"))));
            assertPoolIdle(dataSource);

            DataException failure = assertThrows(
                    DataException.class,
                    () -> JdbcClient.builder()
                            .name("reporting-client")
                            .dataSourceName("missing-reporting-source")
                            .build());
            assertThat(failure.getMessage(),
                       is("JDBC client 'reporting-client' could not resolve SQL data source "
                                  + "'missing-reporting-source'."));
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Verifies direct connection settings use the public client execution path
     * and close owned connections after successful and failed operations.
     */
    @Test
    void executesThroughDirectConnectionAndClosesOwnedConnections() throws Exception {
        String url = MySqlTestDatabase.jdbcUrl("jdbc_client_direct_" + DATABASE_SEQUENCE.incrementAndGet());
        JdbcClient client = JdbcClient.builder()
                .name("direct-client")
                .connection(connection -> connection
                        .url(url)
                        .jdbcDriverClassName("com.mysql.cj.jdbc.Driver")
                        .username(MySqlTestDatabase.username())
                        .password(MySqlTestDatabase.password()))
                .build();

        // Reuse an independent observer so polling does not create additional sessions to clean up.
        try (Connection observer = DriverManager.getConnection(url,
                                                              MySqlTestDatabase.username(),
                                                              MySqlTestDatabase.password())) {
            client.create("CREATE TABLE POKEMON (ID INT PRIMARY KEY, NAME VARCHAR(40) NOT NULL)")
                    .execute();
            assertOwnedConnectionsClosed(observer);
            assertThat(client.create("INSERT INTO POKEMON (ID, NAME) VALUES (?, ?)")
                               .bind(1, 7)
                               .bind(2, "Squirtle")
                               .execute(),
                       is(1L));
            assertOwnedConnectionsClosed(observer);
            assertThat(client.create("SELECT ID, NAME FROM POKEMON")
                               .map(POKEMON_MAPPER)
                               .list(),
                       is(List.of(new Pokemon(7, "Squirtle"))));
            assertOwnedConnectionsClosed(observer);

            assertThrows(DataException.class,
                         () -> client.create("SELECT MISSING_COLUMN FROM POKEMON")
                                 .map(String.class)
                                 .list());
            assertOwnedConnectionsClosed(observer);
        }
    }

    private static void assertPoolIdle(HikariDataSource dataSource) {
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    private static void assertOwnedConnectionsClosed(Connection observer) throws SQLException, InterruptedException {
        long started = System.nanoTime();
        List<String> sessions = new ArrayList<>();
        try (var statement = observer.createStatement()) {
            statement.setQueryTimeout((int) SESSION_CLEANUP_TIMEOUT.toSeconds());
            while (true) {
                sessions.clear();
                try (var rows = statement.executeQuery(
                        "SELECT ID, COMMAND, STATE FROM INFORMATION_SCHEMA.PROCESSLIST"
                                + " WHERE DB = DATABASE() AND ID <> CONNECTION_ID() ORDER BY ID")) {
                    while (rows.next()) {
                        sessions.add("id=" + rows.getLong("ID")
                                             + ", command=" + rows.getString("COMMAND")
                                             + ", state=" + rows.getString("STATE"));
                    }
                }
                if (sessions.isEmpty()) {
                    return;
                }
                if (System.nanoTime() - started >= SESSION_CLEANUP_TIMEOUT.toNanos()) {
                    break;
                }
                // Connector/J close does not wait for MySQL to remove the server-side session.
                Thread.sleep(SESSION_POLL_INTERVAL);
            }
        }
        assertThat("Owned MySQL sessions still open after " + SESSION_CLEANUP_TIMEOUT, sessions, is(List.of()));
    }

    private record Pokemon(int id, String name) {
    }
}
