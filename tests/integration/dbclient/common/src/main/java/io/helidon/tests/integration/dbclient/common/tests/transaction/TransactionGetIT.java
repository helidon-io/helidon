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

import java.util.Optional;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbTransaction;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyPokemon;

/**
 * Test set of basic JDBC get calls in transaction.
 */
public class TransactionGetIT extends AbstractIT {

    /**
     * Verify {@code createNamedGet(String, String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedGetStrStrNamedArgs() {
        DbTransaction tx = DB_CLIENT.transaction();
        Optional<DbRow> maybeRow = tx
                .createNamedGet("select-pikachu", SELECT_POKEMON_NAMED_ARG)
                .addParam("name", POKEMONS.get(1).getName())
                .execute();
        tx.commit();
        verifyPokemon(maybeRow, POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedGetStrNamedArgs() {
        DbTransaction tx = DB_CLIENT.transaction();
        Optional<DbRow> maybeRow = tx
                .createNamedGet("select-pokemon-named-arg")
                .addParam("name", POKEMONS.get(2).getName())
                .execute();
        tx.commit();

        verifyPokemon(maybeRow, POKEMONS.get(2));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedGetStrOrderArgs() {
        DbTransaction tx = DB_CLIENT.transaction();
        Optional<DbRow> maybeRow = tx
                .createNamedGet("select-pokemon-order-arg")
                .addParam(POKEMONS.get(3).getName())
                .execute();
        tx.commit();

        verifyPokemon(maybeRow, POKEMONS.get(3));
    }

    /**
     * Verify {@code createGet(String)} API method with named parameters.
     */
    @Test
    public void testCreateGetNamedArgs() {
        DbTransaction tx = DB_CLIENT.transaction();
        Optional<DbRow> maybeRow = tx
                .createGet(SELECT_POKEMON_NAMED_ARG)
                .addParam("name", POKEMONS.get(4).getName())
                .execute();
        tx.commit();

        verifyPokemon(maybeRow, POKEMONS.get(4));
    }

    /**
     * Verify {@code createGet(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateGetOrderArgs() {
        DbTransaction tx = DB_CLIENT.transaction();
        Optional<DbRow> maybeRow = tx
                .createGet(SELECT_POKEMON_ORDER_ARG)
                .addParam(POKEMONS.get(5).getName())
                .execute();
        tx.commit();

        verifyPokemon(maybeRow, POKEMONS.get(5));
    }

    /**
     * Verify {@code namedGet(String)} API method with ordered parameters passed directly to the {@code query} method.
     */
    @Test
    public void testNamedGetStrOrderArgs() {
        DbTransaction tx = DB_CLIENT.transaction();
        Optional<DbRow> maybeRow = tx
                .namedGet("select-pokemon-order-arg", POKEMONS.get(6).getName());
        tx.commit();

        verifyPokemon(maybeRow, POKEMONS.get(6));
    }

    /**
     * Verify {@code get(String)} API method with ordered parameters passed directly to the {@code query} method.
     */
    @Test
    public void testGetStrOrderArgs() {
        DbTransaction tx = DB_CLIENT.transaction();
        Optional<DbRow> maybeRow = tx
                .get(SELECT_POKEMON_ORDER_ARG, POKEMONS.get(7).getName());
        tx.commit();

        verifyPokemon(maybeRow, POKEMONS.get(7));
    }
}
