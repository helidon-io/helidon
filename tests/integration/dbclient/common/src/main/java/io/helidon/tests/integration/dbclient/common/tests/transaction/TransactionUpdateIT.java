/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.tests.transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.LAST_POKEMON_ID;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.TYPES;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.UPDATE_POKEMON_NAMED_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.UPDATE_POKEMON_ORDER_ARG;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyUpdatePokemon;

/**
 * Test set of basic JDBC updates in transaction.
 */
public class TransactionUpdateIT extends AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(TransactionUpdateIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 220;

    /** Map of pokemons for update tests. */
    private static final Map<Integer, Pokemon> POKEMONS = new HashMap<>();

    private static void addPokemon(Pokemon pokemon) throws ExecutionException, InterruptedException {
        POKEMONS.put(pokemon.getId(), pokemon);
        Long result = DB_CLIENT.execute(exec -> exec
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName())
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Initialize tests of basic JDBC updates.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws ExecutionException when database query failed
     */
    @BeforeAll
    public static void setup() throws ExecutionException, InterruptedException {
        try {
            int curId = BASE_ID;
            addPokemon(new Pokemon(++curId, "Teddiursa", TYPES.get(1)));                // BASE_ID+1
            addPokemon(new Pokemon(++curId, "Ursaring", TYPES.get(1)));                 // BASE_ID+2
            addPokemon(new Pokemon(++curId, "Slugma", TYPES.get(10)));                  // BASE_ID+3
            addPokemon(new Pokemon(++curId, "Magcargo", TYPES.get(6), TYPES.get(10)));  // BASE_ID+4
            addPokemon(new Pokemon(++curId, "Lotad", TYPES.get(11), TYPES.get(12)));    // BASE_ID+5
            addPokemon(new Pokemon(++curId, "Lombre", TYPES.get(11), TYPES.get(12)));   // BASE_ID+6
            addPokemon(new Pokemon(++curId, "Ludicolo", TYPES.get(11), TYPES.get(12))); // BASE_ID+7
        } catch (Exception ex) {
            LOGGER.warning(() -> String.format("Exception in setup: %s", ex));
            throw ex;
        }
    }

    /**
     * Verify {@code createNamedUpdate(String, String)} API method with named parameters.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testCreateNamedUpdateStrStrNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+1);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+1, "Ursaring", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedUpdate("update-spearow", UPDATE_POKEMON_NAMED_ARG)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with named parameters.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testCreateNamedUpdateStrNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+2);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+2, "Teddiursa", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedUpdate("update-pokemon-named-arg")
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with ordered parameters.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testCreateNamedUpdateStrOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+3);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+3, "Magcargo", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedUpdate("update-pokemon-order-arg")
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId()).execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testCreateUpdateNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+4);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+4, "Slugma", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createUpdate(UPDATE_POKEMON_NAMED_ARG)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createUpdate(String)} API method with ordered parameters.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testCreateUpdateOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+5);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+5, "Lombre", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createUpdate(UPDATE_POKEMON_ORDER_ARG)
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId()).execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code namedUpdate(String)} API method with named parameters.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testNamedUpdateNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+6);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+6, "Ludicolo", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .namedUpdate("update-pokemon-order-arg", updatedPokemon.getName(), updatedPokemon.getId())
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code update(String)} API method with ordered parameters.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testUpdateOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+7);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+7, "Lotad", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .update(UPDATE_POKEMON_ORDER_ARG, updatedPokemon.getName(), updatedPokemon.getId())
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

}
