/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.mapper.MapperManager;

/**
 * Helidon database client context.
 * This instance holds configuration and runtimes that are shared by any execution
 * within this client runtime.
 */
public class DbClientContext implements DbContext {

    private final DbMapperManager dbMapperManager;
    private final MapperManager mapperManager;
    private final List<DbClientService> clientServices;
    private final boolean missingMapParametersAsNull;
    private final DbStatements statements;
    private final String dbType;

    /**
     * Create an instance of client context.
     *
     * @param builder Builder for {@link DbClientContext}
     */
    protected DbClientContext(
            BuilderBase<? extends BuilderBase<?, ? extends DbClientContext>, ? extends DbClientContext> builder) {
        this.dbMapperManager = builder.dbMapperManager;
        this.mapperManager = builder.mapperManager;
        this.clientServices = builder.clientServices;
        this.missingMapParametersAsNull = builder.missingMapParametersAsNull;
        this.statements = builder.statements;
        this.dbType = builder.dbType;
    }

    @Override
    public boolean missingMapParametersAsNull() {
        return missingMapParametersAsNull;
    }

    @Override
    public DbStatements statements() {
        return statements;
    }

    @Override
    public DbMapperManager dbMapperManager() {
        return dbMapperManager;
    }

    @Override
    public MapperManager mapperManager() {
        return mapperManager;
    }

    @Override
    public List<DbClientService> clientServices() {
        return clientServices;
    }

    /**
     * Type of this database provider (such as jdbc:mysql, mongoDB etc.).
     *
     * @return name of the database provider
     */
    public String dbType() {
        return dbType;
    }

    /**
     * Create Helidon database client context builder.
     *
     * @return database client context builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DbClientContext}.
     */
    public static final class Builder extends BuilderBase<Builder, DbClientContext> {

        @Override
        public DbClientContext build() {
            return new DbClientContext(this);
        }

    }

    /**
     * Base builder for {@link DbClientContext}.
     *
     * @param <B> type of the builder
     * @param <T> type of the built instance
     */
    public abstract static class BuilderBase<B extends BuilderBase<B, T>, T extends DbClientContext> implements io.helidon.common.Builder<B, T> {

        private DbMapperManager dbMapperManager;
        private MapperManager mapperManager;
        private List<DbClientService> clientServices = List.of();
        private boolean missingMapParametersAsNull;
        private DbStatements statements;
        private String dbType;

        /**
         * Creates an instance of base builder for {@link DbClientContext}.
         */
        protected BuilderBase() {
            this.missingMapParametersAsNull = false;
        }

        /**
         * Configure the DB mapper manager to use.
         *
         * @param dbMapperManager DB mapper manager
         * @return updated builder instance
         */
        public B dbMapperManager(DbMapperManager dbMapperManager) {
            this.dbMapperManager = dbMapperManager;
            return identity();
        }

        /**
         * Configure the mapper manager to use.
         *
         * @param mapperManager mapper manager
         * @return updated builder instance
         */
        public B mapperManager(MapperManager mapperManager) {
            this.mapperManager = mapperManager;
            return identity();
        }

        /**
         * Configure the client services to use.
         *
         * @param clientServices client service list
         * @return updated builder instance
         */
        public B clientServices(List<DbClientService> clientServices) {
            this.clientServices = clientServices;
            return identity();
        }

        /**
         * Missing values in named parameters {@link java.util.Map} are considered as null values.
         * When set to {@code true}, named parameters value missing in the {@code Map} is considered
         * as {@code null}. When set to {@code false}, any parameter value missing in the {@code Map}
         * will cause an exception.
         * @param missingMapParametersAsNull whether missing values in named parameters {@code Map}
         *                                   are considered as null values
         * @return updated builder instance
         */
        public B missingMapParametersAsNull(boolean missingMapParametersAsNull) {
            this.missingMapParametersAsNull = missingMapParametersAsNull;
            return identity();
        }

        /**
         * Configure the db statements to use.
         *
         * @param statements statements
         * @return updated builder instance
         */
        public B statements(DbStatements statements) {
            this.statements = statements;
            return identity();
        }

        /**
         * Configure the type of this database provider.
         *
         * @param dbType database provider type
         * @return updated builder instance
         */
        public B dbType(String dbType) {
            this.dbType = dbType;
            return identity();
        }
    }
}
