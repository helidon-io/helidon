/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientBase;
import io.helidon.dbclient.DbClientContext;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbTransaction;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import static java.util.Objects.requireNonNull;

/**
 * MongoDB driver handler.
 */
public class MongoDbClient extends DbClientBase implements DbClient {
    private final MongoClient client;
    private final MongoDatabase db;

    /**
     * Creates an instance of MongoDB driver handler.
     *
     * @param builder builder for mongoDB database
     */
    MongoDbClient(MongoDbClientBuilder builder) {
        super(DbClientContext.builder()
                .statements(builder.statements())
                .dbMapperManager(builder.dbMapperManager())
                .mapperManager(builder.mapperManager())
                .clientServices(builder.clientServices())
                .dbType(MongoDbClientProvider.DB_TYPE)
                .build());

        MongoDbClientConfig config = builder.dbConfig();
        ConnectionString connectionString = new ConnectionString(config.url());

        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                .applyConnectionString(connectionString);

        String dbName = connectionString.getDatabase();
        if ((config.username() != null) || (config.password() != null)) {
            String credDb = (config.credDb() == null) ? connectionString.getDatabase() : config.credDb();

            MongoCredential credentials = MongoCredential.createCredential(
                    config.username(),
                    requireNonNull(credDb),
                    config.password().toCharArray());

            settingsBuilder.credential(credentials);
        }

        this.client = MongoClients.create(settingsBuilder.build());
        this.db = client.getDatabase(dbName != null ? dbName : "admin");
    }

    /**
     * Creates an instance of MongoDB driver handler with MongoDb client and connection
     * supplied.
     * Used in jUnit tests to mock MongoDB driver internals.
     *
     * @param builder builder for mongoDB database
     * @param client  MongoDB client provided externally
     * @param db      MongoDB database provided externally
     */
    MongoDbClient(MongoDbClientBuilder builder, MongoClient client, MongoDatabase db) {
        super(DbClientContext.builder()
                .statements(builder.statements())
                .dbMapperManager(builder.dbMapperManager())
                .mapperManager(builder.mapperManager())
                .clientServices(builder.clientServices())
                .dbType(MongoDbClientProvider.DB_TYPE)
                .build());

        this.client = client;
        this.db = db;
    }

    @Override
    public DbExecute execute() {
        return new MongoDbExecute(context(), db);
    }

    @Override
    public DbTransaction transaction() {
        throw new UnsupportedOperationException("Transactions are not supported");
    }

    @Override
    public String dbType() {
        return MongoDbClientProvider.DB_TYPE;
    }

    @Override
    public <C> C unwrap(Class<C> cls) {
        if (MongoClient.class.isAssignableFrom(cls)) {
            return cls.cast(client);
        }
        if (MongoDatabase.class.isAssignableFrom(cls)) {
            return cls.cast(db);
        }
        throw new UnsupportedOperationException(String.format(
                "Class %s is not supported for unwrap",
                cls.getName()));
    }
}
