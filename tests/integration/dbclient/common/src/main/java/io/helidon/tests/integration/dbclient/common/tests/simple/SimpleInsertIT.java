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
package io.helidon.tests.integration.dbclient.common.tests.simple;

import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;

/**
 * Test set of basic JDBC inserts.
 */
@SuppressWarnings("SpellCheckingInspection")
public class SimpleInsertIT extends AbstractIT {

    /**
     * Maximum Pokemon ID.
     */
    private static final int BASE_ID = LAST_POKEMON_ID + 10;

    /**
     * Verify {@code createNamedInsert(String, String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedInsertStrStrNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 1, "Bulbasaur", TYPES.get(4), TYPES.get(12));
        long result = DB_CLIENT.execute()
                .createNamedInsert("insert-bulbasaur", INSERT_POKEMON_NAMED_ARG)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedInsert(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedInsertStrNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 2, "Ivysaur", TYPES.get(4), TYPES.get(12));
        long result = DB_CLIENT.execute()
                .createNamedInsert("insert-pokemon-named-arg")
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedInsert(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedInsertStrOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 3, "Venusaur", TYPES.get(4), TYPES.get(12));
        long result = DB_CLIENT.execute()
                .createNamedInsert("insert-pokemon-order-arg")
                .addParam(pokemon.getId()).addParam(pokemon.getName())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createInsert(String)} API method with named parameters.
     */
    @Test
    public void testCreateInsertNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 4, "Magby", TYPES.get(10));
        long result = DB_CLIENT.execute()
                .createInsert(INSERT_POKEMON_NAMED_ARG)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createInsert(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 5, "Magmar", TYPES.get(10));
        long result = DB_CLIENT.execute()
                .createInsert(INSERT_POKEMON_ORDER_ARG)
                .addParam(pokemon.getId()).addParam(pokemon.getName())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code namedInsert(String)} API method with ordered parameters passed directly to the {@code insert} method.
     */
    @Test
    public void testNamedInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 6, "Rattata", TYPES.get(1));
        long result = DB_CLIENT.execute().namedInsert("insert-pokemon-order-arg", pokemon.getId(), pokemon.getName());
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code insert(String)} API method with ordered parameters passed directly to the {@code insert} method.
     */
    @Test
    public void testInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 7, "Raticate", TYPES.get(1));
        long result = DB_CLIENT.execute().insert(INSERT_POKEMON_ORDER_ARG, pokemon.getId(), pokemon.getName());
        verifyInsertPokemon(result, pokemon);
    }

}
