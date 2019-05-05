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
package io.helidon.db.jdbc;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.mapper.MapperManager;
import io.helidon.config.Config;
import io.helidon.db.Db;
import io.helidon.db.DbInterceptor;
import io.helidon.db.DbMapper;
import io.helidon.db.DbMapperManager;
import io.helidon.db.DbStatementType;
import io.helidon.db.DbStatements;
import io.helidon.db.common.InterceptorSupport;
import io.helidon.db.spi.DbMapperProvider;
import io.helidon.db.spi.DbProviderBuilder;

/**
 * Fluent API builder for {@link io.helidon.db.jdbc.JdbcDbProviderBuilder} that implements
 * the {@link io.helidon.db.spi.DbProviderBuilder} from Helidon DB API.
 */
public final class JdbcDbProviderBuilder implements DbProviderBuilder<JdbcDbProviderBuilder> {
    private final InterceptorSupport.Builder interceptors = InterceptorSupport.builder();
    private final DbMapperManager.Builder dbMapperBuilder = DbMapperManager.builder();
    private String url;
    private String username;
    private String password;
    private DbStatements statements;
    private MapperManager mapperManager;
    private DbMapperManager dbMapperManager;
    private Supplier<ExecutorService> executorService;
    private ConnectionPool connectionPool;

    JdbcDbProviderBuilder() {
    }

    @Override
    public Db build() {
        if (null == dbMapperManager) {
            this.dbMapperManager = dbMapperBuilder.build();
        }
        if (null == mapperManager) {
            this.mapperManager = MapperManager.create();
        }
        if (null == connectionPool) {
            connectionPool = ConnectionPool.builder()
                    .url(url)
                    .username(username)
                    .password(password)
                    .build();
        }
        if (null == executorService) {
            executorService = ThreadPoolSupplier.create();
        }
        return new JdbcDb(this);
    }

    @Override
    public JdbcDbProviderBuilder config(Config config) {
        config.get("url").asString().ifPresent(this::url);
        config.get("username").asString().ifPresent(this::username);
        config.get("password").asString().ifPresent(this::password);
        config.get("statements").as(DbStatements::create).ifPresent(this::statements);
        config.get("executor-service").as(ThreadPoolSupplier::create).ifPresent(this::executorService);
        connectionPool(config.as(ConnectionPool::create).get());
        return this;
    }

    /**
     * Configure a connection pool.
     *
     * @param connectionPool connection pool to get connections to a database
     * @return updated builder instance
     */
    public JdbcDbProviderBuilder connectionPool(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
        return this;
    }

    /**
     * Configure an explicit executor service supplier.
     * The executor service is used to execute blocking calls to a database.
     *
     * @param executorServiceSupplier supplier to obtain an executor service from
     * @return updated builder instance
     */
    public JdbcDbProviderBuilder executorService(Supplier<ExecutorService> executorServiceSupplier) {
        this.executorService = executorServiceSupplier;
        return this;
    }

    @Override
    public JdbcDbProviderBuilder url(String url) {
        this.url = url;
        return this;
    }

    @Override
    public JdbcDbProviderBuilder username(String username) {
        this.username = username;
        return this;
    }

    @Override
    public JdbcDbProviderBuilder password(String password) {
        this.password = password;
        return this;
    }

    @Override
    public JdbcDbProviderBuilder statements(DbStatements statements) {
        this.statements = statements;
        return this;
    }

    @Override
    public <TYPE> JdbcDbProviderBuilder addMapper(DbMapper<TYPE> dbMapper, Class<TYPE> mappedClass) {
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
    public <TYPE> JdbcDbProviderBuilder addMapper(DbMapper<TYPE> dbMapper, GenericType<TYPE> mappedType) {
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
    public JdbcDbProviderBuilder mapperManager(MapperManager manager) {
        this.mapperManager = manager;
        return this;
    }

    @Override
    public JdbcDbProviderBuilder addMapperProvider(DbMapperProvider provider) {
        this.dbMapperBuilder.addMapperProvider(provider);
        return this;
    }

    @Override
    public JdbcDbProviderBuilder addInterceptor(DbInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }

    @Override
    public JdbcDbProviderBuilder addInterceptor(DbInterceptor interceptor, String... statementNames) {
        this.interceptors.add(interceptor, statementNames);
        return this;
    }

    @Override
    public JdbcDbProviderBuilder addInterceptor(DbInterceptor interceptor, DbStatementType... dbStatementTypes) {
        this.interceptors.add(interceptor, dbStatementTypes);
        return this;
    }

    DbStatements statements() {
        return statements;
    }

    InterceptorSupport interceptors() {
        return interceptors.build();
    }

    DbMapperManager dbMapperMananger() {
        return dbMapperManager;
    }

    MapperManager mapperManager() {
        return mapperManager;
    }

    ExecutorService executorService() {
        return executorService.get();
    }

    ConnectionPool connectionPool() {
        return connectionPool;
    }
}
