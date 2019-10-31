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
package io.helidon.tests.integration.dbclient.jdbc.init;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.dbClient;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Check minimal functionality needed before running database schema initialization.
 * First test class being executed after database startup.
 */
public class CheckIT extends AbstractIT {

    /** Local logger instance. */
    private static final Logger LOG = Logger.getLogger(CheckIT.class.getName());

    /** Timeout in seconds to wait for database to come up. */
    private static final int TIMEOUT = 60;

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
                dbClient.execute(exec -> exec.namedDml("ping")).toCompletableFuture().get();
                retry = false;
            } catch (ExecutionException | InterruptedException ex) {
                if (System.currentTimeMillis() > endTm) {
                    fail("Database startup failed!", ex);
                }
            }
        }
    }

    /**
     * Setup database for tests.
     * Wait for database to start. Returns after ping query completed successfully or timeout passed.
     */
    @BeforeAll
    public static void setup() {
        LOG.log(Level.INFO, "Initializing Integration Tests");
        waitForStart(dbClient);
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
        long result = dbClient.execute(exec -> exec.namedDml("ping")).toCompletableFuture().get();
        assertThat(result, equalTo(0L));
    }

}
