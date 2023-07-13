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
package io.helidon.tests.integration.dbclient.common.tests.mapping;

import java.lang.System.Logger.Level;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyDeletePokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyPokemon;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyUpdatePokemon;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verify mapping interface.
 */
@SuppressWarnings("SpellCheckingInspection")
public class MapperIT extends AbstractIT {

    /**
     * Local logger instance.
     */
    private static final System.Logger LOGGER = System.getLogger(MapperIT.class.getName());
    /**
     * Maximum Pokemon ID.
     */
    private static final int BASE_ID = LAST_POKEMON_ID + 400;

    private static void addPokemon(Pokemon pokemon) {
        POKEMONS.put(pokemon.getId(), pokemon);
        long result = DB_CLIENT.execute()
                .namedInsert("insert-pokemon", pokemon.getId(), pokemon.getName());
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Initialize tests of basic JDBC updates.
     *
     * @throws InterruptedException if the current thread was interrupted
     * @throws ExecutionException   when database query failed
     */
    @BeforeAll
    public static void setup() throws ExecutionException, InterruptedException {
        try {
            // BASE_ID+1, 2 is used for inserts
            int curId = BASE_ID + 2;
            addPokemon(new Pokemon(++curId, "Moltres", TYPES.get(3), TYPES.get(10)));   // BASE_ID+3
            addPokemon(new Pokemon(++curId, "Masquerain", TYPES.get(3), TYPES.get(7))); // BASE_ID+4
            addPokemon(new Pokemon(++curId, "Makuhita", TYPES.get(2)));                 // BASE_ID+5
            addPokemon(new Pokemon(++curId, "Hariyama", TYPES.get(2)));                 // BASE_ID+6
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
        Pokemon pokemon = new Pokemon(BASE_ID + 1, "Articuno", TYPES.get(3), TYPES.get(15));
        long result = DB_CLIENT.execute()
                .createNamedInsert("insert-pokemon-order-arg-rev")
                .indexedParam(pokemon)
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify insertion of using named mapping.
     */
    @Test
    public void testInsertWithNamedMapping() {
        Pokemon pokemon = new Pokemon(BASE_ID + 2, "Zapdos", TYPES.get(3), TYPES.get(13));
        long result = DB_CLIENT.execute()
                .createNamedInsert("insert-pokemon-named-arg")
                .namedParam(pokemon)
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify update of using indexed mapping.
     */
    @Test
    public void testUpdateWithOrderMapping() {
        Pokemon pokemon = new Pokemon(BASE_ID + 3, "Masquerain", TYPES.get(3), TYPES.get(15));
        long result = DB_CLIENT.execute()
                .createNamedUpdate("update-pokemon-order-arg")
                .indexedParam(pokemon)
                .execute();
        verifyUpdatePokemon(result, pokemon);
    }

    /**
     * Verify update of using named mapping.
     */
    @Test
    public void testUpdateWithNamedMapping() {
        Pokemon pokemon = new Pokemon(BASE_ID + 4, "Moltres", TYPES.get(3), TYPES.get(13));
        long result = DB_CLIENT.execute()
                .createNamedUpdate("update-pokemon-named-arg")
                .namedParam(pokemon)
                .execute();
        verifyUpdatePokemon(result, pokemon);
    }

    /**
     * Verify delete of using indexed mapping.
     */
    @Test
    public void testDeleteWithOrderMapping() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 5);
        long result = DB_CLIENT.execute()
                .createNamedDelete("delete-pokemon-full-order-arg")
                .indexedParam(pokemon)
                .execute();
        verifyDeletePokemon(result, pokemon);
    }

    /**
     * Verify delete of using named mapping.
     */
    @Test
    public void testDeleteWithNamedMapping() {
        Pokemon pokemon = POKEMONS.get(BASE_ID + 6);
        long result = DB_CLIENT.execute()
                .createNamedDelete("delete-pokemon-full-named-arg")
                .namedParam(pokemon)
                .execute();
        verifyDeletePokemon(result, pokemon);
    }

    /**
     * Verify query of as a result using mapping.
     */
    @Test
    public void testQueryWithMapping() {
        Stream<DbRow> rows = DB_CLIENT.execute()
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", POKEMONS.get(2).getName())
                .execute();

        Pokemon pokemon = rows.map(it -> it.as(Pokemon.class)).toList().get(0);
        verifyPokemon(pokemon, POKEMONS.get(2));
    }

    /**
     * Verify get of as a result using mapping.
     */
    @Test
    public void testGetWithMapping() {
        Optional<DbRow> maybeRow = DB_CLIENT.execute()
                .createNamedGet("select-pokemon-named-arg")
                .addParam("name", POKEMONS.get(3).getName())
                .execute();

        assertThat(maybeRow.isPresent(), equalTo(true));
        Pokemon pokemon = maybeRow.get().as(Pokemon.class);
        verifyPokemon(pokemon, POKEMONS.get(3));
    }
}
