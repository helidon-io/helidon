/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.jdbc;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.mapper.MapperManager;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbInterceptor;
import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.DbStatements;
import io.helidon.dbclient.common.InterceptorSupport;
import io.helidon.dbclient.spi.DbClientProviderBuilder;
import io.helidon.dbclient.spi.DbMapperProvider;

import static io.helidon.dbclient.common.DbConfig.Properties.PASSWORD;
import static io.helidon.dbclient.common.DbConfig.Properties.URL;
import static io.helidon.dbclient.common.DbConfig.Properties.USERNAME;
import static io.helidon.dbclient.jdbc.ConnectionPool.Builder.processConfigNode;

/**
 * Fluent API builder for {@link JdbcDbClientProviderBuilder} that implements
 * the {@link io.helidon.dbclient.spi.DbClientProviderBuilder} from Helidon DB API.
 */
public final class JdbcDbClientProviderBuilder implements DbClientProviderBuilder<JdbcDbClientProviderBuilder> {

    private final InterceptorSupport.Builder interceptors = InterceptorSupport.builder();
    private final DbMapperManager.Builder dbMapperBuilder = DbMapperManager.builder();
    private String url;
    private String username;
    private String password;
    private Properties properties;
    private Properties internal;
    private DbStatements statements;
    private MapperManager mapperManager;
    private DbMapperManager dbMapperManager;
    private Supplier<ExecutorService> executorService;
    private ConnectionPool connectionPool;

    JdbcDbClientProviderBuilder() {
        properties = new Properties();
        internal = new Properties();
    }

    @Override
    public DbClient build() {
        if (null == connectionPool) {
            connectionPool = ConnectionPool.builder()
                    .url(url)
                    .username(username)
                    .password(password)
                    .properties(properties)
                    .internalProperties(internal)
                    .build();
        }
        if (null == dbMapperManager) {
            this.dbMapperManager = dbMapperBuilder.build();
        }
        if (null == mapperManager) {
            this.mapperManager = MapperManager.create();
        }
        if (null == executorService) {
            executorService = ThreadPoolSupplier.create();
        }
        return new JdbcDbClient(this);
    }

    @Override
    public JdbcDbClientProviderBuilder config(Config config) {
        config.get("connection").asNode().ifPresentOrElse(conn -> {
            Map<String, String> poolConfig = conn.detach().asMap().get();
            poolConfig.forEach((key, value) -> {
                switch (key) {
                    case URL:
                        url(value);
                        break;
                    case USERNAME:
                        username(value);
                        break;
                    case PASSWORD:
                        password(value);
                        break;
                    default:
                        processConfigNode(key, value, properties, internal);
                }
            });
        }, () -> {
            throw new DbClientException(String.format(
                    "No database connection configuration (%s) was found",
                    config.get("connection").key()));
        });
        config.get("statements").as(DbStatements::create).ifPresent(this::statements);
        config.get("executor-service").as(ThreadPoolSupplier::create).ifPresent(this::executorService);
        return this;
    }

    /**
     * Configure a connection pool.
     *
     * @param connectionPool connection pool to get connections to a database
     * @return updated builder instance
     */
    public JdbcDbClientProviderBuilder connectionPool(ConnectionPool connectionPool) {
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
    public JdbcDbClientProviderBuilder executorService(Supplier<ExecutorService> executorServiceSupplier) {
        this.executorService = executorServiceSupplier;
        return this;
    }

    @Override
    public JdbcDbClientProviderBuilder url(String url) {
        this.url = url;
        return this;
    }

    @Override
    public JdbcDbClientProviderBuilder username(String username) {
        this.username = username;
        return this;
    }

    @Override
    public JdbcDbClientProviderBuilder password(String password) {
        this.password = password;
        return this;
    }

    @Override
    public JdbcDbClientProviderBuilder statements(DbStatements statements) {
        this.statements = statements;
        return this;
    }

    @Override
    public <TYPE> JdbcDbClientProviderBuilder addMapper(DbMapper<TYPE> dbMapper, Class<TYPE> mappedClass) {
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
    public <TYPE> JdbcDbClientProviderBuilder addMapper(DbMapper<TYPE> dbMapper, GenericType<TYPE> mappedType) {
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
    public JdbcDbClientProviderBuilder mapperManager(MapperManager manager) {
        this.mapperManager = manager;
        return this;
    }

    @Override
    public JdbcDbClientProviderBuilder addMapperProvider(DbMapperProvider provider) {
        this.dbMapperBuilder.addMapperProvider(provider);
        return this;
    }

    @Override
    public JdbcDbClientProviderBuilder addInterceptor(DbInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }

    @Override
    public JdbcDbClientProviderBuilder addInterceptor(DbInterceptor interceptor, String... statementNames) {
        this.interceptors.add(interceptor, statementNames);
        return this;
    }

    @Override
    public JdbcDbClientProviderBuilder addInterceptor(DbInterceptor interceptor, DbStatementType... dbStatementTypes) {
        this.interceptors.add(interceptor, dbStatementTypes);
        return this;
    }

    DbStatements statements() {
        return statements;
    }

    InterceptorSupport interceptors() {
        return interceptors.build();
    }

    DbMapperManager dbMapperManager() {
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
