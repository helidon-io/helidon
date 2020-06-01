/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperManager;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatements;
import io.helidon.dbclient.spi.DbClientProviderBuilder;
import io.helidon.dbclient.spi.DbMapperProvider;

/**
 * Builder for mongoDB database.
 */
public final class MongoDbClientProviderBuilder implements DbClientProviderBuilder<MongoDbClientProviderBuilder> {

    private final List<DbClientService> clientServices = new LinkedList<>();
    private final DbMapperManager.Builder dbMapperBuilder = DbMapperManager.builder();

    private String url;
    private String username;
    private String password;
    private String credDb;
    private DbStatements statements;
    private MapperManager mapperManager;
    private DbMapperManager dbMapperManager;
    private MongoDbClientConfig dbConfig;

    MongoDbClientProviderBuilder() {
    }

    @Override
    public DbClient build() {
        if (null == dbMapperManager) {
            this.dbMapperManager = dbMapperBuilder.build();
        }
        if (null == mapperManager) {
            this.mapperManager = MapperManager.create();
        }
        if (null == dbConfig) {
            dbConfig = new MongoDbClientConfig(url, username, password, credDb);
        }

        return new MongoDbClient(this);
    }

    @Override
    public MongoDbClientProviderBuilder config(Config config) {
        config.get("connection").asNode().ifPresentOrElse(conn -> {
            conn.get("url").asString().ifPresent(this::url);
            conn.get("username").asString().ifPresent(this::username);
            conn.get("password").asString().ifPresent(this::password);
        }, () -> {
            throw new DbClientException(String.format(
                    "No database connection configuration (%s) was found",
                    config.get("connection").key()));
        });
        config.get("credDb").asString().ifPresent(this::credDb);
        statements = DbStatements.create(config.get("statements"));
        return this;
    }

    @Override
    public MongoDbClientProviderBuilder url(String url) {
        this.url = url;
        return this;
    }

    @Override
    public MongoDbClientProviderBuilder username(String username) {
        this.username = username;
        return this;
    }

    @Override
    public MongoDbClientProviderBuilder password(String password) {
        this.password = password;
        return this;
    }

    /**
     * Credential database.
     *
     * @param db database name
     * @return updated builder instance
     */
    public MongoDbClientProviderBuilder credDb(String db) {
        this.credDb = db;
        return this;
    }

    @Override
    public MongoDbClientProviderBuilder statements(DbStatements statements) {
        this.statements = statements;
        return this;
    }

    @Override
    public MongoDbClientProviderBuilder addService(DbClientService clientService) {
        this.clientServices.add(clientService);
        return this;
    }

    @Override
    public <TYPE> MongoDbClientProviderBuilder addMapper(DbMapper<TYPE> dbMapper, Class<TYPE> mappedClass) {
        this.dbMapperBuilder.addMapperProvider(new DbMapperProvider() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> Optional<DbMapper<T>> mapper(Class<T> type) {
                if (type.equals(mappedClass)) {
                    return Optional.of((DbMapper<T>) dbMapper);
                }
                return Optional.empty();
            }
        });
        return this;
    }

    @Override
    public <TYPE> MongoDbClientProviderBuilder addMapper(DbMapper<TYPE> dbMapper, GenericType<TYPE> mappedType) {
        this.dbMapperBuilder.addMapperProvider(new DbMapperProvider() {
            @Override
            public <T> Optional<DbMapper<T>> mapper(Class<T> type) {
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> Optional<DbMapper<T>> mapper(GenericType<T> type) {
                if (type.equals(mappedType)) {
                    return Optional.of((DbMapper<T>) dbMapper);
                }
                return Optional.empty();
            }
        });
        return this;
    }

    @Override
    public MongoDbClientProviderBuilder mapperManager(MapperManager manager) {
        this.mapperManager = manager;
        return this;
    }

    @Override
    public MongoDbClientProviderBuilder addMapperProvider(DbMapperProvider provider) {
        this.dbMapperBuilder.addMapperProvider(provider);
        return this;
    }

    List<DbClientService> clientServices() {
        return List.copyOf(clientServices);
    }

    DbMapperManager.Builder dbMapperBuilder() {
        return dbMapperBuilder;
    }

    DbStatements statements() {
        return statements;
    }

    MapperManager mapperManager() {
        return mapperManager;
    }

    DbMapperManager dbMapperManager() {
        return dbMapperManager;
    }

    MongoDbClientConfig dbConfig() {
        return dbConfig;
    }

}
