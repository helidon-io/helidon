/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.spi.DbClientBuilder;
import io.helidon.dbclient.spi.DbMapperProvider;

/**
 * Base {@link DbClientBuilder} implementation.
 *
 * @param <T> type of builder subclass
 */
public abstract class DbClientBuilderBase<T extends DbClientBuilderBase<T>>
        implements DbClientBuilder<T> {

    private final DbMapperManager.Builder dbMapperBuilder = DbMapperManager.builder();
    private final MapperManager.Builder mapperBuilder = MapperManager.builder();

    private String url;
    private String username;
    private String password;
    private boolean missingMapParametersAsNull;
    private DbStatements statements;
    private MapperManager mapperManager;
    private DbMapperManager dbMapperManager;
    private final List<DbClientService> clientServices;

    /**
     * Creates an instance of {@link DbClientBuilderBase}.
     */
    protected DbClientBuilderBase() {
        this.clientServices = new LinkedList<>();
        this.missingMapParametersAsNull = false;
    }

    @Override
    public DbClient build() {
        if (dbMapperManager == null) {
            dbMapperManager = dbMapperBuilder.build();
        }
        if (mapperManager == null) {
            mapperManager = mapperBuilder.build();
        }
        return doBuild();
    }

    /**
     * Actual {@link #build()} implementation for {@link DbClient} subclasses.
     *
     * @return new client
     */
    protected abstract DbClient doBuild();

    @Override
    public T config(Config config) {
        config.get("missing-map-parameters-as-null").as(Boolean.class).ifPresent(this::missingMapParametersAsNull);
        config.get("statements").map(DbStatements::create).ifPresent(this::statements);
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
    public T missingMapParametersAsNull(boolean missingMapParametersAsNull) {
        this.missingMapParametersAsNull = missingMapParametersAsNull;
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
            public <U> Optional<DbMapper<U>> mapper(Class<U> type) {
                if (type.equals(mappedClass)) {
                    return Optional.of((DbMapper<U>) dbMapper);
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
            public <U> Optional<DbMapper<U>> mapper(Class<U> type) {
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            @Override
            public <U> Optional<DbMapper<U>> mapper(GenericType<U> type) {
                if (type.equals(mappedType)) {
                    return Optional.of((DbMapper<U>) dbMapper);
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
    public T dbMapperManager(DbMapperManager manager) {
        this.dbMapperManager = manager;
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
     * Configured missing values in named parameters {@link java.util.Map} handling.
     *
     * @return when set to {@code true}, named parameters value missing in the {@code Map} is considered
     *         as {@code null}, when set to {@code false}, any parameter value missing in the {@code Map}
     *         will cause an exception.
     */
    public boolean missingMapParametersAsNull() {
        return missingMapParametersAsNull;
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
