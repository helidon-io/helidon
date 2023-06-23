/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import io.helidon.tests.integration.dbclient.common.utils.Utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyDeletePokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyUpdatePokemon;

/**
 * Test set of basic JDBC DML statement calls.
 */
public class SimpleDmlIT extends AbstractIT {

    /** Local logger instance. */
    private static final System.Logger LOGGER = System.getLogger(SimpleDmlIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 40;

    /** Map of pokemons for update tests. */
    private static final Map<Integer, Pokemon> POKEMONS = initPokemons();

    private static Map<Integer, Pokemon> initPokemons() {
        Map<Integer, Pokemon> pokemons = new HashMap<>(8);
        // BASE_ID + 1 .. BASE_ID + 9 is reserved for inserts
        // BASE_ID + 10 .. BASE_ID + 19 are pokemons for updates
        int curId = BASE_ID + 10;
        pokemons.put(curId, new Pokemon(curId++, "Piplup", TYPES.get(11)));                 // BASE_ID+10
        pokemons.put(curId, new Pokemon(curId++, "Prinplup", TYPES.get(11)));               // BASE_ID+11
        pokemons.put(curId, new Pokemon(curId++, "Empoleon", TYPES.get(9), TYPES.get(11))); // BASE_ID+12
        pokemons.put(curId, new Pokemon(curId++, "Staryu", TYPES.get(11)));                 // BASE_ID+13
        pokemons.put(curId, new Pokemon(curId++, "Starmie", TYPES.get(11), TYPES.get(14))); // BASE_ID+14
        pokemons.put(curId, new Pokemon(curId++, "Horsea", TYPES.get(11)));                 // BASE_ID+15
        pokemons.put(curId, new Pokemon(curId, "Seadra", TYPES.get(11)));                 // BASE_ID+16
        // BASE_ID + 20 .. BASE_ID + 29 are pokemons for deletes
        curId = BASE_ID + 20;
        pokemons.put(curId, new Pokemon(curId++, "Mudkip", TYPES.get(11)));                  // BASE_ID+20
        pokemons.put(curId, new Pokemon(curId++, "Marshtomp", TYPES.get(5), TYPES.get(11))); // BASE_ID+21
        pokemons.put(curId, new Pokemon(curId++, "Swampert", TYPES.get(5), TYPES.get(11)));  // BASE_ID+22
        pokemons.put(curId, new Pokemon(curId++, "Muk", TYPES.get(4)));                      // BASE_ID+23
        pokemons.put(curId, new Pokemon(curId++, "Grimer", TYPES.get(4)));                   // BASE_ID+24
        pokemons.put(curId, new Pokemon(curId++, "Cubchoo", TYPES.get(15)));                 // BASE_ID+25
        pokemons.put(curId, new Pokemon(curId, "Beartic", TYPES.get(15)));                 // BASE_ID+26
        return pokemons;
    }

    /**
     * Initialize tests of basic JDBC updates.
     */
    @BeforeAll
    public static void setup() {
        try {
            for (Pokemon pokemon : POKEMONS.values()) {
                addPokemon(pokemon);
            }
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
        Pokemon pokemon = new Pokemon(BASE_ID+1, "Torchic", TYPES.get(10));
        Long result = Utils.executeOnce(exec -> exec
                .createNamedDmlStatement("insert-torchic", INSERT_POKEMON_NAMED_ARG)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute()
        );
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID+2, "Combusken", TYPES.get(2), TYPES.get(10));
        Long result = Utils.executeOnce(exec -> exec
                .createNamedDmlStatement("insert-pokemon-named-arg")
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute()
        );
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID+3, "Treecko", TYPES.get(12));
        Long result = Utils.executeOnce(exec -> exec
                .createNamedDmlStatement("insert-pokemon-order-arg")
                .addParam(pokemon.getId()).addParam(pokemon.getName()).execute()
        );
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateDmlWithInsertNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID+4, "Grovyle", TYPES.get(12));
        Long result = Utils.executeOnce(exec -> exec
                .createDmlStatement(INSERT_POKEMON_NAMED_ARG)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute()
        );
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with ordered parameters.
     */
    @Test
    public void testCreateDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID+5, "Sceptile", TYPES.get(12));
        Long result = Utils.executeOnce(exec -> exec
                .createDmlStatement(INSERT_POKEMON_ORDER_ARG)
                .addParam(pokemon.getId()).addParam(pokemon.getName()).execute()
        );
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code namedDml(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID+6, "Snover", TYPES.get(12), TYPES.get(15));
        Long result = Utils.executeOnce(exec -> exec
                .namedDml("insert-pokemon-order-arg", pokemon.getId(), pokemon.getName())
        );
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code dml(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID+7, "Abomasnow", TYPES.get(12), TYPES.get(15));
        Long result = Utils.executeOnce(exec -> exec
                .dml(INSERT_POKEMON_ORDER_ARG, pokemon.getId(), pokemon.getName())
        );
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrStrNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+10);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+10, "Prinplup", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .createNamedDmlStatement("update-piplup", UPDATE_POKEMON_NAMED_ARG)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+11);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+11, "Empoleon", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .createNamedDmlStatement("update-pokemon-named-arg")
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+12);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+12, "Piplup", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .createNamedDmlStatement("update-pokemon-order-arg")
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId()).execute()
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateDmlWithUpdateNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+13);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+13, "Starmie", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .createDmlStatement(UPDATE_POKEMON_NAMED_ARG)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateDmlWithUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+14);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+14, "Staryu", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .createDmlStatement(UPDATE_POKEMON_ORDER_ARG)
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId()).execute()
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+15);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+15, "Seadra", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .namedDml("update-pokemon-order-arg", updatedPokemon.getName(), updatedPokemon.getId())
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code dml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+16);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+16, "Horsea", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .dml(UPDATE_POKEMON_ORDER_ARG, updatedPokemon.getName(), updatedPokemon.getId())
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrStrOrderArgs() {
        Long result = Utils.executeOnce(exec -> exec
                .createNamedDmlStatement("delete-mudkip", DELETE_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(BASE_ID+20).getId()).execute()
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+20));
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrNamedArgs() {
        Long result = Utils.executeOnce(exec -> exec
                .createNamedDmlStatement("delete-pokemon-named-arg")
                .addParam("id", POKEMONS.get(BASE_ID+21).getId()).execute()
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+21));
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrOrderArgs() {
        Long result = Utils.executeOnce(exec -> exec
                .createNamedDmlStatement("delete-pokemon-order-arg")
                .addParam(POKEMONS.get(BASE_ID+22).getId()).execute()
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+22));
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    public void testCreateDmlWithDeleteNamedArgs() {
        Long result = Utils.executeOnce(exec -> exec
                .createDmlStatement(DELETE_POKEMON_NAMED_ARG)
                .addParam("id", POKEMONS.get(BASE_ID+23).getId()).execute()
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+23));
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateDmlWithDeleteOrderArgs() {
        Long result = Utils.executeOnce(exec -> exec
                .createDmlStatement(DELETE_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(BASE_ID+24).getId()).execute()
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+24));
    }

    /**
     * Verify {@code namedDml(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testNamedDmlWithDeleteOrderArgs() {
        Long result = Utils.executeOnce(exec -> exec
                .namedDml("delete-pokemon-order-arg", POKEMONS.get(BASE_ID+25).getId())
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+25));
    }

    /**
     * Verify {@code dml(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testDmlWithDeleteOrderArgs() {
        Long result = Utils.executeOnce(exec -> exec
                .dml(DELETE_POKEMON_ORDER_ARG, POKEMONS.get(BASE_ID+26).getId())
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+26));
    }

}
