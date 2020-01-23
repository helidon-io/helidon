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
package io.helidon.tests.integration.dbclient.common.tests.transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.DELETE_POKEMON_NAMED_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.DELETE_POKEMON_ORDER_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.INSERT_POKEMON_NAMED_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.INSERT_POKEMON_ORDER_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.LAST_POKEMON_ID;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.SELECT_POKEMON_NAMED_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.SELECT_POKEMON_ORDER_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.TYPES;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.UPDATE_POKEMON_NAMED_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.UPDATE_POKEMON_ORDER_ARG;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyDeletePokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyUpdatePokemon;

/**
 * Test set of basic JDBC common statement calls in transaction.
 */
public class TransactionStatementIT extends AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(TransactionStatementIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 270;

    /** Map of pokemons for update tests. */
    private static final Map<Integer, AbstractIT.Pokemon> POKEMONS = new HashMap<>();

    private static void addPokemon(AbstractIT.Pokemon pokemon) throws ExecutionException, InterruptedException {
        POKEMONS.put(pokemon.getId(), pokemon);
        Long result = DB_CLIENT.execute(exec -> exec
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName())
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Initialize tests of basic JDBC updates.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @BeforeAll
    public static void setup() throws ExecutionException, InterruptedException {
        try {
            // BASE_ID + 1 .. BASE_ID + 9 is reserved for inserts
            // BASE_ID + 10 .. BASE_ID + 19 are pokemons for updates
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 10, "Wailmer", TYPES.get(11)));                // BASE_ID+10
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 11, "Wailord", TYPES.get(11)));                // BASE_ID+11
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 12, "Plusle", TYPES.get(13)));                 // BASE_ID+12
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 13, "Minun", TYPES.get(13)));                  // BASE_ID+13
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 14, "Spheal", TYPES.get(11), TYPES.get(15)));  // BASE_ID+14
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 15, "Sealeo", TYPES.get(11), TYPES.get(15)));  // BASE_ID+15
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 16, "Walrein", TYPES.get(11), TYPES.get(15))); // BASE_ID+16
            // BASE_ID + 20 .. BASE_ID + 29 are pokemons for deletes
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 20, "Duskull", TYPES.get(8)));                  // BASE_ID+20
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 21, "Dusclops", TYPES.get(8)));                 // BASE_ID+21
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 22, "Shuppet", TYPES.get(8)));                  // BASE_ID+22
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 23, "Banette", TYPES.get(8)));                  // BASE_ID+23
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 24, "Bagon", TYPES.get(16)));                   // BASE_ID+24
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 25, "Shelgon", TYPES.get(16)));                 // BASE_ID+25
            addPokemon(new AbstractIT.Pokemon(BASE_ID + 26, "Salamence", TYPES.get(3), TYPES.get(16))); // BASE_ID+26
        } catch (Exception ex) {
            LOGGER.warning(() -> String.format("Exception in setup: %s", ex));
            throw ex;
        }
    }

    /**
     * Verify {@code createNamedStatement(String, String)} API method with query with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatementWithQueryStrStrOrderArgs() throws ExecutionException, InterruptedException {
        // This call shall register named query
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("select-pikachu", SELECT_POKEMON_ORDER_ARG)
                .addParam(AbstractIT.POKEMONS.get(1).getName()).execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemon(rows, AbstractIT.POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedStatement(String)} API method with query with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatementWithQueryStrNamedArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("select-pokemon-named-arg")
                .addParam("name", AbstractIT.POKEMONS.get(2).getName()).execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemon(rows, AbstractIT.POKEMONS.get(2));
    }

    /**
     * Verify {@code createNamedStatement(String)} API method with query with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatementWithQueryStrOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("select-pokemon-order-arg")
                .addParam(AbstractIT.POKEMONS.get(3).getName()).execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemon(rows, AbstractIT.POKEMONS.get(3));
    }

    /**
     * Verify {@code createStatement(String)} API method with query with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateStatementWithQueryNamedArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .createStatement(SELECT_POKEMON_NAMED_ARG)
                .addParam("name", AbstractIT.POKEMONS.get(4).getName()).execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemon(rows, AbstractIT.POKEMONS.get(4));
    }

    /**
     * Verify {@code createStatement(String)} API method with query with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateStatementWithQueryOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .createStatement(SELECT_POKEMON_ORDER_ARG)
                .addParam(AbstractIT.POKEMONS.get(5).getName()).execute()
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemon(rows, AbstractIT.POKEMONS.get(5));
    }

    /**
     * Verify {@code namedStatement(String)} API method with query with ordered parameters passed directly to the {@code namedQuery} method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testNamedStatementWithQueryOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .namedStatement("select-pokemon-order-arg", AbstractIT.POKEMONS.get(6).getName())
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemon(rows, AbstractIT.POKEMONS.get(6));
    }

    /**
     * Verify {@code statement(String)} API method with query with ordered parameters passed directly to the {@code query} method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testStatementWithQueryOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .statement(SELECT_POKEMON_ORDER_ARG, AbstractIT.POKEMONS.get(7).getName())
        ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        verifyPokemon(rows, AbstractIT.POKEMONS.get(7));
    }

    /**
     * Verify {@code createNamedStatement(String, String)} API method with insert with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatementWithInsertStrStrNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+1, "Swablu", TYPES.get(1), TYPES.get(3));
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("insert-zubat", INSERT_POKEMON_NAMED_ARG)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedStatement(String)} API method with insert with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatementWithInsertStrNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+2, "Altaria", TYPES.get(3), TYPES.get(16));
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("insert-pokemon-named-arg")
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedStatement(String)} API method with insert with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatementWithInsertStrOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+3, "Corphish", TYPES.get(11));
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("insert-pokemon-order-arg")
                .addParam(pokemon.getId()).addParam(pokemon.getName()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createStatement(String)} API method with insert with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateStatementWithInsertNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+4, "Crawdaunt", TYPES.get(11), TYPES.get(17));
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createStatement(INSERT_POKEMON_NAMED_ARG)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createStatement(String)} API method with insert with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateStatementWithInsertOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+5, "Whismur", TYPES.get(1));
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createStatement(INSERT_POKEMON_ORDER_ARG)
                .addParam(pokemon.getId()).addParam(pokemon.getName()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code namedStatement(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testNamedStatementWithInsertOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+6, "Loudred", TYPES.get(1));
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .namedStatement("insert-pokemon-order-arg", pokemon.getId(), pokemon.getName())
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code statement(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testStatementWithInsertOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon pokemon = new Pokemon(BASE_ID+7, "Exploud", TYPES.get(1));
       Long result = DB_CLIENT.inTransaction(tx -> tx
                .statement(INSERT_POKEMON_ORDER_ARG, pokemon.getId(), pokemon.getName())
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedStatement(String, String)} API method with update with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatementWithUpdateStrStrNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+10);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+10, "Wailord", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("update-piplup", UPDATE_POKEMON_NAMED_ARG)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedStatement(String)} API method with update with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatementWithUpdateStrNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+11);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+11, "Wailmer", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("update-pokemon-named-arg")
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedStatement(String)} API method with update with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatementWithUpdateStrOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+12);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+12, "Minun", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("update-pokemon-order-arg")
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createStatement(String)} API method with update with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateStatemenWithUpdateNamedArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+13);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+13, "Plusle", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createStatement(UPDATE_POKEMON_NAMED_ARG)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createStatement(String)} API method with update with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateStatemenWithUpdateOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+14);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+14, "Sealeo", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createStatement(UPDATE_POKEMON_ORDER_ARG)
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code namedStatement(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testNamedStatemenWithUpdateOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+15);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+15, "Walrein", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .namedStatement("update-pokemon-order-arg", updatedPokemon.getName(), updatedPokemon.getId())
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code statement(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testStatemenWithUpdateOrderArgs() throws ExecutionException, InterruptedException {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+16);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+16, "Spheal", srcPokemon.getTypesArray());
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .statement(UPDATE_POKEMON_ORDER_ARG, updatedPokemon.getName(), updatedPokemon.getId())
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedStatement(String, String)} API method with delete with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatemenWithDeleteStrStrOrderArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("delete-mudkip", DELETE_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(BASE_ID+20).getId()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+20));
    }

    /**
     * Verify {@code createNamedStatement(String)} API method with delete with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatemenWithDeleteStrNamedArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("delete-pokemon-named-arg")
                .addParam("id", POKEMONS.get(BASE_ID+21).getId()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+21));
    }

    /**
     * Verify {@code createNamedStatement(String)} API method with delete with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedStatemenWithDeleteStrOrderArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedStatement("delete-pokemon-order-arg")
                .addParam(POKEMONS.get(BASE_ID+22).getId()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+22));
    }

    /**
     * Verify {@code createStatement(String)} API method with delete with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateStatemenWithDeleteNamedArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createStatement(DELETE_POKEMON_NAMED_ARG)
                .addParam("id", POKEMONS.get(BASE_ID+23).getId()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+23));
    }

    /**
     * Verify {@code createStatement(String)} API method with delete with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateStatemenWithDeleteOrderArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createStatement(DELETE_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(BASE_ID+24).getId()).execute()
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+24));
    }

    /**
     * Verify {@code namedStatement(String)} API method with delete with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testNamedStatemenWithDeleteOrderArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .namedStatement("delete-pokemon-order-arg", POKEMONS.get(BASE_ID+25).getId())
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+25));
    }

    /**
     * Verify {@code statement(String)} API method with delete with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testStatemenWithDeleteOrderArgs() throws ExecutionException, InterruptedException {
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .statement(DELETE_POKEMON_ORDER_ARG, POKEMONS.get(BASE_ID+26).getId())
        ).toCompletableFuture().get().dmlFuture().toCompletableFuture().get();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+26));
    }

}
