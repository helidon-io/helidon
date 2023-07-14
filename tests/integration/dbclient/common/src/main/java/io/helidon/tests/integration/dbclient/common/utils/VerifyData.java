/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;

import static io.helidon.tests.integration.dbclient.common.model.Pokemon.POKEMONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test utilities.
 */
public class VerifyData {

    private static final System.Logger LOGGER = System.getLogger(VerifyData.class.getName());

    private VerifyData() {
    }

    /**
     * Verify that the given rows contain data matching specified IDs range.
     *
     * @param rows  database query result to verify
     * @param idMin beginning of ID range
     * @param idMax end of ID range
     */
    public static void verifyPokemonsIdRange(Stream<DbRow> rows, int idMin, int idMax) {
        // Build Map of valid data
        Map<Integer, Pokemon> valid = range(idMin, idMax);

        // Compare result with valid data
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.toList();
        assertThat(rowsList, hasSize(valid.size()));
        for (DbRow row : rowsList) {
            Integer id = row.column(1).as(Integer.class);
            String name = row.column(2).as(String.class);
            LOGGER.log(Level.INFO, () -> String.format("Pokemon id=%d, name=%s", id, name));
            assertThat(valid.containsKey(id), equalTo(true));
            assertThat(name, equalTo(valid.get(id).getName()));
        }
    }

    /**
     * Verify that the given row contains single data matching specified IDs range.
     *
     * @param maybeRow database query result to verify
     * @param idMin    beginning of ID range
     * @param idMax    end of ID range
     */
    public static void verifyPokemonsIdRange(Optional<DbRow> maybeRow, int idMin, int idMax) {
        Map<Integer, Pokemon> valid = range(idMin, idMax);
        assertThat(maybeRow.isPresent(), equalTo(true));
        DbRow row = maybeRow.get();
        Integer id = row.column(1).as(Integer.class);
        String name = row.column(2).as(String.class);
        assertThat(valid.containsKey(id), equalTo(true));
        assertThat(name, equalTo(valid.get(id).getName()));
    }

    /**
     * Verify that the given rows contain single record with expected data.
     *
     * @param rows     database query result to verify
     * @param expected data to compare with
     */
    public static void verifyPokemon(List<DbRow> rows, Pokemon expected) {
        assertThat(rows, notNullValue());
        assertThat(rows, hasSize(1));
        DbRow row = rows.get(0);
        Integer id = row.column(1).as(Integer.class);
        String name = row.column(2).as(String.class);
        assertThat(id, equalTo(expected.getId()));
        assertThat(name, expected.getName().equals(name));
    }

    /**
     * Verify that the given rows contain single record with expected data.
     *
     * @param rows    database query result to verify
     * @param pokemon data to compare with
     */
    public static void verifyPokemon(Stream<DbRow> rows, Pokemon pokemon) {
        assertThat(rows, notNullValue());
        verifyPokemon(rows.toList(), pokemon);
    }

    /**
     * Verify that the given row contains single record with expected data.
     *
     * @param maybeRow database query result to verify
     * @param expected data to compare with
     */
    public static void verifyPokemon(Optional<DbRow> maybeRow, Pokemon expected) {
        assertThat(maybeRow.isPresent(), equalTo(true));
        DbRow row = maybeRow.get();
        Integer id = row.column(1).as(Integer.class);
        String name = row.column(2).as(String.class);
        assertThat(id, equalTo(expected.getId()));
        assertThat(name, expected.getName().equals(name));
    }

    /**
     * Verify that the given data contains single record with expected data.
     *
     * @param actual   database query result
     * @param expected data to compare with
     */
    public static void verifyPokemon(Pokemon actual, Pokemon expected) {
        assertThat(actual.getId(), equalTo(expected.getId()));
        assertThat(actual.getName(), equalTo(expected.getName()));
    }

    /**
     * Verify that provided data was successfully inserted into the database.
     *
     * @param dbClient database client
     * @param result DML statement result
     * @param data   data to compare with
     */
    public static void verifyInsertPokemon(DbClient dbClient, long result, Pokemon data) {
        assertThat(result, equalTo(1L));
        Optional<DbRow> maybeRow = dbClient.execute()
                .namedGet("select-pokemon-by-id", data.getId());

        assertThat(maybeRow.isPresent(), equalTo(true));
        DbRow row = maybeRow.get();
        Integer id = row.column("id").as(Integer.class);
        String name = row.column("name").as(String.class);
        assertThat(id, equalTo(data.getId()));
        assertThat(name, data.getName().equals(name));
    }

    /**
     * Verify that provided data was successfully updated in the database.
     *
     * @param dbClient database client
     * @param result DML statement result
     * @param data   data to compare with
     */
    public static void verifyUpdatePokemon(DbClient dbClient, long result, Pokemon data) {
        assertThat(result, equalTo(1L));
        Optional<DbRow> maybeRow = dbClient.execute()
                .namedGet("select-pokemon-by-id", data.getId());
        verifyPokemon(maybeRow, data);
    }

    /**
     * Verify that provided data was successfully deleted from the database.
     *
     * @param dbClient database client
     * @param result   DML statement result
     * @param expected data to compare with
     */
    public static void verifyDeletePokemon(DbClient dbClient, long result, Pokemon expected) {
        assertThat(result, equalTo(1L));
        Optional<DbRow> maybeRow = dbClient.execute()
                .namedGet("select-pokemon-by-id", expected.getId());
        assertThat(maybeRow.isPresent(), equalTo(false));
    }

    private static Map<Integer, Pokemon> range(int idMin, int idMax) {
        Map<Integer, Pokemon> map = new HashMap<>(POKEMONS.size());
        for (Map.Entry<Integer, Pokemon> entry : POKEMONS.entrySet()) {
            int id = entry.getKey();
            Pokemon pokemon = entry.getValue();
            if (id > idMin && id < idMax) {
                map.put(id, pokemon);
            }
        }
        return map;
    }
}
