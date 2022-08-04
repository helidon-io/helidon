/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.dbclient.jdbc.oradb.tests;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import io.helidon.dbclient.DbRow;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.jdbc.oradb.init.InitIT.DB_CLIENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test JSON support extension.
 */
public class JsonSupportIT {

    private static final Logger LOGGER = Logger.getLogger(JsonSupportIT.class.getName());

    @Test
    void testSelectPokemonName() {
        LOGGER.fine(() -> "Running testSelectPokemonName");
        DbRow row = null;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-name", 1)
            ).first().await(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        String name = row.column("NAME").as(String.class);
        assertThat(name, equalTo("Pikachu"));
    }

    @Test
    void testUpdatePokemonName() {
        LOGGER.fine(() -> "Running testUpdatePokemonName");
        // Update name
        Long result = null;
        try {
            result = DB_CLIENT.execute(exec -> exec
                    .namedDml("update-pokemon-name", 2)
            ).await(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data DML statement failed: %s", ex.getMessage()));
            throw ex;
        }
        assertThat(result, equalTo(1L));
        List<DbRow> rows = null;
        try {
            rows = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-name", 2)
            ).collectList().await(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        String name = rows.get(0).column("NAME").as(String.class);
        assertThat(name, equalTo("Machoke"));
    }

    @Test
    void testSelectPokemonJsonValue() {
        LOGGER.fine(() -> "Running testSelectPokemonJson");
        DbRow row = null;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-json", 1)
            ).first().await(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        JsonValue json = row.column("POKEMON").as(JsonValue.class);
        LOGGER.log(Level.FINER, () -> String.format("Data: %s", json.toString()));
        assertThat(json.getValueType(), equalTo(JsonValue.ValueType.OBJECT));
    }

    @Test
    void testSelectPokemonJsonObject() {
        LOGGER.fine(() -> "Running testSelectPokemonJsonObject");
        DbRow row = null;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-json", 1)
            ).first().await(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        JsonObject json = row.column("POKEMON").as(JsonObject.class);
        LOGGER.log(Level.FINER, () -> String.format("Data: %s", json.toString()));
        assertThat(json.getValueType(), equalTo(JsonValue.ValueType.OBJECT));
    }


}
