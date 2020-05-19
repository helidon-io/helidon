/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.reactive.Single;
import io.helidon.common.reactive.Subscribable;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.dbclient.spi.DbClientProvider;
import io.helidon.dbclient.spi.DbClientProviderBuilder;
import io.helidon.dbclient.spi.DbInterceptorProvider;
import io.helidon.dbclient.spi.DbMapperProvider;

/**
 * Helidon database client.
 */
public interface DbClient {
    /**
     * Execute database statements in transaction.
     *
     * @param <T>      statement execution result type
     * @param executor database statement executor, see {@link DbExecute}
     * @return statement execution result
     */
    <U, T extends Subscribable<U>> T inTransaction(Function<DbTransaction, T> executor);

    /**
     * Execute database statement.
     *
     * @param <T>      statement execution result type, MUST be either a {@link io.helidon.common.reactive.Multi}
     *                 or a {@link io.helidon.common.reactive.Single}, as returned by all APIs of DbClient
     * @param executor database statement executor, see {@link DbExecute}
     * @return statement execution result
     */
    <U, T extends Subscribable<U>> T execute(Function<DbExecute, T> executor);

    /**
     * Pings the database, completes when DB is up and ready, completes exceptionally if not.
     *
     * @return stage that completes when the ping finished
     */
    Single<Void> ping();

    /**
     * Type of this database provider (such as jdbc:mysql, mongoDB etc.).
     *
     * @return name of the database provider
     */
    String dbType();

    /**
     * Create Helidon database handler builder.
     *
     * @param config name of the configuration node with driver configuration
     * @return database handler builder
     */
    static DbClient create(Config config) {
        return builder(config).build();
    }

    /**
     * Create Helidon database handler builder.
     * <p>Database driver is loaded as SPI provider which implements {@link io.helidon.dbclient.spi.DbClientProvider} interface.
     * First provider on the class path is selected.</p>
     *
     * @return database handler builder
     */
    static Builder builder() {
        DbClientProvider theSource = DbClientProviderLoader.first();
        if (null == theSource) {
            throw new DbClientException(
                    "No DbSource defined on classpath/module path. An implementation of io.helidon.dbclient.spi.DbSource is "
                            + "required "
                            + "to access a DB");
        }

        return builder(theSource);
    }

    /**
     * Create Helidon database handler builder.
     *
     * @param source database driver
     * @return database handler builder
     */
    static Builder builder(DbClientProvider source) {
        return new Builder(source);
    }

    /**
     * Create Helidon database handler builder.
     * <p>Database driver is loaded as SPI provider which implements {@link io.helidon.dbclient.spi.DbClientProvider} interface.
     * Provider on the class path with matching name is selected.</p>
     *
     * @param dbSource SPI provider name
     * @return database handler builder
     */
    static Builder builder(String dbSource) {
        return DbClientProviderLoader.get(dbSource)
                .map(DbClient::builder)
                .orElseThrow(() -> new DbClientException(
                        "No DbSource defined on classpath/module path for name: "
                                + dbSource
                                + ", available names: " + Arrays.toString(DbClientProviderLoader.names())));
    }

    /**
     * Create a Helidon database handler builder from configuration.
     *
     * @param dbConfig configuration that should contain the key {@code source} that defines the type of this database
     *                 and is used to load appropriate {@link io.helidon.dbclient.spi.DbClientProvider} from Java Service loader
     * @return a builder pre-configured from the provided config
     */
    static Builder builder(Config dbConfig) {
        return dbConfig.get("source")
                .asString()
                // use builder for correct DbSource
                .map(DbClient::builder)
                // or use the default one
                .orElseGet(DbClient::builder)
                .config(dbConfig);
    }

    /**
     * Helidon database handler builder.
     */
    final class Builder implements io.helidon.common.Builder<DbClient> {
        static {
            HelidonFeatures.register(HelidonFlavor.SE, "DbClient");
        }

        private final HelidonServiceLoader.Builder<DbInterceptorProvider> interceptorServices = HelidonServiceLoader.builder(
                ServiceLoader.load(DbInterceptorProvider.class));

        /**
         * Provider specific database handler builder instance.
         */
        private final DbClientProviderBuilder<?> theBuilder;
        private Config config;

        /**
         * Create an instance of Helidon database handler builder.
         *
         * @param dbClientProvider provider specific {@link io.helidon.dbclient.spi.DbClientProvider} instance
         */
        private Builder(DbClientProvider dbClientProvider) {
            this.theBuilder = dbClientProvider.builder();
        }

