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

import io.helidon.common.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientBuilderBase;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.spi.DbClientBuilder;

/**
 * Builder for mongoDB database.
 */
public final class MongoDbClientBuilder
        extends DbClientBuilderBase<MongoDbClientBuilder>
        implements DbClientBuilder<MongoDbClientBuilder> {

    private String credDb;
    private MongoDbClientConfig dbConfig;

    /**
     * Create a new instance.
     */
    MongoDbClientBuilder() {
    }

    /**
     * Create a new instance.
     *
     * @return new MongoDB client builder
     */
    public static MongoDbClientBuilder create() {
        return new MongoDbClientBuilder();
    }

    @Override
    public DbClient doBuild() {
        if (null == dbConfig) {
            dbConfig = new MongoDbClientConfig(url(), username(), password(), credDb);
        }
        return new MongoDbClient(this);
    }

    @Override
    public MongoDbClientBuilder config(Config config) {
        Config connConfig = config.get("connection");
        if (!connConfig.exists()) {
            throw new DbClientException(String.format(
                    "No database connection configuration (%s) was found",
                    connConfig.key()));
        }
        connConfig.get("url").asString().ifPresent(this::url);
        connConfig.get("username").asString().ifPresent(this::username);
        connConfig.get("password").asString().ifPresent(this::password);
        config.get("credDb").asString().ifPresent(this::credDb);
        return super.config(config);
    }

    /**
     * Credential database.
     *
     * @param db database name
     * @return updated builder instance
     */
    public MongoDbClientBuilder credDb(String db) {
        this.credDb = db;
        return this;
    }

    MongoDbClientConfig dbConfig() {
        return dbConfig;
    }

}
