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
package io.helidon.db.mongodb;

import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperManager;
import io.helidon.config.Config;
import io.helidon.db.DbInterceptor;
import io.helidon.db.DbMapper;
import io.helidon.db.DbMapperManager;
import io.helidon.db.DbStatements;
import io.helidon.db.Db;
import io.helidon.db.DbStatementType;
import io.helidon.db.common.InterceptorSupport;
import io.helidon.db.spi.DbMapperProvider;
import io.helidon.db.spi.DbProviderBuilder;

/**
 * Builder for mongoDB database.
 */
public final class MongoDbProviderBuilder implements DbProviderBuilder<MongoDbProviderBuilder> {
    private final InterceptorSupport.Builder interceptors = InterceptorSupport.builder();
    private final DbMapperManager.Builder dbMapperBuilder = DbMapperManager.builder();

    private String url;
    private String username;
    private String password;
    private String credDb;
    private DbStatements statements;
    private MapperManager mapperManager;
    private DbMapperManager dbMapperManager;
    private MongoDbConfig dbConfig;

    MongoDbProviderBuilder() {
    }

    @Override
    public Db build() {
        if (null == dbMapperManager) {
            this.dbMapperManager = dbMapperBuilder.build();
        }
        if (null == mapperManager) {
            this.mapperManager = MapperManager.create();
        }
        if (null == dbConfig) {
            dbConfig = new MongoDbConfig(url, username, password, credDb);
        }

        return new MongoDb(this);
    }

    @Override
    public MongoDbProviderBuilder config(Config config) {
        config.get("url").asString().ifPresent(this::url);
        config.get("username").asString().ifPresent(this::username);
        config.get("password").asString().ifPresent(this::password);
        config.get("credDb").asString().ifPresent(this::credDb);
        statements = DbStatements.create(config.get("statements"));
        return this;
    }

    @Override
    public MongoDbProviderBuilder url(String url) {
        this.url = url;
        return this;
    }

    @Override
    public MongoDbProviderBuilder username(String username) {
        this.username = username;
        return this;
    }

    @Override
    public MongoDbProviderBuilder password(String password) {
        this.password = password;
        return this;
    }

    /**
     * Credential database.
     *
     * @param db database name
     * @return updated builder instance
     */
    public MongoDbProviderBuilder credDb(String db) {
        this.credDb = db;
        return this;
    }

    @Override
    public MongoDbProviderBuilder statements(DbStatements statements) {
        this.statements = statements;
        return this;
    }

    @Override
    public MongoDbProviderBuilder addInterceptor(DbInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }

    @Override
    public MongoDbProviderBuilder addInterceptor(DbInterceptor interceptor, String... statementNames) {
        this.interceptors.add(interceptor, statementNames);
        return this;
    }

    @Override
    public MongoDbProviderBuilder addInterceptor(DbInterceptor interceptor, DbStatementType... dbStatementTypes) {
        this.interceptors.add(interceptor, dbStatementTypes);
        return this;
    }

    @Override
    public <TYPE> MongoDbProviderBuilder addMapper(DbMapper<TYPE> dbMapper, Class<TYPE> mappedClass) {
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
    public <TYPE> MongoDbProviderBuilder addMapper(DbMapper<TYPE> dbMapper, GenericType<TYPE> mappedType) {
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
    public MongoDbProviderBuilder mapperManager(MapperManager manager) {
        this.mapperManager = manager;
        return this;
    }

    @Override
    public MongoDbProviderBuilder addMapperProvider(DbMapperProvider provider) {
        this.dbMapperBuilder.addMapperProvider(provider);
        return this;
    }

    InterceptorSupport interceptors() {
        return interceptors.build();
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

    MongoDbConfig dbConfig() {
        return dbConfig;
    }
}
