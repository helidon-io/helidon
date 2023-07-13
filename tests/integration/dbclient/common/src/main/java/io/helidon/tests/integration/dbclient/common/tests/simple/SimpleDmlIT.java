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

import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;

import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyDeletePokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyUpdatePokemon;

/**
 * Test set of basic JDBC DML statement calls.
 */
@SuppressWarnings("SpellCheckingInspection")
public class SimpleDmlIT extends AbstractIT {

    /**
     * Local logger instance.
     */
    private static final System.Logger LOGGER = System.getLogger(SimpleDmlIT.class.getName());

    /**
     * Maximum Pokemon ID.
     */
    private static final int BASE_ID = LAST_POKEMON_ID + 40;

    /**
     * Map of {@code Pokemon} by ID for update tests.
     */
    private static final Map<Integer, Pokemon> POKEMONS = new HashMap<>();

    private static void addPokemon(Pokemon pokemon) {
        POKEMONS.put(pokemon.getId(), pokemon);
        long result = DB_CLIENT.execute()
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName());
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Initialize tests of basic JDBC updates.
     */
    @BeforeAll
    public static void setup() {
        try {
            // [BASE_ID + 1 .. BASE_ID + 9] is reserved for inserts
            // [BASE_ID + 10 .. BASE_ID + 19] are reserved for updates
            addPokemon(new Pokemon(BASE_ID + 10, "Piplup", TYPES.get(11)));                 // BASE_ID+10
            addPokemon(new Pokemon(BASE_ID + 11, "Prinplup", TYPES.get(11)));               // BASE_ID+11
            addPokemon(new Pokemon(BASE_ID + 12, "Empoleon", TYPES.get(9), TYPES.get(11))); // BASE_ID+12
            addPokemon(new Pokemon(BASE_ID + 13, "Staryu", TYPES.get(11)));                 // BASE_ID+13
            addPokemon(new Pokemon(BASE_ID + 14, "Starmie", TYPES.get(11), TYPES.get(14))); // BASE_ID+14
            addPokemon(new Pokemon(BASE_ID + 15, "Horsea", TYPES.get(11)));                 // BASE_ID+15
            addPokemon(new Pokemon(BASE_ID + 16, "Seadra", TYPES.get(11)));                 // BASE_ID+16
            // BASE_ID + 20 .. BASE_ID + 29 are reserved for deletes
            addPokemon(new Pokemon(BASE_ID + 20, "Mudkip", TYPES.get(11)));                  // BASE_ID+20
            addPokemon(new Pokemon(BASE_ID + 21, "Marshtomp", TYPES.get(5), TYPES.get(11))); // BASE_ID+21
            addPokemon(new Pokemon(BASE_ID + 22, "Swampert", TYPES.get(5), TYPES.get(11)));  // BASE_ID+22
            addPokemon(new Pokemon(BASE_ID + 23, "Muk", TYPES.get(4)));                      // BASE_ID+23
            addPokemon(new Pokemon(BASE_ID + 24, "Grimer", TYPES.get(4)));                   // BASE_ID+24
            addPokemon(new Pokemon(BASE_ID + 25, "Cubchoo", TYPES.get(15)));                 // BASE_ID+25
            addPokemon(new Pokemon(BASE_ID + 26, "Beartic", TYPES.get(15)));                 // BASE_ID+26
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, String.format("Exception in setup: %s", ex));
            throw ex;
        }
    }

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrStrNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 1, "Torchic", TYPES.get(10));
        long result = DB_CLIENT.execute()
                .createNamedDmlStatement("insert-torchic", INSERT_POKEMON_NAMED_ARG)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 2, "Combusken", TYPES.get(2), TYPES.get(10));
        long result = DB_CLIENT.execute()
                .createNamedDmlStatement("insert-pokemon-named-arg")
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 3, "Treecko", TYPES.get(12));
        long result = DB_CLIENT.execute()
                .createNamedDmlStatement("insert-pokemon-order-arg")
                .addParam(pokemon.getId()).addParam(pokemon.getName())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateDmlWithInsertNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 4, "Grovyle", TYPES.get(12));
        long result = DB_CLIENT.execute()
                .createDmlStatement(INSERT_POKEMON_NAMED_ARG)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with ordered parameters.
     */
    @Test
    public void testCreateDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 5, "Sceptile", TYPES.get(12));
        long result = DB_CLIENT.execute()
                .createDmlStatement(INSERT_POKEMON_ORDER_ARG)
                .addParam(pokemon.getId()).addParam(pokemon.getName())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code namedDml(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 6, "Snover", TYPES.get(12), TYPES.get(15));
        long result = DB_CLIENT.execute()
                .namedDml("insert-pokemon-order-arg", pokemon.getId(), pokemon.getName());
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code dml(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 7, "Abomasnow", TYPES.get(12), TYPES.get(15));
        long result = DB_CLIENT.execute()
                .dml(INSERT_POKEMON_ORDER_ARG, pokemon.getId(), pokemon.getName());
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrStrNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 10);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 10, "Prinplup", srcPokemon.getTypesArray());
        long result = DB_CLIENT.execute()
                .createNamedDmlStatement("update-piplup", UPDATE_POKEMON_NAMED_ARG)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 11);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 11, "Empoleon", srcPokemon.getTypesArray());
        long result = DB_CLIENT.execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 12);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 12, "Piplup", srcPokemon.getTypesArray());
        long result = DB_CLIENT.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateDmlWithUpdateNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 13);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 13, "Starmie", srcPokemon.getTypesArray());
        long result = DB_CLIENT.execute()
                .createDmlStatement(UPDATE_POKEMON_NAMED_ARG)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateDmlWithUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 14);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 14, "Staryu", srcPokemon.getTypesArray());
        long result = DB_CLIENT.execute()
                .createDmlStatement(UPDATE_POKEMON_ORDER_ARG)
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 15);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 15, "Seadra", srcPokemon.getTypesArray());
        long result = DB_CLIENT.execute()
                .namedDml("update-pokemon-order-arg", updatedPokemon.getName(), updatedPokemon.getId());
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code dml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 16);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 16, "Horsea", srcPokemon.getTypesArray());
        long result = DB_CLIENT.execute()
                .dml(UPDATE_POKEMON_ORDER_ARG, updatedPokemon.getName(), updatedPokemon.getId());
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrStrOrderArgs() {
        long result = DB_CLIENT.execute()
                .createNamedDmlStatement("delete-mudkip", DELETE_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(BASE_ID + 20).getId())
                .execute();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID + 20));
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrNamedArgs() {
        long result = DB_CLIENT.execute()
                .createNamedDmlStatement("delete-pokemon-named-arg")
                .addParam("id", POKEMONS.get(BASE_ID + 21).getId())
                .execute();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID + 21));
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrOrderArgs() {
        long result = DB_CLIENT.execute()
                .createNamedDmlStatement("delete-pokemon-order-arg")
                .addParam(POKEMONS.get(BASE_ID + 22).getId())
                .execute();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID + 22));
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    public void testCreateDmlWithDeleteNamedArgs() {
        long result = DB_CLIENT.execute()
                .createDmlStatement(DELETE_POKEMON_NAMED_ARG)
                .addParam("id", POKEMONS.get(BASE_ID + 23).getId())
                .execute();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID + 23));
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateDmlWithDeleteOrderArgs() {
        long result = DB_CLIENT.execute()
                .createDmlStatement(DELETE_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(BASE_ID + 24).getId())
                .execute();
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID + 24));
    }

    /**
     * Verify {@code namedDml(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testNamedDmlWithDeleteOrderArgs() {
        long result = DB_CLIENT.execute().namedDml("delete-pokemon-order-arg", POKEMONS.get(BASE_ID + 25).getId());
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID + 25));
    }

    /**
     * Verify {@code dml(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testDmlWithDeleteOrderArgs() {
        long result = DB_CLIENT.execute().dml(DELETE_POKEMON_ORDER_ARG, POKEMONS.get(BASE_ID + 26).getId());
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID + 26));
    }
}
