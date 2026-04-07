/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.Mappers;
import io.helidon.config.Config;
import io.helidon.dbclient.spi.DbClientBuilder;
import io.helidon.dbclient.spi.DbMapperProvider;

/**
 * Base {@link DbClientBuilder} implementation.
 *
 * @param <T> type of builder subclass
 */
public abstract class DbClientBuilderBase<T extends DbClientBuilderBase<T>>
        implements DbClientBuilder<T> {

    private final DbClientBuilderState.Builder delegate;

    /**
     * Creates an instance of {@link DbClientBuilderBase}.
     */
    protected DbClientBuilderBase() {
        super();
        this.delegate = DbClientBuilderState.builder();
    }

    @Override
    public DbClient build() {
        if (this.delegate.dbMapperManager().isEmpty()) {
            DbMapperManager.Builder mapperManagerBuilder = DbMapperManager.builder();
            this.delegate.mapperProviders().forEach(mapperManagerBuilder::addMapperProvider);
            this.delegate.dbMapperManager(mapperManagerBuilder.build());
        }
        if (this.delegate.mapperManager().isEmpty()) {
            this.delegate.mapperManager(Mappers.builder()
                                        .useBuiltInMappers(false)
                                        .build());
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
        config.get("statements").as(DbStatements::create).ifPresent(this::statements);
        return identity();
    }

    @Override
    public T url(String url) {
        this.delegate.url(url);
        return identity();
    }

    @Override
    public T username(String username) {
        this.delegate.username(username);
        return identity();
    }

    @Override
    public T password(String password) {
        this.delegate.password(password);
        return identity();
    }

    @Override
    public T missingMapParametersAsNull(boolean missingMapParametersAsNull) {
        this.delegate.missingMapParametersAsNull(missingMapParametersAsNull);
        return identity();
    }

    @Override
    public T statements(DbStatements statements) {
        this.delegate.statements(statements);
        return identity();
    }

    @Override
    public <TYPE> T addMapper(DbMapper<TYPE> dbMapper, Class<TYPE> mappedClass) {
        this.delegate.addMapper(dbMapper, mappedClass);
        return identity();
    }

    @Override
    public <TYPE> T addMapper(DbMapper<TYPE> dbMapper, GenericType<TYPE> mappedType) {
        this.delegate.addMapper(dbMapper, mappedType);
        return identity();
    }

    @Override
    public T mapperManager(Mappers manager) {
        this.delegate.mapperManager(manager);
        return identity();
    }

    @Override
    public T dbMapperManager(DbMapperManager manager) {
        this.delegate.dbMapperManager(manager);
        return identity();
    }

    @Override
    public T addMapperProvider(DbMapperProvider provider) {
        this.delegate.addMapperProvider(provider);
        return identity();
    }

    @Override
    public T addService(DbClientService clientService) {
        this.delegate.addService(clientService);
        return identity();
    }

    /**
     * Get database URL.
     *
     * @return database URL
     */
    public String url() {
        return this.delegate.url().orElse(null);
    }

    /**
     * Get database user name.
     *
     * @return database user name
     */
    public String username() {
        return this.delegate.username().orElse(null);
    }

    /**
     * Get database user password.
     *
     * @return database user password.
     */
    public String password() {
        return this.delegate.password().orElse(null);
    }

    /**
     * Configured missing values in named parameters {@link java.util.Map} handling.
     *
     * @return when set to {@code true}, named parameters value missing in the {@code Map} is considered
     *         as {@code null}, when set to {@code false}, any parameter value missing in the {@code Map}
     *         will cause an exception.
     */
    public boolean missingMapParametersAsNull() {
        return this.delegate.missingMapParametersAsNull();
    }

    /**
     * Get configured statements to be used by database provider.
     *
     * @return statements to be used by database provider
     */
    public DbStatements statements() {
        return this.delegate.statements().orElse(null);
    }

    /**
     * Get configured client services (interceptors).
     * List of services is converted to unmodifiable List.
     *
     * @return client services
     */
    public List<DbClientService> clientServices() {
        return List.copyOf(this.delegate.clientServices());
    }

    /**
     * Get {@link io.helidon.common.mapper.Mapper} manager.
     *
     * @return {@code Mapper} manager.
     */
    public Mappers mapperManager() {
        return this.delegate.mapperManager().orElse(null);
    }

    /**
     * Get manager of all configured {@link DbMapper mappers}.
     *
     * @return manager of all configured {@link DbMapper mappers}
     */
    public DbMapperManager dbMapperManager() {
        return this.delegate.dbMapperManager().orElse(null);
    }

}
