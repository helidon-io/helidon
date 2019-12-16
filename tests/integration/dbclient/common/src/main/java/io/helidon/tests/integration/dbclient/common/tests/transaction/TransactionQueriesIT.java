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

import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.POKEMONS;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.SELECT_POKEMON_NAMED_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.SELECT_POKEMON_ORDER_ARG;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyPokemon;

/**
 * Test set of basic JDBC queries in transaction.
 */
public class TransactionQueriesIT extends AbstractIT {

    /** Local logger instance. */
    static final Logger LOGGER = Logger.getLogger(TransactionQueriesIT.class.getName());

    /**
     * Verify {@code createNamedQuery(String, String)} API method with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedQueryStrStrOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .createNamedQuery("select-pikachu", SELECT_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(1).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedQueryStrNamedArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", POKEMONS.get(2).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(2));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedQueryStrOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .createNamedQuery("select-pokemon-order-arg")
                .addParam(POKEMONS.get(3).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(3));
    }

    /**
     * Verify {@code createQuery(String)} API method with named parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateQueryNamedArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .createQuery(SELECT_POKEMON_NAMED_ARG)
                .addParam("name", POKEMONS.get(4).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(4));
    }

    /**
     * Verify {@code createQuery(String)} API method with ordered parameters.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testCreateQueryOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .createQuery(SELECT_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(5).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(5));
    }

    /**
     * Verify {@code namedQuery(String)} API method with ordered parameters passed directly to the {@code namedQuery} method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testNamedQueryOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .namedQuery("select-pokemon-order-arg", POKEMONS.get(6).getName())
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(6));
    }

    /**
     * Verify {@code query(String)} API method with ordered parameters passed directly to the {@code query} method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .query(SELECT_POKEMON_ORDER_ARG, POKEMONS.get(7).getName())
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(7));
    }

}
