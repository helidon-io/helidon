/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.function.Function;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.reactive.Single;
import io.helidon.common.reactive.Subscribable;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbTransaction;
import io.helidon.dbclient.common.DbClientContext;

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
    static {
        HelidonFeatures.register(HelidonFlavor.SE, "DbClient", "MongoDB");
    }

    private final MongoDbClientConfig config;
    private final MongoClient client;
    private final MongoDatabase db;
    private final ConnectionString connectionString;
    private final DbClientContext clientContext;

    /**
     * Creates an instance of MongoDB driver handler.
     *
     * @param builder builder for mongoDB database
     */
    MongoDbClient(MongoDbClientProviderBuilder builder) {
        this.clientContext = DbClientContext.builder()
                .dbMapperManager(builder.dbMapperManager())
                .mapperManager(builder.mapperManager())
                .clientServices(builder.clientServices())
                .statements(builder.statements())
                .build();

        this.config = builder.dbConfig();
        this.connectionString = new ConnectionString(config.url());
        this.client = initMongoClient();
        this.db = initMongoDatabase();
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
            txFuture.completeExceptionally(t);
        }

        @Override
        public void onComplete() {
            txFuture.complete(tx);
        }

    }

    @Override
    public <U, T extends Subscribable<U>> T inTransaction(Function<DbTransaction, T> executor) {
        // Disable MongoDB transactions until they are tested.
        throw new UnsupportedOperationException("Transactions are not yet supported in MongoDB");

        //        CompletableFuture<ClientSession> txFuture = new CompletableFuture<>();
        //        client.startSession().subscribe(new MongoSessionSubscriber(txFuture));
        //        return txFuture.thenCompose(tx -> {
        //            MongoDbTransaction mongoTx = new MongoDbTransaction(
        //                    db, tx, statements, dbMapperManager, mapperManager, services);
        //            CompletionStage<T> future = executor.apply(mongoTx);
        //            // FIXME: Commit and rollback return Publisher so another future must be introduced here
        //            // to cover commit or rollback. This future may be passed using allRegistered call
        //            // and combined with transaction future
        //            future.thenRun(mongoTx.txManager()::allRegistered);
        //            return future;
        //        });
    }

    @Override
    public <U, T extends Subscribable<U>> T execute(Function<DbExecute, T> executor) {
        return executor.apply(new MongoDbExecute(db, clientContext));
    }

    @Override
    public Single<Void> ping() {
        return execute(exec -> exec
                .get("{\"operation\":\"command\",\"query\":{ping:1}}"))
                .flatMapSingle(it -> Single.empty());
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
