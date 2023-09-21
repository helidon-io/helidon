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
package io.helidon.tests.integration.dbclient.common.tests;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.tests.integration.dbclient.common.model.Pokemon.POKEMONS;
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyPokemon;

/**
 * Test set of basic JDBC get calls.
 */
@ExtendWith(DbClientParameterResolver.class)
public class SimpleGetIT {

    private final DbClient dbClient;
    private final Config config;

    public SimpleGetIT(DbClient dbClient, Config config) {
        this.dbClient = dbClient;
        this.config = config;
    }

    /**
     * Verify {@code createNamedGet(String, String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedGetStrStrNamedArgs() {
        String stmt = config.get("db.statements.select-pokemon-named-arg").asString().get();
        Optional<DbRow> maybeRow =
                dbClient.execute().createNamedGet("select-pikachu", stmt)
                        .addParam("name", POKEMONS.get(1).getName())
                        .execute();
        verifyPokemon(maybeRow, POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedGetStrNamedArgs() {
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemon-named-arg")
                .addParam("name", POKEMONS.get(2).getName())
                .execute();
        verifyPokemon(maybeRow, POKEMONS.get(2));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedGetStrOrderArgs() {
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemon-order-arg")
                .addParam(POKEMONS.get(3).getName())
                .execute();
        verifyPokemon(maybeRow, POKEMONS.get(3));
    }

    /**
     * Verify {@code createGet(String)} API method with named parameters.
     */
    @Test
    public void testCreateGetNamedArgs() {
        String stmt = config.get("db.statements.select-pokemon-named-arg").asString().get();
        Optional<DbRow> maybeRow = dbClient.execute()
                .createGet(stmt)
                .addParam("name", POKEMONS.get(4).getName())
                .execute();
        verifyPokemon(maybeRow, POKEMONS.get(4));
    }

    /**
     * Verify {@code createGet(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateGetOrderArgs() {
        String stmt = config.get("db.statements.select-pokemon-order-arg").asString().get();
        Optional<DbRow> maybeRow = dbClient.execute()
                .createGet(stmt)
                .addParam(POKEMONS.get(5).getName())
                .execute();
        verifyPokemon(maybeRow, POKEMONS.get(5));
    }

    /**
     * Verify {@code namedGet(String)} API method with ordered parameters passed directly to the {@code query} method.
     */
    @Test
    public void testNamedGetStrOrderArgs() {
        Optional<DbRow> maybeRow = dbClient.execute()
                .namedGet("select-pokemon-order-arg", POKEMONS.get(6).getName());
        verifyPokemon(maybeRow, POKEMONS.get(6));
    }

    /**
     * Verify {@code get(String)} API method with ordered parameters passed directly to the {@code query} method.
     */
    @Test
    public void testGetStrOrderArgs() {
        String stmt = config.get("db.statements.select-pokemon-order-arg").asString().get();
        Optional<DbRow> maybeRow = dbClient.execute()
                .get(stmt, POKEMONS.get(7).getName());
        verifyPokemon(maybeRow, POKEMONS.get(7));
    }
}
