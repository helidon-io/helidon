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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.tests.integration.dbclient.jdbc.AbstractIT;

import static io.helidon.tests.integration.dbclient.jdbc.AbstractIT.dbClient;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test utilities.
 */
class Utils {

    private Utils() {
        throw new IllegalStateException("No instances of this class are allowed!");
    }

    /**
     * Verify that {@code DbRows<DbRow> rows} argument contains single record with provided pokemon.
     *
     * @param rows database query result to verify
     * @param pokemon pokemon to compare with
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    static void verifyPokemon(DbRows<DbRow> rows, AbstractIT.Pokemon pokemon) throws ExecutionException, InterruptedException {
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.collect().toCompletableFuture().get();
        assertThat(rowsList, hasSize(1));
        DbRow row = rowsList.get(0);
        Integer id = row.column(1).as(Integer.class);
        String name = row.column(2).as(String.class);
        assertThat(id, equalTo(pokemon.getId()));
        assertThat(name, pokemon.getName().equals(name));
    }

    /**
     * Verify that {@code DbRows<DbRow> rows} argument contains single record with provided pokemon.
     *
     * @param maybeRow database query result to verify
     * @param pokemon pokemon to compare with
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    static void verifyPokemon(Optional<DbRow> maybeRow, AbstractIT.Pokemon pokemon) throws ExecutionException, InterruptedException {
        assertThat(maybeRow.isPresent(), equalTo(true));
        DbRow row = maybeRow.get();
        Integer id = row.column(1).as(Integer.class);
        String name = row.column(2).as(String.class);
        assertThat(id, equalTo(pokemon.getId()));
        assertThat(name, pokemon.getName().equals(name));
    }

    /**
     * Verify that provided pokemon was successfully inserted into the database.
     *
     * @param result DML statement result
     * @param pokemon pokemon to compare with
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    static void verifyInsertPokemon(Long result, AbstractIT.Pokemon pokemon) throws ExecutionException, InterruptedException {
        assertThat(result, equalTo(1L));
        Optional<DbRow> maybeRow = dbClient.execute(exec -> exec
                .namedGet("select-pokemon-order-arg", pokemon.getName())
        ).toCompletableFuture().get();
        assertThat(maybeRow.isPresent(), equalTo(true));
        DbRow row = maybeRow.get();
        Integer id = row.column(1).as(Integer.class);
        String name = row.column(2).as(String.class);
        assertThat(id, equalTo(pokemon.getId()));
        assertThat(name, pokemon.getName().equals(name));
    }

   /**
     * Verify that provided pokemon was successfully updated in the database.
     *
     * @param result DML statement result
     * @param pokemon pokemon to compare with
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    static void verifyUpdatePokemon(Long result, AbstractIT.Pokemon pokemon) throws ExecutionException, InterruptedException {
        assertThat(result, equalTo(1L));
        Optional<DbRow> maybeRow = dbClient.execute(exec -> exec
                .namedGet("select-pokemon-by-id", pokemon.getId())
        ).toCompletableFuture().get();
        assertThat(maybeRow.isPresent(), equalTo(true));
        DbRow row = maybeRow.get();
        Integer id = row.column(1).as(Integer.class);
        String name = row.column(2).as(String.class);
        assertThat(id, equalTo(pokemon.getId()));
        assertThat(name, pokemon.getName().equals(name));
    }

   /**
     * Verify that provided pokemon was successfully deleted from the database.
     *
     * @param result DML statement result
     * @param pokemon pokemon to compare with
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    static void verifyDeletePokemon(Long result, AbstractIT.Pokemon pokemon) throws ExecutionException, InterruptedException {
        assertThat(result, equalTo(1L));
        Optional<DbRow> maybeRow = dbClient.execute(exec -> exec
                .namedGet("select-pokemon-by-id", pokemon.getId())
        ).toCompletableFuture().get();
        assertThat(maybeRow.isPresent(), equalTo(false));
    }

}
