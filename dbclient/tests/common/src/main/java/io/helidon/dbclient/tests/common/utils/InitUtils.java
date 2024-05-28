/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient.tests.common.utils;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.sql.DriverManager;
import java.util.Map;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.tests.common.model.Critter;
import io.helidon.dbclient.tests.common.model.Kind;

/**
 * Test initialization utilities.
 */
public class InitUtils {

    private static final Logger LOGGER = System.getLogger(InitUtils.class.getName());

    /**
     * Check code to be executed periodically while waiting for database container to come up.
     */
    @FunctionalInterface
    public interface StartCheck {
        /**
         * Check whether database is already up and accepts connections.
         *
         * @throws Exception when check failed
         */
        void check() throws Exception;
    }

    /**
     * Wait for database container to come up.
     *
     * @param check container check to be executed periodically until no exception is thrown
     * @param timeout container start up timeout in seconds
     */
    @SuppressWarnings("SleepWhileInLoop")
    public static void waitForStart(StartCheck check, int timeout) {
        LOGGER.log(Level.TRACE, "Waiting for database server to come up");
        long endTm = 1000L * timeout + System.currentTimeMillis();
        while (true) {
            try {
                check.check();
                break;
            } catch (Throwable th) {
                if (System.currentTimeMillis() > endTm) {
                    throw new IllegalStateException("Database startup failed!", th);
                }
                LOGGER.log(Level.DEBUG, () -> String.format("Exception: %s", th.getMessage()), th);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * Wait for database container to come up.
     * With single check timeout modified.
     *
     * @param check container check to be executed periodically until no exception is thrown
     * @param timeout container start up timeout in seconds
     * @param checkTimeout single check timeout in seconds
     */
    public static void waitForStart(StartCheck check, int timeout, int checkTimeout) {
        int currentLoginTimeout = DriverManager.getLoginTimeout();
        DriverManager.setLoginTimeout(checkTimeout);
        InitUtils.waitForStart(check, timeout);
        DriverManager.setLoginTimeout(currentLoginTimeout);
    }

    /**
     * Initialize database schema.
     *
     * @param dbClient database client instance
     */
    public static void initSchema(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        exec.namedDml("create-types");
        exec.namedDml("create-pokemons");
        exec.namedDml("create-poketypes");
    }

    /**
     * Initialize database data.
     *
     * @param dbClient database client instance
     */
    public static void initData(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        long count = 0;
        for (Map.Entry<Integer, Kind> entry : Kind.KINDS.entrySet()) {
            count += exec.namedInsert("insert-type", entry.getKey(), entry.getValue().name());
        }

        for (Map.Entry<Integer, Critter> entry : Critter.CRITTERS.entrySet()) {
            count += exec.namedInsert("insert-pokemon", entry.getKey(), entry.getValue().getName());
        }

        for (Map.Entry<Integer, Critter> entry : Critter.CRITTERS.entrySet()) {
            Critter pokemon = entry.getValue();
            for (Kind type : pokemon.getTypes()) {
                count += exec.namedInsert("insert-poketype", pokemon.getId(), type.id());
            }
        }
        LOGGER.log(System.Logger.Level.INFO, String.format("executed %s statements", count));
    }

    /**
     * Destroy database data.
     *
     * @param dbClient database client instance
     */
    public static void dropSchema(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        exec.namedDml("drop-poketypes");
        exec.namedDml("drop-pokemons");
        exec.namedDml("drop-types");
    }

}
