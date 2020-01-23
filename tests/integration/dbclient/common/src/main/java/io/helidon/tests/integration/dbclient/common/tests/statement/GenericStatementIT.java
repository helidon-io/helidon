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
package io.helidon.tests.integration.dbclient.common.tests.statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.tests.integration.dbclient.common.AbstractIT;
import io.helidon.tests.integration.dbclient.common.utils.RangePoJo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.LAST_POKEMON_ID;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.TYPES;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyPokemonsIdRange;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyUpdatePokemon;

/**
 * Test DbStatementGeneric methods.
 */
public class GenericStatementIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(GenericStatementIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 110;

    /** Map of pokemons for DbStatementDml methods tests. */
    private static final Map<Integer, AbstractIT.Pokemon> POKEMONS = new HashMap<>();

    private static void addPokemon(AbstractIT.Pokemon pokemon) throws ExecutionException, InterruptedException {
        POKEMONS.put(pokemon.getId(), pokemon);
        Long result = DB_CLIENT.execute(exec -> exec
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName())
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Initialize DbStatementDml methods tests.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @BeforeAll
    public static void setup() throws ExecutionException, InterruptedException {
        try {
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 0, "Klink", TYPES.get(9)));                   // BASE_ID+0
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 1, "Klang", TYPES.get(9)));                   // BASE_ID+1
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 2, "Klinklang", TYPES.get(9)));               // BASE_ID+2
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 3, "Drilbur", TYPES.get(5)));                 // BASE_ID+3
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 4, "Excadrill", TYPES.get(5), TYPES.get(9))); // BASE_ID+4
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 5, "Ducklett", TYPES.get(3), TYPES.get(11))); // BASE_ID+5
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 6, "Swanna", TYPES.get(3), TYPES.get(11)));   // BASE_ID+6
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
    public void testQueryArrayParams() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("select-pokemons-idrng-order-arg")
                .params(1, 7)
                .execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryListParams() throws ExecutionException, InterruptedException {
        List<Integer> params = new ArrayList<>(2);
        params.add(1);
        params.add(7);
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("select-pokemons-idrng-order-arg")
                .params(params)
                .execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryMapParams() throws ExecutionException, InterruptedException {
        Map<String, Integer> params = new HashMap<>(2);
        params.put("idmin", 1);
        params.put("idmax", 7);
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("select-pokemons-idrng-named-arg")
                .params(params)
                .execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryOrderParam() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("select-pokemons-idrng-order-arg")
                .addParam(1)
                .addParam(7)
                .execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryNamedParam() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("select-pokemons-idrng-named-arg")
                .addParam("idmin", 1)
                .addParam("idmax", 7)
                .execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryMappedNamedParam() throws ExecutionException, InterruptedException {
        RangePoJo range = new RangePoJo(1, 7);
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("select-pokemons-idrng-named-arg")
                .namedParam(range)
                .execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryMappedOrderParam() throws ExecutionException, InterruptedException {
        RangePoJo range = new RangePoJo(1, 7);
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("select-pokemons-idrng-order-arg")
                .indexedParam(range)
                .execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testDmlArrayParams() throws ExecutionException, InterruptedException {
        AbstractIT.Pokemon pokemon = new AbstractIT.Pokemon(BASE_ID + 0, "Klang", TYPES.get(9));
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("update-pokemon-order-arg")
                .params(pokemon.getName(), pokemon.getId())
                .execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
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
        AbstractIT.Pokemon pokemon = new AbstractIT.Pokemon(BASE_ID + 1, "Klinklang", TYPES.get(9));
        List<Object> params = new ArrayList<>(2);
        params.add(pokemon.getName());
        params.add(pokemon.getId());
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("update-pokemon-order-arg")
                .params(params)
                .execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
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
        AbstractIT.Pokemon pokemon = new AbstractIT.Pokemon(BASE_ID + 2, "Klink", TYPES.get(9));
        Map<String, Object> params = new HashMap<>(2);
        params.put("name", pokemon.getName());
        params.put("id", pokemon.getId());
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("update-pokemon-named-arg")
                .params(params)
                .execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
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
        AbstractIT.Pokemon pokemon = new AbstractIT.Pokemon(BASE_ID + 3, "Excadrill", TYPES.get(5), TYPES.get(9));
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("update-pokemon-order-arg")
                .addParam(pokemon.getName())
                .addParam(pokemon.getId())
                .execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
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
        AbstractIT.Pokemon pokemon = new AbstractIT.Pokemon(BASE_ID + 4, "Drilbur", TYPES.get(5));
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("update-pokemon-named-arg")
                .addParam("name", pokemon.getName())
                .addParam("id", pokemon.getId())
                .execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
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
        AbstractIT.Pokemon pokemon = new AbstractIT.Pokemon(BASE_ID + 5, "Swanna", TYPES.get(3), TYPES.get(11));
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("update-pokemon-named-arg")
                .namedParam(pokemon)
                .execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
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
        AbstractIT.Pokemon pokemon = new AbstractIT.Pokemon(BASE_ID + 6, "Ducklett", TYPES.get(3), TYPES.get(11));
        Long result = DB_CLIENT.execute(exec -> exec
                .createNamedStatement("update-pokemon-order-arg")
                .indexedParam(pokemon)
                .execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyUpdatePokemon(result, pokemon);
    }

}
