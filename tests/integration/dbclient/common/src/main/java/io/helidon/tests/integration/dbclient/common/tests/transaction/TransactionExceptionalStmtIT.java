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
package io.helidon.tests.integration.dbclient.common.tests.transaction;

import java.lang.System.Logger.Level;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import io.helidon.dbclient.DbClientException;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbTransaction;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.POKEMONS;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test exceptional states.
 */
public class TransactionExceptionalStmtIT {

    private static final System.Logger LOGGER = System.getLogger(TransactionExceptionalStmtIT.class.getName());
    private static final Consumer<DbRow> EMPTY_CONSUMER = it -> {};

    /**
     * Verify that execution of query with non-existing named statement throws an exception.
     */
    @Test
    public void testCreateNamedQueryNonExistentStmt() {
        try {
            DbTransaction tx = DB_CLIENT.transaction();
            tx.createNamedQuery("select-pokemons-not-exists")
                    .execute()
                    .forEach(EMPTY_CONSUMER);
            tx.commit();
            fail("Execution of non existing statement shall cause an exception to be thrown.");
        } catch (DbClientException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Expected exception: %s", ex.getMessage()), ex);
        }
    }

    /**
     * Verify that execution of query with both named and ordered arguments throws an exception.
     */
    @Test
    public void testCreateNamedQueryNamedAndOrderArgsWithoutArgs() {
        try {
            DbTransaction tx = DB_CLIENT.transaction();
            tx.createNamedQuery("select-pokemons-error-arg")
                    .execute()
                    .forEach(EMPTY_CONSUMER);
            tx.commit();
            fail("Execution of query with both named and ordered parameters without passing any shall fail.");
        } catch (DbClientException |
                 CompletionException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Expected exception: %s", ex.getMessage()), ex);
        }
    }

    /**
     * Verify that execution of query with both named and ordered arguments throws an exception.
     */
    @Test
    public void testCreateNamedQueryNamedAndOrderArgsWithArgs() {
        try {
            DbTransaction tx = DB_CLIENT.transaction();
            tx.createNamedQuery("select-pokemons-error-arg")
                    .addParam("id", POKEMONS.get(5).getId())
                    .addParam(POKEMONS.get(5).getName())
                    .execute()
                    .forEach(EMPTY_CONSUMER);
            tx.commit();
            fail("Execution of query with both named and ordered parameters without passing them shall fail.");
        } catch (DbClientException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Expected exception: %s", ex.getMessage()), ex);
        }
    }

    /**
     * Verify that execution of query with named arguments throws an exception while trying to set ordered argument.
     */
    @Test
    public void testCreateNamedQueryNamedArgsSetOrderArg() {
        try {
            DbTransaction tx = DB_CLIENT.transaction();
            tx.createNamedQuery("select-pokemon-named-arg")
                    .addParam(POKEMONS.get(5).getName())
                    .execute()
                    .forEach(EMPTY_CONSUMER);
            tx.commit();
            fail("Execution of query with named parameter with passing ordered parameter value shall fail.");
        } catch (DbClientException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Expected exception: %s", ex.getMessage()), ex);
        }
    }

    /**
     * Verify that execution of query with ordered arguments throws an exception while trying to set named argument.
     */
    @Test
    public void testCreateNamedQueryOrderArgsSetNamedArg() {
        try {
            DbTransaction tx = DB_CLIENT.transaction();
            tx.createNamedQuery("select-pokemon-order-arg")
                    .addParam("name", POKEMONS.get(6).getName())
                    .execute()
                    .forEach(EMPTY_CONSUMER);
            tx.commit();
            fail("Execution of query with ordered parameter with passing named parameter value shall fail.");
        } catch (DbClientException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Expected exception: %s", ex.getMessage()), ex);
        }
    }
}
