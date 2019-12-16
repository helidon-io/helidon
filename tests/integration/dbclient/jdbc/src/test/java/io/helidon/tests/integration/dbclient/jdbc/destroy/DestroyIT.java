/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.dbclient.jdbc.destroy;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;

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
    private static void dropSchema(DbClient dbClient) throws ExecutionException, InterruptedException {
        dbClient.execute(exec -> exec
                .namedDml("drop-poketypes")
                .thenCompose(result -> exec.namedDml("drop-pokemons"))
                .thenCompose(result -> exec.namedDml("drop-types"))
        ).toCompletableFuture().get();
    }


    /**
     * Destroy database after tests.
     */
    @BeforeAll
    public static void destroy() {
        try {
            dropSchema(DB_CLIENT);
        } catch (ExecutionException | InterruptedException ex) {
            fail("Database cleanup failed!", ex);
        }
    }

    /**
     * Verify that table Types does not exist.
     *
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testTypesDeleted() throws InterruptedException {
        try {
            DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-types")
            ).toCompletableFuture().get();
            if (rows != null) {
                List<DbRow> rowsList = rows.collect().toCompletableFuture().get();
                LOGGER.warning(() -> String.format("Rows count: %d", rowsList.size()));
                fail("No Types rows shall be returned after database cleanup!");
            }
        } catch (ExecutionException ex) {
            LOGGER.info(() -> String.format("Caught expected exception: %s", ex.getMessage()));
        }
    }

    /**
     * Verify that table Pokemons does not exist.
     *
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testPokemonsDeleted() throws InterruptedException {
        try {
            DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-pokemons")
            ).toCompletableFuture().get();
            if (rows != null) {
                List<DbRow> rowsList = rows.collect().toCompletableFuture().get();
                LOGGER.warning(() -> String.format("Rows count: %d", rowsList.size()));
                fail("No Pokemons rows shall be returned after database cleanup!");
            }
        } catch (ExecutionException ex) {
            LOGGER.info(() -> String.format("Caught expected exception: %s", ex.getMessage()));
        }
    }

    /**
     * Verify that table PokemonTypes does not exist.
     *
     * @throws ExecutionException when database query failed
     */
    @Test
    public void testPokemonTypesDeleted() throws InterruptedException {
        try {
            DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-poketypes-all")
            ).toCompletableFuture().get();
            if (rows != null) {
                List<DbRow> rowsList = rows.collect().toCompletableFuture().get();
                LOGGER.warning(() -> String.format("Rows count: %d", rowsList.size()));
                fail("No PokemonTypes rows shall be returned after database cleanup!");
            }
        } catch (ExecutionException ex) {
            LOGGER.info(() -> String.format("Caught expected exception: %s", ex.getMessage()));
        }
    }

}
