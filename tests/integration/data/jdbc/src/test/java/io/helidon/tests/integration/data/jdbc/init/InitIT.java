/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.data.jdbc.init;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.dbclient.DbRow;
import io.helidon.tests.integration.data.common.AbstractIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Initialize database
 */
public class InitIT extends AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(InitIT.class.getName());

    /**
     * Initializes database schema (tables).
     *
     * @param dbClient Helidon database client
     */
    private static void initSchema(DbClient dbClient) {
        dbClient.execute(exec -> exec
                .namedDml("create-entity")
        ).await();
    }

    /**
     * Initialize database content (rows in tables).
     *
     * @param dbClient Helidon database client
     */
    private static void initData(DbClient dbClient) {
    }

    /**
     * Setup database for tests.
     */
    @BeforeAll
    public static void setup() {
        LOGGER.info(() -> "Initializing Integration Tests");

        initSchema(DB_CLIENT);
        initData(DB_CLIENT);
    }

    /**
     * Verify configured database ping query.
     */
    @Test
    public void testPingQuery() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("ping-query"));

        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.collectList().await(5, TimeUnit.SECONDS);
        assertThat(rowsList, not(empty()));
        assertThat(rowsList, hasSize(is(1)));
    }

}
