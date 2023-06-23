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
package io.helidon.tests.integration.dbclient.common.tests.transaction;

import java.util.HashMap;
import java.util.Map;

import io.helidon.tests.integration.dbclient.common.AbstractIT;
import io.helidon.tests.integration.dbclient.common.utils.Utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyDeletePokemon;

/**
 * Test set of basic JDBC delete calls in transaction.
 */
public class TransactionDeleteIT extends AbstractIT {

    /** Local logger instance. */
    private static final System.Logger LOGGER = System.getLogger(TransactionDeleteIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 230;

    /** Map of pokemons for update tests. */
    private static final Map<Integer, Pokemon> POKEMONS = initPokemons();

    private static Map<Integer, Pokemon> initPokemons() {
        Map<Integer, Pokemon> pokemons = new HashMap<>(8);
        int curId = BASE_ID;
        pokemons.put(++curId, new Pokemon(curId, "Omanyte", TYPES.get(6), TYPES.get(11)));  // BASE_ID+1
        pokemons.put(++curId, new Pokemon(curId, "Omastar", TYPES.get(6), TYPES.get(11)));  // BASE_ID+2
        pokemons.put(++curId, new Pokemon(curId, "Kabuto", TYPES.get(6), TYPES.get(11)));   // BASE_ID+3
        pokemons.put(++curId, new Pokemon(curId, "Kabutops", TYPES.get(6), TYPES.get(11))); // BASE_ID+4
        pokemons.put(++curId, new Pokemon(curId, "Chikorita", TYPES.get(12)));              // BASE_ID+5
        pokemons.put(++curId, new Pokemon(curId, "Bayleef", TYPES.get(12)));                // BASE_ID+6
        pokemons.put(++curId, new Pokemon(curId, "Meganium", TYPES.get(12)));               // BASE_ID+7
        return pokemons;
    }

    /**
     * Initialize tests of basic JDBC deletes.
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
     * Verify {@code createNamedDelete(String, String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedDeleteStrStrOrderArgs() {
        Long result = Utils.transaction(tx -> tx
                .createNamedDelete("delete-rayquaza", DELETE_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(BASE_ID+1).getId()).execute()
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+1));
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedDeleteStrNamedArgs() {
        Long result = Utils.transaction(tx -> tx
                .createNamedDelete("delete-pokemon-named-arg")
                .addParam("id", POKEMONS.get(BASE_ID+2).getId()).execute()
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+2));
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedDeleteStrOrderArgs() {
        Long result = Utils.transaction(tx -> tx
                .createNamedDelete("delete-pokemon-order-arg")
                .addParam(POKEMONS.get(BASE_ID+3).getId()).execute()
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+3));
    }

    /**
     * Verify {@code createDelete(String)} API method with named parameters.
     */
    @Test
    public void testCreateDeleteNamedArgs() {
        Long result = Utils.transaction(exec -> exec
                .createDelete(DELETE_POKEMON_NAMED_ARG)
                .addParam("id", POKEMONS.get(BASE_ID+4).getId()).execute()
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+4));
    }

    /**
     * Verify {@code createDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateDeleteOrderArgs() {
        Long result = Utils.transaction(tx -> tx
                .createDelete(DELETE_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(BASE_ID+5).getId()).execute()
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+5));
    }

    /**
     * Verify {@code namedDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testNamedDeleteOrderArgs() {
        Long result = Utils.transaction(tx -> tx
                .namedDelete("delete-pokemon-order-arg", POKEMONS.get(BASE_ID+6).getId())
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+6));
    }

    /**
     * Verify {@code delete(String)} API method with ordered parameters.
     */
    @Test
    public void testDeleteOrderArgs() {
        Long result = Utils.transaction(tx -> tx
                .delete(DELETE_POKEMON_ORDER_ARG, POKEMONS.get(BASE_ID+7).getId())
        );
        verifyDeletePokemon(result, POKEMONS.get(BASE_ID+7));
    }

}
