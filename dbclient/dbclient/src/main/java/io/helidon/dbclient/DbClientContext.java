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
    private final DbStatements statements;
    private final String dbType;

    private DbClientContext(Builder builder) {
        this.dbMapperManager = builder.dbMapperManager;
        this.mapperManager = builder.mapperManager;
        this.clientServices = builder.clientServices;
        this.statements = builder.statements;
        this.dbType = builder.dbType;
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
    public static class Builder implements io.helidon.common.Builder<Builder, DbClientContext> {

        private DbMapperManager dbMapperManager;
        private MapperManager mapperManager;
        private List<DbClientService> clientServices = List.of();
        private DbStatements statements;
        private String dbType;

        private Builder() {
        }

        @Override
        public DbClientContext build() {
            return new DbClientContext(this);
        }

        /**
         * Configure the DB mapper manager to use.
         *
         * @param dbMapperManager DB mapper manager
         * @return updated builder instance
         */
        public Builder dbMapperManager(DbMapperManager dbMapperManager) {
            this.dbMapperManager = dbMapperManager;
            return this;
        }

        /**
         * Configure the mapper manager to use.
         *
         * @param mapperManager mapper manager
         * @return updated builder instance
         */
        public Builder mapperManager(MapperManager mapperManager) {
            this.mapperManager = mapperManager;
            return this;
        }

        /**
         * Configure the client services to use.
         *
         * @param clientServices client service list
         * @return updated builder instance
         */
        public Builder clientServices(List<DbClientService> clientServices) {
            this.clientServices = clientServices;
            return this;
        }

        /**
         * Configure the db statements to use.
         *
         * @param statements statements
         * @return updated builder instance
         */
        public Builder statements(DbStatements statements) {
            this.statements = statements;
            return this;
        }

        /**
         * Configure the type of this database provider.
         *
         * @param dbType database provider type
         * @return updated builder instance
         */
        public Builder dbType(String dbType) {
            this.dbType = dbType;
            return this;
        }
    }
}
