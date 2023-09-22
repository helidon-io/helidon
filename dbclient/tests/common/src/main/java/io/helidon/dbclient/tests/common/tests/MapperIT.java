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
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.tests.common.model.Critter;
import io.helidon.dbclient.tests.common.utils.TestConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.dbclient.tests.common.model.Critter.CRITTERS;
import static io.helidon.dbclient.tests.common.model.Kind.KINDS;
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyInsertCritter;
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyCritter;
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyUpdateCritter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verify mapping interface.
 */
@SuppressWarnings("SpellCheckingInspection")
public abstract class MapperIT {

    private static final System.Logger LOGGER = System.getLogger(MapperIT.class.getName());
    private static final int BASE_ID = TestConfig.LAST_POKEMON_ID + 400;

    private final DbClient dbClient;

    public MapperIT(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    private static void addCritter(DbClient dbClient, Critter pokemon) {
        CRITTERS.put(pokemon.getId(), pokemon);
        long result = dbClient.execute()
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName());
        verifyInsertCritter(dbClient, result, pokemon);
    }

    @BeforeAll
    public static void setup(DbClient dbClient) throws ExecutionException, InterruptedException {
        try {
            // BASE_ID+1, 2 is used for inserts
            int curId = BASE_ID + 2;
            addCritter(dbClient, new Critter(++curId, "Moltres", KINDS.get(3), KINDS.get(10)));   // BASE_ID+3
            addCritter(dbClient, new Critter(++curId, "Masquerain", KINDS.get(3), KINDS.get(7))); // BASE_ID+4
            addCritter(dbClient, new Critter(++curId, "Makuhita", KINDS.get(2)));                 // BASE_ID+5
            addCritter(dbClient, new Critter(++curId, "Hariyama", KINDS.get(2)));                 // BASE_ID+6
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, String.format("Exception in setup: %s", ex.getMessage()), ex);
            throw ex;
        }
    }

    /**
     * Verify insertion of using indexed mapping.
     */
    @Test
    public void testInsertWithOrderMapping() {
        Critter pokemon = new Critter(BASE_ID + 1, "Articuno", KINDS.get(3), KINDS.get(15));
        long result = dbClient.execute()
                .createNamedInsert("insert-pokemon-order-arg-rev")
                .indexedParam(pokemon)
                .execute();
        verifyInsertCritter(dbClient, result, pokemon);
    }

    /**
     * Verify insertion of using named mapping.
     */
    @Test
    public void testInsertWithNamedMapping() {
        Critter pokemon = new Critter(BASE_ID + 2, "Zapdos", KINDS.get(3), KINDS.get(13));
        long result = dbClient.execute()
                .createNamedInsert("insert-pokemon-named-arg")
                .namedParam(pokemon)
                .execute();
        verifyInsertCritter(dbClient, result, pokemon);
    }

    /**
     * Verify update of using indexed mapping.
     */
    @Test
    public void testUpdateWithOrderMapping() {
        Critter pokemon = new Critter(BASE_ID + 3, "Masquerain", KINDS.get(3), KINDS.get(15));
        long result = dbClient.execute()
                .createNamedUpdate("update-pokemon-order-arg")
                .indexedParam(pokemon)
                .execute();
        verifyUpdateCritter(dbClient, result, pokemon);
    }

    /**
     * Verify update of using named mapping.
     */
    @Test
    public void testUpdateWithNamedMapping() {
        Critter pokemon = new Critter(BASE_ID + 4, "Moltres", KINDS.get(3), KINDS.get(13));
        long result = dbClient.execute()
                .createNamedUpdate("update-pokemon-named-arg")
                .namedParam(pokemon)
                .execute();
        verifyUpdateCritter(dbClient, result, pokemon);
    }

    /**
     * Verify delete of using indexed mapping.
     */
    @Test
    public void testDeleteWithOrderMapping() {
        DbExecute exec = dbClient.execute();
        Critter pokemon = CRITTERS.get(BASE_ID + 5);

        long result = exec.createNamedDelete("delete-pokemon-full-order-arg")
                .indexedParam(pokemon)
                .execute();

        assertThat(result, equalTo(1L));
        Optional<DbRow> maybeRow = exec
                .namedGet("select-pokemon-by-id", pokemon.getId());
        assertThat(maybeRow.isPresent(), equalTo(false));
    }

    /**
     * Verify delete of using named mapping.
     */
    @Test
    public void testDeleteWithNamedMapping() {
        DbExecute exec = dbClient.execute();
        Critter pokemon = CRITTERS.get(BASE_ID + 6);

        long result = exec.createNamedDelete("delete-pokemon-full-named-arg")
                .namedParam(pokemon)
                .execute();

        assertThat(result, equalTo(1L));
        Optional<DbRow> maybeRow = exec
                .namedGet("select-pokemon-by-id", pokemon.getId());
        assertThat(maybeRow.isPresent(), equalTo(false));
    }

    /**
     * Verify query of as a result using mapping.
     */
    @Test
    public void testQueryWithMapping() {
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", CRITTERS.get(2).getName())
                .execute();

        Critter pokemon = rows.map(it -> it.as(Critter.class)).toList().get(0);
        verifyCritter(pokemon, CRITTERS.get(2));
    }

    /**
     * Verify get of as a result using mapping.
     */
    @Test
    public void testGetWithMapping() {
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemon-named-arg")
                .addParam("name", CRITTERS.get(3).getName())
                .execute();

        assertThat(maybeRow.isPresent(), equalTo(true));
        Critter pokemon = maybeRow.get().as(Critter.class);
        verifyCritter(pokemon, CRITTERS.get(3));
    }
}
