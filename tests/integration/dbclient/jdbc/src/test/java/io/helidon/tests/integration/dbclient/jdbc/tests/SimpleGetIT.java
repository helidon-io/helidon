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

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.jdbc.AbstractIT;

import static io.helidon.tests.integration.dbclient.jdbc.AbstractIT.POKEMONS;
import static io.helidon.tests.integration.dbclient.jdbc.AbstractIT.dbClient;
import static io.helidon.tests.integration.dbclient.jdbc.tests.Utils.verifyPokemon;

/**
 * Test set of basic JDBC get calls.
 */
public class SimpleGetIT extends AbstractIT {

    /**
     * Verify {@code createNamedGet(String, String)} API method with named parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedGetStrStrNamedArgs() throws ExecutionException, InterruptedException {
        Optional<DbRow> maybeRow = dbClient.execute(exec -> exec
                .createNamedGet("select-pikachu", "SELECT id, name FROM Pokemons WHERE id=:id")
                .addParam("id", 1).execute()
        ).toCompletableFuture().get();
        verifyPokemon(maybeRow, POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with named parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedGetStrNamedArgs() throws ExecutionException, InterruptedException {
        Optional<DbRow> maybeRow = dbClient.execute(exec -> exec
                .createNamedGet("select-pokemon-named-arg")
                .addParam("name", POKEMONS.get(1).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(maybeRow, POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with ordered parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedGetStrOrderArgs() throws ExecutionException, InterruptedException {
        Optional<DbRow> maybeRow = dbClient.execute(exec -> exec
                .createNamedGet("select-pokemon-order-arg")
                .addParam(POKEMONS.get(1).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(maybeRow, POKEMONS.get(1));
    }

    /**
     * Verify {@code createGet(String)} API method with named parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateGetNamedArgs() throws ExecutionException, InterruptedException {
        Optional<DbRow> maybeRow = dbClient.execute(exec -> exec
                .createGet("SELECT id, name FROM Pokemons WHERE name=:name")
                .addParam("name", POKEMONS.get(1).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(maybeRow, POKEMONS.get(1));
    }

    /**
     * Verify {@code createGet(String)} API method with ordered parameters.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateGetOrderArgs() throws ExecutionException, InterruptedException {
        Optional<DbRow> maybeRow = dbClient.execute(exec -> exec
                .createGet("SELECT id, name FROM Pokemons WHERE name=?")
                .addParam(POKEMONS.get(1).getName()).execute()
        ).toCompletableFuture().get();
        verifyPokemon(maybeRow, POKEMONS.get(1));
    }

    /**
     * Verify {@code namedGet(String)} API method with ordered parameters passed directly to the {@code query} method.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testNamedGetStrOrderArgs() throws ExecutionException, InterruptedException {
        Optional<DbRow> maybeRow = dbClient.execute(exec -> exec
                .namedGet("select-pokemon-order-arg", POKEMONS.get(1).getName())
        ).toCompletableFuture().get();
        verifyPokemon(maybeRow, POKEMONS.get(1));
    }

    /**
     * Verify {@code get(String)} API method with ordered parameters passed directly to the {@code query} method.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testGetStrOrderArgs() throws ExecutionException, InterruptedException {
        Optional<DbRow> maybeRow = dbClient.execute(exec -> exec
                .get("SELECT id, name FROM Pokemons WHERE name=?", POKEMONS.get(1).getName())
        ).toCompletableFuture().get();
        verifyPokemon(maybeRow, POKEMONS.get(1));
    }

}
