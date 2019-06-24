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
package io.helidon.db.spi;

import io.helidon.common.Builder;
import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperManager;
import io.helidon.config.Config;
import io.helidon.db.Db;
import io.helidon.db.DbInterceptor;
import io.helidon.db.DbMapper;
import io.helidon.db.DbStatementType;
import io.helidon.db.DbStatements;

/**
 * Database provider builder.
 *
 * @param <T> type of the builder extending implementing this interface.
 */
public interface DbProviderBuilder<T extends DbProviderBuilder<T>> extends Builder<Db> {

    /**
     * Use database connection configuration from configuration file.
     *
     * @param config {@link io.helidon.config.Config} instance with database connection attributes
     * @return database provider builder
     */
    T config(Config config);

    /**
     * Set database connection string (URL).
     *
     * @param url database connection string
     * @return database provider builder
     */
    T url(String url);

    /**
     * Set database connection user name.
     *
     * @param username database connection user name
     * @return database provider builder
     */
    T username(String username);

    /**
     * Set database connection p¨assword.
     *
     * @param password database connection password
     * @return database provider builder
     */
    T password(String password);

    /**
     * Statements to use either from configuration
     * or manually configured.
     *
     * @param statements Statements to use
     * @return updated builder instance
     */
    T statements(DbStatements statements);

    /**
     * Database schema mappers provider.
     *
     * @param provider database schema mappers provider to use
     * @return updated builder instance
     */
    T addMapperProvider(DbMapperProvider provider);

    /**
     * Add a custom mapper.
     *
     * @param dbMapper    the mapper capable of mapping the mappedClass to various database objects
     * @param mappedClass class that this mapper supports
     * @param <TYPE>      type of the supported class
     * @return updated builder instance.
     */
    <TYPE> T addMapper(DbMapper<TYPE> dbMapper, Class<TYPE> mappedClass);

    /**
     * Add a custom mapper with generic types support.
     *
     * @param dbMapper    the mapper capable of mapping the mappedClass to various database objects
     * @param mappedType  type that this mapper supports
     * @param <TYPE>      type of the supported class
     * @return updated builder instance.
     */
    <TYPE> T addMapper(DbMapper<TYPE> dbMapper, GenericType<TYPE> mappedType);

    /**
     * Mapper manager for generic mapping, such as mapping of parameters to expected types.
     *
     * @param manager mapper manager
     * @return updated builder instance
     */
    T mapperManager(MapperManager manager);

    /**
     * Add an interceptor.
     * This allows to add implementation of tracing, metrics, logging etc. without the need to hard-code these into
     * the base.
     *
     * @param interceptor interceptor instance
     * @return updated builder instance
     */
    T addInterceptor(DbInterceptor interceptor);

    /**
     * Add an interceptor that is active only on the configured statement names.
     * This interceptor is only executed on named statements.
     *
     * @param interceptor interceptor instance
     * @param statementNames statement names to be active on
     * @return updated builder instance
     */
    T addInterceptor(DbInterceptor interceptor, String... statementNames);

    /**
     * Add an interceptor thas is active only on configured statement types.
     * This interceptor is executed on all statements of that type.
     * <p>
     * Note the specific handling of the following types:
     * <ul>
     *     <li>{@link io.helidon.db.DbStatementType#DML} - used only when the statement is created as a DML statement
     *          such as when using {@link io.helidon.db.DbExecute#createDmlStatement(String)}
     *          (this interceptor would not be enabled for inserts, updates, deletes)</li>
     *     <li>{@link io.helidon.db.DbStatementType#UNKNOWN} - used only when the statement is created as a general statement
     *          such as when using {@link io.helidon.db.DbExecute#createStatement(String)}
     *          (this interceptor would not be enabled for any other statements)</li>
     * </ul>
     *
     * @param interceptor interceptor instance
     * @param dbStatementTypes statement types to be active on
     * @return updated builder instance
     */
    T addInterceptor(DbInterceptor interceptor, DbStatementType... dbStatementTypes);

    /**
     * Build database handler for specific provider.
     *
     * @return database handler instance
     */
    @Override
    Db build();
}
