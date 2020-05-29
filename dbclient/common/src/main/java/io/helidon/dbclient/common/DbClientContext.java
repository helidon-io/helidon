/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatements;

/**
 * Context of the whole client.
 * <p>
 * This instance holds configuration and runtimes that are shared by any exec within this client runtime.
 */
public class DbClientContext {
    private final DbMapperManager dbMapperManager;
    private final MapperManager mapperManager;
    private final List<DbClientService> clientServices;
    private final DbStatements statements;

    /**
     * Create an instance from builder.
     *
     * @param builder the builder base your builder must extend
     */
    protected DbClientContext(BuilderBase<?> builder) {
        this.dbMapperManager = builder.dbMapperManager;
        this.mapperManager = builder.mapperManager;
        this.clientServices = builder.clientServices;
        this.statements = builder.statements;
    }

    /**
     * Create a new builder for context.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Invoke all configured client services and return a single that completes once all the
     * client services complete.
     *
     * @param dbContext context for client services
     * @return a single with the same or modified client service context
     */
    public Single<DbClientServiceContext> invokeServices(DbClientServiceContext dbContext) {
        CompletableFuture<DbClientServiceContext> result = CompletableFuture.completedFuture(dbContext);

        dbContext.context(Contexts.context().orElseGet(Context::create));

        for (DbClientService service : clientServices) {
            result = result.thenCompose(service::statement);
        }

        return Single.create(result);
    }

    /**
     * Configured statements.
     *
     * @return statements
     */
    public DbStatements statements() {
        return statements;
    }

    /**
     * Configured DB Mapper manager.
     *
     * @return DB mapper manager
     */
    public DbMapperManager dbMapperManager() {
        return dbMapperManager;
    }

    /**
     * Configured mapper manager.
     *
     * @return mapper manager
     */
    public MapperManager mapperManager() {
        return mapperManager;
    }

    /**
     * Fluent API builder for {@link io.helidon.dbclient.common.DbClientContext}.
     */
    public static final class Builder extends BuilderBase<Builder> implements io.helidon.common.Builder<DbClientContext> {
        @Override
        public DbClientContext build() {
            return new DbClientContext(this);
        }
    }

    /**
     * A common base for builders for classes that want to extend {@link io.helidon.dbclient.common.DbClientContext}.
     *
     * @param <T> type of the builder extending this builder, to keep fluent API
     */
    public static class BuilderBase<T extends BuilderBase<T>> {
        @SuppressWarnings("unchecked")
        private final T me = (T) this;

        private DbMapperManager dbMapperManager;
        private MapperManager mapperManager;
        private List<DbClientService> clientServices;
        private DbStatements statements;

        /**
         * No-op constructor.
         */
        protected BuilderBase() {
        }

        /**
         * Configure the DB mapper manager to use.
         *
         * @param dbMapperManager DB mapper manager
         * @return updated builder instance
         */
        public T dbMapperManager(DbMapperManager dbMapperManager) {
            this.dbMapperManager = dbMapperManager;
            return me;
        }

        /**
         * Configure the mapper manager to use.
         *
         * @param mapperManager mapper manager
         * @return updated builder instance
         */
        public T mapperManager(MapperManager mapperManager) {
            this.mapperManager = mapperManager;
            return me;
        }

        /**
         * Configure the client services to use.
         *
         * @param clientServices client service list
         * @return updated builder instance
         */
        public T clientServices(List<DbClientService> clientServices) {
            this.clientServices = clientServices;
            return me;
        }

        /**
         * Configure the db statements to use.
         *
         * @param statements statements
         * @return updated builder instance
         */
        public T statements(DbStatements statements) {
            this.statements = statements;
            return me;
        }
    }
}
