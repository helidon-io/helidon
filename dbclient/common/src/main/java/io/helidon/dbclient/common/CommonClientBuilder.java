/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.common;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperManager;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatements;
import io.helidon.dbclient.spi.DbClientBuilder;
import io.helidon.dbclient.spi.DbMapperProvider;

/**
 * Provider specific {@link io.helidon.dbclient.DbClient} builder.
 * <p>Common {@link DbClientBuilder} implementations ancestor with {@link DbMapperManager.Builder}
 * and common attributes required to initialize target {@code DbClient} instance.
 *
 * @param <T> type of the builder extending or implementing this interface.
 */
public abstract class CommonClientBuilder<T extends CommonClientBuilder<T>>
        implements DbClientBuilder<T> {

    private final DbMapperManager.Builder dbMapperBuilder = DbMapperManager.builder();
    private String url;
    private String username;
    private String password;
    private DbStatements statements;
    private MapperManager mapperManager;
    private DbMapperManager dbMapperManager;
    private final List<DbClientService> clientServices;

    /**
     * Creates an instance of {@link CommonClientBuilder}.
     */
    protected CommonClientBuilder() {
        this.clientServices = new LinkedList<>();
    }

    @Override
    public T config(Config config) {
        config.get("statements").as(DbStatements::create).ifPresent(this::statements);
        return identity();
    }

    @Override
    public T url(String url) {
        this.url = url;
        return identity();
    }

    @Override
    public T username(String username) {
        this.username = username;
        return identity();
    }

    @Override
    public T password(String password) {
        this.password = password;
        return identity();
    }

    @Override
    public T statements(DbStatements statements) {
        this.statements = statements;
        return identity();
    }

    @Override
    public <TYPE> T addMapper(DbMapper<TYPE> dbMapper, Class<TYPE> mappedClass) {
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
        return identity();
    }

    @Override
    public <TYPE> T addMapper(DbMapper<TYPE> dbMapper, GenericType<TYPE> mappedType) {
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
        return identity();
    }

    @Override
    public T mapperManager(MapperManager manager) {
        this.mapperManager = manager;
        return identity();
    }

    @Override
    public T addMapperProvider(DbMapperProvider provider) {
        this.dbMapperBuilder.addMapperProvider(provider);
        return identity();
    }

    @Override
    public T addService(DbClientService clientService) {
        clientServices.add(clientService);
        return identity();
    }

    /**
     * Get database URL.
     *
     * @return database URL
     */
    public String url() {
        return url;
    }

    /**
     * Get database user name.
     *
     * @return database user name
     */
    public String username() {
        return username;
    }

    /**
     * Get database user password.
     *
     * @return database user password.
     */
    public String password() {
        return password;
    }

    /**
     * Get configured statements to be used by database provider.
     *
     * @return statements to be used by database provider
     */
    public DbStatements statements() {
        return statements;
    }

    /**
     * Get configured client services (interceptors).
     * List of services is converted to unmodifiable List.
     *
     * @return client services
     */
    public List<DbClientService> clientServices() {
        return List.copyOf(clientServices);
    }

    /**
     * Get {@link io.helidon.common.mapper.Mapper} manager.
     *
     * @return {@code Mapper} manager.
     */
    public MapperManager mapperManager() {
        return mapperManager;
    }

    /**
     * Get manager of all configured {@link DbMapper mappers}.
     *
     * @return manager of all configured {@link DbMapper mappers}
     */
    public DbMapperManager dbMapperManager() {
        return dbMapperManager;
    }

}
