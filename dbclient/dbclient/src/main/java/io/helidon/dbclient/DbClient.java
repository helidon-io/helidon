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
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.reactive.Single;
import io.helidon.common.reactive.Subscribable;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.dbclient.spi.DbClientProvider;
import io.helidon.dbclient.spi.DbClientProviderBuilder;
import io.helidon.dbclient.spi.DbClientServiceProvider;
import io.helidon.dbclient.spi.DbMapperProvider;

/**
 * Helidon database client.
 */
public interface DbClient {
    /**
     * Execute database statements in transaction.
     *
     * @param <T>      statement execution result type, MUST be either a {@link io.helidon.common.reactive.Multi}
     *                 or a {@link io.helidon.common.reactive.Single}, as returned by all APIs of DbClient.
     * @param <U>      the type provided by the result type
     * @param executor database statement executor, see {@link DbExecute}
     * @return statement execution result
     */
    <U, T extends Subscribable<U>> T inTransaction(Function<DbTransaction, T> executor);

    /**
     * Execute database statement.
     *
     * @param <T>      statement execution result type, MUST be either a {@link io.helidon.common.reactive.Multi}
     *                 or a {@link io.helidon.common.reactive.Single}, as returned by all APIs of DbClient
     * @param <U>      the type provided by the result type
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

        private final HelidonServiceLoader.Builder<DbClientServiceProvider> clientServiceProviders = HelidonServiceLoader.builder(
                ServiceLoader.load(DbClientServiceProvider.class));

        /**
         * Provider specific database handler builder instance.
         */
        private final DbClientProviderBuilder<?> theBuilder;
        private Config config = Config.empty();

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
            // add client services from service loader
            Config servicesConfig = config.get("services");
            List<DbClientServiceProvider> providers = clientServiceProviders.build().asList();
            for (DbClientServiceProvider provider : providers) {
                Config providerConfig = servicesConfig.get(provider.configKey());
                if (!providerConfig.exists()) {
                    // this client service is on classpath, yet there is no configuration for it, so it is ignored
                    continue;
                }

                provider.create(providerConfig)
                    .forEach(this::addService);
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
        public Builder addServiceProvider(DbClientServiceProvider provider) {
            this.clientServiceProviders.addService(provider);
            return this;
        }

        /**
         * Add a client service.
         *
         * @param clientService clientService to apply
         * @return updated builder instance
         */
        public Builder addService(DbClientService clientService) {
            theBuilder.addService(clientService);
            return this;
        }

        /**
         * Add a client service.
         *
         * @param clientServiceSupplier supplier of client service
         * @return updated builder instance
         */
        public Builder addService(Supplier<? extends DbClientService> clientServiceSupplier) {
            theBuilder.addService(clientServiceSupplier.get());
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
