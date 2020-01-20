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
import java.util.logging.Logger;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatements;
import io.helidon.dbclient.DbTransaction;
import io.helidon.dbclient.common.InterceptorSupport;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.reactivestreams.Subscription;

/**
 * MongoDB driver handler.
 */
public class MongoDbClient implements DbClient {

    private static final Logger LOGGER = Logger.getLogger(MongoDbClient.class.getName());

    private final MongoDbClientConfig config;
    private final DbStatements statements;
    private final MongoClient client;
    private final MongoDatabase db;
    private final MapperManager mapperManager;
    private final DbMapperManager dbMapperManager;
    private final ConnectionString connectionString;
    private final InterceptorSupport interceptors;

    /**
     * Creates an instance of MongoDB driver handler.
     *
     * @param builder builder for mongoDB database
     */
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

    private static final class MongoSessionSubscriber implements org.reactivestreams.Subscriber<ClientSession> {

        private final CompletableFuture<ClientSession> txFuture;
        private ClientSession tx;
        private Subscription subscription;

        MongoSessionSubscriber(CompletableFuture<ClientSession> txFuture) {
            this.txFuture = txFuture;
            this.tx = null;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            this.subscription.request(1);
        }

        @Override
        public void onNext(ClientSession session) {
            this.tx = session;
            this.subscription.cancel();
        }

        @Override
        public void onError(Throwable t) {
            LOGGER.warning(() -> String.format("Transaction error: %s", t.getMessage()));
            txFuture.completeExceptionally(t);
        }

        @Override
        public void onComplete() {
            txFuture.complete(tx);
        }

    }

    @Override
    public <T> CompletionStage<T> inTransaction(Function<DbTransaction, CompletionStage<T>> executor) {
        // Disable MongoDB transactions until they are tested.
        if (true) {
            throw new UnsupportedOperationException("Transactions are not yet supported in MongoDB");
        }
        CompletableFuture<ClientSession> txFuture = new CompletableFuture<>();
        client.startSession().subscribe(new MongoSessionSubscriber(txFuture));
        return txFuture.thenCompose(tx -> {
            MongoDbTransaction mongoTx = new MongoDbTransaction(
                    db, tx, statements, dbMapperManager, mapperManager, interceptors);
            CompletionStage<T> future = executor.apply(mongoTx);
            // FIXME: Commit and rollback return Publisher so another future must be introduced here
            // to cover commit or rollback. This future may be passed using allRegistered call
            // and combined with transaction future
            future.thenRun(mongoTx.txManager()::allRegistered);
            return future;
        });
    }

    @Override
    public <T extends CompletionStage<?>> T execute(Function<DbExecute, T> executor) {
        return executor.apply(new MongoDbExecute(db, statements, dbMapperManager, mapperManager, interceptors));
    }

    @Override
    public CompletionStage<Void> ping() {
        return execute(exec -> exec
                .statement("{\"operation\":\"command\",\"query\":{ping:1}}"))
                .thenRun(() -> {});
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
