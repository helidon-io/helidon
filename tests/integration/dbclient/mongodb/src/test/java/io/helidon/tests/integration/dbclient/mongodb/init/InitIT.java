/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.dbclient.mongodb.init;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Initialize database
 */
public class InitIT extends AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(InitIT.class.getName());

    /**
     * Initialize database content (rows in tables).
     *
     * @param dbClient Helidon database client
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    private static void initData(DbClient dbClient) throws InterruptedException, ExecutionException {
        // Init pokemon types
        dbClient.execute(tx -> {
            CompletionStage<Long> stage = null;
            for (Map.Entry<Integer, Type> entry : TYPES.entrySet()) {
                if (stage == null) {
                    stage = tx.namedInsert("insert-type", entry.getKey(), entry.getValue().getName());
                } else {
                    stage = stage.thenCompose(result -> tx.namedInsert(
                            "insert-type", entry.getKey(), entry.getValue().getName()));
                }
            }
            return stage;
        }).toCompletableFuture().get();
        // Init pokemons
        dbClient.execute(tx -> {
            CompletionStage<Long> stage = null;
            for (Map.Entry<Integer, Pokemon> entry : POKEMONS.entrySet()) {
                if (stage == null) {
                    stage = tx.namedInsert("insert-pokemon", entry.getKey(), entry.getValue().getName());
                } else {
                    stage = stage.thenCompose(result -> tx.namedInsert(
                            "insert-pokemon", entry.getKey(), entry.getValue().getName()));
                }
            }
            return stage;
        }).toCompletableFuture().get();
        // Init pokemon to type relation
        dbClient.execute(tx -> {
            CompletionStage<Long> stage = null;
            for (Map.Entry<Integer, Pokemon> entry : POKEMONS.entrySet()) {
                Pokemon pokemon = entry.getValue();
                for (Type type : pokemon.getTypes()) {
                    if (stage == null) {
                        stage = tx.namedInsert("insert-poketype", pokemon.getId(), type.getId());
                    } else {
                        stage = stage.thenCompose(result -> tx.namedInsert(
                                "insert-poketype", pokemon.getId(), type.getId()));
                    }
                }
            }
            return stage;
        }).toCompletableFuture().get();
    }

    /**
     * Setup database for tests.
     */
    @BeforeAll
    public static void setup() {
        LOGGER.info(() ->  "Initializing Integration Tests");
        try {
            initData(DB_CLIENT);
        } catch (ExecutionException | InterruptedException ex) {
            fail("Database setup failed!", ex);
        }
    }

    /**
     * Verify that database contains properly initialized pokemon types.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testListTypes() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-types")
        ).toCompletableFuture().get();
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.collect().toCompletableFuture().get();
        assertThat(rowsList, not(empty()));
        Set<Integer> ids = new HashSet<>(TYPES.keySet());
        for (DbRow row : rowsList) {
            Integer id = row.column(1).as(Integer.class);
            String name = row.column(2).as(String.class);
            assertThat(ids, hasItem(id));
            ids.remove(id);
            assertThat(name, TYPES.get(id).getName().equals(name));
            LOGGER.info(() -> String.format("Type id=%d name=%s", id, name));
        }
    }

    /**
     * Verify that database contains properly initialized pokemons.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testListPokemons() throws ExecutionException, InterruptedException {
         DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-pokemons")
        ).toCompletableFuture().get();
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.collect().toCompletableFuture().get();
        assertThat(rowsList, not(empty()));
        Set<Integer> ids = new HashSet<>(POKEMONS.keySet());
        for (DbRow row : rowsList) {
            Integer id = row.column(1).as(Integer.class);
            String name = row.column(2).as(String.class);
            assertThat(ids, hasItem(id));
            ids.remove(id);
            assertThat(name, POKEMONS.get(id).getName().equals(name));
            LOGGER.info(() -> String.format("Pokemon id=%d name=%s", id, name));
        }
    }

    /**
     * Verify that database contains properly initialized pokemon types relation.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testListPokemonTypes() throws ExecutionException, InterruptedException {
         DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-pokemons")
        ).toCompletableFuture().get();
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.collect().toCompletableFuture().get();
        assertThat(rowsList, not(empty()));
        for (DbRow row : rowsList) {
            Integer pokemonId = row.column(1).as(Integer.class);
            String pokemonName = row.column(2).as(String.class);
            Pokemon pokemon = POKEMONS.get(pokemonId);
            assertThat(pokemonName, POKEMONS.get(pokemonId).getName().equals(pokemonName));
            LOGGER.info(() -> String.format("Pokemon id=%d name=%s", pokemonId, pokemonName));
            DbRows<DbRow> typeRows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-poketypes", pokemonId)
            ).toCompletableFuture().get();
            List<DbRow> typeRowsList = typeRows.collect().toCompletableFuture().get();
            assertThat(typeRowsList.size(), equalTo(pokemon.getTypes().size()));
            for (DbRow typeRow : typeRowsList) {
                Integer typeId = typeRow.column(2).as(Integer.class);
                LOGGER.info(() -> String.format(" - Type id=%d name=%s", typeId, TYPES.get(typeId).getName()));
                assertThat(pokemon.getTypes(), hasItem(TYPES.get(typeId)));
            }
        }
    }

}
