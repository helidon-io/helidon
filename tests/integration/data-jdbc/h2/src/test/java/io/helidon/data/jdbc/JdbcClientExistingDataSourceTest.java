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

import io.helidon.data.DataException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcClientExistingDataSourceTest {

    private static final AtomicLong DATABASE_SEQUENCE = new AtomicLong();
    private static final JdbcClient.RowMapper<Pokemon> POKEMON_MAPPER = row ->
            new Pokemon(row.get("ID", Integer.class), row.get("NAME", String.class));

    /**
     * Verifies standalone construction from an existing data source without
     * global registration, application ownership of the pool, and connection
     * return after successful and failed terminal operations.
     */
    @Test
    void executesThroughExistingDataSourceAndReturnsConnections() {
        try (HikariDataSource dataSource = dataSource()) {
            JdbcClient client = JdbcClient.builder()
                    .dataSource(dataSource)
                    .build();

            assertThat(client.prototype().dataSource().orElseThrow(), sameInstance(dataSource));
            client.create("CREATE TABLE POKEMON (ID INT PRIMARY KEY, NAME VARCHAR(40) NOT NULL)")
                    .execute();
            assertPoolIdle(dataSource);
            assertThat(client.create("INSERT INTO POKEMON (ID, NAME) VALUES (?, ?)")
                               .bind(1, 25)
                               .bind(2, "Pikachu")
                               .execute(),
                       is(1L));
            assertPoolIdle(dataSource);
            assertThat(client.create("SELECT ID, NAME FROM POKEMON")
                               .map(POKEMON_MAPPER)
                               .list(),
                       is(List.of(new Pokemon(25, "Pikachu"))));
            assertPoolIdle(dataSource);

            assertThrows(DataException.class,
                         () -> client.create("SELECT MISSING_COLUMN FROM POKEMON")
                                 .map(String.class)
                                 .list());
            assertPoolIdle(dataSource);
            assertThat(client.create("SELECT COUNT(*) FROM POKEMON")
                               .map(Long.class)
                               .one(),
                       is(1L));
            assertPoolIdle(dataSource);
            assertThat(dataSource.isClosed(), is(false));
        }
    }

    private static HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:jdbc_client_existing_"
                                  + DATABASE_SEQUENCE.incrementAndGet()
                                  + ";DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }

    private static void assertPoolIdle(HikariDataSource dataSource) {
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections(), is(0));
    }

    private record Pokemon(int id, String name) {
    }
}
