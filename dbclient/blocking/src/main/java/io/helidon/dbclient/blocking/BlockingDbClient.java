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
package io.helidon.dbclient.blocking;

import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;


/**
 * Helidon blocking database client.
 */
public interface BlockingDbClient {
    /**
     * Execute database statements in transaction.
     *
     * @param <T>      statement execution result type, as returned by all APIs of DbClient.
     * @param executor database statement executor, see {@link BlockingDbExecute}
     * @return statement execution result
     */
    <T> T inTransaction(Function<BlockingDbTransaction, T> executor);

    /**
     * Execute database statement.
     *
     * @param <T>      statement execution result typ, as returned by all APIs of DbClient
     * @param executor database statement executor, see {@link BlockingDbExecute}
     * @return statement execution result
     */
    <T> T execute(Function<BlockingDbExecute, T> executor);

    /**
     * Type of this database provider (such as jdbc:mysql, mongoDB etc.).
     *
     * @return name of the database provider
     */
    String dbType();

    /**
     * Create Helidon blocking database handler builder.
     *
     * @param config name of the configuration node with driver configuration
     * @return database client
     */
    static BlockingDbClient create(Config config) {
        return BlockingDbClientImpl.create(config);
    }

    /**
     * Create Helidon blocking database handler builder.
     *
     * @param dbClient already set up client
     * @return database client
     */
    static BlockingDbClient create(DbClient dbClient) {
        return BlockingDbClientImpl.create(dbClient);
    }

}
