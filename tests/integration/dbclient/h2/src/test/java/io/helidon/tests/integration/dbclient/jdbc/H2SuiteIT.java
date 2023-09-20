/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.jdbc;

import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.model.Type;
import io.helidon.tests.integration.dbclient.common.tests.MapperIT;
import io.helidon.tests.integration.harness.AfterSuite;
import io.helidon.tests.integration.harness.BeforeSuite;

import org.h2.tools.Server;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import static io.helidon.tests.integration.dbclient.common.model.Pokemon.POKEMONS;
import static io.helidon.tests.integration.dbclient.common.model.Type.TYPES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Test JDBC Database Client with H2 database.
 */
@Suite
@SelectPackages("io.helidon.tests.integration.dbclient.common.tests")
@IncludeClassNamePatterns(".*IT")
class H2SuiteIT {

    private static final System.Logger LOGGER = System.getLogger(MapperIT.class.getName());
    private static final int TIMEOUT = 60;

    @BeforeSuite
    static Map<String, Object> setup() {
        Config config = Config.create(ConfigSources.classpath("h2.yaml"));
        Config dbConfig = config.get("db");
        startH2(dbConfig);
        DbClient dbClient = DbClient.builder(dbConfig).build();
        waitForStart(dbClient);
        initSchema(dbClient);
        initData(dbClient);
        testListTypes(dbClient);
        testListPokemons(dbClient);
        testListPokemonTypes(dbClient);
        return Map.of("config", config,
                      "dbClient", dbClient);
    }

    @AfterSuite
    static void tearDown(DbClient dbClient) {
        deleteSchema(dbClient);
        testDeletedSchema(dbClient);
    }

    private static void startH2(Config config) {
        String password = config.get("password").asString().get();
        String port = config.get("port").asString().get();
        String database = config.get("database").asString().get();
        String baseDir = Paths.get("").toAbsolutePath().resolve("target").resolve(database).toString();
        try {
            Server.main(
                    "-web",
                    "-webAllowOthers",
                    "-tcp",
                    "-tcpAllowOthers",
                    "-tcpAllowOthers",
                    "-tcpPassword",
                    password,
                    "-tcpPort",
                    port,
                    "-baseDir",
                    baseDir,
                    "-ifNotExists");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void ping(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        DbRow row = exec.namedQuery("ping").findFirst().orElseThrow();
        Double ok = row.column("ok").as(Double.class).get();
        assertThat(ok, equalTo(1.0));
        LOGGER.log(System.Logger.Level.DEBUG, () -> String.format("Command ping row: %s", row));
    }

    private static void initSchema(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        exec.namedDml("create-types");
        exec.namedDml("create-pokemons");
        exec.namedDml("create-poketypes");
    }

    private static void initData(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        long count = 0;
        for (Map.Entry<Integer, Type> entry : TYPES.entrySet()) {
            count += exec.namedInsert("insert-type", entry.getKey(), entry.getValue().name());
        }

        for (Map.Entry<Integer, Pokemon> entry : POKEMONS.entrySet()) {
            count += exec.namedInsert("insert-pokemon", entry.getKey(), entry.getValue().getName());
        }

        for (Map.Entry<Integer, Pokemon> entry : POKEMONS.entrySet()) {
            Pokemon pokemon = entry.getValue();
            for (Type type : pokemon.getTypes()) {
                count += exec.namedInsert("insert-poketype", pokemon.getId(), type.id());
            }
        }
        LOGGER.log(System.Logger.Level.INFO, String.format("executed %s statements", count));
    }

    private static void testListTypes(DbClient dbClient) {
        DbExecute exec = dbClient.execute();

        List<DbRow> rows = exec.namedQuery("select-types").toList();
        assertThat(rows, not(empty()));

        Set<Integer> ids = new HashSet<>(TYPES.keySet());
        for (DbRow row : rows) {
            Integer id = row.column(1).as(Integer.class).get();
            String name = row.column(2).as(String.class).get();
            assertThat(ids, hasItem(id));
            assertThat(name, TYPES.get(id).name().equals(name));
        }
    }

    private static void testListPokemons(DbClient dbClient) {
        DbExecute exec = dbClient.execute();

        List<DbRow> rowsList = exec.namedQuery("select-pokemons").toList();
        assertThat(rowsList, not(empty()));

        Set<Integer> ids = new HashSet<>(POKEMONS.keySet());
        for (DbRow row : rowsList) {
            Integer id = row.column(1).as(Integer.class).get();
            String name = row.column(2).as(String.class).get();
            assertThat(ids, hasItem(id));
            assertThat(name, POKEMONS.get(id).getName().equals(name));
        }
    }

    private static void testListPokemonTypes(DbClient dbClient) {
        DbExecute exec = dbClient.execute();

        List<DbRow> rows = exec.namedQuery("select-pokemons").toList();
        assertThat(rows, not(empty()));

        for (DbRow row : rows) {
            Integer pokemonId = row.column(1).as(Integer.class).get();
            String pokemonName = row.column(2).as(String.class).get();
            Pokemon pokemon = POKEMONS.get(pokemonId);
            assertThat(pokemonName, POKEMONS.get(pokemonId).getName().equals(pokemonName));
            Stream<DbRow> typeRows = exec.namedQuery("select-poketypes", pokemonId);

            List<DbRow> typeRowsList = typeRows.toList();
            assertThat(typeRowsList.size(), equalTo(pokemon.getTypes().size()));
            for (DbRow typeRow : typeRowsList) {
                Integer typeId = typeRow.column(2).as(Integer.class).get();
                assertThat(pokemon.getTypes(), hasItem(TYPES.get(typeId)));
            }
        }
    }

    private static void deleteSchema(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        exec.namedDelete("delete-poketypes");
        exec.namedDelete("delete-pokemons");
        exec.namedDelete("delete-types");
    }

    private static void testDeletedSchema(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        assertThat(exec.namedQuery("select-types").count(), is(0));
        assertThat(exec.namedQuery("select-pokemons").count(), is(0));
        assertThat(exec.namedQuery("select-pokemons-all").count(), is(0));
    }

    @SuppressWarnings("BusyWait")
    private static void waitForStart(DbClient dbClient) {
        long endTm = 1000 * TIMEOUT + System.currentTimeMillis();
        while (true) {
            try {
                dbClient.execute().namedGet("ping");
                break;
            } catch (Throwable th) {
                if (System.currentTimeMillis() > endTm) {
                    throw new IllegalStateException("Database startup failed!", th);
                }
                LOGGER.log(System.Logger.Level.DEBUG, () -> String.format("Exception: %s", th.getMessage()), th);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }
}
