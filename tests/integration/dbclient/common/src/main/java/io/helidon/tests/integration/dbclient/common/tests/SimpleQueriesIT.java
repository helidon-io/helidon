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

import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.tests.integration.dbclient.common.model.Pokemon.POKEMONS;
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyPokemon;

/**
 * Test set of basic JDBC queries.
 */
@ExtendWith(DbClientParameterResolver.class)
public class SimpleQueriesIT {

    private final DbClient dbClient;
    private final Config config;

    public SimpleQueriesIT(DbClient dbClient, Config config) {
        this.dbClient = dbClient;
        this.config = config;
    }

    /**
     * Verify {@code createNamedQuery(String, String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedQueryStrStrOrderArgs() {
        String stmt = config.get("db.statements.select-pokemon-order-arg").asString().get();
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pikachu", stmt)
                .addParam(POKEMONS.get(1).getName())
                .execute();

        verifyPokemon(rows, POKEMONS.get(1));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedQueryStrNamedArgs() {
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", POKEMONS.get(2).getName())
                .execute();
        verifyPokemon(rows, POKEMONS.get(2));
    }

    /**
     * Verify {@code createNamedQuery(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedQueryStrOrderArgs() {
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pokemon-order-arg")
                .addParam(POKEMONS.get(3).getName())
                .execute();
        verifyPokemon(rows, POKEMONS.get(3));
    }

    /**
     * Verify {@code createQuery(String)} API method with named parameters.
     */
    @Test
    public void testCreateQueryNamedArgs() {
        String stmt = config.get("db.statements.select-pokemon-named-arg").asString().get();
        Stream<DbRow> rows = dbClient.execute()
                .createQuery(stmt)
                .addParam("name", POKEMONS.get(4).getName())
                .execute();
        verifyPokemon(rows, POKEMONS.get(4));
    }

    /**
     * Verify {@code createQuery(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateQueryOrderArgs() {
        String stmt = config.get("db.statements.select-pokemon-order-arg").asString().get();
        Stream<DbRow> rows = dbClient.execute()
                .createQuery(stmt)
                .addParam(POKEMONS.get(5).getName())
                .execute();
        verifyPokemon(rows, POKEMONS.get(5));
    }

    /**
     * Verify {@code namedQuery(String)} API method with ordered parameters passed directly to the {@code namedQuery} method.
     */
    @Test
    public void testNamedQueryOrderArgs() {
        Stream<DbRow> rows = dbClient.execute()
                .namedQuery("select-pokemon-order-arg", POKEMONS.get(6).getName());
        verifyPokemon(rows, POKEMONS.get(6));
    }

    /**
     * Verify {@code query(String)} API method with ordered parameters passed directly to the {@code query} method.
     */
    @Test
    public void testQueryOrderArgs() {
        String stmt = config.get("db.statements.select-pokemon-order-arg").asString().get();
        Stream<DbRow> rows = dbClient.execute()
                .query(stmt, POKEMONS.get(7).getName());
        verifyPokemon(rows, POKEMONS.get(7));
    }

}
