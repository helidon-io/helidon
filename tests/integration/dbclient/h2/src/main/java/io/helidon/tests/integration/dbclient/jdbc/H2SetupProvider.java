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
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.model.Type;
import io.helidon.tests.integration.dbclient.common.spi.SetupProvider;
import io.helidon.tests.integration.dbclient.common.tests.MapperIT;

import org.h2.tools.Server;

/**
 * H2 database setup.
 * Provides tests {@link Config} and {@link DbClient} instance for junit tests.
 * Starts and initializes H2 database.
 */
public class H2SetupProvider implements SetupProvider {

    private static final System.Logger LOGGER = System.getLogger(MapperIT.class.getName());
    private static final Config CONFIG = initConfig();
    private static final int TIMEOUT = 60;

    // Start and initialize H2 database
    static {
        Config dbConfig = CONFIG.get("db");
        startH2(dbConfig);
        DbClient dbClient = DbClient.builder(dbConfig).build();
        waitForStart(dbClient);
        initSchema(dbClient);
        initData(dbClient);
    }

    private static Config initConfig() {
        return Config.create(ConfigSources.classpath("h2.yaml"));
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

    private static void initSchema(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        exec.namedDml("create-types");
        exec.namedDml("create-pokemons");
        exec.namedDml("create-poketypes");
    }

    private static void initData(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        long count = 0;
        for (Map.Entry<Integer, Type> entry : Type.TYPES.entrySet()) {
            count += exec.namedInsert("insert-type", entry.getKey(), entry.getValue().name());
        }

        for (Map.Entry<Integer, Pokemon> entry : Pokemon.POKEMONS.entrySet()) {
            count += exec.namedInsert("insert-pokemon", entry.getKey(), entry.getValue().getName());
        }

        for (Map.Entry<Integer, Pokemon> entry : Pokemon.POKEMONS.entrySet()) {
            Pokemon pokemon = entry.getValue();
            for (Type type : pokemon.getTypes()) {
                count += exec.namedInsert("insert-poketype", pokemon.getId(), type.id());
            }
        }
        LOGGER.log(System.Logger.Level.INFO, String.format("executed %s statements", count));
    }

    public H2SetupProvider() {
    }

    @Override
    public Config config() {
        return CONFIG;
    }

    @Override
    public DbClient dbClient() {
        return DbClient.builder(CONFIG.get("db")).build();
    }

}
