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
import io.helidon.dbclient.DbTransaction;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.utils.TestConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.tests.integration.dbclient.common.model.Type.TYPES;
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyInsertPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyUpdatePokemon;

/**
 * Test set of basic JDBC updates in transaction.
 */
@SuppressWarnings("SpellCheckingInspection")
@ExtendWith(DbClientParameterResolver.class)
public class TransactionUpdateIT {

    private static final System.Logger LOGGER = System.getLogger(TransactionUpdateIT.class.getName());
    private static final int BASE_ID = TestConfig.LAST_POKEMON_ID + 220;
    private static final Map<Integer, Pokemon> POKEMONS = new HashMap<>();

    private final DbClient dbClient;
    private final Config config;

    public TransactionUpdateIT(DbClient dbClient, Config config) {
        this.dbClient = dbClient;
        this.config = config;
    }

    private static void addPokemon(DbClient dbClient, Pokemon pokemon) {
        POKEMONS.put(pokemon.getId(), pokemon);
        long result = dbClient.execute()
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName());
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    @BeforeAll
    public static void setup(DbClient dbClient) {
        try {
            int curId = BASE_ID;
            addPokemon(dbClient, new Pokemon(++curId, "Teddiursa", TYPES.get(1)));                // BASE_ID+1
            addPokemon(dbClient, new Pokemon(++curId, "Ursaring", TYPES.get(1)));                 // BASE_ID+2
            addPokemon(dbClient, new Pokemon(++curId, "Slugma", TYPES.get(10)));                  // BASE_ID+3
            addPokemon(dbClient, new Pokemon(++curId, "Magcargo", TYPES.get(6), TYPES.get(10)));  // BASE_ID+4
            addPokemon(dbClient, new Pokemon(++curId, "Lotad", TYPES.get(11), TYPES.get(12)));    // BASE_ID+5
            addPokemon(dbClient, new Pokemon(++curId, "Lombre", TYPES.get(11), TYPES.get(12)));   // BASE_ID+6
            addPokemon(dbClient, new Pokemon(++curId, "Ludicolo", TYPES.get(11), TYPES.get(12))); // BASE_ID+7
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
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 1, "Ursaring", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedUpdate("update-spearow", stmt)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        tx.commit();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedUpdateStrNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 2);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 2, "Teddiursa", srcPokemon.getTypesArray());
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedUpdate("update-pokemon-named-arg")
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        tx.commit();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedUpdate(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedUpdateStrOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 3);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 3, "Magcargo", srcPokemon.getTypesArray());
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedUpdate("update-pokemon-order-arg")
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                .execute();
        tx.commit();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     */
    @Test
    public void testCreateUpdateNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 4);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 4, "Slugma", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createUpdate(stmt)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        tx.commit();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createUpdate(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 5);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 5, "Lombre", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createUpdate(stmt)
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                .execute();
        tx.commit();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code namedUpdate(String)} API method with named parameters.
     */
    @Test
    public void testNamedUpdateNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 6);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 6, "Ludicolo", srcPokemon.getTypesArray());
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .namedUpdate("update-pokemon-order-arg", updatedPokemon.getName(), updatedPokemon.getId());
        tx.commit();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code update(String)} API method with ordered parameters.
     */
    @Test
    public void testUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 7);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 7, "Lotad", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .update(stmt, updatedPokemon.getName(), updatedPokemon.getId());
        tx.commit();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }
}
