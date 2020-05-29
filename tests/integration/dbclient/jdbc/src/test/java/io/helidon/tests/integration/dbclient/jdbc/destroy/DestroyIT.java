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
package io.helidon.tests.integration.dbclient.jdbc.destroy;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import io.helidon.common.reactive.Multi;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private static void dropSchema(DbClient dbClient) throws ExecutionException, InterruptedException {
        dbClient.execute(exec -> exec
                .namedDml("drop-poketypes")
                .flatMapSingle(result -> exec.namedDml("drop-pokemons"))
                .flatMapSingle(result -> exec.namedDml("drop-types"))
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
     */
    @Test
    void testTypesDeleted() {
        testTableNotExist("select-types");
    }

    /**
     * Verify that table Pokemons does not exist.
     */
    @Test
    public void testPokemonsDeleted() {
        testTableNotExist("select-pokemons");
    }

    /**
     * Verify that table PokemonTypes does not exist.
     */
    @Test
    public void testPokemonTypesDeleted() {
        testTableNotExist("select-poketypes-all");
    }

    private void testTableNotExist(String statementName) {
        Multi<DbRow> result = DB_CLIENT.execute(exec -> exec.namedQuery(statementName));
        assertThrows(CompletionException.class, () -> result.collectList().await(), "Table should have been dropped");
    }
}
