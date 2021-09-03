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
package io.helidon.tests.integration.dbclient.common.tests.simple;

import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import io.helidon.dbclient.DbClientException;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test exceptional states.
 */
public class ExceptionalStmtIT extends AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(ExceptionalStmtIT.class.getName());

    /** Maximum Pokemon ID. */
    private static final int BASE_ID = LAST_POKEMON_ID + 40;

    /**
     * Verify that execution of query with non existing named statement throws an exception.
     *
     */
    @Test
    public void testCreateNamedQueryNonExistentStmt() {
        LOGGER.info(() -> "Starting test");
        try {
            DB_CLIENT.execute(exec -> exec
                    .createNamedQuery("select-pokemons-not-exists")
                    .execute())
                    .collectList()
                    .await();
            LOGGER.warning(() -> "Test failed");
            fail("Execution of non existing statement shall cause an exception to be thrown.");
        } catch (DbClientException ex) {
            LOGGER.info(() -> String.format("Expected exception: %s", ex.getMessage()));
        }
    }

    /**
     * Verify that execution of query with both named and ordered arguments throws an exception.
     *
     */
    @Test
    public void testCreateNamedQueryNamedAndOrderArgsWithoutArgs() {
        LOGGER.info(() -> "Starting test");
        try {
            DB_CLIENT.execute(exec -> exec
                    .createNamedQuery("select-pokemons-error-arg")
                    .execute())
                    .collectList()
                    .await();
            LOGGER.warning(() -> "Test failed");
            fail("Execution of query with both named and ordered parameters without passing any shall fail.");
        } catch (DbClientException | CompletionException ex) {
            LOGGER.info(() -> String.format("Expected exception: %s", ex.getMessage()));
        }
    }

    /**
     * Verify that execution of query with both named and ordered arguments throws an exception.
     *
     */
    @Test
    public void testCreateNamedQueryNamedAndOrderArgsWithArgs() {
        LOGGER.info(() -> "Starting test");
        try {
            DB_CLIENT.execute(exec -> exec
                    .createNamedQuery("select-pokemons-error-arg")
                    .addParam("id", POKEMONS.get(5).getId())
                    .addParam(POKEMONS.get(5).getName())
                    .execute())
                    .collectList()
                    .await();
            LOGGER.warning(() -> "Test failed");
            fail("Execution of query with both named and ordered parameters without passing them shall fail.");
        } catch (DbClientException | CompletionException ex) {
            LOGGER.info(() -> String.format("Expected exception: %s", ex.getMessage()));
        }
    }

    /**
     * Verify that execution of query with named arguments throws an exception while trying to set ordered argument.
     *
     */
    @Test
    public void testCreateNamedQueryNamedArgsSetOrderArg() {
        LOGGER.info(() -> "Starting test");
        try {
            DB_CLIENT.execute(exec -> exec
                    .createNamedQuery("select-pokemon-named-arg")
                    .addParam(POKEMONS.get(5).getName())
                    .execute())
                    .collectList()
                    .await();
            LOGGER.warning(() -> "Test failed");
            fail("Execution of query with named parameter with passing ordered parameter value shall fail.");
        } catch (DbClientException | CompletionException ex) {
            LOGGER.info(() -> String.format("Expected exception: %s", ex.getMessage()));
        }
    }

    /**
     * Verify that execution of query with ordered arguments throws an exception while trying to set named argument.
     *
     */
    @Test
    public void testCreateNamedQueryOrderArgsSetNamedArg() {
        LOGGER.info(() -> "Starting test");
        try {
            DB_CLIENT.execute(exec -> exec
                    .createNamedQuery("select-pokemon-order-arg")
                    .addParam("name", POKEMONS.get(6).getName())
                    .execute())
                    .collectList()
                    .await();
            LOGGER.warning(() -> "Test failed");
            fail("Execution of query with ordered parameter with passing named parameter value shall fail.");
        } catch (DbClientException | CompletionException ex) {
            LOGGER.info(() -> String.format("Expected exception: %s", ex.getMessage()));
        }
    }

}
