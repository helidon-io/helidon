/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.reactive.Multi;
import io.helidon.dbclient.DbClient;
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

    /** Local logger instance. */
    static final Logger LOGGER = Logger.getLogger(DestroyIT.class.getName());

    /**
     * Delete database content.
     *
     * @param dbClient Helidon database client
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    private static void deleteSchema(DbClient dbClient) throws ExecutionException, InterruptedException {
        dbClient.execute(exec -> exec
                .namedDelete("delete-poketypes")
                .flatMapSingle(result -> exec.namedDelete("delete-pokemons"))
                .flatMapSingle(result -> exec.namedDelete("delete-types"))
        ).await(10, TimeUnit.SECONDS);
    }

    /**
     * Destroy database after tests.
     */
    @BeforeAll
    public static void destroy() {
        try {
            deleteSchema(DB_CLIENT);
        } catch (ExecutionException | InterruptedException ex) {
            fail("Database cleanup failed!", ex);
        }
    }

    /**
     * Verify that table Types does not exist.
     */
    @Test
    public void testTypesDeleted() throws InterruptedException {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-types"));

        if (rows != null) {
            List<DbRow> rowsList = rows.collectList().await();
            LOGGER.warning(() -> String.format("Rows count: %d", rowsList.size()));
            assertThat(rowsList, empty());
        }
    }

    /**
     * Verify that table Pokemons does not exist.
     */
    @Test
    public void testPokemonsDeleted() throws InterruptedException {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-pokemons"));

        if (rows != null) {
            List<DbRow> rowsList = rows.collectList().await();
            LOGGER.warning(() -> String.format("Rows count: %d", rowsList.size()));
            assertThat(rowsList, empty());
        }
    }

    /**
     * Verify that table PokemonTypes does not exist.
     *
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testPokemonTypesDeleted() throws InterruptedException {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-poketypes-all"));

        if (rows != null) {
            List<DbRow> rowsList = rows.collectList().await();
            LOGGER.warning(() -> String.format("Rows count: %d", rowsList.size()));
            assertThat(rowsList, empty());
        }
    }
}
