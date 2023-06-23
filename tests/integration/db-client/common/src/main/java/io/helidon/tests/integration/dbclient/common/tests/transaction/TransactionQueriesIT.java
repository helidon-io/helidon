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

import java.util.stream.Stream;

import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.AbstractIT;
import io.helidon.tests.integration.dbclient.common.utils.Utils;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyPokemon;

/**
 * Test set of basic JDBC queries in transaction.
 */
public class TransactionQueriesIT extends AbstractIT {

    /**
     * Verify {@code createNamedQuery(String, String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedQueryStrStrOrderArgs() {
        Utils.transaction(tx -> {
            try (Stream<DbRow> rows = DB_CLIENT.transaction()
                    .createNamedQuery("select-pikachu", SELECT_POKEMON_ORDER_ARG)
                    .addParam(POKEMONS.get(1).getName())
                    .execute()) {
                verifyPokemon(rows, POKEMONS.get(1));
            }
            return null;
        });
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedQueryStrNamedArgs() {
        Utils.transaction(tx -> {
            try (Stream<DbRow> rows = DB_CLIENT.transaction()
                    .createNamedQuery("select-pokemon-named-arg")
                    .addParam("name", POKEMONS.get(2).getName())
                    .execute()) {
                verifyPokemon(rows, POKEMONS.get(2));
            }
            return null;
        });
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedQueryStrOrderArgs() {
        Utils.transaction(tx -> {
            try (Stream<DbRow> rows = DB_CLIENT.transaction()
                    .createNamedQuery("select-pokemon-order-arg")
                    .addParam(POKEMONS.get(3).getName())
                    .execute()) {
                verifyPokemon(rows, POKEMONS.get(3));
            }
            return null;
        });
    }

    /**
     * Verify {@code createQuery(String)} API method with named parameters.
     */
    @Test
    public void testCreateQueryNamedArgs() {
        Utils.transaction(tx -> {
            try (Stream<DbRow> rows = DB_CLIENT.transaction()
                    .createQuery(SELECT_POKEMON_NAMED_ARG)
                    .addParam("name", POKEMONS.get(4).getName())
                    .execute()) {
                verifyPokemon(rows, POKEMONS.get(4));
            }
            return null;
        });
    }

    /**
     * Verify {@code createQuery(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateQueryOrderArgs() {
        Utils.transaction(tx -> {
            try (Stream<DbRow> rows = DB_CLIENT.transaction()
                    .createQuery(SELECT_POKEMON_ORDER_ARG)
                    .addParam(POKEMONS.get(5).getName())
                    .execute()) {
                verifyPokemon(rows, POKEMONS.get(5));
            }
            return null;
        });
    }

    /**
     * Verify {@code namedQuery(String)} API method with ordered parameters passed directly to the {@code namedQuery} method.
     */
    @Test
    public void testNamedQueryOrderArgs() {
        Utils.transaction(tx -> {
            try (Stream<DbRow> rows = DB_CLIENT.transaction()
                    .namedQuery("select-pokemon-order-arg", POKEMONS.get(6).getName())) {
                verifyPokemon(rows, POKEMONS.get(6));
            }
            return null;
        });
    }

    /**
     * Verify {@code query(String)} API method with ordered parameters passed directly to the {@code query} method.
     */
    @Test
    public void testQueryOrderArgs() {
        Utils.transaction(tx -> {
            try (Stream<DbRow> rows = DB_CLIENT.transaction()
                    .query(SELECT_POKEMON_ORDER_ARG, POKEMONS.get(7).getName())) {
                verifyPokemon(rows, POKEMONS.get(7));
            }
            return null;
        });
    }

}
