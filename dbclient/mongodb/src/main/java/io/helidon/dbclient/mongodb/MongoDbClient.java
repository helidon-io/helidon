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
package io.helidon.dbclient.mongodb;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatements;
import io.helidon.dbclient.common.InterceptorSupport;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * MongoDB driver handler.
 */
public class MongoDbClient implements DbClient {
    private final MongoDbClientConfig config;
    private final DbStatements statements;
    private final MongoClient client;
    private final MongoDatabase db;
    private final MapperManager mapperManager;
    private final DbMapperManager dbMapperManager;
    private final ConnectionString connectionString;
    private final InterceptorSupport interceptors;

    MongoDbClient(MongoDbClientProviderBuilder builder) {
        this.config = builder.dbConfig();
        this.connectionString = new ConnectionString(config.url());
        this.statements = builder.statements();
        this.mapperManager = builder.mapperManager();
        this.dbMapperManager = builder.dbMapperManager();
        this.client = initMongoClient();
        this.db = initMongoDatabase();
        this.interceptors = builder.interceptors();
    }

    @Override
    public <T> T inTransaction(Function<DbExecute, T> executor) {
        return executor.apply(new MongoDbExecute(db, statements, dbMapperManager, mapperManager, interceptors));
    }

    @Override
    public <T> T execute(Function<DbExecute, T> executor) {
        return executor.apply(new MongoDbExecute(db, statements, dbMapperManager, mapperManager, interceptors));
    }

    @Override
    public CompletionStage<Void> ping() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String dbType() {
        return MongoDbClientProvider.DB_TYPE;
    }

    /**
     * Constructor helper to build MongoDB client from provided configuration.
     */
    private MongoClient initMongoClient() {
        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                .applyConnectionString(connectionString);

        if ((config.username() != null) || (config.password() != null)) {
            String credDb = (config.credDb() == null) ? connectionString.getDatabase() : config.credDb();

            MongoCredential credentials = MongoCredential.createCredential(
                    config.username(),
                    credDb,
                    config.password().toCharArray());

            settingsBuilder.credential(credentials);
        }

        return MongoClients.create(settingsBuilder.build());
    }

    /**
     * Constructor helper to build MongoDB database from provided configuration and client.
     */
    private MongoDatabase initMongoDatabase() {
        return client.getDatabase(connectionString.getDatabase());
    }
}
