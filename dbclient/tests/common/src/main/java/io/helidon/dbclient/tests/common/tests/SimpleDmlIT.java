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
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyUpdateCritter;

/**
 * Test set of basic JDBC DML statement calls.
 */
@SuppressWarnings("SpellCheckingInspection")
public abstract class SimpleDmlIT {

    private static final System.Logger LOGGER = System.getLogger(SimpleDmlIT.class.getName());
    private static final int BASE_ID = TestConfig.LAST_POKEMON_ID + 40;
    private static final Map<Integer, Critter> POKEMONS = new HashMap<>();
    private final DbClient dbClient;
    private final Config config;

    public SimpleDmlIT(DbClient dbClient, Config config) {
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
            // [BASE_ID + 1 .. BASE_ID + 9] is reserved for inserts
            // [BASE_ID + 10 .. BASE_ID + 19] are reserved for updates
            addCritter(dbClient, new Critter(BASE_ID + 10, "Piplup", KINDS.get(11)));                 // BASE_ID+10
            addCritter(dbClient, new Critter(BASE_ID + 11, "Prinplup", KINDS.get(11)));               // BASE_ID+11
            addCritter(dbClient, new Critter(BASE_ID + 12, "Empoleon", KINDS.get(9), KINDS.get(11))); // BASE_ID+12
            addCritter(dbClient, new Critter(BASE_ID + 13, "Staryu", KINDS.get(11)));                 // BASE_ID+13
            addCritter(dbClient,new Critter(BASE_ID + 14, "Starmie", KINDS.get(11), KINDS.get(14))); // BASE_ID+14
            addCritter(dbClient,new Critter(BASE_ID + 15, "Horsea", KINDS.get(11)));                 // BASE_ID+15
            addCritter(dbClient,new Critter(BASE_ID + 16, "Seadra", KINDS.get(11)));                 // BASE_ID+16
            // BASE_ID + 20 .. BASE_ID + 29 are reserved for deletes
            addCritter(dbClient,new Critter(BASE_ID + 20, "Mudkip", KINDS.get(11)));                  // BASE_ID+20
            addCritter(dbClient,new Critter(BASE_ID + 21, "Marshtomp", KINDS.get(5), KINDS.get(11))); // BASE_ID+21
            addCritter(dbClient,new Critter(BASE_ID + 22, "Swampert", KINDS.get(5), KINDS.get(11)));  // BASE_ID+22
            addCritter(dbClient,new Critter(BASE_ID + 23, "Muk", KINDS.get(4)));                      // BASE_ID+23
            addCritter(dbClient,new Critter(BASE_ID + 24, "Grimer", KINDS.get(4)));                   // BASE_ID+24
            addCritter(dbClient,new Critter(BASE_ID + 25, "Cubchoo", KINDS.get(15)));                 // BASE_ID+25
            addCritter(dbClient,new Critter(BASE_ID + 26, "Beartic", KINDS.get(15)));                 // BASE_ID+26
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
        Critter pokemon = new Critter(BASE_ID + 1, "Torchic", KINDS.get(10));
        String stmt = config.get("db.statements.insert-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createNamedDmlStatement("insert-torchic", stmt)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrNamedArgs() {
        Critter pokemon = new Critter(BASE_ID + 2, "Combusken", KINDS.get(2), KINDS.get(10));
        long result = dbClient.execute()
                .createNamedDmlStatement("insert-pokemon-named-arg")
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with insert with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithInsertStrOrderArgs() {
        Critter pokemon = new Critter(BASE_ID + 3, "Treecko", KINDS.get(12));
        long result = dbClient.execute()
                .createNamedDmlStatement("insert-pokemon-order-arg")
                .addParam(pokemon.getId()).addParam(pokemon.getName())
                .execute();
        verifyInsertCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with named parameters.
     */
    @Test
    public void testCreateDmlWithInsertNamedArgs() {
        Critter pokemon = new Critter(BASE_ID + 4, "Grovyle", KINDS.get(12));
        String stmt = config.get("db.statements.insert-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName())
                .execute();
        verifyInsertCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with insert with ordered parameters.
     */
    @Test
    public void testCreateDmlWithInsertOrderArgs() {
        Critter pokemon = new Critter(BASE_ID + 5, "Sceptile", KINDS.get(12));
        String stmt = config.get("db.statements.insert-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam(pokemon.getId()).addParam(pokemon.getName())
                .execute();
        verifyInsertCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code namedDml(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithInsertOrderArgs() {
        Critter pokemon = new Critter(BASE_ID + 6, "Snover", KINDS.get(12), KINDS.get(15));
        long result = dbClient.execute()
                .namedDml("insert-pokemon-order-arg", pokemon.getId(), pokemon.getName());
        verifyInsertCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code dml(String)} API method with insert with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithInsertOrderArgs() {
        Critter pokemon = new Critter(BASE_ID + 7, "Abomasnow", KINDS.get(12), KINDS.get(15));
        String stmt = config.get("db.statements.insert-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .dml(stmt, pokemon.getId(), pokemon.getName());
        verifyInsertCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrStrNamedArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 10);
        Critter updatedCritter = new Critter(BASE_ID + 10, "Prinplup", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createNamedDmlStatement("update-piplup", stmt)
                .addParam("name", updatedCritter.getName()).addParam("id", updatedCritter.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrNamedArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 11);
        Critter updatedCritter = new Critter(BASE_ID + 11, "Empoleon", srcCritter.getTypesArray());
        long result = dbClient.execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .addParam("name", updatedCritter.getName()).addParam("id", updatedCritter.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithUpdateStrOrderArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 12);
        Critter updatedCritter = new Critter(BASE_ID + 12, "Piplup", srcCritter.getTypesArray());
        long result = dbClient.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .addParam(updatedCritter.getName()).addParam(updatedCritter.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with named parameters.
     */
    @Test
    public void testCreateDmlWithUpdateNamedArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 13);
        Critter updatedCritter = new Critter(BASE_ID + 13, "Starmie", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam("name", updatedCritter.getName()).addParam("id", updatedCritter.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
     */
    @Test
    public void testCreateDmlWithUpdateOrderArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 14);
        Critter updatedCritter = new Critter(BASE_ID + 14, "Staryu", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam(updatedCritter.getName()).addParam(updatedCritter.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testNamedDmlWithUpdateOrderArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 15);
        Critter updatedCritter = new Critter(BASE_ID + 15, "Seadra", srcCritter.getTypesArray());
        long result = dbClient.execute()
                .namedDml("update-pokemon-order-arg", updatedCritter.getName(), updatedCritter.getId());
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code dml(String)} API method with update with ordered parameters passed directly
     * to the {@code insert} method.
     */
    @Test
    public void testDmlWithUpdateOrderArgs() {
        Critter srcCritter = POKEMONS.get(BASE_ID + 16);
        Critter updatedCritter = new Critter(BASE_ID + 16, "Horsea", srcCritter.getTypesArray());
        String stmt = config.get("db.statements.update-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .dml(stmt, updatedCritter.getName(), updatedCritter.getId());
        verifyUpdateCritter(dbClient, result, updatedCritter);
    }

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrStrOrderArgs() {
        Critter pokemon = POKEMONS.get(BASE_ID + 20);
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createNamedDmlStatement("delete-mudkip", stmt)
                .addParam(pokemon.getId())
                .execute();
        verifyDeleteCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrNamedArgs() {
        Critter pokemon = POKEMONS.get(BASE_ID + 21);
        long result = dbClient.execute()
                .createNamedDmlStatement("delete-pokemon-named-arg")
                .addParam("id", pokemon.getId())
                .execute();
        verifyDeleteCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateNamedDmlWithDeleteStrOrderArgs() {
        Critter pokemon = POKEMONS.get(BASE_ID + 22);
        long result = dbClient.execute()
                .createNamedDmlStatement("delete-pokemon-order-arg")
                .addParam(pokemon.getId())
                .execute();
        verifyDeleteCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with named parameters.
     */
    @Test
    public void testCreateDmlWithDeleteNamedArgs() {
        Critter pokemon = POKEMONS.get(BASE_ID + 23);
        String stmt = config.get("db.statements.delete-pokemon-named-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam("id", pokemon.getId())
                .execute();
        verifyDeleteCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testCreateDmlWithDeleteOrderArgs() {
        Critter pokemon = POKEMONS.get(BASE_ID + 24);
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        long result = dbClient.execute()
                .createDmlStatement(stmt)
                .addParam(pokemon.getId())
                .execute();
        verifyDeleteCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code namedDml(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testNamedDmlWithDeleteOrderArgs() {
        Critter pokemon = POKEMONS.get(BASE_ID + 25);
        long result = dbClient.execute().namedDml("delete-pokemon-order-arg", pokemon.getId());
        verifyDeleteCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code dml(String)} API method with delete with ordered parameters.
     */
    @Test
    public void testDmlWithDeleteOrderArgs() {
        Critter pokemon = POKEMONS.get(BASE_ID + 26);
        String stmt = config.get("db.statements.delete-pokemon-order-arg").asString().get();
        long result = dbClient.execute().dml(stmt, pokemon.getId());
        verifyDeleteCritter(dbClient, result, pokemon);
    }
}
