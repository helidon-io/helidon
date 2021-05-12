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
import static io.helidon.tests.integration.dbclient.common.AbstractIT.DELETE_POKEMON_ORDER_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.LAST_POKEMON_ID;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.TYPES;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyDeletePokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;

/**
 * Test set of basic JDBC delete calls in transaction.
 */
public class TransactionDeleteIT extends AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(TransactionDeleteIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 230;

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
     * Initialize tests of basic JDBC deletes.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @BeforeAll
    public static void setup() throws ExecutionException, InterruptedException {
        try {
            int curId = BASE_ID;
            addPokemon(new Pokemon(++curId, "Omanyte", TYPES.get(6), TYPES.get(11)));  // BASE_ID+1
            addPokemon(new Pokemon(++curId, "Omastar", TYPES.get(6), TYPES.get(11)));  // BASE_ID+2
            addPokemon(new Pokemon(++curId, "Kabuto", TYPES.get(6), TYPES.get(11)));   // BASE_ID+3
            addPokemon(new Pokemon(++curId, "Kabutops", TYPES.get(6), TYPES.get(11))); // BASE_ID+4
            addPokemon(new Pokemon(++curId, "Chikorita", TYPES.get(12)));              // BASE_ID+5
            addPokemon(new Pokemon(++curId, "Bayleef", TYPES.get(12)));                // BASE_ID+6
            addPokemon(new Pokemon(++curId, "Meganium", TYPES.get(12)));               // BASE_ID+7
        } catch (Exception ex) {
            LOGGER.warning(() -> String.format("Exception in setup: %s", ex));
            throw ex;
        }
    }

    /**
     * Verify {@code createNamedDelete(String, String)} API method with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedDeleteStrStrOrderArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedDelete("delete-rayquaza", DELETE_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(BASE_ID+1).getId()).execute()
        ).toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+1));
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedDeleteStrNamedArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedDelete("delete-pokemon-named-arg")
                .addParam("id", POKEMONS.get(BASE_ID+2).getId()).execute()
        ).toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+2));
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedDeleteStrOrderArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedDelete("delete-pokemon-order-arg")
                .addParam(POKEMONS.get(BASE_ID+3).getId()).execute()
        ).toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+3));
    }

    /**
     * Verify {@code createDelete(String)} API method with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateDeleteNamedArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(exec -> exec
                .createDelete(DELETE_POKEMON_NAMED_ARG)
                .addParam("id", POKEMONS.get(BASE_ID+4).getId()).execute()
        ).toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+4));
    }

    /**
     * Verify {@code createDelete(String)} API method with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateDeleteOrderArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.execute(tx -> tx
                .createDelete(DELETE_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(BASE_ID+5).getId()).execute()
        ).toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+5));
    }

    /**
     * Verify {@code namedDelete(String)} API method with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testNamedDeleteOrderArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .namedDelete("delete-pokemon-order-arg", POKEMONS.get(BASE_ID+6).getId())
        ).toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+6));
    }

    /**
     * Verify {@code delete(String)} API method with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testDeleteOrderArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .delete(DELETE_POKEMON_ORDER_ARG, POKEMONS.get(BASE_ID+7).getId())
        ).toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+7));
    }

}
