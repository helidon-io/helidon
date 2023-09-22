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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.tests.common.model.Critter;
import io.helidon.dbclient.tests.common.utils.TestConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.dbclient.tests.common.model.Kind.KINDS;
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyInsertCritter;
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyUpdateCritter;

/**
 * Test DbStatementDml methods.
 */
@SuppressWarnings("SpellCheckingInspection")
public abstract class StatementDmlIT {

    private static final System.Logger LOGGER = System.getLogger(StatementDmlIT.class.getName());
    private static final int BASE_ID = TestConfig.LAST_POKEMON_ID + 100;

    private final DbClient dbClient;

    public StatementDmlIT(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    private static void addCritter(DbClient dbClient, Critter pokemon) {
        long result = dbClient.execute()
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName());
        verifyInsertCritter(dbClient, result, pokemon);
    }

    /**
     * Initialize DbStatementDml methods tests.
     */
    @BeforeAll
    public static void setup(DbClient dbClient) {
        try {
            addCritter(dbClient, new Critter(BASE_ID, "Shinx", KINDS.get(13)));                      // BASE_ID+0
            addCritter(dbClient, new Critter(BASE_ID + 1, "Luxio", KINDS.get(13)));               // BASE_ID+1
            addCritter(dbClient, new Critter(BASE_ID + 2, "Luxray", KINDS.get(13)));              // BASE_ID+2
            addCritter(dbClient, new Critter(BASE_ID + 3, "Kricketot", KINDS.get(7)));            // BASE_ID+3
            addCritter(dbClient, new Critter(BASE_ID + 4, "Kricketune", KINDS.get(7)));           // BASE_ID+4
            addCritter(dbClient, new Critter(BASE_ID + 5, "Phione", KINDS.get(11)));              // BASE_ID+5
            addCritter(dbClient, new Critter(BASE_ID + 6, "Chatot", KINDS.get(1), KINDS.get(3))); // BASE_ID+6
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, String.format("Exception in setup: %s", ex), ex);
            throw ex;
        }
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     */
    @Test
    public void testDmlArrayParams() {
        Critter pokemon = new Critter(BASE_ID, "Luxio", KINDS.get(13));
        long result = dbClient.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .params(pokemon.getName(), pokemon.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     */
    @Test
    public void testDmlListParams() {
        Critter pokemon = new Critter(BASE_ID + 1, "Luxray", KINDS.get(13));
        List<Object> params = new ArrayList<>(2);
        params.add(pokemon.getName());
        params.add(pokemon.getId());
        long result = dbClient.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .params(params)
                .execute();
        verifyUpdateCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     */
    @Test
    public void testDmlMapParams() {
        Critter pokemon = new Critter(BASE_ID + 2, "Shinx", KINDS.get(13));
        Map<String, Object> params = new HashMap<>(2);
        params.put("name", pokemon.getName());
        params.put("id", pokemon.getId());
        long result = dbClient.execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .params(params)
                .execute();
        verifyUpdateCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     */
    @Test
    public void testDmlOrderParam() {
        Critter pokemon = new Critter(BASE_ID + 3, "Kricketune", KINDS.get(7));
        long result = dbClient.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .addParam(pokemon.getName())
                .addParam(pokemon.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     */
    @Test
    public void testDmlNamedParam() {
        Critter pokemon = new Critter(BASE_ID + 4, "Kricketot", KINDS.get(7));
        long result = dbClient.execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .addParam("name", pokemon.getName())
                .addParam("id", pokemon.getId())
                .execute();
        verifyUpdateCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testDmlMappedNamedParam() {
        Critter pokemon = new Critter(BASE_ID + 5, "Chatot", KINDS.get(1), KINDS.get(3));
        long result = dbClient.execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .namedParam(pokemon)
                .execute();
        verifyUpdateCritter(dbClient, result, pokemon);
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testDmlMappedOrderParam() {
        Critter pokemon = new Critter(BASE_ID + 6, "Phione", KINDS.get(11));
        long result = dbClient.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .indexedParam(pokemon)
                .execute();
        verifyUpdateCritter(dbClient, result, pokemon);
    }
}
