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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.OptionalHelper;
import io.helidon.common.mapper.MapperException;
import io.helidon.dbclient.spi.DbMapperProvider;

/**
 * Default implementation of the DbMapperManager.
 */
class DbMapperManagerImpl implements DbMapperManager {
    public static final String ERROR_NO_MAPPER_FOUND = "Failed to find DB mapper.";
    private final List<DbMapperProvider> providers;
    private final Map<Class<?>, DbMapper<?>> byClass = new ConcurrentHashMap<>();
    private final Map<GenericType<?>, DbMapper<?>> byType = new ConcurrentHashMap<>();

    DbMapperManagerImpl(Builder builder) {
        this.providers = builder.mapperProviders();
    }

    @Override
    public <T> T read(DbRow row, Class<T> expectedType) {
        return executeMapping(() -> findMapper(expectedType, false)
                                      .read(row),
                              row,
                              TYPE_DB_ROW,
                              GenericType.create(expectedType));
    }

    @Override
    public <T> T read(DbRow row, GenericType<T> expectedType) {
        return executeMapping(() -> findMapper(expectedType, false)
                                      .read(row),
                              row,
                              TYPE_DB_ROW,
                              expectedType);
    }

    @Override
    public <T> Map<String, ?> toNamedParameters(T value, Class<T> valueClass) {
        return executeMapping(() -> findMapper(valueClass, false)
                                      .toNamedParameters(value),
                              value,
                              GenericType.create(valueClass),
                              TYPE_NAMED_PARAMS);
    }

    @Override
    public <T> List<?> toIndexedParameters(T value, Class<T> valueClass) {
        return executeMapping(() -> findMapper(valueClass, false)
                                      .toIndexedParameters(value),
                              value,
                              GenericType.create(valueClass),
                              TYPE_INDEXED_PARAMS);
    }

    private <T> T executeMapping(Supplier<T> mapping, Object source, GenericType<?> sourceType, GenericType<?> targetType) {
        try {
            return mapping.get();
        } catch (MapperException e) {
            throw e;
        } catch (Exception e) {
            throw createMapperException(source, sourceType, targetType, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> DbMapper<T> findMapper(Class<T> type, boolean fromTypes) {
        DbMapper<?> mapper = byClass.computeIfAbsent(type, aClass -> {
            return fromProviders(type)
                    .orElseGet(() -> {
                        GenericType<T> targetType = GenericType.create(type);
                        if (fromTypes) {
                            return notFoundMapper(targetType);
                        }
                        return findMapper(targetType, true);
                    });
        });

        return (DbMapper<T>) mapper;
    }

    @SuppressWarnings("unchecked")
    private <T> DbMapper<T> findMapper(GenericType<T> type, boolean fromClasses) {
        DbMapper<?> mapper = byType.computeIfAbsent(type, aType -> {
            return fromProviders(type)
                    .orElseGet(() -> {
                        if (!fromClasses && type.isClass()) {
                            return findMapper((Class<T>) type.rawType(), true);
                        }
                        return notFoundMapper(type);
                    });
        });

        return (DbMapper<T>) mapper;
    }

    private <T> Optional<DbMapper<T>> fromProviders(Class<T> type) {
        return providers.stream()
                .flatMap(provider -> OptionalHelper.from(provider.mapper(type)).stream())
                .findFirst();
    }

    private <T> Optional<DbMapper<T>> fromProviders(GenericType<T> type) {
        return providers.stream()
                .flatMap(provider -> OptionalHelper.from(provider.mapper(type)).stream())
                .findFirst();
    }

    private RuntimeException createMapperException(Object source,
                                                   GenericType<?> sourceType,
                                                   GenericType<?> targetType,
                                                   Throwable throwable) {

        throw new MapperException(sourceType,
                                  targetType,
                                  "Failed to map source of class '" + source.getClass().getName() + "'",
                                  throwable);
    }

    private static <T> DbMapper<T> notFoundMapper(GenericType<T> type) {
        MapperException rowException = new MapperException(TYPE_DB_ROW, type, ERROR_NO_MAPPER_FOUND);
        MapperException namedParamsException = new MapperException(type, TYPE_NAMED_PARAMS, ERROR_NO_MAPPER_FOUND);
        MapperException indexedParamsException = new MapperException(type, TYPE_INDEXED_PARAMS, ERROR_NO_MAPPER_FOUND);
        return new DbMapper<T>() {
            @Override
            public T read(DbRow row) {
                throw rowException;
            }

            @Override
            public Map<String, ?> toNamedParameters(T value) {
                throw namedParamsException;
            }

            @Override
            public List<?> toIndexedParameters(T value) {
                throw indexedParamsException;
            }
        };
    }
}
