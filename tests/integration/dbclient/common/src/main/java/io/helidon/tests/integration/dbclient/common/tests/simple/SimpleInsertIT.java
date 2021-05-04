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
package io.helidon.tests.integration.dbclient.common.tests.simple;

import java.util.concurrent.ExecutionException;

import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;

/**
 * Test set of basic JDBC inserts.
 */
public class SimpleInsertIT extends AbstractIT {

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 10;

    /**
     * Verify {@code createNamedInsert(String, String)} API method with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedInsertStrStrNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+1, "Bulbasaur", TYPES.get(4), TYPES.get(12));
        Long result = DB_CLIENT.execute(exec -> exec
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
        Pokemon pokemon = new Pokemon(BASE_ID+2, "Ivysaur", TYPES.get(4), TYPES.get(12));
       Long result = DB_CLIENT.execute(exec -> exec
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
        Pokemon pokemon = new Pokemon(BASE_ID+3, "Venusaur", TYPES.get(4), TYPES.get(12));
       Long result = DB_CLIENT.execute(exec -> exec
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
        Pokemon pokemon = new Pokemon(BASE_ID+4, "Magby", TYPES.get(10));
       Long result = DB_CLIENT.execute(exec -> exec
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
        Pokemon pokemon = new Pokemon(BASE_ID+5, "Magmar", TYPES.get(10));
       Long result = DB_CLIENT.execute(exec -> exec
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
        Pokemon pokemon = new Pokemon(BASE_ID+6, "Rattata", TYPES.get(1));
        Long result = DB_CLIENT.execute(exec -> exec
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
        Pokemon pokemon = new Pokemon(BASE_ID+7, "Raticate", TYPES.get(1));
       Long result = DB_CLIENT.execute(exec -> exec
                .insert(INSERT_POKEMON_ORDER_ARG, pokemon.getId(), pokemon.getName())
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

}
