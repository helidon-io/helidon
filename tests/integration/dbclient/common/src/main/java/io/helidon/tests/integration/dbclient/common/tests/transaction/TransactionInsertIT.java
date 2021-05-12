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

import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.INSERT_POKEMON_NAMED_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.INSERT_POKEMON_ORDER_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.LAST_POKEMON_ID;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.TYPES;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;

/**
 * Test set of basic JDBC inserts in transaction.
 */
public class TransactionInsertIT extends AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(TransactionInsertIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 210;

    /**
     * Verify {@code createNamedInsert(String, String)} API method with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedInsertStrStrNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+1, "Sentret", TYPES.get(1));
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedInsert("insert-bulbasaur", INSERT_POKEMON_NAMED_ARG)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute()
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedInsert(String)} API method with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedInsertStrNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+2, "Furret", TYPES.get(1));
       Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedInsert("insert-pokemon-named-arg")
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute()
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedInsert(String)} API method with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedInsertStrOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+3, "Chinchou", TYPES.get(11), TYPES.get(13));
       Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedInsert("insert-pokemon-order-arg")
                .addParam(pokemon.getId()).addParam(pokemon.getName()).execute()
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createInsert(String)} API method with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateInsertNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+4, "Lanturn", TYPES.get(11), TYPES.get(13));
       Long result = DB_CLIENT.inTransaction(tx -> tx
                .createInsert(INSERT_POKEMON_NAMED_ARG)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute()
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createInsert(String)} API method with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateInsertOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+5, "Swinub", TYPES.get(5), TYPES.get(15));
       Long result = DB_CLIENT.inTransaction(tx -> tx
                .createInsert(INSERT_POKEMON_ORDER_ARG)
                .addParam(pokemon.getId()).addParam(pokemon.getName()).execute()
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code namedInsert(String)} API method with ordered parameters passed directly to the {@code insert} method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testNamedInsertOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+6, "Piloswine", TYPES.get(5), TYPES.get(15));
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .namedInsert("insert-pokemon-order-arg", pokemon.getId(), pokemon.getName())
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code insert(String)} API method with ordered parameters passed directly to the {@code insert} method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testInsertOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+7, "Mamoswine", TYPES.get(5), TYPES.get(15));
       Long result = DB_CLIENT.inTransaction(tx -> tx
                .insert(INSERT_POKEMON_ORDER_ARG, pokemon.getId(), pokemon.getName())
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

}
