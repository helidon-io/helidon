/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient.tests.common.tests;

import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.tests.common.model.Critter;
import io.helidon.dbclient.tests.common.utils.TestConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.dbclient.tests.common.model.Kind.KINDS;
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyInsertCritter;
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyUpdateCritter;

/**
 * Test set of basic JDBC updates.
 */
@SuppressWarnings("SpellCheckingInspection")
public abstract class SimpleUpdateIT {

    private static final System.Logger LOGGER = System.getLogger(SimpleUpdateIT.class.getName());
    private static final int BASE_ID = TestConfig.LAST_POKEMON_ID + 20;
    private static final Map<Integer, Critter> POKEMONS = new HashMap<>();

    private final DbClient dbClient;
    private final Config config;

    public SimpleUpdateIT(DbClient dbClient, Config config) {
        this.dbClient = dbClient;
        this.config = config;
    }

    private static void addCritter(DbClient dbClient, Critter pokemon) {
        POKEMONS.put(pokemon.getId(), pokemon);
        long result = dbClient.execute()
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName());
        verifyInsertCritter(dbClient, result, pokemon);
    }

    /**
     * Initialize tests of basic JDBC updates.
     */
    @BeforeAll
    public static void setup(DbClient dbClient) {
        try {
            int curId = BASE_ID;
            addCritter(dbClient, new Critter(++curId, "Spearow", KINDS.get(1), KINDS.get(3))); // BASE_ID+1
            addCritter(dbClient, new Critter(++curId, "Fearow", KINDS.get(1), KINDS.get(3)));  // BASE_ID+2
            addCritter(dbClient, new Critter(++curId, "Ekans", KINDS.get(4)));                 // BASE_ID+3
            addCritter(dbClient, new Critter(++curId, "Arbok", KINDS.get(4)));                 // BASE_ID+4
            addCritter(dbClient, new Critter(++curId, "Sandshrew", KINDS.get(5)));             // BASE_ID+5
            addCritter(dbClient, new Critter(++curId, "Sandslash", KINDS.get(5)));             // BASE_ID+6
            addCritter(dbClient, new Critter(++curId, "Diglett", KINDS.get(5)));               // BASE_ID+7
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
        Critter srcCritter = POKEMONS.get(BASE_ID + 1);
        Critter updatedCritter = new Critter(BASE_ID + 1, "Fearow", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createNamedUpdate("update-spearow", stmt)
                .addParam("name", updatedCritter.getName()).addParam("id", updatedCritter.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedUpdateStrNamedArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 2);
        Critter updatedCritter = new Critter(BASE_ID + 2, "Spearow", srcCritter.getTypesArray());
        long result = dbClient.execute()
                .createNamedUpdate("update-pokemon-named-arg")
                .addParam("name", updatedCritter.getName()).addParam("id", updatedCritter.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedUpdateStrOrderArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 3);
        Critter updatedCritter = new Critter(BASE_ID + 3, "Arbok", srcCritter.getTypesArray());
        long result = dbClient.execute()
                .createNamedUpdate("update-pokemon-order-arg")
                .addParam(updatedCritter.getName()).addParam(updatedCritter.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateUpdateNamedArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 4);
        Critter updatedCritter = new Critter(BASE_ID + 4, "Ekans", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createUpdate(stmt)
                .addParam("name", updatedCritter.getName()).addParam("id", updatedCritter.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createUpdate(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateUpdateOrderArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 5);
        Critter updatedCritter = new Critter(BASE_ID + 5, "Diglett", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createUpdate(stmt)
                .addParam(updatedCritter.getName()).addParam(updatedCritter.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code namedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testNamedUpdateNamedArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 6);
        Critter updatedCritter = new Critter(BASE_ID + 6, "Sandshrew", srcCritter.getTypesArray());
        long result = dbClient.execute()
                .namedUpdate("update-pokemon-order-arg", updatedCritter.getName(), updatedCritter.getId());
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code update(String)} API method with ordered parameters.
     */
    @Test
    public void testUpdateOrderArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 7);
        Critter updatedCritter = new Critter(BASE_ID + 7, "Sandslash", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .update(stmt, updatedCritter.getName(), updatedCritter.getId());
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }
}
