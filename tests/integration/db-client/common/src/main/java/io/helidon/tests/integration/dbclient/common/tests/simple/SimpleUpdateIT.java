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

import java.util.HashMap;
import java.util.Map;

import io.helidon.tests.integration.dbclient.common.AbstractIT;
import io.helidon.tests.integration.dbclient.common.utils.Utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyUpdatePokemon;

/**
 * Test set of basic JDBC updates.
 */
public class SimpleUpdateIT extends AbstractIT {

    /** Local logger instance. */
    private static final System.Logger LOGGER = System.getLogger(SimpleUpdateIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 20;

    /** Map of pokemons for update tests. */
    private static final Map<Integer, Pokemon> POKEMONS = initPokemons();

    private static Map<Integer, Pokemon> initPokemons() {
        Map<Integer, Pokemon> pokemons = new HashMap<>(8);
        int curId = BASE_ID;
        pokemons.put(++curId, new Pokemon(curId, "Spearow", TYPES.get(1), TYPES.get(3))); // BASE_ID+1
        pokemons.put(++curId, new Pokemon(curId, "Fearow", TYPES.get(1), TYPES.get(3)));  // BASE_ID+2
        pokemons.put(++curId, new Pokemon(curId, "Ekans", TYPES.get(4)));                 // BASE_ID+3
        pokemons.put(++curId, new Pokemon(curId, "Arbok", TYPES.get(4)));                 // BASE_ID+4
        pokemons.put(++curId, new Pokemon(curId, "Sandshrew", TYPES.get(5)));             // BASE_ID+5
        pokemons.put(++curId, new Pokemon(curId, "Sandslash", TYPES.get(5)));             // BASE_ID+6
        pokemons.put(++curId, new Pokemon(curId, "Diglett", TYPES.get(5)));               // BASE_ID+7
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
            LOGGER.log(System.Logger.Level.WARNING, String.format("Exception in setup: %s", ex));
            throw ex;
        }
    }

    /**
     * Verify {@code createNamedUpdate(String, String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedUpdateStrStrNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+1);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+1, "Fearow", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .createNamedUpdate("update-spearow", UPDATE_POKEMON_NAMED_ARG)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedUpdateStrNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+2);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+2, "Spearow", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .createNamedUpdate("update-pokemon-named-arg")
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedUpdateStrOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+3);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+3, "Arbok", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .createNamedUpdate("update-pokemon-order-arg")
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId()).execute()
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateUpdateNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+4);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+4, "Ekans", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .createUpdate(UPDATE_POKEMON_NAMED_ARG)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId()).execute()
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code createUpdate(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+5);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+5, "Diglett", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .createUpdate(UPDATE_POKEMON_ORDER_ARG)
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId()).execute()
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code namedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testNamedUpdateNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+6);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+6, "Sandshrew", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .namedUpdate("update-pokemon-order-arg", updatedPokemon.getName(), updatedPokemon.getId())
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

    /**
     * Verify {@code update(String)} API method with ordered parameters.
     */
    @Test
    public void testUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID+7);
        Pokemon updatedPokemon = new Pokemon(BASE_ID+7, "Sandslash", srcPokemon.getTypesArray());
        Long result = Utils.executeOnce(exec -> exec
                .update(UPDATE_POKEMON_ORDER_ARG, updatedPokemon.getName(), updatedPokemon.getId())
        );
        verifyUpdatePokemon(result, updatedPokemon);
    }

}
