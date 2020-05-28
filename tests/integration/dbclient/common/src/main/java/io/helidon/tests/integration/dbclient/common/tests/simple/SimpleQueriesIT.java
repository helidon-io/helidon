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

import java.util.logging.Logger;

import io.helidon.common.reactive.Multi;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyPokemon;

/**
 * Test set of basic JDBC queries.
 */
public class SimpleQueriesIT extends AbstractIT {

    /** Local logger instance. */
    static final Logger LOGGER = Logger.getLogger(SimpleQueriesIT.class.getName());

    /**
     * Verify {@code createNamedQuery(String, String)} API method with ordered parameters.
     *
     */
    @Test
    public void testCreateNamedQueryStrStrOrderArgs() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedQuery("select-pikachu", SELECT_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(1).getName())
                .execute());

        verifyPokemon(rows, POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with named parameters.
     *
     */
    @Test
    public void testCreateNamedQueryStrNamedArgs() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", POKEMONS.get(2).getName()).execute()
        );
        verifyPokemon(rows, POKEMONS.get(2));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with ordered parameters.
     *
     */
    @Test
    public void testCreateNamedQueryStrOrderArgs() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedQuery("select-pokemon-order-arg")
                .addParam(POKEMONS.get(3).getName()).execute()
        );
        verifyPokemon(rows, POKEMONS.get(3));
    }

    /**
     * Verify {@code createQuery(String)} API method with named parameters.
     *
     */
    @Test
    public void testCreateQueryNamedArgs() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createQuery(SELECT_POKEMON_NAMED_ARG)
                .addParam("name", POKEMONS.get(4).getName()).execute()
        );
        verifyPokemon(rows, POKEMONS.get(4));
    }

    /**
     * Verify {@code createQuery(String)} API method with ordered parameters.
     *
     */
    @Test
    public void testCreateQueryOrderArgs() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createQuery(SELECT_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(5).getName()).execute()
        );
        verifyPokemon(rows, POKEMONS.get(5));
    }

    /**
     * Verify {@code namedQuery(String)} API method with ordered parameters passed directly to the {@code namedQuery} method.
     *
     */
    @Test
    public void testNamedQueryOrderArgs() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-pokemon-order-arg", POKEMONS.get(6).getName())
        );
        verifyPokemon(rows, POKEMONS.get(6));
    }

    /**
     * Verify {@code query(String)} API method with ordered parameters passed directly to the {@code query} method.
     *
     */
    @Test
    public void testQueryOrderArgs() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .query(SELECT_POKEMON_ORDER_ARG, POKEMONS.get(7).getName())
        );
        verifyPokemon(rows, POKEMONS.get(7));
    }

}
