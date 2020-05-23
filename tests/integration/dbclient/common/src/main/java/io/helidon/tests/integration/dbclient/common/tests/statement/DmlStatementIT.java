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
package io.helidon.tests.integration.dbclient.common.tests.statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyUpdatePokemon;

/**
 * Test DbStatementDml methods.
 */
public class DmlStatementIT extends AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(DmlStatementIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 100;

    /** Map of pokemons for DbStatementDml methods tests. */
    private static final Map<Integer, Pokemon> POKEMONS = new HashMap<>();

    private static void addPokemon(Pokemon pokemon) {
        POKEMONS.put(pokemon.getId(), pokemon);
        Long result = DB_CLIENT.execute(exec -> exec
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName()))
                .await();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Initialize DbStatementDml methods tests.
     *
     */
    @BeforeAll
    public static void setup() {
        try {
            addPokemon(new Pokemon(BASE_ID + 0, "Shinx", TYPES.get(13)));               // BASE_ID+0
            addPokemon(new Pokemon(BASE_ID + 1, "Luxio", TYPES.get(13)));               // BASE_ID+1
            addPokemon(new Pokemon(BASE_ID + 2, "Luxray", TYPES.get(13)));              // BASE_ID+2
            addPokemon(new Pokemon(BASE_ID + 3, "Kricketot", TYPES.get(7)));            // BASE_ID+3
            addPokemon(new Pokemon(BASE_ID + 4, "Kricketune", TYPES.get(7)));           // BASE_ID+4
            addPokemon(new Pokemon(BASE_ID + 5, "Phione", TYPES.get(11)));              // BASE_ID+5
            addPokemon(new Pokemon(BASE_ID + 6, "Chatot", TYPES.get(1), TYPES.get(3))); // BASE_ID+6
        } catch (Exception ex) {
            LOGGER.warning(() -> String.format("Exception in setup: %s", ex));
            throw ex;
        }
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testDmlArrayParams() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID + 0, "Luxio", TYPES.get(13));
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedDmlStatement("update-pokemon-order-arg")
                .params(pokemon.getName(), pokemon.getId())
                .execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, pokemon);
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testDmlListParams() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID + 1, "Luxray", TYPES.get(13));
        List<Object> params = new ArrayList<>(2);
        params.add(pokemon.getName());
        params.add(pokemon.getId());
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedDmlStatement("update-pokemon-order-arg")
                .params(params)
                .execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, pokemon);
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testDmlMapParams() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID + 2, "Shinx", TYPES.get(13));
        Map<String, Object> params = new HashMap<>(2);
        params.put("name", pokemon.getName());
        params.put("id", pokemon.getId());
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedDmlStatement("update-pokemon-named-arg")
                .params(params)
                .execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, pokemon);
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testDmlOrderParam() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID + 3, "Kricketune", TYPES.get(7));
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedDmlStatement("update-pokemon-order-arg")
                .addParam(pokemon.getName())
                .addParam(pokemon.getId())
                .execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, pokemon);
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testDmlNamedParam() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID + 4, "Kricketot", TYPES.get(7));
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedDmlStatement("update-pokemon-named-arg")
                .addParam("name", pokemon.getName())
                .addParam("id", pokemon.getId())
                .execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, pokemon);
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testDmlMappedNamedParam() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID + 5, "Chatot", TYPES.get(1), TYPES.get(3));
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedDmlStatement("update-pokemon-named-arg")
                .namedParam(pokemon)
                .execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, pokemon);
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testDmlMappedOrderParam() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID + 6, "Phione", TYPES.get(11));
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedDmlStatement("update-pokemon-order-arg")
                .indexedParam(pokemon)
                .execute()
        ).toCompletableFuture().get();
        verifyUpdatePokemon(result, pokemon);
    }

}
