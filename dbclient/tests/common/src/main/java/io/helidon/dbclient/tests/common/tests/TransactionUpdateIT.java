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
import io.helidon.dbclient.DbTransaction;
import io.helidon.dbclient.tests.common.model.Critter;
import io.helidon.dbclient.tests.common.utils.TestConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.dbclient.tests.common.model.Kind.KINDS;
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyInsertCritter;
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyUpdateCritter;

/**
 * Test set of basic JDBC updates in transaction.
 */
@SuppressWarnings("SpellCheckingInspection")
public abstract class TransactionUpdateIT {

    private static final System.Logger LOGGER = System.getLogger(TransactionUpdateIT.class.getName());
    private static final int BASE_ID = TestConfig.LAST_POKEMON_ID + 220;
    private static final Map<Integer, Critter> POKEMONS = new HashMap<>();

    private final DbClient dbClient;
    private final Config config;

    public TransactionUpdateIT(DbClient dbClient, Config config) {
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
            addCritter(dbClient, new Critter(++curId, "Teddiursa", KINDS.get(1)));                // BASE_ID+1
            addCritter(dbClient, new Critter(++curId, "Ursaring", KINDS.get(1)));                 // BASE_ID+2
            addCritter(dbClient, new Critter(++curId, "Slugma", KINDS.get(10)));                  // BASE_ID+3
            addCritter(dbClient, new Critter(++curId, "Magcargo", KINDS.get(6), KINDS.get(10)));  // BASE_ID+4
            addCritter(dbClient, new Critter(++curId, "Lotad", KINDS.get(11), KINDS.get(12)));    // BASE_ID+5
            addCritter(dbClient, new Critter(++curId, "Lombre", KINDS.get(11), KINDS.get(12)));   // BASE_ID+6
            addCritter(dbClient, new Critter(++curId, "Ludicolo", KINDS.get(11), KINDS.get(12))); // BASE_ID+7
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
        Critter updatedCritter = new Critter(BASE_ID + 1, "Ursaring", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedUpdate("update-spearow", stmt)
                .addParam("name", updatedCritter.getName()).addParam("id", updatedCritter.getId())
                .execute();
        tx.commit();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedUpdateStrNamedArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 2);
        Critter updatedCritter = new Critter(BASE_ID + 2, "Teddiursa", srcCritter.getTypesArray());
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedUpdate("update-pokemon-named-arg")
                .addParam("name", updatedCritter.getName()).addParam("id", updatedCritter.getId())
                .execute();
        tx.commit();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedUpdateStrOrderArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 3);
        Critter updatedCritter = new Critter(BASE_ID + 3, "Magcargo", srcCritter.getTypesArray());
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedUpdate("update-pokemon-order-arg")
                .addParam(updatedCritter.getName()).addParam(updatedCritter.getId())
                .execute();
        tx.commit();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateUpdateNamedArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 4);
        Critter updatedCritter = new Critter(BASE_ID + 4, "Slugma", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createUpdate(stmt)
                .addParam("name", updatedCritter.getName()).addParam("id", updatedCritter.getId())
                .execute();
        tx.commit();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createUpdate(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateUpdateOrderArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 5);
        Critter updatedCritter = new Critter(BASE_ID + 5, "Lombre", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createUpdate(stmt)
                .addParam(updatedCritter.getName()).addParam(updatedCritter.getId())
                .execute();
        tx.commit();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code namedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testNamedUpdateNamedArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 6);
        Critter updatedCritter = new Critter(BASE_ID + 6, "Ludicolo", srcCritter.getTypesArray());
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .namedUpdate("update-pokemon-order-arg", updatedCritter.getName(), updatedCritter.getId());
        tx.commit();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code update(String)} API method with ordered parameters.
     */
    @Test
    public void testUpdateOrderArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 7);
        Critter updatedCritter = new Critter(BASE_ID + 7, "Lotad", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .update(stmt, updatedCritter.getName(), updatedCritter.getId());
        tx.commit();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }
}
