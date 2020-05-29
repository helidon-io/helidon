/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.jdbc.init;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Initialize database
 */
public class InitIT extends AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(InitIT.class.getName());

    /**
     * Initializes database schema (tables).
     *
     * @param dbClient Helidon database client
     */
    private static void initSchema(DbClient dbClient) {
        dbClient.execute(exec -> exec
                .namedDml("create-types")
                .flatMapSingle(result -> exec.namedDml("create-pokemons"))
                .flatMapSingle(result -> exec.namedDml("create-poketypes"))
        ).await();
    }

    /**
     * Initialize database content (rows in tables).
     *
     * @param dbClient Helidon database client
     */
    private static void initData(DbClient dbClient) {
        // Init pokemon types
        dbClient.inTransaction(tx -> {
            Single<Long> stage = null;
            for (Map.Entry<Integer, Type> entry : TYPES.entrySet()) {
                if (stage == null) {
                    stage = tx.namedDml("insert-type", entry.getKey(), entry.getValue().getName());
                } else {
                    stage = stage.flatMapSingle(result -> tx.namedDml(
                            "insert-type", entry.getKey(), entry.getValue().getName()));
                }
            }
            return stage;
        }).await();

        // Init pokemons
        dbClient.inTransaction(tx -> {
            Single<Long> stage = null;
            for (Map.Entry<Integer, Pokemon> entry : POKEMONS.entrySet()) {
                if (stage == null) {
                    stage = tx.namedDml("insert-pokemon", entry.getKey(), entry.getValue().getName());
                } else {
                    stage = stage.flatMapSingle(result -> tx.namedDml(
                            "insert-pokemon", entry.getKey(), entry.getValue().getName()));
                }
            }
            return stage;
        }).await();

        // Init pokemon to type relation
        dbClient.inTransaction(tx -> {
            Single<Long> stage = null;
            for (Map.Entry<Integer, Pokemon> entry : POKEMONS.entrySet()) {
                Pokemon pokemon = entry.getValue();
                for (Type type : pokemon.getTypes()) {
                    if (stage == null) {
                        stage = tx.namedDml("insert-poketype", pokemon.getId(), type.getId());
                    } else {
                        stage = stage.flatMapSingle(result -> tx.namedDml(
                                "insert-poketype", pokemon.getId(), type.getId()));
                    }
                }
            }
            return stage;
        }).await();
    }

    /**
     * Setup database for tests.
     */
    @BeforeAll
    public static void setup() {
        LOGGER.info(() -> "Initializing Integration Tests");

        initSchema(DB_CLIENT);
        initData(DB_CLIENT);
    }

    /**
     * Verify that database contains properly initialized pokemon types.
     *
     */
    @Test
    public void testListTypes() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-types"));

        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.collectList().await(5, TimeUnit.SECONDS);
        assertThat(rowsList, not(empty()));
        Set<Integer> ids = new HashSet<>(TYPES.keySet());
        for (DbRow row : rowsList) {
            Integer id = row.column(1).as(Integer.class);
            String name = row.column(2).as(String.class);
            assertThat(ids, hasItem(id));
            ids.remove(id);
            assertThat(name, TYPES.get(id).getName().equals(name));
        }
    }

    /**
     * Verify that database contains properly initialized pokemons.
     *
     */
    @Test
    public void testListPokemons() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-pokemons"));

        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.collectList().await();
        assertThat(rowsList, not(empty()));
        Set<Integer> ids = new HashSet<>(POKEMONS.keySet());
        for (DbRow row : rowsList) {
            Integer id = row.column(1).as(Integer.class);
            String name = row.column(2).as(String.class);
            assertThat(ids, hasItem(id));
            ids.remove(id);
            assertThat(name, POKEMONS.get(id).getName().equals(name));
        }
    }

    /**
     * Verify that database contains properly initialized pokemon types relation.
     *
     */
    @Test
    public void testListPokemonTypes() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-pokemons"));
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.collectList().await();
        assertThat(rowsList, not(empty()));

        for (DbRow row : rowsList) {
            Integer pokemonId = row.column(1).as(Integer.class);
            String pokemonName = row.column(2).as(String.class);
            Pokemon pokemon = POKEMONS.get(pokemonId);
            assertThat(pokemonName, POKEMONS.get(pokemonId).getName().equals(pokemonName));
            Multi<DbRow> typeRows = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-poketypes", pokemonId));

            List<DbRow> typeRowsList = typeRows.collectList().await();
            assertThat(typeRowsList.size(), equalTo(pokemon.getTypes().size()));
            for (DbRow typeRow : typeRowsList) {
                Integer typeId = typeRow.column(2).as(Integer.class);
                assertThat(pokemon.getTypes(), hasItem(TYPES.get(typeId)));
            }
        }
    }

}
