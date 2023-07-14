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
package io.helidon.tests.integration.dbclient.common.tests;

import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.harness.SetUp;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.model.Type.TYPES;
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyInsertPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyUpdatePokemon;

/**
 * Test set of basic JDBC updates.
 */
@SuppressWarnings("SpellCheckingInspection")
public class SimpleUpdateIT extends AbstractIT {

    private static final System.Logger LOGGER = System.getLogger(SimpleUpdateIT.class.getName());
    private static final int BASE_ID = LAST_POKEMON_ID + 20;
    private static final Map<Integer, Pokemon> POKEMONS = new HashMap<>();

    private final DbClient dbClient;
    private final Config config;

    public SimpleUpdateIT(DbClient dbClient, Config config) {
        this.dbClient = dbClient;
        this.config = config;
    }

    private static void addPokemon(DbClient dbClient, Pokemon pokemon) {
        POKEMONS.put(pokemon.getId(), pokemon);
        long result = dbClient.execute()
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName());
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Initialize tests of basic JDBC updates.
     */
    @SetUp
    public static void setup(DbClient dbClient) {
        try {
            int curId = BASE_ID;
            addPokemon(dbClient, new Pokemon(++curId, "Spearow", TYPES.get(1), TYPES.get(3))); // BASE_ID+1
            addPokemon(dbClient, new Pokemon(++curId, "Fearow", TYPES.get(1), TYPES.get(3)));  // BASE_ID+2
            addPokemon(dbClient, new Pokemon(++curId, "Ekans", TYPES.get(4)));                 // BASE_ID+3
            addPokemon(dbClient, new Pokemon(++curId, "Arbok", TYPES.get(4)));                 // BASE_ID+4
            addPokemon(dbClient, new Pokemon(++curId, "Sandshrew", TYPES.get(5)));             // BASE_ID+5
            addPokemon(dbClient, new Pokemon(++curId, "Sandslash", TYPES.get(5)));             // BASE_ID+6
            addPokemon(dbClient, new Pokemon(++curId, "Diglett", TYPES.get(5)));               // BASE_ID+7
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, String.format("Exception in setup: %s", ex));
            throw ex;
        }
    }

    /**
     * Verify {@code createNamedUpdate(String, String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedUpdateStrStrNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 1);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 1, "Fearow", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createNamedUpdate("update-spearow", stmt)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedUpdateStrNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 2);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 2, "Spearow", srcPokemon.getTypesArray());
        long result = dbClient.execute()
                .createNamedUpdate("update-pokemon-named-arg")
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedUpdateStrOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 3);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 3, "Arbok", srcPokemon.getTypesArray());
        long result = dbClient.execute()
                .createNamedUpdate("update-pokemon-order-arg")
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateUpdateNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 4);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 4, "Ekans", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createUpdate(stmt)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createUpdate(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 5);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 5, "Diglett", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createUpdate(stmt)
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code namedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testNamedUpdateNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 6);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 6, "Sandshrew", srcPokemon.getTypesArray());
        long result = dbClient.execute()
                .namedUpdate("update-pokemon-order-arg", updatedPokemon.getName(), updatedPokemon.getId());
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code update(String)} API method with ordered parameters.
     */
    @Test
    public void testUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 7);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 7, "Sandslash", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .update(stmt, updatedPokemon.getName(), updatedPokemon.getId());
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }
}
