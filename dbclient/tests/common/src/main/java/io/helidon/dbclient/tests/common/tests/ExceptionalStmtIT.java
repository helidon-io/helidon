/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient.tests.common.tests;

import java.lang.System.Logger.Level;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientException;

import org.junit.jupiter.api.Test;

import static io.helidon.dbclient.tests.common.model.Critter.CRITTERS;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test exceptional states.
 */
public abstract class ExceptionalStmtIT {

    private static final System.Logger LOGGER = System.getLogger(ExceptionalStmtIT.class.getName());

    private final DbClient dbClient;

    public ExceptionalStmtIT(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * Verify that execution of query with non-existing named statement throws an exception.
     */
    @Test
    void testCreateNamedQueryNonExistentStmt() {
        try {
            dbClient.execute()
                    .createNamedQuery("select-pokemons-not-exists")
                    .execute()
                    .forEach(it -> {});
            fail("Execution of non existing statement shall cause an exception to be thrown.");
        } catch (DbClientException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Expected exception: %s", ex.getMessage()), ex);
        }
    }

    /**
     * Verify that execution of query with both named and ordered arguments throws an exception.
     */
    @Test
    void testCreateNamedQueryNamedAndOrderArgsWithoutArgs() {
        try {
            dbClient.execute()
                    .createNamedQuery("select-pokemons-error-arg")
                    .execute()
                    .forEach(it -> {});
            fail("Execution of query with both named and ordered parameters without passing any shall fail.");
        } catch (DbClientException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Expected exception: %s", ex.getMessage()), ex);
        }
    }

    /**
     * Verify that execution of query with both named and ordered arguments throws an exception.
     */
    @Test
    void testCreateNamedQueryNamedAndOrderArgsWithArgs() {
        try {
            dbClient.execute()
                    .createNamedQuery("select-pokemons-error-arg")
                    .addParam("id", CRITTERS.get(5).getId())
                    .addParam(CRITTERS.get(5).getName())
                    .execute()
                    .forEach(it -> {});
            fail("Execution of query with both named and ordered parameters without passing them shall fail.");
        } catch (DbClientException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Expected exception: %s", ex.getMessage()), ex);
        }
    }

    /**
     * Verify that execution of query with named arguments throws an exception while trying to set ordered argument.
     */
    @Test
    void testCreateNamedQueryNamedArgsSetOrderArg() {
        try {
            dbClient.execute()
                    .createNamedQuery("select-pokemon-named-arg")
                    .addParam(CRITTERS.get(5).getName())
                    .execute()
                    .forEach(it -> {});
            fail("Execution of query with named parameter with passing ordered parameter value shall fail.");
        } catch (DbClientException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Expected exception: %s", ex.getMessage()), ex);
        }
    }

    /**
     * Verify that execution of query with ordered arguments throws an exception while trying to set named argument.
     */
    @Test
    void testCreateNamedQueryOrderArgsSetNamedArg() {
        try {
            dbClient.execute()
                    .createNamedQuery("select-pokemon-order-arg")
                    .addParam("name", CRITTERS.get(6).getName())
                    .execute()
                    .forEach(it -> {});
            fail("Execution of query with ordered parameter with passing named parameter value shall fail.");
        } catch (DbClientException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Expected exception: %s", ex.getMessage()), ex);
        }
    }
}
