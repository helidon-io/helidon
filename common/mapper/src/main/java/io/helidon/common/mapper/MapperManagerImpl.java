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
package io.helidon.common.mapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.spi.MapperProvider;

/**
 * Implementation of {@link io.helidon.common.mapper.MapperManager}.
 */
final class MapperManagerImpl implements MapperManager {
    private final List<MapperProvider> providers;
    private final Map<ClassCacheKey, Mapper<?, ?>> classCache = new ConcurrentHashMap<>();
    private final Map<GenericCacheKey, Mapper<?, ?>> typeCache = new ConcurrentHashMap<>();

    MapperManagerImpl(Builder builder) {
        this.providers = builder.mapperProviders();
    }

    @Override
    public <SOURCE, TARGET> TARGET map(SOURCE source, GenericType<SOURCE> sourceType, GenericType<TARGET> targetType) {
        try {
            return findMapper(sourceType, targetType, false)
                    .map(source);
        } catch (MapperException e) {
            throw e;
        } catch (Exception e) {
            throw createMapperException(source, sourceType, targetType, e);
        }
    }

    @Override
    public <SOURCE, TARGET> TARGET map(SOURCE source, Class<SOURCE> sourceType, Class<TARGET> targetType) {
        try {
            return findMapper(sourceType, targetType, false)
                    .map(source);
        } catch (MapperException e) {
            throw e;
        } catch (Exception e) {
            throw createMapperException(source, GenericType.create(sourceType), GenericType.create(targetType), e);
        }
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

    @SuppressWarnings("unchecked")
    private <SOURCE, TARGET> Mapper<SOURCE, TARGET> findMapper(Class<SOURCE> sourceType,
                                                               Class<TARGET> targetType,
                                                               boolean fromTypes) {
        Mapper<?, ?> mapper = classCache.computeIfAbsent(new ClassCacheKey(sourceType, targetType), key -> {
            // first attempt to find by classes
            return fromProviders(sourceType, targetType)
                    .orElseGet(() -> {
                        GenericType<SOURCE> sourceGenericType = GenericType.create(sourceType);
                        GenericType<TARGET> targetGenericType = GenericType.create(targetType);
                        if (targetType.isAssignableFrom(sourceType)) {
                            // the same type
                            return it -> it;
                        }
                        if (fromTypes) {
                            return notFoundMapper(sourceGenericType, targetGenericType);
                        }
                        return findMapper(sourceGenericType, targetGenericType, true);
                    });
        });
        return (Mapper<SOURCE, TARGET>) mapper;
    }

    @SuppressWarnings("unchecked")
    private <SOURCE, TARGET> Mapper<SOURCE, TARGET> findMapper(GenericType<SOURCE> sourceType,
                                                               GenericType<TARGET> targetType,
                                                               boolean fromClasses) {
        Mapper<?, ?> mapper = typeCache.computeIfAbsent(new GenericCacheKey(sourceType, targetType), key -> {
            // first attempt to find by types
            return fromProviders(sourceType, targetType)
                    .orElseGet(() -> {
                        // and then by classes (unless we are already called from findMapper(Class, Class)
                        if (!fromClasses && (sourceType.isClass() && targetType.isClass())) {
                            return findMapper((Class<SOURCE>) sourceType.rawType(), (Class<TARGET>) targetType.rawType(), true);
                        }
                        if (sourceType.equals(targetType)) {
                            return it -> it;
                        }
                        return notFoundMapper(sourceType, targetType);
                    });
        });
        return (Mapper<SOURCE, TARGET>) mapper;
    }

    private <SOURCE, TARGET> Optional<Mapper<?, ?>> fromProviders(Class<SOURCE> sourceType,
                                                                            Class<TARGET> targetType) {
        return providers.stream()
                .flatMap(provider -> provider.mapper(sourceType, targetType).stream())
                .findFirst();
    }

    private <SOURCE, TARGET> Optional<Mapper<?, ?>> fromProviders(GenericType<SOURCE> sourceType,
                                                                            GenericType<TARGET> targetType) {
        return providers.stream()
                .flatMap(provider -> provider.mapper(sourceType, targetType).stream())
                .findFirst();
    }

    private static <SOURCE, TARGET> Mapper<SOURCE, TARGET> notFoundMapper(GenericType<SOURCE> sourceType,
                                                                          GenericType<TARGET> targetType) {
        return source -> {
            throw new MapperException(sourceType, targetType, "Failed to find mapper.");
        };
    }

    private static final class GenericCacheKey {
        private final GenericType<?> sourceType;
        private final GenericType<?> targetType;

        private GenericCacheKey(GenericType<?> sourceType, GenericType<?> targetType) {
            this.sourceType = sourceType;
            this.targetType = targetType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof GenericCacheKey)) {
                return false;
            }
            GenericCacheKey that = (GenericCacheKey) o;
            return sourceType.equals(that.sourceType)
                    && targetType.equals(that.targetType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceType, targetType);
        }
    }

    private static final class ClassCacheKey {
        private final Class<?> sourceType;
        private final Class<?> targetType;

        private ClassCacheKey(Class<?> sourceType, Class<?> targetType) {
            this.sourceType = sourceType;
            this.targetType = targetType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ClassCacheKey)) {
                return false;
            }
            ClassCacheKey that = (ClassCacheKey) o;
            return sourceType.equals(that.sourceType)
                    && targetType.equals(that.targetType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceType, targetType);
        }
    }
}
