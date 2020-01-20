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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.LAST_POKEMON_ID;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.POKEMONS;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test exceptional states.
 */
public class TransactionExceptionalStmtIT {

    /** Local logger instance. */
    private static final Logger LOG = Logger.getLogger(TransactionExceptionalStmtIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 40;

    /**
     * Verify that execution of query with non existing named statement throws an exception.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedQueryNonExistentStmt() throws ExecutionException, InterruptedException {
        try {
            DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                    .createNamedQuery("select-pokemons-not-exists")
                    .execute()
            ).toCompletableFuture().get();
            fail("Execution of non existing statement shall cause an exception to be thrown.");
        } catch (DbClientException ex) {
            LOG.log(Level.INFO, "Expected exception: {0}", ex.getMessage());
        }
    }

    /**
     * Verify that execution of query with both named and ordered arguments throws an exception.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedQueryNamedAndOrderArgsWithoutArgs() throws ExecutionException, InterruptedException {
        try {
            DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                    .createNamedQuery("select-pokemons-error-arg")
                    .execute()
            ).toCompletableFuture().get();
            fail("Execution of query with both named and ordered parameters without passing any shall fail.");
        } catch (DbClientException | ExecutionException ex) {
            LOG.log(Level.INFO, "Expected exception: {0}", ex.getMessage());
        }
    }

    /**
     * Verify that execution of query with both named and ordered arguments throws an exception.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedQueryNamedAndOrderArgsWithArgs() throws ExecutionException, InterruptedException {
        try {
            DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                    .createNamedQuery("select-pokemons-error-arg")
                    .addParam("id", POKEMONS.get(5).getId())
                    .addParam(POKEMONS.get(5).getName())
                    .execute()
            ).toCompletableFuture().get();
            fail("Execution of query with both named and ordered parameters without passing them shall fail.");
        } catch (DbClientException | ExecutionException ex) {
            LOG.log(Level.INFO, "Expected exception: {0}", ex.getMessage());
        }
    }

    /**
     * Verify that execution of query with named arguments throws an exception while trying to set ordered argument.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedQueryNamedArgsSetOrderArg() throws ExecutionException, InterruptedException {
        try {
            DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                    .createNamedQuery("select-pokemon-named-arg")
                    .addParam(POKEMONS.get(5).getName())
                    .execute()
            ).toCompletableFuture().get();
            fail("Execution of query with named parameter with passing ordered parameter value shall fail.");
        } catch (DbClientException | ExecutionException ex) {
            LOG.log(Level.INFO, "Expected exception: {0}", ex.getMessage());
        }
    }

    /**
     * Verify that execution of query with ordered arguments throws an exception while trying to set named argument.
     *
     * @throws InterruptedException when database query failed
     * @throws ExecutionException if the current thread was interrupted
     */
    @Test
    public void testCreateNamedQueryOrderArgsSetNamedArg() throws ExecutionException, InterruptedException {
        try {
            DbRows<DbRow> rows = DB_CLIENT.inTransaction(tx -> tx
                .createNamedQuery("select-pokemon-order-arg")
                .addParam("name", POKEMONS.get(6).getName())
                .execute()
            ).toCompletableFuture().get();
            fail("Execution of query with ordered parameter with passing named parameter value shall fail.");
        } catch (DbClientException | ExecutionException ex) {
            LOG.log(Level.INFO, "Expected exception: {0}", ex.getMessage());
        }
    }

}
