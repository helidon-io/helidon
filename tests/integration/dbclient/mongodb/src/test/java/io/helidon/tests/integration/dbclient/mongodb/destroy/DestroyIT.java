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
package io.helidon.tests.integration.dbclient.mongodb.destroy;

import java.util.List;
import java.util.stream.Stream;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbRow;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Destroy database
 */
public class DestroyIT {

    /**
     * Delete database content.
     *
     * @param dbClient Helidon database client
     */
    private static void deleteSchema(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        exec.namedDelete("delete-poketypes");
        exec.namedDelete("delete-pokemons");
        exec.namedDelete("delete-types");
    }

    /**
     * Destroy database after tests.
     */
    @BeforeAll
    public static void destroy() {
        try {
            deleteSchema(DB_CLIENT);
        } catch (Throwable ex) {
            fail("Database cleanup failed!", ex);
        }
    }

    /**
     * Verify that table {@code Types} does not exist.
     */
    @Test
    public void testTypesDeleted() {
        Stream<DbRow> rows = DB_CLIENT.execute().namedQuery("select-types");
        if (rows != null) {
            List<DbRow> rowsList = rows.toList();
            assertThat(rowsList, empty());
        }
    }

    /**
     * Verify that table {@code Pokemon} does not exist.
     */
    @Test
    public void testPokemonsDeleted() {
        Stream<DbRow> rows = DB_CLIENT.execute().namedQuery("select-pokemons");
        if (rows != null) {
            List<DbRow> rowsList = rows.toList();
            assertThat(rowsList, empty());
        }
    }

    /**
     * Verify that table {@code PokemonTypes} does not exist.
     */
    @Test
    public void testPokemonTypesDeleted() {
        Stream<DbRow> rows = DB_CLIENT.execute().namedQuery("select-poketypes-all");
        if (rows != null) {
            List<DbRow> rowsList = rows.toList();
            assertThat(rowsList, empty());
        }
    }
}
