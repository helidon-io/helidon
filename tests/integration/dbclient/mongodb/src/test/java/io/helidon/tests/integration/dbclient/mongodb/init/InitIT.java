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
package io.helidon.tests.integration.dbclient.mongodb.init;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Initialize database
 */
public class InitIT extends AbstractIT {

    /**
     * Initialize database content (rows in tables).
     *
     * @param dbClient Helidon database client
     */
    private static void initData(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        long count = 0;

        for (Map.Entry<Integer, Type> entry : TYPES.entrySet()) {
            count += exec.namedInsert("insert-type", entry.getKey(), entry.getValue().name());
        }

        for (Map.Entry<Integer, Pokemon> entry : POKEMONS.entrySet()) {
            count += exec.namedInsert("insert-pokemon", entry.getKey(), entry.getValue().getName());
        }

        for (Map.Entry<Integer, Pokemon> entry : POKEMONS.entrySet()) {
            Pokemon pokemon = entry.getValue();
            for (Type type : pokemon.getTypes()) {
                count += exec.namedInsert("insert-poketype", pokemon.getId(), type.id());
            }
        }
    }

    /**
     * Setup database for tests.
     */
    @BeforeAll
    public static void setup() {
        initData(DB_CLIENT);
    }

    /**
     * Verify that the {@code Types} was properly initialized.
     */
    @Test
    public void testListTypes() {
        Stream<DbRow> rows = DB_CLIENT.execute().namedQuery("select-types");

        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.toList();
        assertThat(rowsList, not(empty()));
        Set<Integer> ids = new HashSet<>(TYPES.keySet());
        for (DbRow row : rowsList) {
            Integer id = row.column(1).as(Integer.class);
            String name = row.column(2).as(String.class);
            assertThat(ids, hasItem(id));
            ids.remove(id);
            assertThat(name, TYPES.get(id).name().equals(name));
        }
    }

    /**
     * Verify that the {@code Pokemon} was properly initialized.
     */
    @Test
    public void testListPokemons() {
        Stream<DbRow> rows = DB_CLIENT.execute().namedQuery("select-pokemons");

        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.toList();
        assertThat(rowsList, not(empty()));
        Set<Integer> ids = new HashSet<>(POKEMONS.keySet());
        for (DbRow row : rowsList) {
            Integer id = row.column(1).as(Integer.class);
            String name = row.column(2).as(String.class);
            assertThat(ids, hasItem(id));
            ids.remove(id);
            assertThat(name, POKEMONS.get(id).getName().equals(name));
        }
    }

    /**
     * Verify that the {@code PokemonTypes} was properly initialized.
     */
    @Test
    public void testListPokemonTypes() {
        DbExecute exec = DB_CLIENT.execute();
        Stream<DbRow> rows = exec.namedQuery("select-pokemons");
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.toList();
        assertThat(rowsList, not(empty()));

        for (DbRow row : rowsList) {
            Integer pokemonId = row.column(1).as(Integer.class);
            String pokemonName = row.column(2).as(String.class);
            Pokemon pokemon = POKEMONS.get(pokemonId);
            assertThat(pokemonName, POKEMONS.get(pokemonId).getName().equals(pokemonName));
            Stream<DbRow> typeRows = exec.namedQuery("select-poketypes", pokemonId);

            List<DbRow> typeRowsList = typeRows.toList();
            assertThat(typeRowsList.size(), equalTo(pokemon.getTypes().size()));
            for (DbRow typeRow : typeRowsList) {
                Integer typeId = typeRow.column(2).as(Integer.class);
                assertThat(pokemon.getTypes(), hasItem(TYPES.get(typeId)));
            }
        }
    }

}
