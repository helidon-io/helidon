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

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcClientPublicConstructionTest {

    private static final AtomicLong DATABASE_SEQUENCE = new AtomicLong();
    private static final JdbcClient.RowMapper<Pokemon> POKEMON_MAPPER = row ->
            new Pokemon(row.get("ID", Integer.class), row.get("NAME", String.class));

    /**
     * Verifies a named data source is resolved through the SQL data source
     * registry and that a missing source reports both safe configured names.
     */
    @Test
    void executesThroughNamedDataSourceAndReportsMissingSource() {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(manager.registry());
        try (HikariDataSource dataSource = dataSource()) {
            Services.setNamed(DataSource.class, dataSource, "pokemon-source");
            JdbcClient client = JdbcClient.builder()
                    .name("pokemon-client")
                    .dataSource("pokemon-source")
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
                            .dataSource("missing-reporting-source")
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
    void executesThroughDirectConnectionAndClosesOwnedConnections() {
        String url = "jdbc:h2:mem:jdbc_client_direct_"
                + DATABASE_SEQUENCE.incrementAndGet()
                + ";DB_CLOSE_DELAY=-1";
        JdbcClient client = JdbcClient.builder()
                .name("direct-client")
                .connection(connection -> connection
                        .url(url)
                        .jdbcDriverClassName("org.h2.Driver"))
                .build();

        client.create("CREATE TABLE POKEMON (ID INT PRIMARY KEY, NAME VARCHAR(40) NOT NULL)")
                .execute();
        assertThat(openSessions(client), is(1L));
        assertThat(client.create("INSERT INTO POKEMON (ID, NAME) VALUES (?, ?)")
                           .bind(1, 7)
                           .bind(2, "Squirtle")
                           .execute(),
                   is(1L));
        assertThat(openSessions(client), is(1L));
        assertThat(client.create("SELECT ID, NAME FROM POKEMON")
                           .map(POKEMON_MAPPER)
                           .list(),
                   is(List.of(new Pokemon(7, "Squirtle"))));
        assertThat(openSessions(client), is(1L));

        assertThrows(DataException.class,
                     () -> client.create("SELECT MISSING_COLUMN FROM POKEMON")
                             .map(String.class)
                             .list());
        assertThat(openSessions(client), is(1L));
    }

    private static HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:jdbc_client_public_"
                                  + DATABASE_SEQUENCE.incrementAndGet()
                                  + ";DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }

    private static void assertPoolIdle(HikariDataSource dataSource) {
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    private static long openSessions(JdbcClient client) {
        return client.create("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS")
                .map(Long.class)
                .one();
    }

    private record Pokemon(int id, String name) {
    }
}
