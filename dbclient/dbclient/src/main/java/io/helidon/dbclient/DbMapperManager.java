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
package io.helidon.dbclient;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.dbclient.spi.DbMapperProvider;

/**
 * Mapper manager of all configured {@link io.helidon.dbclient.DbMapper mappers}.
 */
public interface DbMapperManager {
    /**
     * Generic type for the {@link io.helidon.dbclient.DbRow} class.
     */
    GenericType<Object> TYPE_DB_ROW = GenericType.create(DbRow.class);
    /**
     * Generic type for the {@link Map} of String to value pairs for named parameters.
     */
    GenericType<Map<String, ?>> TYPE_NAMED_PARAMS = new GenericType<Map<String, ?>>() { };
    /**
     * Generic type for the {@link List} of indexed parameters.
     */
    GenericType<List<?>> TYPE_INDEXED_PARAMS = new GenericType<List<?>>() { };

    /**
     * Create a fluent API builder to configure the mapper manager.
     *
     * @return a new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new mapper manager from Java Service loader only.
     *
     * @return mapper manager
     */
    static DbMapperManager create() {
        return DbMapperManager.builder().build();
    }

    /**
     * Create a new mapper manager from customized {@link io.helidon.common.serviceloader.HelidonServiceLoader}.
     *
     * @param serviceLoader service loader to use to read all {@link io.helidon.dbclient.spi.DbMapperProvider}
     * @return mapper manager
     */
    static DbMapperManager create(HelidonServiceLoader<DbMapperProvider> serviceLoader) {
        return DbMapperManager.builder()
                .serviceLoader(serviceLoader)
                .build();
    }

    /**
     * Read database row into a typed value.
     *
     * @param row          row from a database
     * @param expectedType class of the response
     * @param <T>          type of the response
     * @return instance with data from the row
     * @throws MapperException in case the mapper was not found
     * @see io.helidon.dbclient.DbRow#as(Class)
     */
    <T> T read(DbRow row, Class<T> expectedType) throws MapperException;

    /**
     * Read database row into a typed value.
     *
     * @param row          row from a database
     * @param expectedType generic type of the response
     * @param <T>          type of the response
     * @return instance with data from the row
     * @throws MapperException in case the mapper was not found
     * @see io.helidon.dbclient.DbRow#as(io.helidon.common.GenericType)
     */
    <T> T read(DbRow row, GenericType<T> expectedType) throws MapperException;

    /**
     * Read object into a map of named parameters.
     *
     * @param value      the typed value
     * @param valueClass type of the value object
     * @param <T>        type of value
     * @return map with the named parameters
     * @see io.helidon.dbclient.DbStatement#namedParam(Object)
     */
    <T> Map<String, ?> toNamedParameters(T value, Class<T> valueClass);

    /**
     * Read object into a list of indexed parameters.
     *
     * @param value      the typed value
     * @param valueClass type of the value object
     * @param <T>        type of value
     * @return list with indexed parameters (in the order expected by statements using this object)
     * @see io.helidon.dbclient.DbStatement#indexedParam(Object)
     */
    <T> List<?> toIndexedParameters(T value, Class<T> valueClass);

    /**
     * Fluent API builder for {@link io.helidon.dbclient.DbMapperManager}.
     */
    final class Builder implements io.helidon.common.Builder<DbMapperManager> {
        private HelidonServiceLoader.Builder<DbMapperProvider> providers = HelidonServiceLoader
                .builder(ServiceLoader.load(DbMapperProvider.class));
        private HelidonServiceLoader<DbMapperProvider> providerLoader;

        private Builder() {
        }

        @Override
        public DbMapperManager build() {
            return new DbMapperManagerImpl(this);
        }

        /**
         * Add a mapper provider.
         *
         * @param provider prioritized provider
         * @return updated builder instance
         */
        public Builder addMapperProvider(DbMapperProvider provider) {
            this.providers.addService(provider);
            return this;
        }

        /**
         * Add a mapper provider with custom priority.
         *
         * @param provider provider
         * @param priority priority to use
         * @return updated builder instance
         * @see io.helidon.common.Prioritized
         * @see javax.annotation.Priority
         */
        public Builder addMapperProvider(DbMapperProvider provider, int priority) {
            this.providers.addService(provider, priority);
            return this;
        }

        // to be used by implementation
        List<DbMapperProvider> mapperProviders() {
            if (null == providerLoader) {
                return providers.build().asList();
            } else {
                return providerLoader.asList();
            }
        }

        private Builder serviceLoader(HelidonServiceLoader<DbMapperProvider> serviceLoader) {
            this.providerLoader = serviceLoader;
            return this;
        }
    }
}
