/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.helidon.common.reactive.Multi;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.dbclient.DbRow;

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
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    private static void deleteSchema(DbClient dbClient) throws ExecutionException, InterruptedException {
        dbClient.execute(exec -> exec
                .namedDelete("delete-poketypes")
                .flatMapSingle(result -> exec.namedDelete("delete-pokemons"))
                .flatMapSingle(result -> exec.namedDelete("delete-types"))
        ).await(Duration.ofSeconds(10));
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
    public void testTypesDeleted() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-types"));

        if (rows != null) {
            List<DbRow> rowsList = rows.collectList().await();
            assertThat(rowsList, empty());
        }
    }

    /**
     * Verify that table Pokemons does not exist.
     */
    @Test
    public void testPokemonsDeleted() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-pokemons"));

        if (rows != null) {
            List<DbRow> rowsList = rows.collectList().await();
            assertThat(rowsList, empty());
        }
    }

    /**
     * Verify that table PokemonTypes does not exist.
     */
    @Test
    public void testPokemonTypesDeleted() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-poketypes-all"));

        if (rows != null) {
            List<DbRow> rowsList = rows.collectList().await();
            assertThat(rowsList, empty());
        }
    }
}
