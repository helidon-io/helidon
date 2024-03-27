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

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;

import org.junit.jupiter.api.Test;

import static io.helidon.dbclient.tests.common.model.Critter.CRITTERS;
import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyCritter;

/**
 * Test set of basic JDBC get calls.
 */
public abstract class SimpleGetIT {

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
                        .addParam("name", CRITTERS.get(1).getName())
                        .execute();
        verifyCritter(maybeRow, CRITTERS.get(1));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with named parameters.
     */
    @Test
    public void testCreateNamedGetStrNamedArgs() {
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemon-named-arg")
                .addParam("name", CRITTERS.get(2).getName())
                .execute();
        verifyCritter(maybeRow, CRITTERS.get(2));
    }

    /**
     * Verify {@code createNamedGet(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateNamedGetStrOrderArgs() {
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemon-order-arg")
                .addParam(CRITTERS.get(3).getName())
                .execute();
        verifyCritter(maybeRow, CRITTERS.get(3));
    }

    /**
     * Verify {@code createGet(String)} API method with named parameters.
     */
    @Test
    public void testCreateGetNamedArgs() {
        String stmt = config.get("db.statements.select-pokemon-named-arg").asString().get();
        Optional<DbRow> maybeRow = dbClient.execute()
                .createGet(stmt)
                .addParam("name", CRITTERS.get(4).getName())
                .execute();
        verifyCritter(maybeRow, CRITTERS.get(4));
    }

    /**
     * Verify {@code createGet(String)} API method with ordered parameters.
     */
    @Test
    public void testCreateGetOrderArgs() {
        String stmt = config.get("db.statements.select-pokemon-order-arg").asString().get();
        Optional<DbRow> maybeRow = dbClient.execute()
                .createGet(stmt)
                .addParam(CRITTERS.get(5).getName())
                .execute();
        verifyCritter(maybeRow, CRITTERS.get(5));
    }

    /**
     * Verify {@code namedGet(String)} API method with ordered parameters passed directly to the {@code query} method.
     */
    @Test
    public void testNamedGetStrOrderArgs() {
        Optional<DbRow> maybeRow = dbClient.execute()
                .namedGet("select-pokemon-order-arg", CRITTERS.get(6).getName());
        verifyCritter(maybeRow, CRITTERS.get(6));
    }

    /**
     * Verify {@code get(String)} API method with ordered parameters passed directly to the {@code query} method.
     */
    @Test
    public void testGetStrOrderArgs() {
        String stmt = config.get("db.statements.select-pokemon-order-arg").asString().get();
        Optional<DbRow> maybeRow = dbClient.execute()
                .get(stmt, CRITTERS.get(7).getName());
        verifyCritter(maybeRow, CRITTERS.get(7));
    }
}
