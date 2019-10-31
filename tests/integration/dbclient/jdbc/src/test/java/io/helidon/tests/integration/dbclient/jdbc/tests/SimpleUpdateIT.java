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
package io.helidon.tests.integration.dbclient.jdbc.tests;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.helidon.tests.integration.dbclient.jdbc.AbstractIT;

import static io.helidon.tests.integration.dbclient.jdbc.tests.Utils.verifyInsertPokemon;
import static io.helidon.tests.integration.dbclient.jdbc.tests.Utils.verifyUpdatePokemon;

/**
 * Test set of basic JDBC updates.
 */
public class SimpleUpdateIT extends AbstractIT {

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 20;

    /** Map of pokemons for update tests. */
    private static final Map<Integer, Pokemon> POKEMONS = new HashMap<>();

    private static void addPokemon(Pokemon pokemon) throws ExecutionException, InterruptedException {
        POKEMONS.put(pokemon.getId(), pokemon);
        Long result = dbClient.execute(exec -> exec
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName())
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Initialize tests of basic JDBC updates.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @BeforeAll
    public static void setup() throws ExecutionException, InterruptedException {
        int curId = BASE_ID;
        addPokemon(new Pokemon(++curId, "Spearow", TYPES.get(1), TYPES.get(3))); // BASE_ID+1
        addPokemon(new Pokemon(++curId, "Fearow", TYPES.get(1), TYPES.get(3)));  // BASE_ID+2
        addPokemon(new Pokemon(++curId, "Ekans", TYPES.get(4)));                 // BASE_ID+3
        addPokemon(new Pokemon(++curId, "Arbok", TYPES.get(4)));                 // BASE_ID+4
        addPokemon(new Pokemon(++curId, "Sandshrew", TYPES.get(5)));             // BASE_ID+5
        addPokemon(new Pokemon(++curId, "Sandslash", TYPES.get(5)));             // BASE_ID+6
        addPokemon(new Pokemon(++curId, "Diglett", TYPES.get(5)));               // BASE_ID+7
    }

    /**
     * Verify {@code createNamedUpdate(String, String)} API method with named parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedUpdateStrStrNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+1);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+1, "Fearow", srcPokemon.getTypesArray());
        Long result = dbClient.execute(exec -> exec
                .createNamedUpdate("update-spearow", "UPDATE Pokemons SET name=:name WHERE id=:id")
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with named parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedUpdateStrNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+2);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+2, "Spearow", srcPokemon.getTypesArray());
        Long result = dbClient.execute(exec -> exec
                .createNamedUpdate("update-pokemon-named-arg")
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with ordered parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedUpdateStrOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+3);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+3, "Arbok", srcPokemon.getTypesArray());
        Long result = dbClient.execute(exec -> exec
                .createNamedUpdate("update-pokemon-order-arg")
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId()).execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateUpdateNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+4);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+4, "Ekans", srcPokemon.getTypesArray());
        Long result = dbClient.execute(exec -> exec
                .createUpdate("UPDATE Pokemons SET name=:name WHERE id=:id")
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createUpdate(String)} API method with ordered parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateUpdateOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+5);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+5, "Diglett", srcPokemon.getTypesArray());
        Long result = dbClient.execute(exec -> exec
                .createUpdate("UPDATE Pokemons SET name=? WHERE id=?")
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId()).execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code namedUpdate(String)} API method with named parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testNamedUpdateNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+6);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+6, "Sandshrew", srcPokemon.getTypesArray());
        Long result = dbClient.execute(exec -> exec
                .namedUpdate("update-pokemon-order-arg", updatedPokemon.getName(), updatedPokemon.getId())
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code update(String)} API method with ordered parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testUpdateOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+7);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+7, "Sandslash", srcPokemon.getTypesArray());
        Long result = dbClient.execute(exec -> exec
                .update("UPDATE Pokemons SET name=? WHERE id=?", updatedPokemon.getName(), updatedPokemon.getId())
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

}
