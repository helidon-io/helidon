/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.spi.DbClientBuilder;
import io.helidon.dbclient.spi.DbClientProvider;
import io.helidon.dbclient.spi.DbClientServiceProvider;
import io.helidon.dbclient.spi.DbMapperProvider;

/**
 * Helidon database client.
 */
public interface DbClient extends AutoCloseable {

    /**
     * Qualifier used for mapping using {@link io.helidon.common.mapper.MapperManager#map(Object, Class, Class, String...)}.
     */
    String MAPPING_QUALIFIER = "dbclient";

    /**
     * Type of this database provider (such as jdbc:mysql, mongoDB etc.).
     *
     * @return name of the database provider
     */
    String dbType();

    /**
     * Execute database statements.
     *
     * @return database statements executor
     */
    DbExecute execute();

    /**
     * Execute database statements in transaction.
     * {@link DbTransaction} life-cycle must always be finished
     * with {@link io.helidon.dbclient.DbTransaction#commit()}
     * or {@link io.helidon.dbclient.DbTransaction#rollback()}.
     * Those two methods mentioned above will also release database resources allocated
     * by this transaction.
     *
     * @return transaction to execute database statements
     */
    DbTransaction transaction();

    /**
     * Unwrap database client internals.
     * Only database connection is supported.
     * <p>When {@code java.sql.Connection} is requested for JDBC provider, this connection must be closed
     * by user code using {@code close()} method on returned {@code Connection} instance.
     *
     * @param <C> target class to be unwrapped
     * @param cls target class to be unwrapped
     * @return database client internals matching provided class.
     * @throws UnsupportedOperationException when provided class is not supported
     */
    <C> C unwrap(Class<C> cls);


    /**
     * Closes the DbClient and releases any associated resources.
     */
    @Override
    void close();

    /**
     * Create Helidon database client.
     *
     * @param config name of the configuration node with driver configuration
     * @return database client instance
     */
    static DbClient create(Config config) {
        return builder(config).build();
    }

    /**
     * Create Helidon database client builder.
     * <p>Database driver is loaded as SPI provider which implements the {@link DbClientProvider} interface.
     * First provider on the class path is selected.</p>
     *
     * @return database client builder
     */
    static Builder builder() {
        DbClientProvider provider = DbClientProviderLoader.first();
        if (null == provider) {
            throw new DbClientException(
                    "No DbSource defined on classpath/module path. An implementation of io.helidon.dbclient.spi.DbClientProvider"
                    + " is required  to access a DB");
        }

        return builder(provider);
    }

    /**
     * Create Helidon database client builder using specific SPI provider.
     *
     * @param provider SPI provider to use
     * @return database client builder
     */
    static Builder builder(DbClientProvider provider) {
        return new Builder(provider);
    }

    /**
     * Create Helidon database client builder.
     * <p>Database driver is loaded as SPI provider which implements the {@link DbClientProvider} interface.
     * Provider matching {@code providerName} on the class path is selected.</p>
     *
     * @param providerName SPI provider name
     * @return database handler builder
     * @throws DbClientException when {@code providerName} was not found
     */
    static Builder builder(String providerName) {
        return DbClientProviderLoader.get(providerName)
                .map(DbClient::builder)
                .orElseThrow(() -> new DbClientException(
                        String.format("No DbClientProvider with name \"%s\" was found on the classpath/module path."
                                              + " Available names: %s",
                                      providerName,
                                      Arrays.toString(DbClientProviderLoader.names()))));
    }

    /**
     * Create Helidon database client builder from configuration.
     *
     * @param dbConfig configuration that should contain the key {@code source} that defines the type of this database
     *                 and is used to load appropriate {@link DbClientProvider} from Java Service loader
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
    final class Builder implements io.helidon.common.Builder<Builder, DbClient> {

        // Provider specific DbClient builder.
        private final DbClientBuilder<?> clientBuilder;
        // DbClient configuration
        private Config config = Config.empty();

        private final HelidonServiceLoader.Builder<DbClientServiceProvider> clientServiceProviders = HelidonServiceLoader.builder(
                ServiceLoader.load(DbClientServiceProvider.class));

        /**
         * Create an instance of Helidon database handler builder.
         *
         * @param dbClientProvider provider specific {@link DbClientProvider} instance
         */
        private Builder(DbClientProvider dbClientProvider) {
            this.clientBuilder = dbClientProvider.builder();
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

            return clientBuilder.build();
        }

        /**
         * Use database connection configuration from configuration file.
         *
         * @param config {@link Config} instance with database connection attributes
         * @return database provider builder
         */
        public Builder config(Config config) {
            this.clientBuilder.config(config);
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
            clientBuilder.statements(statements);
            return this;
        }

        /**
         * Database schema mappers provider.
         * Mappers associated with types in this provider will override existing types associations loaded
         * as {@link DbMapperProvider} Java services.
         *
         * @param provider database schema mappers provider to use
         * @return updated builder instance
         */
        public Builder mapperProvider(DbMapperProvider provider) {
            clientBuilder.addMapperProvider(provider);
            return this;
        }

        /**
         * Mapper manager for generic mapping, such as mapping of parameters to expected types.
         *
         * @param manager mapper manager
         * @return updated builder instance
         */
        public Builder mapperManager(MapperManager manager) {
            clientBuilder.mapperManager(manager);
            return this;
        }

        /**
         * Add a client service.
         *
         * @param clientService clientService to apply
         * @return updated builder instance
         */
        public Builder addService(DbClientService clientService) {
            clientBuilder.addService(clientService);
            return this;
        }

        /**
         * Add a client service.
         *
         * @param clientServiceSupplier supplier of client service
         * @return updated builder instance
         */
        public Builder addService(Supplier<? extends DbClientService> clientServiceSupplier) {
            clientBuilder.addService(clientServiceSupplier.get());
            return this;
        }

    }

}
