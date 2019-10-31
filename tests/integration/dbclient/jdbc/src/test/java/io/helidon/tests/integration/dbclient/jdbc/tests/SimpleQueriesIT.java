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

import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.tests.integration.dbclient.jdbc.AbstractIT;

import static io.helidon.tests.integration.dbclient.jdbc.AbstractIT.dbClient;
import static io.helidon.tests.integration.dbclient.jdbc.tests.Utils.verifyPokemon;

/**
 * Test set of basic JDBC queries.
 */
public class SimpleQueriesIT extends AbstractIT {

    static final Logger LOG = Logger.getLogger(SimpleQueriesIT.class.getName());

    /**
     * Verify {@code createNamedQuery(String, String)} API method with ordered parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedQueryStrStrOrderArgs() throws ExecutionException, InterruptedException {
        // This call shall register named query
        DbRows<DbRow> rows = dbClient.execute(exec -> exec
                .createNamedQuery("select-pikachu", "SELECT id, name FROM Pokemons WHERE id=?")
                .addParam(POKEMONS.get(1).getId()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(1));
// Not implemented yet
//        // Run newly created named query
//        rows = dbClient.execute(exec -> exec
//                .createNamedQuery("select-pikachu").addParam("id", 1).execute()
//        ).toCompletableFuture().get();
//        assertThat(rows, notNullValue());
//        rowsList = rows.collect().toCompletableFuture().get();
//        row = rowsList.get(0);
//        name = row.column(2).as(String.class);
//        assertThat(name, POKEMONS.get(1).getName().equals(name));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with named parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedQueryStrNamedArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = dbClient.execute(exec -> exec
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", POKEMONS.get(2).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(2));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with ordered parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedQueryStrOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = dbClient.execute(exec -> exec
                .createNamedQuery("select-pokemon-order-arg")
                .addParam(POKEMONS.get(3).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(3));
    }

    /**
     * Verify {@code createQuery(String)} API method with named parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateQueryNamedArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = dbClient.execute(exec -> exec
                .createQuery("SELECT id, name FROM Pokemons WHERE name=:name")
                .addParam("name", POKEMONS.get(4).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(4));
    }

    /**
     * Verify {@code createQuery(String)} API method with ordered parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateQueryOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = dbClient.execute(exec -> exec
                .createQuery("SELECT id, name FROM Pokemons WHERE name=?")
                .addParam(POKEMONS.get(5).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(5));
    }

    /**
     * Verify {@code namedQuery(String)} API method with ordered parameters passed directly to the {@code namedQuery} method.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testNamedQueryOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = dbClient.execute(exec -> exec
                .namedQuery("select-pokemon-order-arg", POKEMONS.get(6).getName())
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(6));
    }

    /**
     * Verify {@code query(String)} API method with ordered parameters passed directly to the {@code query} method.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testQueryOrderArgs() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = dbClient.execute(exec -> exec
                .query("SELECT id, name FROM Pokemons WHERE name=?", POKEMONS.get(7).getName())
        ).toCompletableFuture().get();
        verifyPokemon(rows, POKEMONS.get(7));
    }

}
