/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.dbclient.jdbc.oradb.init;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.ConfigIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Initialize database
 */
public class InitIT {

    private static final Logger LOGGER = Logger.getLogger(InitIT.class.getName());

    public static final Config CONFIG = Config.create(ConfigSources.classpath(ConfigIT.configFile()));

    public static final DbClient DB_CLIENT = initDbClient();

    private static DbClient initDbClient() {
        Config dbConfig = CONFIG.get("db");
        return DbClient.builder(dbConfig).build();
    }

    // Execute DML statement without failing the test suite on error.
    private static void execDmlNoFail(final DbClient dbClient, final String dmlName) {
        try {
            dbClient.execute(exec -> exec
                    .namedDml(dmlName)
            ).await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex,
                    () -> String.format("Statement %s execution failed: %s", dmlName, ex.getMessage()));
        }
    }

    /**
     * Initializes database schema (tables).
     *
     * @param dbClient Helidon database client
     */
    private static void initSchema(DbClient dbClient) {
        execDmlNoFail(dbClient, "drop-doc");
        try {
            dbClient.execute(exec -> {
                    Single<Long> execResult = exec.namedDml("create-doc");
                    if (CONFIG.get("db.statements.alter-doc").exists()) {
                        execResult = execResult.flatMapSingle(result -> exec.namedDml("alter-doc"));
                    }
                    return execResult;
                 }).await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Database tables creation failed: %s", ex.getMessage()));
            throw ex;
        }
    }

    public static final Map<Integer, JsonObject> POKEMONS = new HashMap<>();

    static {
        // Pikachu
        JsonObjectBuilder pokemonBuilder = Json.createObjectBuilder();
        pokemonBuilder.add("name", "Pikachu");
        pokemonBuilder.add("type", "electric");
        pokemonBuilder.add("cp", 827);
        pokemonBuilder.add("evolve", true);
        POKEMONS.put(1, pokemonBuilder.build());
        // Machop
        pokemonBuilder = Json.createObjectBuilder();
        pokemonBuilder.add("name", "Machop");
        pokemonBuilder.add("type", "fighting");
        pokemonBuilder.add("cp", 915);
        pokemonBuilder.add("evolve", true);
        POKEMONS.put(2, pokemonBuilder.build());
        // Snorlax
        pokemonBuilder = Json.createObjectBuilder();
        pokemonBuilder.add("name", "Snorlax");
        pokemonBuilder.add("type", "normal");
        pokemonBuilder.add("cp", 1085);
        pokemonBuilder.add("evolve", false);
        POKEMONS.put(3, pokemonBuilder.build());
    }

    /**
     * Initialize database content (rows in tables).
     *
     * @param dbClient Helidon database client
     */
    private static void initData(DbClient dbClient) {
        POKEMONS.forEach((id, pokemon) -> {
        StringWriter sw = new StringWriter(128);
        try (JsonWriter jw = Json.createWriter(sw)) {
            jw.write(pokemon);
        }
        String pokemonStr = sw.toString();
        try {
            dbClient.execute(exec -> exec
                    .namedDml("insert-doc", id, pokemonStr)
            ).await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data initialization failed: %s", ex.getMessage()));
            throw ex;
        }
        });
    }

    /**
     * Setup database for tests.
     */
    @BeforeAll
    public static void setup() {
        LOGGER.info(() -> "Initializing Integration Tests");
        try {
        initSchema(DB_CLIENT);
        initData(DB_CLIENT);
        } catch (Throwable t) {
            LOGGER.log(Level.FINER, t, () -> String.format("Tests setup failed: %s", t.getMessage()));
            throw t;
        }
    }

    /**
     * Verify that database contains properly initialized pokemon types.
     *
     */
    @Test
    public void test() {
        DbRow row = null;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-count")
            ).first().await(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            LOGGER.log(Level.FINER, ex, () -> String.format("Data verification failed: %s", ex.getMessage()));
            throw ex;
        }
        int count = row.column("COUNT").as(Integer.class);
        assertThat(count, greaterThan(0));
    }

}
