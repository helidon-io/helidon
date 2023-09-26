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

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbTransaction;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.utils.TestConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.tests.integration.dbclient.common.model.Type.TYPES;
import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyInsertPokemon;

/**
 * Test set of basic JDBC inserts in transaction.
 */
@SuppressWarnings("SpellCheckingInspection")
@ExtendWith(DbClientParameterResolver.class)
public class TransactionInsertIT {

    private static final int BASE_ID = TestConfig.LAST_POKEMON_ID + 210;

    private final DbClient dbClient;
    private final Config config;

    public TransactionInsertIT(DbClient dbClient, Config config) {
        this.dbClient = dbClient;
        this.config = config;
    }

    /**
     * Verify {@code createNamedInsert(String, String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedInsertStrStrNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 1, "Sentret", TYPES.get(1));
        String stmt = config.get("db.statements.insert-pokemon-named-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedInsert("insert-bulbasaur", stmt)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute();
        tx.commit();
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedInsert(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedInsertStrNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 2, "Furret", TYPES.get(1));
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedInsert("insert-pokemon-named-arg")
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute();
        tx.commit();
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createNamedInsert(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedInsertStrOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 3, "Chinchou", TYPES.get(11), TYPES.get(13));
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createNamedInsert("insert-pokemon-order-arg")
                .addParam(pokemon.getId()).addParam(pokemon.getName()).execute();
        tx.commit();
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createInsert(String)} API method with named parameters.
     */
    @Test
    public void testCreateInsertNamedArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 4, "Lanturn", TYPES.get(11), TYPES.get(13));
        String stmt = config.get("db.statements.insert-pokemon-named-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createInsert(stmt)
                .addParam("id", pokemon.getId()).addParam("name", pokemon.getName()).execute();
        tx.commit();
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code createInsert(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 5, "Swinub", TYPES.get(5), TYPES.get(15));
        String stmt = config.get("db.statements.insert-pokemon-order-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .createInsert(stmt)
                .addParam(pokemon.getId()).addParam(pokemon.getName()).execute();
        tx.commit();
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code namedInsert(String)} API method with ordered parameters passed directly to the {@code insert} method.
     */
    @Test
    public void testNamedInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 6, "Piloswine", TYPES.get(5), TYPES.get(15));
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .namedInsert("insert-pokemon-order-arg", pokemon.getId(), pokemon.getName());
        tx.commit();
        verifyInsertPokemon(dbClient, result, pokemon);
    }

    /**
     * Verify {@code insert(String)} API method with ordered parameters passed directly to the {@code insert} method.
     */
    @Test
    public void testInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(BASE_ID + 7, "Mamoswine", TYPES.get(5), TYPES.get(15));
        String stmt = config.get("db.statements.insert-pokemon-order-arg").asString().get();
        DbTransaction tx = dbClient.transaction();
        long result = tx
                .insert(stmt, pokemon.getId(), pokemon.getName());
        tx.commit();
        verifyInsertPokemon(dbClient, result, pokemon);
    }
}