        /**
         * Build provider specific database handler.
         *
         * @return new database handler instance
         */
        @Override
        public DbClient build() {
            // add interceptors from service loader
            if (null != config) {
                Config interceptors = config.get("interceptors");
                List<DbInterceptorProvider> providers = interceptorServices.build().asList();
                for (DbInterceptorProvider provider : providers) {
                    Config providerConfig = interceptors.get(provider.configKey());
                    if (!providerConfig.exists()) {
                        continue;
                    }
                    // if configured, we want to at least add a global one
                    AtomicBoolean added = new AtomicBoolean(false);
                    Config global = providerConfig.get("global");
                    if (global.exists() && !global.isLeaf()) {
                        // we must iterate through nodes
                        global.asNodeList().ifPresent(configs -> {
                            configs.forEach(globalConfig -> {
                                added.set(true);
                                addInterceptor(provider.create(globalConfig));
                            });
                        });
                    }

                    Config named = providerConfig.get("named");
                    if (named.exists()) {
                        // we must iterate through nodes
                        named.asNodeList().ifPresent(configs -> {
                            configs.forEach(namedConfig -> {
                                ConfigValue<List<String>> names = namedConfig.get("names").asList(String.class);
                                names.ifPresent(nameList -> {
                                    added.set(true);
                                    addInterceptor(provider.create(namedConfig), nameList.toArray(new String[0]));
                                });
                            });
                        });
                    }
                    Config typed = providerConfig.get("typed");
                    if (typed.exists()) {
                        typed.asNodeList().ifPresent(configs -> {
                            configs.forEach(typedConfig -> {
                                ConfigValue<List<String>> types = typedConfig.get("types").asList(String.class);
                                types.ifPresent(typeList -> {
                                    DbStatementType[] typeArray = typeList.stream()
                                            .map(DbStatementType::valueOf)
                                            .toArray(DbStatementType[]::new);

                                    added.set(true);
                                    addInterceptor(provider.create(typedConfig), typeArray);
                                });
                            });
                        });
                    }
                    if (!added.get()) {
                        if (global.exists()) {
                            addInterceptor(provider.create(global));
                        } else {
                            addInterceptor(provider.create(providerConfig));
                        }
                    }
                }
            }

            return theBuilder.build();
        }

        /**
         * Add an interceptor provider.
         * The provider is only used when configuration is used ({@link #config(io.helidon.config.Config)}.
         *
         * @param provider provider to add to the list of loaded providers
         * @return updated builder instance
         */
        public Builder addInterceptorProvider(DbInterceptorProvider provider) {
            this.interceptorServices.addService(provider);
            return this;
        }

        /**
         * Add a global interceptor.
         *
         * A global interceptor is applied to each statement.
         * @param interceptor interceptor to apply
         * @return updated builder instance
         */
        public Builder addInterceptor(DbInterceptor interceptor) {
            theBuilder.addInterceptor(interceptor);
            return this;
        }

        /**
         * Add an interceptor to specific named statements.
         *
         * @param interceptor interceptor to apply
         * @param statementNames names of statements to apply it on
         * @return updated builder instance
         */
        public Builder addInterceptor(DbInterceptor interceptor, String... statementNames) {
            theBuilder.addInterceptor(interceptor, statementNames);
            return this;
        }

        /**
         * Add an interceptor to specific statement types.
         *
         * @param interceptor interceptor to apply
         * @param dbStatementTypes types of statements to apply it on
         * @return updated builder instance
         */
        public Builder addInterceptor(DbInterceptor interceptor, DbStatementType... dbStatementTypes) {
            theBuilder.addInterceptor(interceptor, dbStatementTypes);
            return this;
        }

        /**
         * Use database connection configuration from configuration file.
         *
         * @param config {@link io.helidon.config.Config} instance with database connection attributes
         * @return database provider builder
         */
        public Builder config(Config config) {
            theBuilder.config(config);

            this.config = config;

            return this;
        }

        /**
         * Statements to use either from configuration
         * or manually configured.
         *
         * @param statements Statements to use
         * @return updated builder instance
         */
        public Builder statements(DbStatements statements) {
            theBuilder.statements(statements);
            return this;
        }

        /**
         * Database schema mappers provider.
         * Mappers associated with types in this provider will override existing types associations loaded
         * as {@link io.helidon.dbclient.spi.DbMapperProvider} Java services.
         *
         * @param provider database schema mappers provider to use
         * @return updated builder instance
         */
        public Builder mapperProvider(DbMapperProvider provider) {
            theBuilder.addMapperProvider(provider);
            return this;
        }

        /**
         * Mapper manager for generic mapping, such as mapping of parameters to expected types.
         *
         * @param manager mapper manager
         * @return updated builder instance
         */
        public Builder mapperManager(MapperManager manager) {
            theBuilder.mapperManager(manager);
            return this;
        }
    }

}
