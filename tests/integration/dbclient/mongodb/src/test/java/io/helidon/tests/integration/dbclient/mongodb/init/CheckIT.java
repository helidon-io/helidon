/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.dbclient.mongodb.init;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.CONFIG;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Check minimal functionality needed before running database schema initialization.
 * First test class being executed after database startup.
 */
public class CheckIT extends AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(CheckIT.class.getName());

    /** Timeout in seconds to wait for database to come up. */
    private static final int TIMEOUT = 60;

    /** Helidon DB client with admin database access. */
    public static final DbClient DB_ADMIN = initDbAdmin();

    private static DbClient initDbAdmin() {
        Config dbConfig = CONFIG.get("dbadmin");
        return DbClient.builder(dbConfig).build();
    }

    /**
     * Wait for database server to start.
     *
     * @param dbClient Helidon database client
     */
    private static void waitForStart(DbClient dbClient) {
        long endTm = 1000 * TIMEOUT + System.currentTimeMillis();
        boolean retry = true;
        while (retry) {
            try {
                dbClient.execute(exec -> exec.namedStatement("ping"))
                        .toCompletableFuture().get().rsFuture().toCompletableFuture().get();
                retry = false;
            } catch (ExecutionException | InterruptedException ex) {
                if (System.currentTimeMillis() > endTm) {
                    fail("Database startup failed!", ex);
                } else {
                    LOGGER.info(() -> String.format("Exception: %s", ex.getMessage()));
                    LOGGER.log(Level.INFO, "Exception details: ", ex);
                }
            }
        }
    }

    /**
     * Initialize user for test database.
     *
     * @param dbClient Helidon database client
     */
    private static void initUser(DbClient dbClient) {
        try {
            dbClient.execute(exec -> exec
                    .namedStatement("use")
                    .thenCompose(result -> exec.namedStatement("create-user"))
            ).toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        } catch (ExecutionException | InterruptedException ex) {
                LOGGER.warning(() -> String.format("Exception: %s", ex.getMessage()));
                fail("Database user setup failed!", ex);
        }
    }

    /**
     * Setup database for tests.
     * Wait for database to start. Returns after ping query completed successfully or timeout passed.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @BeforeAll
    public static void setup() throws ExecutionException, InterruptedException {
        LOGGER.info(() -> String.format("Initializing Integration Tests"));
        waitForStart(DB_ADMIN);
        //initUser(DB_ADMIN);
    }

    /**
     * Simple test to verify that DML query execution works.
     * Used before running database schema initialization.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testDmlStatementExecution() throws ExecutionException, InterruptedException {
        DbRows<DbRow> result = DB_CLIENT.execute(exec -> exec.namedStatement("ping"))
                .toCompletableFuture().get().rsFuture().toCompletableFuture().get();
        List<DbRow> rowsList = result.collect().toCompletableFuture().get();
        DbRow row = rowsList.get(0);
        Double ok = row.column("ok").as(Double.class);
        assertThat(ok, equalTo(1.0));
        LOGGER.info(() -> String.format("Command ping row: %s", row.toString()));
    }

}
