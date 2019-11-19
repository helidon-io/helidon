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
package io.helidon.tests.integration.dbclient.common.tests.transaction;

import java.util.concurrent.ExecutionException;

import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.INSERT_POKEMON_NAMED_ARG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.LAST_POKEMON_ID;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.TYPES;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyInsertPokemon;

/**
 * Test simple transactions.
 */
public class SimpleTransactionIT {

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 200;

    /**
     * Insert pokemon in transaction.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testInsertPokemon() throws ExecutionException, InterruptedException {
        AbstractIT.Pokemon pokemon = new AbstractIT.Pokemon(BASE_ID+1, "Lillipup", TYPES.get(1));
        Long result = DB_CLIENT.inTransaction(tx -> tx
                .createNamedInsert("insert-lillipup", INSERT_POKEMON_NAMED_ARG)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute()
        ).toCompletableFuture().get();
        verifyInsertPokemon(result, pokemon);
    }

}
