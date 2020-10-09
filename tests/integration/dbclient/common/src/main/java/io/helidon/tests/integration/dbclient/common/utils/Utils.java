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
package io.helidon.tests.integration.dbclient.common.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.common.reactive.Multi;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.AbstractIT;
import io.helidon.tests.integration.dbclient.common.AbstractIT.Pokemon;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.POKEMONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test utilities.
 */
public class Utils {

    /* Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(Utils.class.getName());

    private Utils() {
        throw new IllegalStateException("No instances of this class are allowed!");
    }

    /**
     * Verify that {@code Multi<DbRow> rows} argument contains pokemons matching specified IDs range.
     * @param rows database query result to verify
     * @param idMin beginning of ID range
     * @param idMax end of ID range
     */
    public static void verifyPokemonsIdRange(Multi<DbRow> rows, int idMin, int idMax) {
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.collectList().await();
        // Build Map of valid pokemons
        Map<Integer, Pokemon> valid = new HashMap<>(POKEMONS.size());
        for (Map.Entry<Integer, Pokemon> entry : POKEMONS.entrySet()) {
            int id = entry.getKey();
            Pokemon pokemon = entry.getValue();
            if (id > idMin && id < idMax) {
                valid.put(id, pokemon);
            }
        }
        // Compare result with valid pokemons
        //assertThat(rowsList, hasSize(valid.size()));
        for (DbRow row : rowsList) {
            Integer id = row.column(1).as(Integer.class);
            String name = row.column(2).as(String.class);
            LOGGER.info(() -> String.format("Pokemon id=%d, name=%s", id, name));
            assertThat(valid.containsKey(id), equalTo(true));
            assertThat(name, equalTo(valid.get(id).getName()));
        }
    }

    /**
     * Verify that {@code Multi<DbRow> rows} argument contains single pokemon matching specified IDs range.
     * @param maybeRow database query result to verify
     * @param idMin beginning of ID range
     * @param idMax end of ID range
     */
    public static void verifyPokemonsIdRange(Optional<DbRow> maybeRow, int idMin, int idMax) {
        assertThat(maybeRow.isPresent(), equalTo(true));
        DbRow row = maybeRow.get();
        // Build Map of valid pokemons
        Map<Integer, Pokemon> valid = new HashMap<>(POKEMONS.size());
        for (Map.Entry<Integer, Pokemon> entry : POKEMONS.entrySet()) {
            int id = entry.getKey();
            Pokemon pokemon = entry.getValue();
            if (id > idMin && id < idMax) {
                valid.put(id, pokemon);
            }
        }
        Integer id = row.column(1).as(Integer.class);
        String name = row.column(2).as(String.class);
        assertThat(valid.containsKey(id), equalTo(true));
        assertThat(name, equalTo(valid.get(id).getName()));
    }

    /**
     * Verify that {@code Multi<DbRow> rows} argument contains single record with provided pokemon.
     *
     * @param rows database query result to verify
     * @param pokemon pokemon to compare with
     */
    public static void verifyPokemon(Multi<DbRow> rows, AbstractIT.Pokemon pokemon) {
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.collectList().await();
        assertThat(rowsList, hasSize(1));
        DbRow row = rowsList.get(0);
        Integer id = row.column(1).as(Integer.class);
        String name = row.column(2).as(String.class);
        assertThat(id, equalTo(pokemon.getId()));
        assertThat(name, pokemon.getName().equals(name));
    }

    /**
     * Verify that {@code Multi<DbRow> rows} argument contains single record with provided pokemon.
     *
     * @param maybeRow database query result to verify
     * @param pokemon pokemon to compare with
     */
    public static void verifyPokemon(Optional<DbRow> maybeRow, AbstractIT.Pokemon pokemon) {
        assertThat(maybeRow.isPresent(), equalTo(true));
        DbRow row = maybeRow.get();
        Integer id = row.column(1).as(Integer.class);
        String name = row.column(2).as(String.class);
        assertThat(id, equalTo(pokemon.getId()));
        assertThat(name, pokemon.getName().equals(name));
    }

    /**
     * Verify that {@code Pokemon result} argument contains single record with provided pokemon.
     *
     * @param result database query result mapped to Pokemon PoJo to verify
     * @param pokemon pokemon to compare with
     */
    public static void verifyPokemon(AbstractIT.Pokemon result, AbstractIT.Pokemon pokemon) {
        assertThat(result.getId(), equalTo(pokemon.getId()));
        assertThat(result.getName(), equalTo(pokemon.getName()));
    }

    /**
     * Verify that provided pokemon was successfully inserted into the database.
     *
     * @param result DML statement result
     * @param pokemon pokemon to compare with
     */
    public static void verifyInsertPokemon(Long result, AbstractIT.Pokemon pokemon) {
        assertThat(result, equalTo(1L));
        Optional<DbRow> maybeRow = DB_CLIENT.execute(exec -> exec
                .namedGet("select-pokemon-by-id", pokemon.getId()))
                .await();

        assertThat(maybeRow.isPresent(), equalTo(true));
        DbRow row = maybeRow.get();
        Integer id = row.column("id").as(Integer.class);
        String name = row.column("name").as(String.class);
        assertThat(id, equalTo(pokemon.getId()));
        assertThat(name, pokemon.getName().equals(name));
    }

    /**
     * Verify that provided pokemon was successfully updated in the database.
     *
     * @param result DML statement result
     * @param pokemon pokemon to compare with
     */
    public static void verifyUpdatePokemon(Long result, AbstractIT.Pokemon pokemon) {
        assertThat(result, equalTo(1L));
        Optional<DbRow> maybeRow = DB_CLIENT.execute(exec -> exec
                .namedGet("select-pokemon-by-id", pokemon.getId()))
                .await();

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
     */
    public static void verifyDeletePokemon(Long result, AbstractIT.Pokemon pokemon) {
        assertThat(result, equalTo(1L));
        Optional<DbRow> maybeRow = DB_CLIENT.execute(exec -> exec
                .namedGet("select-pokemon-by-id", pokemon.getId()))
                .await();

        assertThat(maybeRow.isPresent(), equalTo(false));
    }
}
