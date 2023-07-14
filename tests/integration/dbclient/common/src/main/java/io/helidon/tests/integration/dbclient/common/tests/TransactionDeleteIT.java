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
import java.util.concurrent.ExecutionException;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbTransaction;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.harness.SetUp;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.model.Type.TYPES;
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyDeletePokemon;
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyInsertPokemon;

/**
 * Test set of basic JDBC delete calls in transaction.
 */
@SuppressWarnings("SpellCheckingInspection")
public class TransactionDeleteIT extends AbstractIT {

    private static final System.Logger LOGGER = System.getLogger(TransactionDeleteIT.class.getName());
    private static final int BASE_ID = LAST_POKEMON_ID + 230;
    private static final Map<Integer, Pokemon> POKEMONS = new HashMap<>();

    private final DbClient dbClient;
    private final Config config;

    public TransactionDeleteIT(DbClient dbClient, Config config) {
        this.dbClient = dbClient;
        this.config = config;
    }

    private static void addPokemon(DbClient dbClient, Pokemon pokemon) {
        POKEMONS.put(pokemon.getId(), pokemon);
        long result = dbClient.execute()
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName());
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    @SetUp
    public static void setup(DbClient dbClient) throws ExecutionException, InterruptedException {
        try {
            int curId = BASE_ID;
            addPokemon(dbClient, new Pokemon(++curId, "Omanyte", TYPES.get(6), TYPES.get(11)));  // BASE_ID+1
            addPokemon(dbClient, new Pokemon(++curId, "Omastar", TYPES.get(6), TYPES.get(11)));  // BASE_ID+2
            addPokemon(dbClient, new Pokemon(++curId, "Kabuto", TYPES.get(6), TYPES.get(11)));   // BASE_ID+3
            addPokemon(dbClient, new Pokemon(++curId, "Kabutops", TYPES.get(6), TYPES.get(11))); // BASE_ID+4
            addPokemon(dbClient, new Pokemon(++curId, "Chikorita", TYPES.get(12)));              // BASE_ID+5
            addPokemon(dbClient, new Pokemon(++curId, "Bayleef", TYPES.get(12)));                // BASE_ID+6
            addPokemon(dbClient, new Pokemon(++curId, "Meganium", TYPES.get(12)));               // BASE_ID+7
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
        Pokemon pokemon = POKEMONS.get(BASE_ID + 1);
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedDelete("delete-rayquaza", stmt)
                .addParam(pokemon.getId())
                .execute();
        tx.commit();
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedDeleteStrNamedArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 2);
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedDelete("delete-pokemon-named-arg")
                .addParam("id", pokemon.getId())
                .execute();
        tx.commit();
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedDeleteStrOrderArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 3);
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedDelete("delete-pokemon-order-arg")
                .addParam(pokemon.getId())
                .execute();
        tx.commit();
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createDelete(String)} API method with named parameters.
     */
    @Test
    public void testCreateDeleteNamedArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 4);
        String stmt = config.get("db.statements.delete-pokemon-named-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createDelete(stmt)
                .addParam("id", pokemon.getId())
                .execute();
        tx.commit();
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateDeleteOrderArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 5);
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createDelete(stmt)
                .addParam(pokemon.getId())
                .execute();
        tx.commit();
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code namedDelete(String)} API method with ordered parameters.
     */
    @Test
    public void testNamedDeleteOrderArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 6);
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .namedDelete("delete-pokemon-order-arg", pokemon.getId());
        tx.commit();
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code delete(String)} API method with ordered parameters.
     */
    @Test
    public void testDeleteOrderArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 7);
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .delete(stmt, pokemon.getId());
        tx.commit();
        verifyDeletePokemon(dbClient, result, pokemon);
    }
}
