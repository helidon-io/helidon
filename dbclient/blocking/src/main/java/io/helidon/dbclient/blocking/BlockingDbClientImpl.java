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

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;


/**
 * Implementation of {@link BlockingDbClient}.
 * {@inheritDoc}
 */
class BlockingDbClientImpl implements BlockingDbClient {
    private final DbClient dbClient;

    private BlockingDbClientImpl(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T inTransaction(Function<BlockingDbTransaction, T> executor) {
        return dbClient.inTransaction(it -> {
            CompletableFuture<T> result = new CompletableFuture<>();
            try {
                result.complete(executor.apply(BlockingDbTransaction.create(it)));
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
            return Single.create(result);
        }).await();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T execute(Function<BlockingDbExecute, T> executor) {
        return dbClient.execute(it -> {
            CompletableFuture<T> result = new CompletableFuture<>();
            try {
                result.complete(executor.apply(BlockingDbExecute.create(it)));
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
            return Single.create(result);
        }).await();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String dbType() {
        return dbClient.dbType();
    }

    /**
     * Create Blocking DB Client via provided configuration.
     *
     * @param config provided configuration.
     * @return Blocking DB Client
     */
    static BlockingDbClient create(Config config) {
        return new BlockingDbClientImpl(DbClient.builder(config).build());

    }


    /**
     * Create Blocking DB Client via provided configured DBClient.
     *
     * @param dbClient already configured DB client
     * @return Blocking DB Client
     */
    static BlockingDbClient create(DbClient dbClient) {
        return new BlockingDbClientImpl(dbClient);
    }


}
