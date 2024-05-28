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
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyDeleteCritter;
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyInsertCritter;

/**
 * Test set of basic JDBC delete calls.
 */
@SuppressWarnings("SpellCheckingInspection")
public abstract class SimpleDeleteIT {

    private static final System.Logger LOGGER = System.getLogger(SimpleDeleteIT.class.getName());
    private static final int BASE_ID = TestConfig.LAST_POKEMON_ID + 30;
    private static final Map<Integer, Critter> POKEMONS = new HashMap<>();

    private final DbClient dbClient;
    private final Config config;

    public SimpleDeleteIT(DbClient dbClient, Config config) {
        this.dbClient = dbClient;
        this.config = config;
    }

    private static void addCritter(DbClient dbClient, Critter pokemon) {
        POKEMONS.put(pokemon.getId(), pokemon);
        long result = dbClient.execute()
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName());
        verifyInsertCritter(dbClient, result, pokemon);
    }

    @BeforeAll
    public static void setup(DbClient dbClient) {
        try {
            int curId = BASE_ID;
            addCritter(dbClient, new Critter(++curId, "Rayquaza", KINDS.get(3), KINDS.get(16))); // BASE_ID+1
            addCritter(dbClient, new Critter(++curId, "Lugia", KINDS.get(3), KINDS.get(14)));    // BASE_ID+2
            addCritter(dbClient, new Critter(++curId, "Ho-Oh", KINDS.get(3), KINDS.get(10)));    // BASE_ID+3
            addCritter(dbClient, new Critter(++curId, "Raikou", KINDS.get(13)));                 // BASE_ID+4
            addCritter(dbClient, new Critter(++curId, "Giratina", KINDS.get(8), KINDS.get(16))); // BASE_ID+5
            addCritter(dbClient, new Critter(++curId, "Regirock", KINDS.get(6)));                // BASE_ID+6
            addCritter(dbClient, new Critter(++curId, "Kyogre", KINDS.get(11)));                 // BASE_ID+7
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, String.format("Exception in setup: %s", ex));
            throw ex;
        }
    }


    /**
     * Verify {@code createNamedDelete(String, String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedDeleteStrStrOrderArgs() {
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createNamedDelete("delete-rayquaza", stmt)
                .addParam(POKEMONS.get(BASE_ID + 1).getId()).execute();
        verifyDeleteCritter(dbClient, result, POKEMONS.get(BASE_ID + 1));
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedDeleteStrNamedArgs() {
        long result = dbClient.execute()
                .createNamedDelete("delete-pokemon-named-arg")
                .addParam("id", POKEMONS.get(BASE_ID + 2).getId()).execute();
        verifyDeleteCritter(dbClient, result, POKEMONS.get(BASE_ID + 2));
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedDeleteStrOrderArgs() {
        long result = dbClient.execute()
                .createNamedDelete("delete-pokemon-order-arg")
                .addParam(POKEMONS.get(BASE_ID + 3).getId()).execute();
        verifyDeleteCritter(dbClient, result, POKEMONS.get(BASE_ID + 3));
    }

    /**
     * Verify {@code createDelete(String)} API method with named parameters.
     */
    @Test
    public void testCreateDeleteNamedArgs() {
        String stmt = config.get("db.statements.delete-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createDelete(stmt)
                .addParam("id", POKEMONS.get(BASE_ID + 4).getId()).execute();
        verifyDeleteCritter(dbClient, result, POKEMONS.get(BASE_ID + 4));
    }

    /**
     * Verify {@code createDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateDeleteOrderArgs() {
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createDelete(stmt)
                .addParam(POKEMONS.get(BASE_ID + 5).getId()).execute();
        verifyDeleteCritter(dbClient, result, POKEMONS.get(BASE_ID + 5));
    }

    /**
     * Verify {@code namedDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testNamedDeleteOrderArgs() {
        long result = dbClient.execute()
                .namedDelete("delete-pokemon-order-arg", POKEMONS.get(BASE_ID + 6).getId());
        verifyDeleteCritter(dbClient, result, POKEMONS.get(BASE_ID + 6));
    }

    /**
     * Verify {@code delete(String)} API method with ordered parameters.
     */
    @Test
    public void testDeleteOrderArgs() {
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .delete(stmt, POKEMONS.get(BASE_ID + 7).getId());
        verifyDeleteCritter(dbClient, result, POKEMONS.get(BASE_ID + 7));
    }

}
