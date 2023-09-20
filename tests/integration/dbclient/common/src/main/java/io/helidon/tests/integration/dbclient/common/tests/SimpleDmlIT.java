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
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyDeletePokemon;
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyInsertPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyUpdatePokemon;

/**
 * Test set of basic JDBC DML statement calls.
 */
@SuppressWarnings("SpellCheckingInspection")
public class SimpleDmlIT extends AbstractIT {

    private static final System.Logger LOGGER = System.getLogger(SimpleDmlIT.class.getName());
    private static final int BASE_ID = LAST_POKEMON_ID + 40;
    private static final Map<Integer, Pokemon> POKEMONS = new HashMap<>();
    private final DbClient dbClient;
    private final Config config;

    public SimpleDmlIT(DbClient dbClient, Config config) {
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
    public static void setup(DbClient dbClient) {
        try {
            // [BASE_ID + 1 .. BASE_ID + 9] is reserved for inserts
            // [BASE_ID + 10 .. BASE_ID + 19] are reserved for updates
            addPokemon(dbClient, new Pokemon(BASE_ID + 10, "Piplup", TYPES.get(11)));                 // BASE_ID+10
            addPokemon(dbClient, new Pokemon(BASE_ID + 11, "Prinplup", TYPES.get(11)));               // BASE_ID+11
            addPokemon(dbClient, new Pokemon(BASE_ID + 12, "Empoleon", TYPES.get(9), TYPES.get(11))); // BASE_ID+12
            addPokemon(dbClient, new Pokemon(BASE_ID + 13, "Staryu", TYPES.get(11)));                 // BASE_ID+13
            addPokemon(dbClient,new Pokemon(BASE_ID + 14, "Starmie", TYPES.get(11), TYPES.get(14))); // BASE_ID+14
            addPokemon(dbClient,new Pokemon(BASE_ID + 15, "Horsea", TYPES.get(11)));                 // BASE_ID+15
            addPokemon(dbClient,new Pokemon(BASE_ID + 16, "Seadra", TYPES.get(11)));                 // BASE_ID+16
            // BASE_ID + 20 .. BASE_ID + 29 are reserved for deletes
            addPokemon(dbClient,new Pokemon(BASE_ID + 20, "Mudkip", TYPES.get(11)));                  // BASE_ID+20
            addPokemon(dbClient,new Pokemon(BASE_ID + 21, "Marshtomp", TYPES.get(5), TYPES.get(11))); // BASE_ID+21
            addPokemon(dbClient,new Pokemon(BASE_ID + 22, "Swampert", TYPES.get(5), TYPES.get(11)));  // BASE_ID+22
            addPokemon(dbClient,new Pokemon(BASE_ID + 23, "Muk", TYPES.get(4)));                      // BASE_ID+23
            addPokemon(dbClient,new Pokemon(BASE_ID + 24, "Grimer", TYPES.get(4)));                   // BASE_ID+24
            addPokemon(dbClient,new Pokemon(BASE_ID + 25, "Cubchoo", TYPES.get(15)));                 // BASE_ID+25
            addPokemon(dbClient,new Pokemon(BASE_ID + 26, "Beartic", TYPES.get(15)));                 // BASE_ID+26
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
        String stmt = config.get("db.statements.insert-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createNamedDmlStatement("insert-torchic", stmt)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 2, "Combusken", TYPES.get(2), TYPES.get(10));
        long result = dbClient.execute()
                .createNamedDmlStatement("insert-pokemon-named-arg")
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 3, "Treecko", TYPES.get(12));
        long result = dbClient.execute()
                .createNamedDmlStatement("insert-pokemon-order-arg")
                .addParam(pokemon.getId()).addParam(pokemon.getName())
                .execute();
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateDmlWithInsertNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 4, "Grovyle", TYPES.get(12));
        String stmt = config.get("db.statements.insert-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with ordered parameters.
     */
    @Test
    public void testCreateDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 5, "Sceptile", TYPES.get(12));
        String stmt = config.get("db.statements.insert-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam(pokemon.getId()).addParam(pokemon.getName())
                .execute();
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code namedDml(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 6, "Snover", TYPES.get(12), TYPES.get(15));
        long result = dbClient.execute()
                .namedDml("insert-pokemon-order-arg", pokemon.getId(), pokemon.getName());
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code dml(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 7, "Abomasnow", TYPES.get(12), TYPES.get(15));
        String stmt = config.get("db.statements.insert-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .dml(stmt, pokemon.getId(), pokemon.getName());
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrStrNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 10);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 10, "Prinplup", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createNamedDmlStatement("update-piplup", stmt)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 11);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 11, "Empoleon", srcPokemon.getTypesArray());
        long result = dbClient.execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 12);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 12, "Piplup", srcPokemon.getTypesArray());
        long result = dbClient.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateDmlWithUpdateNamedArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 13);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 13, "Starmie", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateDmlWithUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 14);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 14, "Staryu", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                .execute();
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 15);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 15, "Seadra", srcPokemon.getTypesArray());
        long result = dbClient.execute()
                .namedDml("update-pokemon-order-arg", updatedPokemon.getName(), updatedPokemon.getId());
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code dml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithUpdateOrderArgs() {
        Pokemon srcPokemon = POKEMONS.get(BASE_ID + 16);
        Pokemon updatedPokemon = new Pokemon(BASE_ID + 16, "Horsea", srcPokemon.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .dml(stmt, updatedPokemon.getName(), updatedPokemon.getId());
        verifyUpdatePokemon(dbClient, result, updatedPokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrStrOrderArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 20);
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createNamedDmlStatement("delete-mudkip", stmt)
                .addParam(pokemon.getId())
                .execute();
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrNamedArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 21);
        long result = dbClient.execute()
                .createNamedDmlStatement("delete-pokemon-named-arg")
                .addParam("id", pokemon.getId())
                .execute();
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrOrderArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 22);
        long result = dbClient.execute()
                .createNamedDmlStatement("delete-pokemon-order-arg")
                .addParam(pokemon.getId())
                .execute();
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    public void testCreateDmlWithDeleteNamedArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 23);
        String stmt = config.get("db.statements.delete-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam("id", pokemon.getId())
                .execute();
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateDmlWithDeleteOrderArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 24);
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam(pokemon.getId())
                .execute();
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code namedDml(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testNamedDmlWithDeleteOrderArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 25);
        long result = dbClient.execute().namedDml("delete-pokemon-order-arg", pokemon.getId());
        verifyDeletePokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code dml(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testDmlWithDeleteOrderArgs() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 26);
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        long result = dbClient.execute().dml(stmt, pokemon.getId());
        verifyDeletePokemon(dbClient, result, pokemon);
    }
}
